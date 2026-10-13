package com.habaiwa.goetyrevenant.client.overlay;

import com.habaiwa.goetyrevenant.client.ClientSoulState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 灵魂 HUD —— **完全不用文字**，全部靠图形表达。
 *
 * <h3>位置：本体灵魂能量条的位置</h3>
 * <p>诡厄巫法本体的灵魂能量条由 {@code goety.toml} 的 `soulGuiHorizontal / soulGuiVertical` 定位，
 * 我们在灵魂状态下会把它**藏掉**（那个数是你身体里的，跟你没关系），
 * 然后**在同一块地方**用**同一张贴图**画**你灵魂自己的**能量 —— 位置不跳、外观不变，
 * 变的只是「这个数是谁的」。</p>
 *
 * <h3>只显示两样东西</h3>
 * <table border="1">
 *   <tr><th>元素</th><th>含义</th></tr>
 *   <tr><td>能量条（本体贴图）</td><td>**灵魂自己的灵魂能量** / 上限（上限含图腾与灵魂方舟的加成）</td></tr>
 *   <tr><td>上方的指针</td><td>身体在哪个方向（跟着视角实时转）</td></tr>
 * </table>
 *
 * <p><b>契合度、剩余时间、境界那些一律不画</b> —— 灵魂身上只有「灵魂能量」这一个数，
 * 别的都是身体的事。</p>
 *
 * <p>指针的三种形态：</p>
 * <ul>
 *   <li><b>指针</b> —— 身体在附近：按「方位角 − 视角朝向」转，颜色按距离从红到绿</li>
 *   <li><b>脉冲圆环</b> —— 身体就在 6 米内：不用指了，直接画个会呼吸的圈</li>
 *   <li><b>紫色方块</b> —— 身体在**另一个维度**：没有方向可言，只告诉你「在那边」</li>
 * </ul>
 *
 * <p>整块 HUD 只在**自己是灵魂**时才画 —— 别人看不到你的指针，你也看不到别人的。</p>
 */
public class SoulHudOverlay implements IGuiOverlay {
    /** 本体（诡厄巫法）的能量条贴图，直接复用，外观才连得上。 */
    private static final ResourceLocation BAR_TEXTURE =
            new ResourceLocation("goety", "textures/gui/soul_energy_bar.png");
    private static final int TEX_WIDTH = 234;
    private static final int TEX_HEIGHT = 5;

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    /** 距右边缘 / 底边的留白。 */
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_BOTTOM = 40;
    /** 指针离能量条多远。 */
    private static final int NEEDLE_OFFSET = 26;
    private static final int NEEDLE_LENGTH = 11;

    private static final int COLOR_BACKDROP = 0x90100818;
    private static final int COLOR_OTHER_DIM = 0xFF9B6BE0;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        // ------------------------------------------------------------------
        //  **灵魂状态下无论如何都要画** —— 那条能量就是灵魂的血。
        //
        //  解锁之后再扩展到"活人也画"（它要随灵魂温度变色，本体那张固定贴图做不到）。
        //  之前只有一个 unlocked 条件，于是没跳井的玩家变成灵魂后看不到能量条 ——
        //  而灵魂状态恰恰是最需要看它的时候（那就是唯一的血条）。
        // ------------------------------------------------------------------
        // ------------------------------------------------------------------
        //  **只在灵魂状态下画这条能量条**（设计 §1284 原文：
        //  「整块 HUD 只在 ClientSoulState.isSoul() 时画 —— 而那个标记是
        //    服务端单独发给你自己的」）。
        //
        //  这里踩过一次坑：条件原本写的是 if (!unlocked) return;
        //  —— `unlocked` 是**七窍系统**的"跳哭泣之井解锁"（§391），和 HUD 无关。
        //  后果是：灵魂状态 + 没跳井 → `SoulClientEvents` 把本体那条藏掉了、
        //  我们这边又不画 → 右下角**什么都看不到**，而那条能量正是灵魂唯一的血。
        //
        //  （另：附身时会挨打扣灵魂能量，但设计没说附身时画不画。
        //    这一版先严格按设计走 —— 只在灵魂状态画。）
        // ------------------------------------------------------------------
        if (!ClientSoulState.isSoul()) {
            return;
        }

        int x = width - BAR_WIDTH - MARGIN_RIGHT;
        int y = height - MARGIN_BOTTOM;

        // 指向身体的指针（这个类只在灵魂状态下会被调用）
        renderNeedle(graphics, minecraft, x + BAR_WIDTH / 2, y - NEEDLE_OFFSET);
        renderEnergy(graphics, x, y);
    }

    /**
     * 主能量条：用本体的贴图，按比例裁切（和本体一样的画法）。
     *
     * <p>分母是服务端算好的**真实上限**（基础量 + 图腾 / 灵魂方舟），
     * 所以戴了图腾、绑了方舟之后条会变得「更长」—— 这正是本体那套算法。</p>
     */
    private void renderEnergy(GuiGraphics graphics, int x, int y) {
        int energy = ClientSoulState.energy();
        float ratio = Math.max(0.0F, Math.min(1.0F, energy / (float) ClientSoulState.energyMax()));
        int fill = Math.round(BAR_WIDTH * ratio);

        // 底衬：不然在亮背景（雪地/天空）上看不清
        graphics.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, COLOR_BACKDROP);

        // **条的颜色 = 灵魂温度**：低温偏蓝、温是本色、高温偏橙红。
        // 温度不单独占一块 UI，它长在这条能量条上。
        int temp = com.habaiwa.goetyrevenant.common.config.RevenantConfig.hudTemperature
                ? com.habaiwa.goetyrevenant.client.BodyClientState.temperature()
                : 50;   // 关掉开关 = 按"温"算 = 贴图本色
        float[] tint = tintOf(temp);
        graphics.setColor(tint[0], tint[1], tint[2], 1.0F);
        try {
            graphics.blit(BAR_TEXTURE, x, y, 0.0F, 0.0F, BAR_WIDTH, BAR_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
            if (fill > 0) {
                graphics.blit(BAR_TEXTURE, x, y, 0.0F, 0.0F, fill, BAR_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
            }
        } finally {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * **灵魂温度 → 能量条的染色**（1.0 表示不变色）。
     *
     * <p>低温偏蓝（0.35, 0.72, 0.94）；温是贴图本色；高温偏橙红（0.94, 0.41, 0.16）。
     * 温度不单独占一块 UI —— 它是长在这条能量条上的。</p>
     */
    private static float[] tintOf(int temp) {
        float t = Math.max(0.0F, Math.min(100.0F, temp)) / 100.0F;
        if (t < 0.5F) {
            float k = t * 2.0F;
            return new float[]{0.35F + 0.65F * k, 0.72F + 0.28F * k, 0.94F + 0.06F * k};
        }
        float k = (t - 0.5F) * 2.0F;
        return new float[]{1.0F - 0.06F * k, 1.0F - 0.59F * k, 1.0F - 0.84F * k};
    }

    /**
     * 指向身体的**指针**。
     *
     * <p>旋转量 = `身体方位角 − 玩家视角 yaw`，所以在 GUI 的 Y 轴朝下的坐标系里
     * 正角度看起来是顺时针 —— 目标在右就顺时针转、在左就逆时针转，符合直觉。</p>
     */
    private void renderNeedle(GuiGraphics graphics, Minecraft minecraft, int cx, int cy) {
        if (!ClientSoulState.hasBody()) {
            return;
        }
        // 身体在别的维度：没有方向，画个紫色方块表示「在那边」
        if (ClientSoulState.bodyOtherDimension()) {
            graphics.fill(cx - 4, cy - 4, cx + 4, cy + 4, COLOR_OTHER_DIM);
            graphics.fill(cx - 6, cy - 6, cx + 6, cy + 6, 0x609B6BE0);
            return;
        }
        // 身体就在眼前：不用指了，画个会呼吸的圈
        if (ClientSoulState.bodyIsHere()) {
            float pulse = (float) (Math.sin((System.currentTimeMillis() % 1200L) / 1200.0D * Math.PI * 2.0D)
                    * 0.5D + 0.5D);
            int r = 5 + Math.round(pulse * 3.0F);
            ring(graphics, cx, cy, r, 0xFFB9F0C0);
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFEFFFEF);
            return;
        }

        float relative = ClientSoulState.bodyBearing() - minecraft.player.getYRot();
        int color = distanceColor(ClientSoulState.bodyDistance());

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(relative));
        // 针杆：从圆心往外
        graphics.fill(-1, -NEEDLE_LENGTH, 1, 1, color);
        // 针尖加粗一点，方向更好认
        graphics.fill(-2, -NEEDLE_LENGTH, 2, -NEEDLE_LENGTH + 4, color);
        // 针尾：深色小方块，做视觉配重
        graphics.fill(-2, 1, 2, 4, 0x80000000);
        pose.popPose();

        // 中心点
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
    }

    /** 距离越近越绿，越远越红。 */
    private static int distanceColor(int distance) {
        if (distance <= 16) {
            return 0xFF7CE38B;
        }
        if (distance <= 64) {
            return 0xFFFFD24A;
        }
        return 0xFFFF7A6B;
    }

    /** 画一个空心圆（脉冲圈 / 瞄准圈）。 */
    private static void ring(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            graphics.fill(cx - half, cy + dy, cx - half + 1, cy + dy + 1, color);
            graphics.fill(cx + half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
        for (int dx = -r; dx <= r; dx++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - dx * dx));
            graphics.fill(cx + dx, cy - half, cx + dx + 1, cy - half + 1, color);
            graphics.fill(cx + dx, cy + half, cx + dx + 1, cy + half + 1, color);
        }
    }
}
