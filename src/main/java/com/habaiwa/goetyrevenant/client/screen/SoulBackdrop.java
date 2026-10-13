package com.habaiwa.goetyrevenant.client.screen;

import com.habaiwa.goetyrevenant.client.tree.TreeLayout;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * **夜空与星空** —— 完全重做。
 *
 * <h3>画面要的是什么</h3>
 * <pre>
 *   往上    星空       星点密集、有银河、有星云      内容图 0.00 ~ 0.30
 *   ────────────────────────────────────────────────────────────
 *   中间    少量星的夜空  树长在这里（它的背景不该抢戏）  0.30 ~ 0.50
 *   ────────────────────────────────────────────────────────────
 *   往下    没有星      土壤 / 过渡 / 血肉土壤把它盖住了  0.50 以下
 * </pre>
 *
 * <h3>实现上的一个决定：能烤的全烤了</h3>
 * <p>星空有几千颗星、几层星云、一条银河 —— 它们<b>根本不动</b>，
 * 每帧重画是浪费。所以除了"少数星的呼吸"，全部画进一张贴图，
 * 运行时只有一次 blit。几千颗星的代价是零。</p>
 *
 * <h3>星点怎么撒才像真的</h3>
 * <ul>
 *   <li><b>高度遮罩</b>：上方密集、中部稀疏、下面几乎没有（就是上面那张图）</li>
 *   <li><b>亮度幂律</b>：绝大多数很暗、少数很亮（均匀撒出来的是噪点）</li>
 *   <li><b>星色不齐</b>：蓝白 25% / 黄白 55% / 橙 15% / 红 5%</li>
 *   <li><b>成团</b>：围着几个团心密、别处稀（均匀 = 重复感）</li>
 *   <li><b>银河</b>：一条斜带 + 把带切开的暗尘带</li>
 *   <li><b>亮星</b>：带六道衍射星芒</li>
 * </ul>
 */
public final class SoulBackdrop {
    /** 日志器 —— 计时信息走它（System.out 在启动器里不一定进日志）。 */
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final ResourceLocation TEX =
            new ResourceLocation("goety_revenant", "dynamic/soul_starfield");

    private static final int VOID_TOP = 0xFF02040A;      // 最上面：深空
    private static final int VOID_MID = 0xFF060C16;      // 中部夜空
    private static final int GLOW_CORE = 0xFF18403C;     // 托着树根的那团光
    private static final int SOUL = 0xFF52E8DC;
    private static final int SOUL_SOFT = 0xFF2E8C88;

    /** 真实星空的星色（按 25:55:15:5 混）。 */
    private static final int[] STAR_COLORS = {
            0xFFCFE8FF,   // 蓝白（热星）
            0xFFFFF8E8,   // 黄白（太阳型）
            0xFFFFD8A8,   // 橙
            0xFFFFA878,   // 红（冷巨星）
    };
    private static final int[] STAR_WEIGHTS = {25, 55, 15, 5};

    // ======================================================================
    //  **星空之上是宇宙** —— 参考真实天文照片。
    //
    //  越往上看，东西是这样变的：
    //    · 底更深（几乎黑，带一点极暗的紫蓝）
    //    · 星点【更稀】—— 近处的星空星多，远看宇宙反而空
    //    · 主体是【星系】：带核的光斑，被拉成椭圆，有的看得出两条旋臂
    //    · 还有大尺度的星云团（比星空里那些大得多、淡得多）
    // ======================================================================
    /** 宇宙区占内容图上面多少。 */

    /** 星系：位置 / 半径 / 扁平度 / 倾角 / 亮度 / 有没有旋臂。 */
    private static final int GALAXIES = 5;
    private static final float[] GAL_X = {0.22F, 0.68F, 0.44F, 0.86F, 0.10F};
    private static final float[] GAL_Y = {0.045F, 0.075F, 0.115F, 0.030F, 0.100F};
    private static final float[] GAL_R = {0.085F, 0.062F, 0.105F, 0.048F, 0.070F};
    private static final float[] GAL_FLAT = {0.42F, 0.68F, 0.30F, 0.82F, 0.52F};
    private static final float[] GAL_ANG = {0.5F, 1.9F, 0.15F, 2.6F, 1.1F};
    private static final float[] GAL_BRIGHT = {0.95F, 0.70F, 1.10F, 0.55F, 0.80F};
    private static final boolean[] GAL_ARMS = {true, false, true, false, true};

    /** 星云：位置 / 半径 / 强度 / 长宽比 / 倾角（各团不同才不会看出重复）。 */
    private static final float[] NEBULA_X = {0.22F, 0.74F, 0.48F, 0.90F};
    private static final float[] NEBULA_Y = {0.20F, 0.17F, 0.28F, 0.24F};
    private static final float[] NEBULA_R = {0.34F, 0.28F, 0.40F, 0.24F};
    private static final float[] NEBULA_A = {0.070F, 0.055F, 0.048F, 0.040F};
    private static final float[] NEBULA_STRETCH = {1.30F, 1.12F, 1.45F, 1.22F};
    private static final float[] NEBULA_ANGLE = {0.62F, 2.10F, 0.18F, 1.35F};
    private static final int[] NEBULA_C = {0xFF2E8C88, 0xFF4A3A96, 0xFF1C4A4C, 0xFF3A6A78};

    /** 星团：围着这几个点密（撒星点时加权）。 */
    private static final int CLUSTERS = 7;
    private static final float[] CLUSTER_X = {0.18F, 0.46F, 0.70F, 0.86F, 0.32F, 0.60F, 0.09F};
    private static final float[] CLUSTER_Y = {0.18F, 0.24F, 0.16F, 0.28F, 0.32F, 0.22F, 0.20F};
    private static final float[] CLUSTER_R = {0.10F, 0.13F, 0.09F, 0.11F, 0.10F, 0.12F, 0.08F};

    /** 会眨眼的星：数量与峰值亮度（加在一起只有这一档是动态的）。 */
    public static final int DYNAMIC_STARS = 16;
    public static final int DYNAMIC_PEAK = 110;

    private NativeImage image;
    private DynamicTexture texture;
    private int width = -1;
    private int height = -1;

    // ------------------------------------------------------------------
    //  **生成放到后台线程**
    //
    //  这块星空是"一次性生成 + 缓存"的：生成完之后每帧只有 1 次 blit。
    //  但**那一次生成**很重（几百万像素、几千颗星），跑在主线程上就是
    //  "点开界面卡住几秒"。
    //
    //  NativeImage 的像素填充是纯内存操作（不碰 GL），所以能在别的线程做；
    //  只有"把它变成纹理"这一步必须回主线程 —— 见 draw() 开头。
    // ------------------------------------------------------------------
    private volatile NativeImage pending;
    private volatile int pendingW = -1;
    private volatile int pendingH = -1;

    public void build(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        // 已经好了、尺寸也对 —— 什么都不做
        if (this.image != null && this.width == width && this.height == height) {
            return;
        }
        // 这张尺寸已经在后台做着了 —— 别重复开线程
        if (this.pendingW == width && this.pendingH == height) {
            return;
        }
        this.width = width;
        this.height = height;
        this.pendingW = width;
        this.pendingH = height;
        final int w = width;
        final int h = height;
        Thread worker = new Thread(() -> {
            try {
                // 只有这一步耗时，而它不碰 GL
                this.pending = this.buildImage(w, h);
            } catch (Throwable ignored) {
                this.pendingW = -1;
                this.pendingH = -1;
            }
        }, "goety-revenant-starfield");
        worker.setDaemon(true);
        worker.start();
    }

    /** 后台做好了一张 —— 回主线程上传成纹理。 */
    private void takePending() {
        NativeImage img = this.pending;
        if (img == null) {
            return;
        }
        this.pending = null;
        if (img.getWidth() != this.width || img.getHeight() != this.height) {
            img.close();      // 尺寸已经变了，这张作废
            return;
        }
        if (this.texture != null) {
            this.texture.close();
        }
        long upT = System.nanoTime();
        this.image = img;
        this.texture = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(TEX, this.texture);
        LOG.info("[REVENANT-T] 上传背景 "
                + ((System.nanoTime() - upT) / 1_000_000L) + "ms  尺寸 "
                + img.getWidth() + "x" + img.getHeight());
    }

    /* ================================================================== */
    /*  密度：星点的分布规则                                                */
    /* ================================================================== */

    /**
     * **星点密度按高度** —— 这是"往上看才是星空"的实现。
     *
     * @param fy 0（顶）~ 1（底）
     */
    private static float skyMask(float fy) {
        // **星空要铺到树冠所在的高度**（0.20 ~ 0.34 是树冠）。
        // 之前星星只到 0.15 就没了，于是大树是"立在一片纯黑里"——
        // 真实的大树夜景不是那样的：树冠的轮廓映在星空上、
        // 树冠的缝隙里透着星。所以星星要一直铺到 0.28 左右。
        // 宇宙区（0.00 ~ 0.14）：星点【更稀】—— 近处星多、远处空
        if (fy <= 0.14F) {
            return 0.32F;
        }
        // 星空区（0.14 ~ 0.34）：满
        if (fy <= 0.34F) {
            return 0.32F + (fy - 0.14F) / 0.20F * 0.68F;   // 0.32 → 1.00
        }
        if (fy <= 0.42F) {
            return 1.0F - (fy - 0.34F) / 0.08F * 0.55F;    // 1.00 → 0.45（树冠这一段）
        }
        if (fy <= 0.54F) {
            return 0.45F - (fy - 0.42F) / 0.12F * 0.39F;   // 0.45 → 0.06
        }
        return 0.02F;                                      // 下面：几乎没有（反正会被土层盖住）
    }

    /** 银河带密度（斜穿星空区的一条带）。 */
    static float bandDensity(float x, float y, int w, int h) {
        float fx = x / Math.max(1.0F, w);
        float fy = y / Math.max(1.0F, h);
        // 只在上方那一片存在（下面没有银河）
        float mask = Math.max(0.0F, 1.0F - fy / 0.36F);
        if (mask <= 0.0F) {
            return 0.0F;
        }
        float d = (fx - 0.5F) * 0.47F - (fy - 0.16F) * 0.88F;
        d += 0.070F * (float) Math.sin(fx * Math.PI * 1.3D + 0.7F);
        float t = Math.abs(d) / 0.17F;
        return (float) Math.exp(-t * t * 2.1D) * mask;
    }

    /** 暗尘带（把银河切开的那条暗纹）。 */
    static float dustDensity(float x, float y, int w, int h) {
        float fx = x / Math.max(1.0F, w);
        float fy = y / Math.max(1.0F, h);
        float d = (fx - 0.5F) * 0.47F - (fy - 0.16F) * 0.88F;
        d += 0.028F * (float) Math.sin(fy * Math.PI * 2.4D);
        float t = Math.abs(d) / 0.032F;
        return (float) Math.exp(-t * t * 3.0D);
    }

    /** 某点受星团影响的程度（0~1）。 */
    private static float clusterBoost(float x, float y, int w, int h) {
        float fx = x / Math.max(1.0F, w);
        float fy = y / Math.max(1.0F, h);
        float best = 0.0F;
        for (int c = 0; c < CLUSTERS; c++) {
            float dx = fx - CLUSTER_X[c];
            float dy = fy - CLUSTER_Y[c];
            float d = (float) Math.sqrt(dx * dx + dy * dy) / CLUSTER_R[c];
            best = Math.max(best, (float) Math.exp(-d * d * 1.6D));
        }
        return best;
    }

    /* ================================================================== */
    /*  静态层：一次性烤好                                                  */
    /* ================================================================== */

    private NativeImage buildImage(int w, int h) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        Random rng = new Random(0x5EED77L + w * 31L + h);

        float glowCx = w / 2.0F;
        float glowCy = h * 0.62F;
        float rx = w * 0.46F;
        float ry = h * 0.52F;

        // ---- ① 底 + 星云 + 银河（逐像素）----
        for (int y = 0; y < h; y++) {
            float t = y / Math.max(1.0F, h - 1.0F);
            // 上深下略亮的深空
            int base = mix(VOID_TOP, VOID_MID, Math.min(1.0F, t * 1.6F));
            for (int x = 0; x < w; x++) {
                // 托着树根的那团光
                float dx = (x - glowCx) / rx;
                float dy = (y - glowCy) / ry;
                float gd = (float) Math.sqrt(dx * dx + dy * dy);
                float glow = Math.max(0.0F, 1.0F - gd);
                int color = mix(base, GLOW_CORE, glow * glow * glow * 0.80F);

                // 星云（每团自己的拉伸与倾角；二次方衰减 = 没有边界）
                for (int n = 0; n < NEBULA_X.length; n++) {
                    float dx0 = x - NEBULA_X[n] * w;
                    float dy0 = y - NEBULA_Y[n] * h;
                    double ca = Math.cos(NEBULA_ANGLE[n]);
                    double sa = Math.sin(NEBULA_ANGLE[n]);
                    float lx = (float) (dx0 * ca + dy0 * sa);
                    float ly = (float) (-dx0 * sa + dy0 * ca);
                    float nx = lx / (NEBULA_R[n] * w * NEBULA_STRETCH[n]);
                    float ny = ly / (NEBULA_R[n] * h);
                    float nd = (float) Math.sqrt(nx * nx + ny * ny);
                    if (nd < 1.0F) {
                        color = mix(color, NEBULA_C[n],
                                (float) Math.pow(1.0F - nd, 2.0D) * NEBULA_A[n]);
                    }
                }

                // 银河：带身 + 芯 + 丝状
                float band = bandDensity(x, y, w, h);
                if (band > 0.01F) {
                    float fil = (float) (Math.sin(x * 0.13F + y * 0.07F) * 0.5D
                            + Math.sin(x * 0.041F - y * 0.09F) * 0.5D);
                    color = mix(color, 0xFF9FC8C4, band * 0.12F * (0.7F + 0.3F * fil));
                    color = mix(color, 0xFFD8ECEA, band * band * 0.09F);
                    if (band > 0.6F && fil > 0.55F) {
                        color = mix(color, 0xFFEAF8F6, (band - 0.6F) * 0.45F);
                    }
                }
                // 暗尘带
                float dust = dustDensity(x, y, w, h);
                if (dust > 0.01F) {
                    color = mix(color, 0xFF03060A, dust * 0.58F * Math.max(0.25F, band));
                }
                img.setPixelRGBA(x, y, abgr(color));
            }
        }

        // ---- ①.5 **宇宙**：星系 + 大尺度星云（只在上方那一段）----
        for (int g = 0; g < GALAXIES; g++) {
            this.galaxy(img, GAL_X[g] * w, GAL_Y[g] * h, GAL_R[g] * w,
                    GAL_FLAT[g], GAL_ANG[g], GAL_BRIGHT[g], GAL_ARMS[g], rng);
        }
        // 大尺度星云团（比星空里的那些大得多、淡得多）
        for (int i = 0; i < 4; i++) {
            float nx = rng.nextFloat() * w;
            float ny = rng.nextFloat() * h * TreeLayout.UNIVERSE_END;
            float nr = w * (0.10F + rng.nextFloat() * 0.16F);
            int nc = (i % 2 == 0) ? 0xFF241A3A : 0xFF1A2A38;
            // 极淡的一团（二次方衰减，没有边界）
            for (int y = (int) (ny - nr); y <= ny + nr; y++) {
                for (int x = (int) (nx - nr); x <= nx + nr; x++) {
                    if (x < 0 || y < 0 || x >= w || y >= h) {
                        continue;
                    }
                    float dx = (x - nx) / nr;
                    float dy = (y - ny) / nr;
                    float d2 = dx * dx + dy * dy;
                    if (d2 >= 1.0F) {
                        continue;
                    }
                    this.blend(img, x, y, nc, (1.0F - d2) * 0.055F);
                }
            }
        }

        // ---- ② 星点（按高度遮罩 + 银河 + 星团加权）----
        int total = (int) (w * h * 0.0032F);      // 大约几千颗的基数
        for (int i = 0; i < total; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);
            float fy = y / (float) h;
            float mask = skyMask(fy);
            float band = bandDensity(x, y, w, h);
            float cluster = clusterBoost(x, y, w, h);
            // 留存概率 = 高度遮罩 ×（底噪 + 银河 + 星团）
            double keep = mask * (0.10D + band * 0.62D + cluster * 0.55D);
            if (rng.nextDouble() > keep) {
                continue;
            }
            // 暗尘带里的星被挡住
            if (dustDensity(x, y, w, h) > 0.5F) {
                continue;
            }
            this.star(img, x, y, rng);
        }

        // ---- ③ 亮星：大 + 六道衍射星芒（都在星空区）----
        int bright = 12 + rng.nextInt(6);
        for (int i = 0; i < bright; i++) {
            int bx = rng.nextInt(w);
            int by = (int) (h * (0.14F + rng.nextFloat() * 0.22F));   // 星空区
            int c = STAR_COLORS[pickColor(rng)];
            float size = 1.4F + rng.nextFloat() * 1.5F;
            this.disc(img, bx, by, size * 3.2F, c, 0.16F);
            this.disc(img, bx, by, size * 1.7F, c, 0.32F);
            this.disc(img, bx, by, size, 0xFFFFFFFF, 0.85F);
            int len = (int) (size * (7.0F + rng.nextFloat() * 6.0F));
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3.0D;
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                for (int s = 1; s <= len; s++) {
                    float kk = 1.0F - s / (float) len;
                    this.blend(img, (int) (bx + cos * s), (int) (by + sin * s), c,
                            kk * kk * 0.48F);
                }
            }
        }
        return img;
    }

    /**
     * **一个星系** —— 参考真实天文照片。
     *
     * <p>结构是三样东西叠出来的：</p>
     * <ol>
     *   <li><b>核</b>：中心一小团很亮的（星系核球）</li>
     *   <li><b>盘</b>：被拉扁成椭圆的整片光（{@code flat} 越小越扁）</li>
     *   <li><b>旋臂</b>：从核旋出去的两条（只有正面朝向的星系才看得到）</li>
     * </ol>
     */
    private void galaxy(NativeImage img, float cx, float cy, float r, float flat,
                        float ang, float bright, boolean arms, Random rng) {
        double ca = Math.cos(ang);
        double sa = Math.sin(ang);
        int rad = (int) (r * 1.3F);
        for (int y = (int) (cy - rad); y <= cy + rad; y++) {
            for (int x = (int) (cx - rad); x <= cx + rad; x++) {
                if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
                    continue;
                }
                // 转到星系自己的坐标（绕中心旋转），再按扁平度压扁
                float dx0 = x - cx;
                float dy0 = y - cy;
                float lx = (float) (dx0 * ca + dy0 * sa);
                float ly = (float) (-dx0 * sa + dy0 * ca) / Math.max(0.05F, flat);
                float d = (float) Math.sqrt(lx * lx + ly * ly) / Math.max(1.0F, r);
                if (d >= 1.0F) {
                    continue;
                }
            }
        }
        // 盘：靠近中心越来越亮（2.4 次方衰减 —— 边缘很糊，像真的星系）
        for (int y = (int) (cy - rad); y <= cy + rad; y++) {
            for (int x = (int) (cx - rad); x <= cx + rad; x++) {
                float dx0 = x - cx;
                float dy0 = y - cy;
                float lx = (float) (dx0 * ca + dy0 * sa);
                float ly = (float) (-dx0 * sa + dy0 * ca) / Math.max(0.05F, flat);
                float d = (float) Math.sqrt(lx * lx + ly * ly) / Math.max(1.0F, r);
                if (d >= 1.0F) {
                    continue;
                }
                float k = (float) Math.pow(1.0F - d, 2.4D) * 0.34F * bright;
                this.blend(img, x, y, 0xFFD8CCB8, k);
            }
        }
        // 核（一小团很亮）
        this.disc(img, (int) cx, (int) cy, r * 0.26F, 0xFFFFF4E0, 0.55F * bright);
        this.disc(img, (int) cx, (int) cy, r * 0.11F, 0xFFFFFFFF, 0.85F * bright);
        // 旋臂：两条从核旋出去
        if (arms) {
            for (int a = 0; a < 2; a++) {
                double base = a * Math.PI;
                for (int s = 3; s < (int) r; s++) {
                    double th = base + s * 0.055D;
                    float sx = cx + (float) Math.cos(th) * s;
                    float sy = cy + (float) Math.sin(th) * s * flat;
                    float fade = 1.0F - s / r;
                    this.blend(img, (int) sx, (int) sy, 0xFFC8BCD0, fade * fade * 0.22F * bright);
                    // 臂上有些亮点（年轻的星团）
                    if (rng.nextInt(9) == 0) {
                        this.blend(img, (int) sx, (int) sy, 0xFFFFFFFF, 0.20F * fade);
                    }
                }
            }
        }
    }

    /** 一颗星：亮度按幂律、颜色按真实比例。 */
    private void star(NativeImage img, int x, int y, Random rng) {
        float bright = (float) Math.pow(rng.nextDouble(), 3.0D);
        int c = STAR_COLORS[pickColor(rng)];
        if (bright < 0.20F) {
            this.blend(img, x, y, c, 0.30F);
        } else if (bright < 0.48F) {
            this.blend(img, x, y, c, 0.55F);
        } else if (bright < 0.74F) {
            this.disc(img, x, y, 1.2F, c, 0.80F);
            this.disc(img, x, y, 3.0F, c, 0.15F);
        } else {
            this.disc(img, x, y, 1.6F, 0xFFFFFFFF, 0.88F);
            this.disc(img, x, y, 3.8F, c, 0.24F);
        }
    }

    private int pickColor(Random rng) {
        int roll = rng.nextInt(100);
        int acc = 0;
        for (int i = 0; i < STAR_WEIGHTS.length; i++) {
            acc += STAR_WEIGHTS[i];
            if (roll < acc) {
                return i;
            }
        }
        return 1;
    }

    /* ================================================================== */
    /*  动态层：只有"会眨眼的星" + 一点星尘 + 暗角                          */
    /* ================================================================== */

    public void draw(GuiGraphics graphics, float heartbeat) {
        // 主线程：后台做好的那张在这儿变成纹理（这一句很快）
        this.takePending();
        if (this.texture == null) {
            // 还没好 —— 先铺一层底色，界面不至于全黑
            graphics.fill(0, 0, this.width, this.height, 0xFF02040A);
            return;
        }
        long now = System.currentTimeMillis();
        // 一次 blit：几千颗星、银河、星云全在这一张里
        graphics.blit(TEX, 0, 0, 0.0F, 0.0F, this.width, this.height, this.width, this.height);

        // 会眨眼的星（都在星空区，颜色和背景同一套）
        for (int i = 0; i < DYNAMIC_STARS; i++) {
            double seed = i * 24.7D;
            int sx = (int) ((Math.sin(seed) * 0.5D + 0.5D) * this.width);
            int sy = (int) (this.height * (0.15F + (Math.sin(seed * 1.7D + 0.9D) * 0.5D + 0.5D) * 0.18F));
            float blink = (float) (Math.sin(now / (1300.0D + i * 240.0D) + i * 2.1D) * 0.5D + 0.5D);
            int a = (int) (blink * DYNAMIC_PEAK);
            if (a <= 12) {
                continue;
            }
            int rgb = STAR_COLORS[i % STAR_COLORS.length] & 0xFFFFFF;
            graphics.fill(sx, sy, sx + 1, sy + 1, (a << 24) | rgb);
            if (a > 70) {
                graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, (a / 5 << 24) | rgb);
            }
        }

        // 星尘：几道极淡的光从左上往右下淌（星辉落下的方向）
        for (int i = 0; i < 3; i++) {
            float speed = 0.008F + i * 0.003F;
            float p = ((now / 1000.0F) * speed + i * 0.27F) % 1.0F;
            int px = (int) (this.width * (0.10F + i * 0.28F) - p * this.width * 0.30F);
            int py = (int) (this.height * 0.04F + p * this.height * 0.32F);
            int len = 50 + i * 18;
            int a = 8 + i * 2;
            for (int s = 0; s < len; s += 4) {
                int x = px + s * 3;
                int y = py + s;
                if (x < 0 || y < 0 || x > this.width || y > this.height) {
                    continue;
                }
                graphics.fill(x, y, x + 9, y + 1, (a << 24) | (SOUL & 0xFFFFFF));
            }
        }

        // 暗角
        int edge = (int) (Math.min(this.width, this.height) * 0.16F);
        for (int i = 0; i < 4; i++) {
            int a = 20 - i * 4;
            if (a <= 0) {
                continue;
            }
            int pad = edge * i / 4;
            graphics.fill(0, pad, this.width, pad + 5, (a << 24));
            graphics.fill(0, this.height - pad - 5, this.width, this.height - pad, (a << 24));
            graphics.fill(pad, 0, pad + 5, this.height, (a << 24));
            graphics.fill(this.width - pad - 5, 0, this.width - pad, this.height, (a << 24));
        }
    }

    /** **背景现在是个什么状态** —— 右上角那行诊断字用它。 */
    public String status() {
        if (this.texture != null) {
            return "星空 已就绪 " + this.width + "x" + this.height;
        }
        if (this.pendingW > 0) {
            return "星空 生成中…";
        }
        return "星空 未开始";
    }

    /** 当空之宫的星色（和背景星同一套色）。 */
    static int signColor(int index) {
        return STAR_COLORS[Math.floorMod(index, STAR_COLORS.length)];
    }

    /* ------------------------------------------------------------------ */

    private void disc(NativeImage img, int cx, int cy, float r, int color, float strength) {
        int rad = Math.max(1, (int) Math.ceil(r));
        for (int y = cy - rad; y <= cy + rad; y++) {
            for (int x = cx - rad; x <= cx + rad; x++) {
                float ddx = x - cx;
                float ddy = y - cy;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                if (d > r) {
                    continue;
                }
                this.blend(img, x, y, color, (1.0F - d / Math.max(0.6F, r)) * strength);
            }
        }
    }

    private void blend(NativeImage img, int x, int y, int argb, float k) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight() || k <= 0.002F) {
            return;
        }
        if (k > 1.0F) {
            k = 1.0F;
        }
        int base = img.getPixelRGBA(x, y);
        int br = base & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = (base >> 16) & 0xFF;
        int cr = (argb >> 16) & 0xFF;
        int cg = (argb >> 8) & 0xFF;
        int cb = argb & 0xFF;
        img.setPixelRGBA(x, y, abgr2(
                (int) (br + (cr - br) * k),
                (int) (bg + (cg - bg) * k),
                (int) (bb + (cb - bb) * k)));
    }

    private static int mix(int a, int b, float t) {
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

    private static int abgr(int argb) {
        return abgr2((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
    }

    private static int abgr2(int r, int g, int b) {
        return 0xFF000000 | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF);
    }
}
