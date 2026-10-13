package com.habaiwa.goetyrevenant.client.fx;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * **让字有质感** —— 原版那一行平铺的字太素了。
 *
 * <h3>四样</h3>
 * <ul>
 *   <li><b>外发光</b>：字周围糊一圈同色的淡光 —— 灵魂色的字会"亮出来"</li>
 *   <li><b>硬描边</b>：八个方向的实心描边（比原版那一道影子硬朗得多），
 *       压在血肉背景上字才立得住</li>
 *   <li><b>渐变</b>：逐字插色，一行字从暗到亮（标题用）</li>
 *   <li><b>呼吸</b>：整行字的亮度跟着心跳微微起伏</li>
 * </ul>
 *
 * <p>全部基于原版 {@code drawString}，没有着色器 —— 所以再老的机子也跑得动。</p>
 */
public final class TextFx {
    /** 八个方向（描边与发光都用它）。 */
    private static final int[][] DIRS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1}};

    private TextFx() {
    }

    /** 带外发光的字。 */
    public static void glow(GuiGraphics graphics, Font font, Component text,
                            int x, int y, int color, int glowColor, float strength) {
        int a = (int) (Math.max(0.0F, Math.min(1.0F, strength)) * 46.0F);
        if (a > 3) {
            int c = (a << 24) | (glowColor & 0xFFFFFF);
            for (int[] d : DIRS) {
                graphics.drawString(font, text, x + d[0], y + d[1], c, false);
            }
            // 再远一圈（更淡），光晕才铺得开
            int a2 = a / 2;
            if (a2 > 3) {
                int c2 = (a2 << 24) | (glowColor & 0xFFFFFF);
                for (int[] d : DIRS) {
                    graphics.drawString(font, text, x + d[0] * 2, y + d[1] * 2, c2, false);
                }
            }
        }
        graphics.drawString(font, text, x, y, color, false);
    }

    /** 居中 + 发光。 */
    public static void glowCentered(GuiGraphics graphics, Font font, Component text,
                                    int cx, int y, int color, int glowColor, float strength) {
        glow(graphics, font, text, cx - font.width(text) / 2, y, color, glowColor, strength);
    }

    /** 硬描边（八个方向压深色），再写正文。 */
    public static void stroke(GuiGraphics graphics, Font font, Component text,
                              int x, int y, int color, int strokeColor) {
        for (int[] d : DIRS) {
            graphics.drawString(font, text, x + d[0], y + d[1], strokeColor, false);
        }
        graphics.drawString(font, text, x, y, color, false);
    }

    public static void strokeCentered(GuiGraphics graphics, Font font, Component text,
                                      int cx, int y, int color, int strokeColor) {
        stroke(graphics, font, text, cx - font.width(text) / 2, y, color, strokeColor);
    }

    /** 描边 + 发光 一起上（重要标题）。 */
    public static void bold(GuiGraphics graphics, Font font, Component text, int cx, int y,
                            int color, int strokeColor, int glowColor, float strength) {
        int a = (int) (Math.max(0.0F, Math.min(1.0F, strength)) * 40.0F);
        int x = cx - font.width(text) / 2;
        if (a > 3) {
            int c = (a << 24) | (glowColor & 0xFFFFFF);
            for (int[] d : DIRS) {
                graphics.drawString(font, text, x + d[0] * 2, y + d[1] * 2, c, false);
            }
        }
        stroke(graphics, font, text, x, y, color, strokeColor);
    }

    /** 逐字渐变（从左边的 from 过渡到右边的 to）。 */
    public static void gradient(GuiGraphics graphics, Font font, String text,
                                int x, int y, int from, int to, boolean shadow) {
        int n = Math.max(1, text.length() - 1);
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int color = mix(from, to, i / (float) n);
            graphics.drawString(font, ch, cx, y, color, shadow);
            cx += font.width(ch);
        }
    }

    public static void gradientCentered(GuiGraphics graphics, Font font, String text,
                                        int cx, int y, int from, int to, boolean shadow) {
        gradient(graphics, font, text, cx - font.width(text) / 2, y, from, to, shadow);
    }

    /**
     * **按序列的性子渲染一个名字** —— 七条序列，七种笔法。
     *
     * <table>
     *   <tr><th>序列</th><th>怎么动</th></tr>
     *   <tr><td>烛守</td><td>暖橙，像火苗一样**快而不规则地窜**</td></tr>
     *   <tr><td>拾荒</td><td>铜色，几乎不动，偶尔**闪过一丝金属反光**</td></tr>
     *   <tr><td>缄口</td><td>灰紫，呼吸**极弱极慢**，像快熄了</td></tr>
     *   <tr><td>追影</td><td>青蓝，**快速抖动 + 位置微飘**，抓不住</td></tr>
     *   <tr><td>筑墙</td><td>石白，**纹丝不动**，硬描边最重</td></tr>
     *   <tr><td>归墟</td><td>紫，**缓慢下沉**的脉动，越沉越暗</td></tr>
     *   <tr><td>回声</td><td>青，身后**拖三层残影**，一圈圈荡开</td></tr>
     * </table>
     *
     * @param sequence 序列序号（{@code Sequence.ordinal()}）
     */
    public static void sequenceName(GuiGraphics graphics, Font font, String name,
                                    int cx, int y, int sequence, long now, float appear) {
        float a = Math.max(0.0F, Math.min(1.0F, appear));
        int x = cx - font.width(name) / 2;
        // 配色**从 Sequence 取** —— 单一来源，不在渲染层复制第二份色表
        int base = com.habaiwa.goetyrevenant.common.sequence.Sequence.colorOf(sequence);
        int dark = com.habaiwa.goetyrevenant.common.sequence.Sequence.colorDarkOf(sequence);
        int strokeColor = withA(0xE00A0608, a);

        switch (sequence) {
            case 0 -> {          // 烛守：颜色像火苗一样在字上烧（流得快）
                flowing(graphics, font, name, cx, y, dark, base, now, 3.4F, strokeColor);
                double p = 0.5 + 0.5 * Math.sin(now / 95.0) * 0.6 + 0.4 * Math.sin(now / 41.0);
                glow(graphics, font, Component.literal(name), x, y,
                        withA(mix(base, 0xFFFFF0C0, (float) Math.max(0.0, Math.min(1.0, p))), a * 0.5F),
                        base, a * 0.45F);
            }
            case 1 -> {          // 拾荒：颜色几乎不动，偶尔一道反光扫过
                flowing(graphics, font, name, cx, y, dark, base, now, 0.22F, strokeColor);
                double sweep = (now % 3400) / 3400.0;
                if (sweep > 0.42 && sweep < 0.58) {
                    float k = (1.0F - (float) Math.abs(sweep - 0.5) / 0.08F) * 0.55F;
                    stroke(graphics, font, Component.literal(name), x, y, withA(0xFFFFFFFF, a * k), 0);
                }
            }
            case 2 -> {          // 缄口：颜色极慢地一亮一暗，慢到几乎看不出
                double p = 0.5 + 0.5 * Math.sin(now / 1150.0);
                float k = 0.55F + (float) p * 0.45F;
                flowing(graphics, font, name, cx, y, dark, mix(base, 0xFFC8B8E0, (float) p), now, 0.16F,
                        withA(0xD0000000, a * k));
            }
            case 3 -> {          // 追影：颜色流得最快 + 整行抓不住地抖
                double jitter = Math.sin(now / 42.0) * 1.5 + Math.sin(now / 17.0) * 0.8;
                flowing(graphics, font, name, cx + (int) Math.round(jitter), y, dark, base, now, 5.0F,
                        strokeColor);
                glow(graphics, font, Component.literal(name), x, y,
                        withA(mix(base, 0xFFC0E8FF, 0.25F), a * 0.4F), base, a * 0.35F);
            }
            case 4 -> {          // 筑墙：颜色**完全静止**（流速 0），只有最硬的边
                flowing(graphics, font, name, cx, y, dark, base, now, 0.0F, withA(0xF0100C0A, a));
                stroke(graphics, font, Component.literal(name), x, y + 1,
                        withA(mix(base, 0xFF000000, 0.45F), a * 0.65F), 0);
            }
            case 5 -> {          // 归墟：颜色往下沉 —— 位移下沉 + 一路变暗
                double p = (now % 3600) / 3600.0;
                int dy = (int) (p * 3.0);
                int dim = mix(base, dark, (float) p * 0.65F);
                flowing(graphics, font, name, cx, y + dy, dark, dim, now, 0.5F, withA(0xE0080410, a));
            }
            default -> {         // 回声：三层残影 + 颜色一圈圈荡开
                for (int i = 3; i >= 1; i--) {
                    int c = mix(dark, base, 0.35F / i);
                    stroke(graphics, font, Component.literal(name), x - i * 2, y + i,
                            withA(c, a * (0.34F / i)), 0);
                }
                flowing(graphics, font, name, cx, y, dark, base, now, 2.2F, withA(0xE0041414, a));
            }
        }
    }

    /**
     * **流动渐变**：一行字逐字插色，而且**色相会沿着字慢慢移动**。
     *
     * <p>死渐变的颜色是钉在字上的（第一个字永远最暗）；这里给相位加上时间，
     * 于是"亮的那一段"会从字的一头淌到另一头 —— 像有光在字上面走。</p>
     *
     * @param speed 每秒淌过多少个字（0 = 不流动，退回普通渐变）
     */
    public static void flowing(GuiGraphics graphics, Font font, String text, int cx, int y,
                               int from, int to, long now, float speed, int strokeColor) {
        int n = Math.max(1, text.length());
        int x = cx - font.width(text) / 2;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float t = (i / (float) n + now / 1000.0F * speed / n) % 1.0F;
            // 用三角波让过渡来回走，而不是"到头就跳"
            float k = t < 0.5F ? t * 2.0F : (1.0F - t) * 2.0F;
            int color = mix(from, to, k);
            if (strokeColor != 0) {
                stroke(graphics, font, Component.literal(ch), x, y, color, strokeColor);
            } else {
                graphics.drawString(font, ch, x, y, color, false);
            }
            x += font.width(ch);
        }
    }

    private static int withA(int argb, float alpha) {
        int a = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * ((argb >>> 24) & 0xFF));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** 按"呼吸强度"把颜色整体提亮或压暗（0 = 原样，1 = 最亮）。 */
    public static int breathe(int color, float k) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float f = 1.0F + Math.max(0.0F, Math.min(1.0F, k)) * 0.45F;
        return (a << 24)
                | (Math.min(255, (int) (r * f)) << 16)
                | (Math.min(255, (int) (g * f)) << 8)
                | Math.min(255, (int) (b * f));
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }
}
