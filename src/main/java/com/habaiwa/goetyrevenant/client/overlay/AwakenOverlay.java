package com.habaiwa.goetyrevenant.client.overlay;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.client.QuizClientState;
import com.habaiwa.goetyrevenant.common.sequence.Sequence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.common.Mod;

/**
 * **觉醒那一下** —— 被序列看中的瞬间，名字在屏幕正中亮起来，几秒后散掉。
 *
 * <h3>三段</h3>
 * <pre>
 *   0 ~ 450ms      出现：字从 0.55 倍涨到 1 倍、碎屑从外收拢、光环向外铺开
 *   450 ~ 3300ms   停留：轻轻呼吸
 *   3300 ~ 4200ms  消失：字慢慢涨大淡出、碎屑向外散开
 * </pre>
 *
 * <p>颜色当然是被看中那条序列的颜色。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, value = Dist.CLIENT)
public final class AwakenOverlay implements IGuiOverlay {
    private static final long IN_MS = 450L;
    private static final long STAY_END_MS = 3300L;

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics graphics,
                       float partialTick, int width, int height) {
        if (!QuizClientState.awakening()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        long elapsed = QuizClientState.awakenElapsed();
        Sequence sequence = Sequence.byCn(QuizClientState.awakenedName());
        int color = sequence == null ? 0xFF8AA8A8 : sequence.color();

        int cx = width / 2;
        int cy = height / 2;

        // 三段进度
        float appear = Math.min(1.0F, elapsed / (float) IN_MS);
        appear = 1.0F - (1.0F - appear) * (1.0F - appear);            // 缓出
        float fade = elapsed <= STAY_END_MS ? 1.0F
                : Math.max(0.0F, 1.0F - (elapsed - STAY_END_MS)
                        / (float) (QuizClientState.AWAKEN_TOTAL_MS - STAY_END_MS));

        this.motes(graphics, cx, cy, color, appear, fade);

        // 名字（带缩放）
        var pose = graphics.pose();
        pose.pushPose();
        float scale = 0.55F + 0.45F * appear + (1.0F - fade) * 0.35F;
        float breathe = 1.0F + (float) Math.sin(elapsed / 380.0D) * 0.015F;
        // 原点就放在屏幕正中 —— 之前在这里多减了 6px，整个框跟着偏上
        pose.translate(cx, cy, 0.0F);
        pose.scale(scale * breathe, scale * breathe, 1.0F);

        String name = QuizClientState.awakenedName();
        int nameColor = (int) (Math.min(1.0F, appear * fade) * 255.0F) << 24 | (color & 0xFFFFFF);
        // 名字用**该序列自己的笔法**写（烛守窜、追影抖、回声拖残影…）
        int seqIndex = sequence == null ? 0 : sequence.ordinal();
        // **不写"被忽视"那类字** —— 失去是内核，不是该念给玩家听的东西。
        // 屏幕上只留"是谁看中了你"。
        // **一个框，把这两个字装起来。**
        // 名字自己飘着太散；加一圈序列色的光边 + 四角折角，它才像"授予"出来的东西。
        int tw = minecraft.font.width(name);
        float bw = tw + 46.0F;
        float bh = 30.0F;
        float bx = -bw / 2.0F;
        float by = -bh / 2.0F;
        com.habaiwa.goetyrevenant.client.fx.GlowRenderer.glowOutline(
                graphics, bx, by, bw, bh, color & 0xFFFFFF, (int) ((110 + 90 * appear) * fade));
        // 四角折角（让框看起来是"有接口的"）
        float c = 7.0F;
        int ca = (int) (200 * appear * fade);
        for (int[] q : new int[][]{{0, 0, 1, 1}, {1, 0, -1, 1}, {0, 1, 1, -1}, {1, 1, -1, -1}}) {
            float ox = q[0] == 0 ? bx : bx + bw;
            float oy = q[1] == 0 ? by : by + bh;
            com.habaiwa.goetyrevenant.client.fx.GlowRenderer.glowBox(graphics,
                    Math.min(ox, ox + q[2] * c), oy - (q[3] < 0 ? 1.4F : 0),
                    Math.max(ox, ox + q[2] * c), oy + (q[3] > 0 ? 1.4F : 0), color & 0xFFFFFF, ca);
            com.habaiwa.goetyrevenant.client.fx.GlowRenderer.glowBox(graphics,
                    ox - (q[2] < 0 ? 1.4F : 0), Math.min(oy, oy + q[3] * c),
                    ox + (q[2] > 0 ? 1.4F : 0), Math.max(oy, oy + q[3] * c), color & 0xFFFFFF, ca);
        }
        // **框里的名字** —— 用该序列自己的笔法写（烛守窜、追影抖、回声拖残影…）
        com.habaiwa.goetyrevenant.client.fx.TextFx.sequenceName(graphics, minecraft.font,
                name, 0, -4, seqIndex, elapsed, Math.min(1.0F, appear * fade));
        pose.popPose();
    }

    /** 碎屑：出现时从外收拢，消失时向外散开。 */
    private void motes(GuiGraphics graphics, int cx, int cy, int color, float appear, float fade) {
        float spread = (1.0F - appear) + (1.0F - fade);
        if (spread <= 0.01F) {
            return;
        }
        for (int i = 0; i < 72; i++) {
            double ang = i * 2.399963D;
            float dist = spread * (70.0F + (i % 13) * 22.0F);
            int px = cx + (int) (Math.cos(ang) * dist);
            int py = cy + (int) (Math.sin(ang) * dist * 0.5F);
            int alpha = (int) (Math.min(1.0F, spread) * 200 * Math.max(0.25F, fade));
            if (alpha <= 4) {
                continue;
            }
            int r = 1 + (i % 3);
            graphics.fill(px - r, py - r, px + r + 1, py + r + 1,
                    (alpha << 24) | (color & 0xFFFFFF));
        }
    }

}
