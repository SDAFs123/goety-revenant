package com.habaiwa.goetyrevenant.client.overlay;

import com.habaiwa.goetyrevenant.client.state.BodyClientState;
import com.habaiwa.goetyrevenant.client.state.ClientShenShi;
import com.habaiwa.goetyrevenant.common.embed.OrganType;
import com.habaiwa.goetyrevenant.common.network.BodyStateMessage;
import com.habaiwa.goetyrevenant.common.network.ShenShiSyncMessage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * **神识的画面**。
 *
 * <h3>五层表现</h3>
 * <table border="1">
 *   <tr><th>层</th><th>画什么</th><th>什么时候</th></tr>
 *   <tr><td><b>扩散波纹</b></td><td>从屏幕中心向外推开几圈半透明的涟漪</td><td>主动扫视（N）时</td></tr>
 *   <tr><td><b>穿墙虚影 + 描边</b></td><td>每个被感知到的目标，在它**真实位置**画一个方框准星（投影到屏幕坐标，所以墙挡不住）</td><td>常驻</td></tr>
 *   <tr><td><b>常驻光点</b></td><td>不在视野里的目标 → 屏幕边缘一圈光点指示方位（雷达）</td><td>常驻</td></tr>
 *   <tr><td><b>感知照亮</b></td><td>扫视瞬间一层惨白闪光（配合服务端给的短夜视）</td><td>主动扫视时</td></tr>
 *   <tr><td><b>血色暗角</b></td><td>灵魂能量越低，四周血色越浓</td><td>能量 <45% 时</td></tr>
 * </table>
 *
 * <h3>为什么"穿墙"是免费的</h3>
 * <p>这些标记画在 <b>GUI 层</b>（`IGuiOverlay`），而 GUI 渲染**根本没有深度测试** ——
 * 所以只要把世界坐标投影成屏幕坐标再画上去，它天然就是"穿墙"的，
 * 不需要自己搞 RenderType、也不需要改原版实体的发光标记。</p>
 *
 * <p>投影用的是游戏自己的 `GameRenderer#getProjectionMatrix` 加相机矩阵，
 * 所以视野、缩放、设备缩放都跟着走，不会飘。</p>
 */
public class ShenShiOverlay implements IGuiOverlay {
    /** 波纹持续多久。 */
    private static final long PULSE_MS = 1100L;
    /** 波纹圈数。 */
    private static final int PULSE_RINGS = 3;
    /** 血点尺寸与间距。 */
    private static final int PIP = 7;
    private static final int GAP = 5;
    private static final float LOW_THRESHOLD = 0.45F;
    /** 灵魂能量低于这个比例才预警「魂魄将散」（仅灵魂状态下）。 */
    private static final float LOW_ENERGY_WARN = 0.15F;
    /** 目标离得多远时，边缘光点贴到最外圈。 */
    private static final double RADAR_FAR = 40.0D;

    private static int lastOpened = -1;
    private static int lastEmbedded = -1;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        BodyStateMessage state = BodyClientState.get();
        if (state == null || !state.unlocked()) {
            return;
        }
        // 血角 / 血点 / 预警这些「感知层」只在**灵体状态**或**神识开启**时显示。
        // 活人的灵魂能量本来就是 0（没方舟、没图腾）—— 拿 0/上限 去比，
        // 等于天天给活人扣一顶「枯竭」的帽子（跳进哭泣之井解锁后尤其明显）。
        boolean soulOrSense = com.habaiwa.goetyrevenant.client.state.ClientSoulState.isSoul()
                || ClientShenShi.active();
        if (!soulOrSense) {
            return;
        }

        renderBloodVignette(graphics, width, height, state);
        renderPips(graphics, width, height, state);
        detectChanges(minecraft, state);

        if (!ClientShenShi.active()) {
            return;
        }
        renderPulse(graphics, width, height);
        renderContacts(graphics, minecraft, width, height, partialTick);
    }

    // ===== 扩散波纹 + 感知照亮 =====

    private void renderPulse(GuiGraphics graphics, int width, int height) {
        long age = ClientShenShi.pulseAgeMs();
        if (age < 0 || age > PULSE_MS) {
            return;
        }
        float progress = age / (float) PULSE_MS;

        // 「感知照亮周围」：一层惨白闪光，很快褪掉
        int flash = (int) (0x38 * (1.0F - progress) * (1.0F - progress));
        if (flash > 0) {
            graphics.fill(0, 0, width, height, (flash << 24) | 0xBFE8FF);
        }

        // 扩散波纹：几圈同心涟漪
        int cx = width / 2;
        int cy = height / 2;
        int maxR = (int) (Math.hypot(width, height) * 0.5D);
        for (int ring = 0; ring < PULSE_RINGS; ring++) {
            float local = progress - ring * 0.16F;
            if (local <= 0.0F || local >= 1.0F) {
                continue;
            }
            int r = (int) (maxR * local);
            int alpha = (int) (0x6A * (1.0F - local));
            if (alpha <= 0) {
                continue;
            }
            ringDisc(graphics, cx, cy, r, (alpha << 24) | 0x9FD8FF);
        }
    }

    // ===== 穿墙虚影 / 描边 / 常驻光点 =====

    private void renderContacts(GuiGraphics graphics, Minecraft minecraft, int width, int height,
                                float partialTick) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        // 世界 → 相机 → 裁剪：和游戏自己渲染实体用的是同一套矩阵
        Matrix4f modelView = new Matrix4f()
                .rotate(camera.rotation())
                .translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
        Matrix4f projection = minecraft.gameRenderer.getProjectionMatrix(partialTick);

        for (ShenShiSyncMessage.Contact contact : ClientShenShi.contacts()) {
            int color = colorOf(contact.kind());
            double[] screen = project(contact, modelView, projection, width, height);
            if (screen != null) {
                // 在视野里：**方框准星**（描边）+ 中心一个虚影点
                bracket(graphics, (int) screen[0], (int) screen[1], color);
                float pulse = (float) (Math.sin(System.currentTimeMillis() % 1000L / 1000.0D * Math.PI * 2.0D)
                        * 0.5D + 0.5D);
                int r = 2 + Math.round(pulse);
                graphics.fill((int) screen[0] - r, (int) screen[1] - r,
                        (int) screen[0] + r, (int) screen[1] + r, color);
                // 自己的躯壳额外给一圈金环
                if (contact.kind() == ShenShiSyncMessage.KIND_OWN_CORPSE) {
                    ringDisc(graphics, (int) screen[0], (int) screen[1], 10 + r, 0xFFFFD24A);
                }
            } else {
                // 不在视野里：**屏幕边缘光点**（雷达），方位准、距离用半径表达
                radarDot(graphics, minecraft, camera, contact, width, height, color);
            }
        }
    }

    /**
     * 世界坐标 → 屏幕坐标。
     *
     * @return `{x, y}`，在相机背后 / 超出裁剪范围时返回 null（交给雷达画）
     */
    private static double[] project(ShenShiSyncMessage.Contact contact, Matrix4f modelView,
                                    Matrix4f projection, int width, int height) {
        Vector4f v = new Vector4f((float) contact.x(), (float) contact.y(), (float) contact.z(), 1.0F);
        // JOML 里向量乘矩阵叫 mul（不是 transform）
        v.mul(modelView);
        v.mul(projection);
        if (v.w() <= 0.0001F) {
            return null;
        }
        float ndcX = v.x() / v.w();
        float ndcY = v.y() / v.w();
        if (ndcX < -1.4F || ndcX > 1.4F || ndcY < -1.4F || ndcY > 1.4F) {
            return null;
        }
        double sx = (ndcX * 0.5D + 0.5D) * width;
        double sy = (1.0D - (ndcY * 0.5D + 0.5D)) * height;
        return new double[]{sx, sy};
    }

    /** 屏幕边缘的雷达光点：角度 = 目标相对视角的方位，半径 = 距离。 */
    private void radarDot(GuiGraphics graphics, Minecraft minecraft, Camera camera,
                          ShenShiSyncMessage.Contact contact, int width, int height, int color) {
        Vec3 cam = camera.getPosition();
        double dx = contact.x() - cam.x;
        double dz = contact.z() - cam.z;
        double distance = Math.sqrt(dx * dx + dz * dz + (contact.y() - cam.y) * (contact.y() - cam.y));

        // 目标的世界方位角 − 玩家视角 yaw（和寻尸箭头同一套约定，已经验证过）
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Math.toRadians(bearing - minecraft.player.getYRot());

        int cx = width / 2;
        int cy = height / 2;
        double base = Math.min(width, height) * 0.36D;
        double extra = Math.min(1.0D, distance / RADAR_FAR) * Math.min(width, height) * 0.09D;
        double radius = base + extra;

        int x = (int) (cx + Math.sin(relative) * radius);
        int y = (int) (cy - Math.cos(relative) * radius);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
        // 自己的躯壳给个大一点的金点
        if (contact.kind() == ShenShiSyncMessage.KIND_OWN_CORPSE) {
            ringDisc(graphics, x, y, 6, 0xFFFFD24A);
        }
    }

    // ===== 血色暗角 / 七窍血点（原样保留）=====

    private void renderBloodVignette(GuiGraphics graphics, int width, int height, BodyStateMessage state) {
        int max = Math.max(1, state.energyMax());
        float ratio = Math.max(0.0F, Math.min(1.0F, state.soulEnergy() / (float) max));
        if (ratio >= LOW_THRESHOLD) {
            return;
        }
        float severity = 1.0F - ratio / LOW_THRESHOLD;
        int band = (int) (Math.min(width, height) * 0.16F * severity) + 1;
        int baseAlpha = (int) (0x90 * severity);
        for (int i = 0; i < band; i += 2) {
            int alpha = (int) (baseAlpha * (1.0F - i / (float) band));
            if (alpha <= 0) {
                continue;
            }
            int color = (alpha << 24) | 0x8A0000;
            graphics.fill(i, 0, i + 2, height, color);
            graphics.fill(width - i - 2, 0, width - i, height, color);
            graphics.fill(0, i, width, i + 2, color);
            graphics.fill(0, height - i - 2, width, height - i, color);
        }
    }

    private void renderPips(GuiGraphics graphics, int width, int height, BodyStateMessage state) {
        OrganType[] organs = OrganType.values();
        int total = organs.length * PIP + (organs.length - 1) * GAP;
        int x = width / 2 - total / 2;
        int y = height - 62;

        for (OrganType organ : organs) {
            BodyStateMessage.Entry entry = entryOf(state, organ);
            boolean locked = entry == null || entry.locked();
            boolean embedded = entry != null && entry.hasFocus();
            int color = locked ? 0xFF3A3038
                    : embedded ? (0xFF000000 | organ.brewColor())
                    : 0xFF6B1E22;
            graphics.fill(x - 1, y - 1, x + PIP + 1, y + PIP + 1, 0xA0000000);
            if (locked) {
                graphics.fill(x, y, x + PIP, y + PIP, color);
            } else if (embedded) {
                fillDisc(graphics, x + PIP / 2, y + PIP / 2, PIP / 2 + 1, color);
            } else {
                ringDisc(graphics, x + PIP / 2, y + PIP / 2, PIP / 2 + 1, color);
            }
            x += PIP + GAP;
        }

        // 「魂魄将散」不该是个常驻横幅 —— 只在**真的在烧灵魂能量**的时候才提醒：
        //   · 神识开着（每秒扣灵魂能量），或
        //   · 人正处于灵魂游离状态
        // 而且必须有真实的上限数据（原来 Math.max(1, 0) 会把"没有数据"当成"满格"，
        // 于是活人 0/1 = 0 直接触发误报）。
        int max = state.energyMax();
        if ((ClientShenShi.active() || com.habaiwa.goetyrevenant.client.state.ClientSoulState.isSoul())
                && max > 0
                && state.soulEnergy() / (float) max < LOW_ENERGY_WARN) {
            graphics.drawCenteredString(Minecraft.getInstance().font,
                    Component.translatable("hud.goety_revenant.shenshi_failing"),
                    width / 2, y - 12, 0xFFFF6B5A);
        }
    }

    private void detectChanges(Minecraft minecraft, BodyStateMessage state) {
        int opened = 0;
        int embedded = 0;
        for (BodyStateMessage.Entry entry : state.entries()) {
            if (entry.opened()) {
                opened++;
            }
            if (entry.hasFocus()) {
                embedded++;
            }
        }
        if (lastOpened >= 0 && opened > lastOpened) {
            minecraft.player.playSound(SoundEvents.WITHER_HURT, 0.6F, 0.55F);
        }
        if (lastEmbedded >= 0 && embedded > lastEmbedded) {
            minecraft.player.playSound(SoundEvents.PLAYER_HURT, 0.35F, 0.6F);
        }
        lastOpened = opened;
        lastEmbedded = embedded;
    }

    // ===== 画法小工具 =====

    private static int colorOf(byte kind) {
        return switch (kind) {
            case ShenShiSyncMessage.KIND_OWN_CORPSE -> 0xFFFFD24A;   // 自己的躯壳：金
            case ShenShiSyncMessage.KIND_CORPSE -> 0xFF8B2A2A;       // 别的尸体：暗红
            case ShenShiSyncMessage.KIND_PLAYER -> 0xFFE8E8E8;       // 玩家：白
            case ShenShiSyncMessage.KIND_FOCUS -> 0xFF4BD4E8;        // 地上的聚晶：青
            default -> 0xFFE23A3A;                                    // 活物：血红
        };
    }

    /** 方框准星 —— 比一个点更像「被神识锁定」。 */
    private static void bracket(GuiGraphics graphics, int x, int y, int color) {
        int s = 7;
        int t = 1;
        graphics.fill(x - s, y - s, x - s + t + 2, y + s, color);
        graphics.fill(x + s - 2, y - s, x + s, y + s, color);
        graphics.fill(x - s, y - s, x + s, y - s + t + 2, color);
        graphics.fill(x - s, y + s - 2, x + s, y + s, color);
    }

    private static void fillDisc(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    private static void ringDisc(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int inner = (int) Math.sqrt(Math.max(0, (r - 2) * (r - 2) - dy * dy));
            if (outer > inner) {
                graphics.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
                graphics.fill(cx + inner, cy + dy, cx + outer, cy + dy + 1, color);
            }
        }
    }

    private static BodyStateMessage.Entry entryOf(BodyStateMessage state, OrganType organ) {
        for (BodyStateMessage.Entry entry : state.entries()) {
            if (entry.organ().equals(organ.getId())) {
                return entry;
            }
        }
        return null;
    }
}
