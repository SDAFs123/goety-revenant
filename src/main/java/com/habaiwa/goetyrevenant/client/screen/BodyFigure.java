package com.habaiwa.goetyrevenant.client.screen;

import com.habaiwa.goetyrevenant.common.body.BodyLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

/**
 * **七窍界面上的那具人体**。
 *
 * <h3>为什么要有这个类</h3>
 * <p>原来的人体是六块方块拼的：头是矩形、脖子是矩形、躯干是个梯形循环、手脚是两条竖条。
 * 窍的位置是 {@link BodyLayout} 定的（面板 176×222 的比例坐标），
 * 但那具身体<b>根本没照着窍的位置长</b> —— 眼睛那两窍（x 0.430 / 0.570）压根就戳在脑袋外面。</p>
 *
 * <p>这里按**解剖图**重画：轮廓用连续函数定义（不是拼方块），每行再做横向圆柱明暗，
 * 所以有体积感；然后叠上锁骨、胸骨、肋骨、胸肌、腹肌、胯骨这些结构。</p>
 *
 * <h3>对齐</h3>
 * <p>全部按 {@link BodyLayout#PANEL_WIDTH}×{@link BodyLayout#PANEL_HEIGHT} 的设计像素画，
 * 绘制时乘 {@code scale} —— 和面板本身同一个换算，所以换分辨率永远对得上。</p>
 *
 * <h3>缓存</h3>
 * <p>和 {@code FleshBackdrop} 一样：整套形状只在第一次生成一次，之后每帧照着矩形清单发绘制调用。
 * 人体是静止的，没有理由每帧重算。</p>
 */
final class BodyFigure {
    private static final int W = BodyLayout.PANEL_WIDTH;
    private static final int H = BodyLayout.PANEL_HEIGHT;
    private static final int CX = W / 2;

    // 血肉色阶
    private static final int SKIN_DARK = 0xFF3A0D10;
    private static final int SKIN = 0xFF6B1E22;
    private static final int SKIN_LIT = 0xFF9A3A38;
    private static final int MUSCLE = 0xFF5E1A1E;
    private static final int MUSCLE_LIT = 0xFF8A2A2E;
    private static final int SINEW = 0xFFB08A6A;
    private static final int BONE = 0xFFD8CBBA;
    private static final int BLOOD_DARK = 0xFF6A0A10;
    private static final int BLOOD = 0xFFA8121C;
    private static final int SCAB = 0xFF2A0508;

    /** 横向分几段做圆柱明暗（多了费，少了假）。 */
    private static final int COLS = 5;
    /** 四肢的段数。 */
    private static final int LIMB_COLS = 4;

    private int[][] ops = new int[0][];

    void draw(GuiGraphics graphics, int left, int top, float scale) {
        if (this.ops.length == 0) {
            build();
        }
        for (int[] op : this.ops) {
            int x0 = left + Math.round(op[0] * scale);
            int y0 = top + Math.round(op[1] * scale);
            int x1 = left + Math.round(op[2] * scale);
            int y1 = top + Math.round(op[3] * scale);
            if (x1 <= x0 || y1 <= y0) {
                continue;
            }
            graphics.fill(x0, y0, x1, y1, dim(op[4]));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  轮廓：y 处的半宽（设计像素）。0 = 这一行没有身体                     */
    /* ------------------------------------------------------------------ */

    /**
     * 人体轮廓。
     *
     * <h3>用的是绘画的人体比例</h3>
     * <p>标准画法「7.5~8 头身」：<b>肩线在 1.3 头高、肩宽 = 2 头高、
     * 腰最细在 2.7 头、胯在 3.75 头（全身中点）</b>。</p>
     *
     * <p>但 176×222 的面板装不下全身 —— 真按 7.5 头身画，头只有 29 像素，
     * 比器官圈还小。所以这里取<b>头 + 躯干到胯（4 头身）</b>：
     * 头高 52 像素，肩宽 104 像素，器官看得清清楚楚。</p>
     */
    private static float torsoHalf(float y) {
        if (y < 6.0F) {
            return 0.0F;
        }
        if (y < 58.0F) {
            // 头：蛋形，高 52（= 1 头身）。顶窄、颧骨最宽、下巴收、颈根衔接留宽。
            // 不是正圆 —— 正圆画出来是气球。
            float t = (y - 6.0F) / 52.0F;
            float[] hw = {13.0F, 18.5F, 19.5F, 14.0F, 9.5F};   // t = 0, 0.25, 0.5, 0.75, 1.0
            float u = t * 4.0F;
            int i = (int) u;
            if (i >= 4) {
                return 9.5F;
            }
            float f = u - i;
            return hw[i] + (hw[i + 1] - hw[i]) * f;
        }
        if (y < 62.0F) {
            return 10.0F;                                    // 颈（短：只有 4 像素）
        }
        if (y < 95.0F) {
            // 肩：从颈根到肩峰是**一条平滑凸弧**（斜方肌+三角肌连着本来就是一条）。
            // 原来分两段画，两段之间出现两个鼓包 —— 人看起来像拼的。现在一条 smoothstep 到底
            float s = (y - 62.0F) / 33.0F;
            return 10.0F + 42.0F * (s * s * (3.0F - 2.0F * s));
        }
        if (y < 103.0F) {
            // 腋下内收 —— 肩 52 → 腋 36
            float s = (y - 95.0F) / 8.0F;
            return 52.0F - 16.0F * (s * s * (3.0F - 2.0F * s));
        }
        if (y < 126.0F) {
            return 36.0F - 2.0F * ((y - 103.0F) / 23.0F);    // 胸侧
        }
        if (y < 150.0F) {
            return 34.0F - 3.0F * ((y - 126.0F) / 24.0F);    // 腰：2.7 头处最细
        }
        if (y < 190.0F) {
            return 31.0F + 15.0F * ((y - 150.0F) / 40.0F);   // 髂骨到胯：3.75 头处最宽
        }
        if (y < 210.0F) {
            return 46.0F - 12.0F * ((y - 190.0F) / 20.0F);   // 大腿根
        }
        return 0.0F;
    }

    /* ------------------------------------------------------------------ */

    private void build() {
        List<int[]> ops = new ArrayList<>(4096);
        Random rng = new Random(0x5EEDL);

        drawTorso(ops);
        // 手臂：从三角肌正下方（y≈84）贴着身体垂下 —— 腋窝内侧贴着躯干外侧
        drawLimb(ops, 100.0F, 176.0F, 42.0F, 46.0F, 7.5F, 5.5F, 0.0F);       // 左臂（内缘叠进躯干，看不出接缝）
        drawLimb(ops, 100.0F, 176.0F, -42.0F, -46.0F, 7.5F, 5.5F, 0.0F);     // 右臂
        // 腿：从胯下（y≈188）接出去，大腿粗小腿细
        drawLimb(ops, 188.0F, 222.0F, 21.0F, 17.0F, 15.0F, 9.0F, 0.18F);     // 左腿（从胯下缘长出）
        drawLimb(ops, 188.0F, 222.0F, -21.0F, -17.0F, 15.0F, 9.0F, 0.18F);   // 右腿

        drawAnatomy(ops);
        drawBlood(ops, rng);

        this.ops = ops.toArray(new int[0][]);
    }

    /** 正中那一条：头 + 颈 + 躯干。每行分 COLS 段做横向明暗，画出来是「圆柱」不是「纸板」。 */
    private void drawTorso(List<int[]> ops) {
        for (int y = 2; y < 210; y++) {
            float half = torsoHalf(y);
            if (half <= 0.5F) {
                continue;
            }
            for (int c = 0; c < COLS; c++) {
                float u0 = c / (float) COLS;
                float u1 = (c + 1) / (float) COLS;
                float uc = (u0 + u1) * 0.5F;
                // 主光从左上前方来 —— 左边偏亮，右侧沉到暗里
                float lit = 1.0F - Math.abs(uc - 0.34F) * 1.9F;
                // 上下也压一压，胸腔最亮、两头烂掉
                float vert = 1.0F - Math.abs((y - 74.0F) / 150.0F);
                float v = Math.max(0.0F, Math.min(1.0F, lit * 0.75F + vert * 0.35F));
                int color = lerp(SKIN_DARK, SKIN_LIT, v * 0.72F);
                add(ops, CX - half + 2.0F * half * u0, y, CX - half + 2.0F * half * u1, y + 1, color);
            }
            // 轮廓：左右各一条暗边
            add(ops, CX - half, y, CX - half + 1.6F, y + 1, SCAB);
            add(ops, CX + half - 1.6F, y, CX + half, y + 1, SCAB);
        }
    }

    /** 一条四肢：从 (y0,cx0) 走到 (y1,cx1)，半宽从 h0 收到 h1。 */
    private void drawLimb(List<int[]> ops, float y0, float y1, float cx0, float cx1,
                          float h0, float h1, float darken) {
        for (int y = (int) y0; y < (int) y1; y++) {
            float t = (y - y0) / (y1 - y0);
            float c = CX + cx0 + (cx1 - cx0) * t;
            float h = h0 + (h1 - h0) * t;
            for (int k = 0; k < LIMB_COLS; k++) {
                float u0 = k / (float) LIMB_COLS;
                float u1 = (k + 1) / (float) LIMB_COLS;
                float uc = (u0 + u1) * 0.5F;
                // 和躯干同一套光照公式：左来光、上亮下暗 —— 四肢和躯干是一整张皮
                float lit = 1.0F - Math.abs(uc - 0.34F) * 1.9F;
                float vert = 1.0F - Math.abs((y - 74.0F) / 150.0F);
                float v = Math.max(0.0F, Math.min(1.0F, lit * 0.75F + vert * 0.35F - darken));
                int color = lerp(SKIN_DARK, SKIN_LIT, v * 0.72F);
                add(ops, c - h + 2.0F * h * u0, y, c - h + 2.0F * h * u1, y + 1, color);
            }
            // 皮缘：同色系暗影，只画**外侧** —— 内侧贴着躯干，不画线才没有"拼缝"。
            // 原来左右各一条 SCAB 深边，和躯干那条并排贴着 = 一道缝
            add(ops, c + h - 1.0F, y, c + h, y + 1, lerp(SKIN_DARK, SKIN_LIT, 0.10F));
        }
    }

    /* ------------------------------------------------------------------ */
    /*  解剖结构                                                           */
    /* ------------------------------------------------------------------ */

    private void drawAnatomy(List<int[]> ops) {
        // 锁骨：从胸骨上端斜着摊到肩
        for (int i = 0; i < 12; i++) {
            float t = i / 11.0F;
            int y = (int) (64.0F + t * 28.0F);
            add(ops, CX + 3.0F + t * 31.0F, y, CX + 5.0F + t * 31.0F, y + 1.4F, SINEW);
            add(ops, CX - 5.0F - t * 31.0F, y, CX - 3.0F - t * 31.0F, y + 1.4F, SINEW);
        }

        // 胸骨
        for (int y = 70; y < 106; y++) {
            add(ops, CX - 1.2F, y, CX + 1.2F, y + 1, SINEW);
        }

        // 肋骨：五对，越往下越短、越平
        for (int r = 0; r < 5; r++) {
            float ry = 100.0F + r * 7.0F;
            float reach = 30.0F - r * 3.0F;
            for (int i = 0; i < 7; i++) {
                float t = i / 6.0F;
                int y = (int) (ry + t * t * 6.0F);
                add(ops, CX + 3.0F + t * reach, y, CX + 5.0F + t * reach, y + 1.3F, BONE);
                add(ops, CX - 5.0F - t * reach, y, CX - 3.0F - t * reach, y + 1.3F, BONE);
            }
        }

        // 胸肌：两块偏亮的鼓包
        ellipse(ops, CX - 17, 106, 15, 9, MUSCLE_LIT, 0x66);
        ellipse(ops, CX + 17, 106, 15, 9, MUSCLE_LIT, 0x66);

        // 腹肌：三排两列，中间留暗缝
        for (int row = 0; row < 3; row++) {
            int y = 106 + row * 11;
            ellipse(ops, CX - 8, y, 8, 5, MUSCLE, 0x88);
            ellipse(ops, CX + 8, y, 8, 5, MUSCLE, 0x88);
        }
        // 腹白线
        for (int y = 104; y < 138; y++) {
            add(ops, CX - 0.8F, y, CX + 0.8F, y + 1, SCAB);
        }

        // 肚脐
        ellipse(ops, CX, 142, 3, 2, SCAB, 0xC0);

        // 胯骨：两小块骨头尖
        for (int i = 0; i < 8; i++) {
            float t = i / 7.0F;
            int y = (int) (144.0F + t * 6.0F);
            add(ops, CX + 8.0F + t * 12.0F, y, CX + 11.0F + t * 12.0F, y + 1.4F, SINEW);
            add(ops, CX - 11.0F - t * 12.0F, y, CX - 8.0F - t * 12.0F, y + 1.4F, SINEW);
        }

        // 上臂肌肉 / 膝盖
        ellipse(ops, CX + 48, 122, 7, 12, MUSCLE_LIT, 0x50);
        ellipse(ops, CX - 48, 122, 7, 12, MUSCLE_LIT, 0x50);
        for (int y = 188; y < 191; y++) {
            add(ops, CX + 8.0F, y, CX + 30.0F, y + 1, SCAB);
            add(ops, CX - 30.0F, y, CX - 8.0F, y + 1, SCAB);
        }
    }

    /** 从每一个窍往下淌的血 —— 窍在哪，血就从哪流出来。 */
    private void drawBlood(List<int[]> ops, Random rng) {
        for (BodyLayout.Spot spot : BodyLayout.SPOTS) {
            float sx = spot.x() * W;
            float sy = spot.y() * H + spot.radius() * W;
            int streams = 2 + rng.nextInt(2);
            for (int k = 0; k < streams; k++) {
                float x = sx + (rng.nextFloat() - 0.5F) * spot.radius() * W * 1.2F;
                float len = 16.0F + rng.nextInt(34);
                int thickness = 1 + rng.nextInt(2);
                add(ops, x, sy, x + thickness, sy + len, BLOOD);
                // 血滴：流到一半积出来的
                if (rng.nextBoolean()) {
                    ellipse(ops, (int) x, (int) (sy + len), 2, 2, BLOOD, 0xFF);
                }
            }
        }
        // 腹腔底下积的一摊血
        for (int y = 146; y < 152; y++) {
            float half = torsoHalf(y);
            if (half > 4.0F) {
                add(ops, CX - half * 0.8F, y, CX + half * 0.8F, y + 1, BLOOD_DARK);
            }
        }
        // 几道干掉的血痕
        for (int i = 0; i < 14; i++) {
            float x = rng.nextInt(W);
            float y = 40 + rng.nextInt(120);
            float len = 6 + rng.nextInt(18);
            add(ops, x, y, x + 1, y + len, (0x60 << 24) | (BLOOD_DARK & 0xFFFFFF));
        }
    }

    /* ------------------------------------------------------------------ */

    /** 一个椭圆 —— 逐行算出半宽，一行一个矩形。 */
    private static void ellipse(List<int[]> ops, int cx, int cy, int rx, int ry, int color, int alpha) {
        int argb = (alpha << 24) | (color & 0xFFFFFF);
        for (int dy = -ry; dy <= ry; dy++) {
            float k = 1.0F - (dy / (float) ry) * (dy / (float) ry);
            int half = (int) (rx * (float) Math.sqrt(Math.max(0.0F, k)));
            if (half <= 0) {
                continue;
            }
            add(ops, cx - half, cy + dy, cx + half, cy + dy + 1, argb);
        }
    }

    private static void add(List<int[]> ops, float x0, float y0, float x1, float y1, int color) {
        int a = Math.round(x0);
        int b = Math.round(y0);
        int c = Math.round(x1);
        int d = Math.round(y1);
        if (c <= a || d <= b) {
            return;
        }
        ops.add(new int[]{a, b, c, d, color});
    }

    /** 把颜色压暗一半 —— 人体要退到背景里，不能跟器官抢。 */
    private static int dim(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = ((argb >> 16) & 0xFF) * 70 / 100;
        int g = ((argb >> 8) & 0xFF) * 70 / 100;
        int b = (argb & 0xFF) * 70 / 100;
        return ((a * 4 / 5) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
