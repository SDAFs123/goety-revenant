package com.habaiwa.goetyrevenant.client.screen.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

/**
 * **魂树** —— 一棵有整体感、有细节的树。
 *
 * <h3>为什么上一版"不像一个整体"</h3>
 * <ul>
 *   <li>每一级枝用一种固定颜色 → 枝和干之间颜色是<b>跳</b>的</li>
 *   <li>子枝的起始粗细是父枝的 0.58 → 接口处<b>突然细一圈</b></li>
 *   <li>分叉点什么都没有 → 看着像几根棍子插在一起</li>
 * </ul>
 *
 * <h3>这一版怎么连成一棵树</h3>
 * <ol>
 *   <li><b>一条统一色带</b>：从根部的深褐一路过渡到末梢的灵魂青，
 *       颜色由"这段枝在树里的深度"决定 —— 不按层级跳色</li>
 *   <li><b>粗细平滑衔接</b>：子枝起笔粗细 = 父枝收笔粗细（0.88），接口不缩</li>
 *   <li><b>分叉处按上树瘤</b>：每个分叉点画一个小圆结，把接口盖住</li>
 *   <li><b>树皮纹理</b>：主干上纵向的深色裂纹</li>
 *   <li><b>立体光影</b>：右边压暗、左边提亮，树才有厚度</li>
 * </ol>
 *
 * <h3>生长</h3>
 * <p>每段带一个生长位置 {@code g}（0~1），绘制时按全局进度筛 ——
 * 根 → 主干 → 一级 → 二级 → 三级 → 末梢。序列升阶时从头播一次。</p>
 */
public final class TreeFigure {
    private final List<int[]> segs = new ArrayList<>(8192);
    private final List<int[]> leaves = new ArrayList<>(1024);
    private final List<int[]> knots = new ArrayList<>(512);
    private final List<int[]> embedNodes = new ArrayList<>(8);
    private final List<int[]> upgradeNodes = new ArrayList<>(64);

    private static final float[] LEVEL_G0 = {0.00F, 0.24F, 0.44F, 0.62F, 0.78F, 0.90F};
    private static final float[] LEVEL_G1 = {0.24F, 0.44F, 0.62F, 0.78F, 0.90F, 1.00F};
    private static final int[] LEVEL_TOTAL = {1, 6, 18, 36, 72, 72};

    private final int[] levelCounter = new int[6];
    /** 强化槽的稳定编号（一级枝 0-5、二级枝 6-23）。 */
    private int upgradeCounter = 0;

    private int width;
    private int height;
    private int stage = Integer.MIN_VALUE;
    private int seed = 0;

    /** **统一色带**：根 → 干 → 枝 → 末梢。整棵树只从这里取色。 */
    private static final int[] BAND = {
            0xFF140806,   // 根：几乎全黑
            0xFF25100B,   // 干（下）
            0xFF3A1810,   // 干（中）
            0xFF4E2018,   // 干（上）
            0xFF6A2E1E,   // 一级枝
            0xFF8A4426,   // 二级枝
            0xFFA25A2C,   // 三级枝
            0xFF2E8C84,   // 四级枝：开始带灵魂色
            0xFF52E8DC    // 末梢：灵魂青
    };

    private static final int SOUL = 0xFF52E8DC;
    private static final int SOUL_DIM = 0xFF1C6660;

    public void build(int width, int height, int stage, int soulSeed) {
        if (this.stage == stage && this.width == width && this.height == height && this.seed == soulSeed) {
            return;
        }
        this.width = width;
        this.height = height;
        this.stage = stage;
        this.seed = soulSeed;
        this.segs.clear();
        this.leaves.clear();
        this.knots.clear();
        this.embedNodes.clear();
        this.upgradeNodes.clear();
        for (int i = 0; i < this.levelCounter.length; i++) {
            this.levelCounter[i] = 0;
        }
        this.upgradeCounter = 0;

        Random rng = new Random(0x7EE0L + soulSeed);
        float cx = width / 2.0F;
        float baseY = height - 14.0F;
        float trunkLen = Math.max(90.0F, (height - 52.0F) * 0.54F);
        float trunkW = Math.max(16.0F, height * 0.052F);
        float topX = cx + (rng.nextFloat() - 0.5F) * 5.0F;
        float topY = baseY - trunkLen;

        // ---- 根：六条，往两侧和下方扎 ----
        for (int i = 0; i < 6; i++) {
            float dir = (i % 2 == 0) ? 1.0F : -1.0F;
            float spread = 1.2F + (i / 6.0F) * 1.4F;
            float len = trunkW * (1.6F + rng.nextFloat() * 0.9F);
            segment(cx, baseY, cx + dir * len * spread * 0.5F, baseY + len * 0.7F,
                    trunkW * 0.34F, BAND[0], 0.0F, 0.03F);
        }
        knot(cx, baseY, trunkW * 0.5F, BAND[1], 0.0F);

        // ---- 主干：13 段，渐细 + 摇摆 + 树皮裂纹 ----
        int trunkSteps = 13;
        float prevX = cx;
        float prevY = baseY;
        for (int s = 1; s <= trunkSteps; s++) {
            float t = s / (float) trunkSteps;
            float x = cx + (topX - cx) * t + (float) Math.sin(t * 3.1F) * 3.5F;
            float y = baseY + (topY - baseY) * t;
            segment(prevX, prevY, x, y, trunkW * (1.0F - 0.42F * t),
                    bandAt(0.04F + t * 0.26F), 0.05F + 0.19F * t, 0.03F);
            prevX = x;
            prevY = y;
        }
        // 树皮裂纹：主干上的几条纵向深色
        for (int i = 0; i < 7; i++) {
            float t0 = 0.12F + rng.nextFloat() * 0.62F;
            float t1 = t0 + 0.06F + rng.nextFloat() * 0.10F;
            float side = rng.nextFloat() < 0.5F ? -1.0F : 1.0F;
            float px0 = cx + (topX - cx) * t0 + (float) Math.sin(t0 * 3.1F) * 3.5F;
            float py0 = baseY + (topY - baseY) * t0;
            float px1 = cx + (topX - cx) * t1 + (float) Math.sin(t1 * 3.1F) * 3.5F;
            float py1 = baseY + (topY - baseY) * t1;
            segment(px0 + side * trunkW * 0.22F, py0, px1 + side * trunkW * 0.20F, py1,
                    1.6F, shade(bandAt(0.06F + t0 * 0.24F), 0.55F), 0.06F + t0 * 0.18F, 0.02F);
        }

        // ---- 一级枝 6 条 ----
        for (int i = 0; i < 6; i++) {
            float t = 0.30F + 0.54F * (i / 5.0F);
            float bx = cx + (topX - cx) * t + (float) Math.sin(t * 3.1F) * 3.5F;
            float by = baseY + (topY - baseY) * t;
            float side = (i % 2 == 0) ? 1.0F : -1.0F;
            float angle = side * (1.04F - 0.46F * (t + 0.16F));
            float len = trunkLen * (0.80F - 0.18F * t);
            float w = trunkW * (1.0F - 0.42F * t) * 0.92F;   // 接着主干收笔的粗细
            knot(bx, by, w * 0.62F, bandAt(0.30F + t * 0.06F), 0.22F);
            limb(bx, by, angle, len, w, 1, rng, side);

            float g = 0.24F + 0.20F * (i / 6.0F);
            this.embedNodes.add(new int[]{(int) (bx + side * trunkW * 0.62F), (int) by, i, (int) (g * 1000)});
        }
    }

    /** 一条枝：分几段画、角度逐段收回（向上翘），带生长区间。 */
    private void limb(float x, float y, float angle, float len, float w, int level,
                      Random rng, float side) {
        if (level > 5 || len < 3.0F || w < 0.8F) {
            return;
        }
        // 末梢不画枝，只撒叶簇（省一百多条枝，也像树冠）
        if (level >= 5) {
            int li = 5;
            int idx = this.levelCounter[li]++;
            float g0 = LEVEL_G0[li] + (LEVEL_G1[li] - LEVEL_G0[li])
                    * (idx / (float) Math.max(1, LEVEL_TOTAL[li]));
            for (int i = 0; i < 4; i++) {
                int lx = (int) (x + (rng.nextFloat() - 0.5F) * 13.0F);
                int ly = (int) (y + (rng.nextFloat() - 0.5F) * 13.0F);
                this.leaves.add(new int[]{lx, ly, rng.nextInt(3), (int) ((g0 + 0.012F) * 1000),
                        rng.nextInt(2)});
            }
            return;
        }
        int li = Math.min(5, level);
        int idx = this.levelCounter[li]++;
        float g0 = LEVEL_G0[li] + (LEVEL_G1[li] - LEVEL_G0[li])
                * (idx / (float) Math.max(1, LEVEL_TOTAL[li]));
        float gSpan = (LEVEL_G1[li] - LEVEL_G0[li]) / Math.max(1, LEVEL_TOTAL[li]);

        int steps = 4;
        float step = len / steps;
        float cx = x;
        float cy = y;
        float ca = angle;
        float cw = w;
        for (int s = 0; s < steps; s++) {
            float t = s / (float) steps;
            float na = ca * 0.80F;
            float nx = cx + (float) Math.sin(na) * step;
            float ny = cy - (float) Math.cos(na) * step;
            float nw = w * (1.0F - 0.22F * (t + 1.0F / steps));   // 缓慢收细
            float depth = (level - 1 + (s + 1) / (float) steps) / 4.6F;
            segment(cx, cy, nx, ny, nw, bandAt(0.30F + depth * 0.62F),
                    g0 + gSpan * ((s + 1) / (float) steps), gSpan * 0.35F);
            cx = nx;
            cy = ny;
            ca = na;
            cw = nw;
        }

        // 枝梢按个小结（接口不突兀）
        knot(cx, cy, Math.max(1.2F, cw * 0.8F), bandAt(0.34F + (level / 5.0F) * 0.55F), g0 + gSpan);

        if (level <= 2) {
            this.upgradeNodes.add(new int[]{(int) cx, (int) cy, level, (int) ((g0 + gSpan) * 1000),
                    this.upgradeCounter++});
        }

        int kids = switch (level) {
            case 1 -> 3;
            case 2, 3 -> 2;
            default -> 1;
        };
        for (int k = 0; k < kids; k++) {
            float spread = (level == 1 ? 0.56F : 0.46F) * (k - (kids - 1) * 0.5F);
            float na = ca + spread * side + (rng.nextFloat() - 0.5F) * 0.11F;
            // **子枝起笔 = 父枝收笔**（0.88），接口处不会突然细一圈
            limb(cx, cy, na, len * 0.67F, cw * 0.88F, level + 1, rng, side);
        }
    }

    /** 色带取样：t = 0（根）~ 1（末梢）。 */
    private static int bandAt(float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        float u = t * (BAND.length - 1);
        int i = (int) u;
        if (i >= BAND.length - 1) {
            return BAND[BAND.length - 1];
        }
        return lerp(BAND[i], BAND[i + 1], u - i);
    }

    private void segment(float x0, float y0, float x1, float y1, float w, int color,
                         float g, float fade) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.4F) {
            return;
        }
        int steps = Math.max(1, Math.min(12, (int) (len / Math.max(2.0F, w * 1.15F))));
        int half = Math.max(0, (int) (w * 0.5F));
        float mid = this.width / 2.0F;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = (int) (x0 + dx * t);
            int y = (int) (y0 + dy * t);
            int h2 = Math.max(0, half - (int) (half * t * 0.2F));
            // 立体：右暗左亮
            float light = 1.0F - Math.max(0.0F, (x - mid) / mid) * 0.22F
                    + Math.max(0.0F, (mid - x) / mid) * 0.10F;
            this.segs.add(new int[]{x - h2, y - h2, x + h2 + 1, y + h2 + 1,
                    shade(color, light), (int) ((g - fade * (1.0F - t)) * 1000)});
        }
    }

    /** 树瘤 / 分叉结：一个小圆块，用来盖住接口。 */
    private void knot(float x, float y, float r, int color, float g) {
        int ri = Math.max(1, (int) r);
        this.knots.add(new int[]{(int) x, (int) y, ri, shade(color, 0.92F), (int) (g * 1000)});
    }

    public void draw(GuiGraphics graphics, float pulse, float growth) {
        int cutoff = (int) (Math.max(0.0F, Math.min(1.0F, growth)) * 1000);
        for (int[] s : this.segs) {
            if (s[5] <= cutoff) {
                graphics.fill(s[0], s[1], s[2], s[3], s[4]);
            }
        }
        for (int[] k : this.knots) {
            if (k[4] > cutoff) {
                continue;
            }
            graphics.fill(k[0] - k[2], k[1] - k[2], k[0] + k[2] + 1, k[1] + k[2] + 1, k[3]);
        }
        float breathe = 0.85F + pulse * 0.3F;
        for (int[] l : this.leaves) {
            if (l[3] > cutoff) {
                continue;
            }
            int r = 1 + l[2];
            int alpha = (int) ((l[4] == 0 ? 150 : 100) * breathe);
            graphics.fill(l[0] - r, l[1] - r, l[0] + r + 1, l[1] + r + 1,
                    0xFF000000 | (alpha << 24) | (SOUL & 0x00FFFFFF));
        }
        // 强化槽由界面按「选了什么方向」染色 —— 这里不画
        for (int[] n : this.embedNodes) {
            if (n[3] > cutoff) {
                continue;
            }
            int x = n[0];
            int y = n[1];
            int r = 3 + (int) (pulse * 1.5F);
            graphics.fill(x - r - 2, y - r - 2, x + r + 3, y + r + 3, 0x55000000);
            graphics.fill(x - r, y - r, x + r + 1, y + r + 1, SOUL_DIM);
        }
    }

    public int[] embedNodeAt(int index) {
        for (int[] n : this.embedNodes) {
            if (n[2] == index) {
                return n;
            }
        }
        return null;
    }

    public int[] upgradeNodeNear(int mouseX, int mouseY) {
        for (int[] n : this.upgradeNodes) {
            if (Math.abs(mouseX - n[0]) <= 7 && Math.abs(mouseY - n[1]) <= 7) {
                return n;
            }
        }
        return null;
    }

    /** 全部强化槽：{x, y, 层级, 生长, 编号}。 */
    public java.util.List<int[]> upgradeNodes() {
        return this.upgradeNodes;
    }

    public int upgradeCount() {
        return this.upgradeNodes.size();
    }

    private static int shade(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lerp(int a, int b, float t) {
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
