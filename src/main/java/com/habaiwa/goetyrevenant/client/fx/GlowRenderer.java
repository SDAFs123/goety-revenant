package com.habaiwa.goetyrevenant.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * **会发光的绘制** —— 加法混合（additive）那一路。
 *
 * <h3>为什么不用普通的 fill</h3>
 * <p>普通 {@code fill} 是 <b>alpha 混合</b>：在暗底上叠几层同色，结果是"越叠越灰"，
 * 永远亮不起来。而全息、魂火、血光这些东西之所以"亮"，靠的是<b>加法混合</b> ——
 * 把颜色<b>加到</b>画面上去，叠得越多越白，中间自然过曝。
 * 这也是参考那个模组里 {@code blit} 的做法（它把混合函数换成了 {@code SRC_ALPHA, ONE}）。</p>
 *
 * <h3>用法约定</h3>
 * <p>每个方法内部自己开混合、自己关，<b>退出时恢复默认混合</b>，
 * 所以外面可以随便穿插普通绘制，不会把后面的东西染坏。</p>
 */
public final class GlowRenderer {
    private GlowRenderer() {
    }

    /** 加法混合下的一层光（从中心往外衰减的一层）。 */
    public static void glowBox(GuiGraphics graphics, float x0, float y0, float x1, float y1,
                               int rgb, int alpha) {
        if (alpha <= 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        Matrix4f mat = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        buf.vertex(mat, x0, y1, 0.0F).color(r, g, b, alpha / 255.0F).endVertex();
        buf.vertex(mat, x1, y1, 0.0F).color(r, g, b, alpha / 255.0F).endVertex();
        buf.vertex(mat, x1, y0, 0.0F).color(r, g, b, alpha / 255.0F).endVertex();
        buf.vertex(mat, x0, y0, 0.0F).color(r, g, b, alpha / 255.0F).endVertex();
        Tesselator.getInstance().end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * **中心亮、边缘淡**的一团光（用几层同心矩形逼近）。
     *
     * <p>加法混合下叠出来的效果就是"过曝的核心 + 柔和的光晕"，
     * 比普通半透明色块好看得多。</p>
     */
    public static void halo(GuiGraphics graphics, float cx, float cy, float radiusX, float radiusY,
                            int rgb, int layers, int peakAlpha) {
        for (int i = layers; i >= 1; i--) {
            float k = i / (float) layers;
            int a = (int) (peakAlpha * Math.pow(1.0F - k + 0.15F, 1.6F));
            glowBox(graphics, cx - radiusX * k, cy - radiusY * k,
                    cx + radiusX * k, cy + radiusY * k, rgb, Math.min(255, a));
        }
    }

    /** 发光的边框：外圈一层光晕 + 四条亮线。 */
    public static void glowOutline(GuiGraphics graphics, float x, float y, float w, float h,
                                   int rgb, int strength) {
        // 外光晕（往外扩一层，很淡）
        halo(graphics, x + w / 2.0F, y + h / 2.0F, w / 2.0F + 3.0F, h / 2.0F + 3.0F,
                rgb, 3, Math.max(10, strength / 3));
        // 四条边
        float t = 1.2F;
        glowBox(graphics, x, y, x + w, y + t, rgb, strength);
        glowBox(graphics, x, y + h - t, x + w, y + h, rgb, strength);
        glowBox(graphics, x, y, x + t, y + h, rgb, strength);
        glowBox(graphics, x + w - t, y, x + w, y + h, rgb, strength);
    }

    /**
     * **会发光的字**：同一行字画两遍（一遍散开、一遍本体），加法混合叠上去。
     *
     * <p>比多层偏移描边省事得多，而且是真"亮"，不是描了一圈边。</p>
     */
    public static void glowText(GuiGraphics graphics, Font font, Component text,
                                int x, int y, int rgb, int strength) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ZERO);
        int faint = (Math.min(255, strength) << 24) | (rgb & 0xFFFFFF);
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            graphics.drawString(font, text, x + d[0], y + d[1], faint, false);
        }
        graphics.drawString(font, text, x, y, (Math.min(255, strength * 2) << 24) | (rgb & 0xFFFFFF), false);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    public static void glowTextCentered(GuiGraphics graphics, Font font, Component text,
                                        int cx, int y, int rgb, int strength) {
        glowText(graphics, font, text, cx - font.width(text) / 2, y, rgb, strength);
    }
}
