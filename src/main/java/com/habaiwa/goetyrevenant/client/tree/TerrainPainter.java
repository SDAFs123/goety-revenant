package com.habaiwa.goetyrevenant.client.tree;

import java.util.Random;

/**
 * **地层绘制** —— 土壤 / 过渡层 / 血肉土壤。
 *
 * <p>从 `TreeTexture` 抽出来的（那个文件原本什么都干）。这一层只认
 * "内容图像素坐标"和画布原语，不认识纹理、缓存、视口那些事。</p>
 *
 * <h3>三段的画法</h3>
 * <pre>
 *   土壤      颗粒 + 层理 + 石子，星光照在土面上
 *   过渡层    纹理与土壤同密度，只有【颜色】从土色渐变到血肉色（亮度单向下降）
 *   血肉土壤  血浸透的土：血管网、颗粒、孔隙、血潭、深部血光
 * </pre>
 *
 * <p>用一次就丢：每次绘制 new 一个、传齐参数、调 `paint()`。不保留状态。</p>
 */
public final class TerrainPainter {
    private final TreeCanvas c;
    private final Random rng;
    /** 土壤表面（内容图坐标）。 */
    private final float soilTop;
    /** 土壤底 = 过渡层顶。 */
    private final float blendTop;
    /** 过渡层底 = 血肉土壤顶。 */
    private final float fleshTop;

    public TerrainPainter(TreeCanvas canvas, Random rng, float soilTop, float blendTop, float fleshTop) {
        this.c = canvas;
        this.rng = rng;
        this.soilTop = soilTop;
        this.blendTop = blendTop;
        this.fleshTop = fleshTop;
    }

    /** 画完整个地层（土壤顶 → 内容图底）。 */
    public void paint(float soilH, float trunkW, float trunkH) {
        float cx = this.c.width / 2.0F;
        this.soil(soilH);
        this.starLightOnSoil(cx, trunkW, trunkH * 0.42F);
        this.blendZone(this.blendTop, this.fleshTop);
        this.fleshLayer(this.fleshTop);
    }

    /* ------------------------------------------------------------------ */
    /*  配色（只被这一层用，所以放在这里）                                    */
    /* ------------------------------------------------------------------ */
    /** 土壤颗粒的两种土色（过渡层要用它和肉混）。 */
    private static final int SOIL_GRAIN_A = 0xFF4A3626;
    // ======================================================================
    //  **血肉土壤的色** —— 参考系：Terraria 的猩红之地（The Crimson）。
    //
    //  上一版我做成"褐土里掺血"，出来是【脏褐色】—— 那不对。
    //  猩红之地的土不是"褐土染红"，是【它本身就是红的】：
    //  红通道明显高于绿蓝，但暗、不鲜、偏紫。
    // ======================================================================
    // ----------------------------------------------------------------------
    //  **亮度必须单向下降** —— 对着上下两层的颜色重新排过。
    //
    //  之前是这样的：
    //      土壤 4A3626 (R 74) → 过渡底 5E2626 (R 94) → 血肉顶 5E2626 (R 94)
    //                                          ↑ 比土壤亮 20，再往下才掉下来
    //  于是：往下走先变亮、再变暗 —— 过渡层和上下都不是一回事，
    //  而且"靠血肉那头明显发亮"。
    //
    //  地下的规律是越深越暗（上面的光到不了下面）。所以重排成一条单调的链：
    //      土壤 R74 → 过渡 R74~66 → 血肉 R66 → R54 → R40
    //  并且让**过渡的底 == 血肉的顶**（同一个 421A1A），接缝处一个像素都不差。
    // ----------------------------------------------------------------------
    /** 红褐的最亮一档 —— 但比土壤（R 74）暗一点，这样往下是变暗的。 */
    private static final int FLESH_SOIL_TOP = 0xFF421A1A;
    /** 中层。 */
    private static final int FLESH_SOIL_MID = 0xFF361416;
    /** 深处。 */
    private static final int FLESH_SOIL_DEEP = 0xFF280E10;
    /** 颗粒的亮面（红调，但整体压暗 —— 之前 74322E 太跳）。 */
    private static final int FLESH_GRAIN = 0xFF542420;
    /** 颗粒的暗面。 */
    private static final int FLESH_GRAIN_DIM = 0xFF240A0C;
    /** 血管脉络：暗的。 */
    private static final int FLESH_VEIN = 0xFF28080C;
    /** 血管脉络：亮芯（血管可以比周围亮，但不要过 —— 之前 8A3238 太扎眼）。 */
    private static final int FLESH_VEIN_LIT = 0xFF6E2A30;
    /** 湿润的反光 —— 是暗红，不是白。 */
    private static final int FLESH_WET = 0xFF6E3A3E;


    /**
     * **血肉浸染的土壤**。
     *
     * <h3>它到底是什么</h3>
     * <p>不是一块肉，是<b>土</b> —— 只是被血浸透了。底子仍然是土：颗粒、层理、
     * 石子、大小三级的结构全在；区别在于<b>缝里填的是血</b>：</p>
     * <ul>
     *   <li>土的颗粒还是土的色，但颗粒之间那些缝是暗红的</li>
     *   <li>血顺着缝隙<b>往下渗</b> —— 留下一条条竖直的暗红痕</li>
     *   <li>深处血积成不规则的<b>暗块</b>（像凝住的地方）</li>
     *   <li>偶尔一点<b>组织残迹</b>：比周围的土更实、更深的不规则小块</li>
     *   <li>越深越暗 —— 深处几乎只剩黑色里透一点红</li>
     * </ul>
     *
     * <p>这样它和上面的土壤是<b>同一种东西</b>，只是浸染程度不同 ——
     * 过渡也就不需要"做出来"，因为它本来就是一回事。</p>
     */
    /**
     * **血肉土壤** —— 参考 Terraria 的猩红之地。
     *
     * <h3>和上一版的区别（关键在底色）</h3>
     * <p>上一版是"褐土 + 掺血"，出来是<b>脏褐色</b>；
     * 猩红之地的土不是褐土染红，是<b>它本身就是红的</b> ——
     * 红通道明显高于绿蓝、但暗、不鲜、偏紫。所以底色直接换成红褐，不掺。</p>
     *
     * <h3>四层结构</h3>
     * <ol>
     *   <li>底：红褐，越深越暗</li>
     *   <li><b>血管脉络</b>：暗紫红的分叉网络爬在土里（像地下的根须）——
     *       猩红之地最有辨识度的东西就是它</li>
     *   <li>颗粒：土的颗粒感还在，但颗粒本身是红调的</li>
     *   <li><b>肉质光泽</b>：局部暗红的湿润反光 —— 土不会这么亮，肉才会</li>
     * </ol>
     */
    private void soakedSoil(float top, float bottom) {
        float span = Math.max(1.0F, bottom - top);
        // ① 底：红褐（不掺土黄）
        for (int y = (int) top; y < bottom; y++) {
            float t = Math.max(0.0F, Math.min(1.0F, (y - top) / span));
            int c;
            if (t < 0.5F) {
                c = this.c.mix(FLESH_SOIL_TOP, FLESH_SOIL_MID, t * 2.0F);
            } else {
                c = this.c.mix(FLESH_SOIL_MID, FLESH_SOIL_DEEP, (t - 0.5F) * 2.0F);
            }
            // 逐像素抖 ±2：打散色带，同时让"底"本身也带一点颗粒感 ——
            // 就算哪里被颗粒漏过去，也不是一块平色
            for (int x = 0; x < this.c.width; x++) {
                int j = this.rng.nextInt(7) - 3;
                this.c.putRaw(x, y, this.c.shift(c, j));
            }
        }
        // ② **血管脉络** —— 猩红之地最认得出的东西。
        // 主脉往下走，沿途分叉出细的（递归），而不是一条条平行线。
        int mains = 12 + this.rng.nextInt(8);
        for (int i = 0; i < mains; i++) {
            float vx = this.rng.nextFloat() * this.c.width;
            float vy = top - span * 0.25F + this.rng.nextFloat() * span * 0.35F;
            double ang = Math.PI / 2.0D + (this.rng.nextDouble() - 0.5D) * 1.1D;
            int steps = (int) (span * (0.55F + this.rng.nextFloat() * 0.6F));
            float wid = 1.6F + this.rng.nextFloat() * 1.4F;
            for (int s = 0; s < steps; s++) {
                ang += (this.rng.nextDouble() - 0.5D) * 0.22D;
                vx += (float) Math.cos(ang) * 1.25F;
                vy += (float) Math.sin(ang) * 1.25F;
                if (vy < top || vy >= bottom || vx < 0 || vx >= this.c.width) {
                    break;
                }
                // 外圈暗、内芯亮一点 —— 像血在管子里
                this.c.disc((int) vx, (int) vy, wid + 1.6F, FLESH_VEIN, 0.55F);
                this.c.disc((int) vx, (int) vy, wid, 0xFF3E1216, 0.60F);
                this.c.blendDot((int) vx, (int) vy, FLESH_VEIN_LIT, 0.26F);
                // 沿途分细的（递归毛细血管）
                if (s % 22 == 0) {
                    this.c.capillary(vx, vy, ang + (this.rng.nextBoolean() ? 0.95D : -0.95D),
                            top, bottom, 2);
                }
            }
        }
        // ------------------------------------------------------------------
        //  ③ 颗粒：**密度必须和上面的土壤层、过渡层完全一致**。
        //
        //  之前这里是 0.55 / 0.10（比上面稀五倍）—— 于是底色露出来，
        //  看着就是"一片很深的红、没有纹理"。而那片红其实就是这张底。
        //  三层用同一套密度（2.6 / 0.75 / 0.14），质感才连得上。
        // ------------------------------------------------------------------
        int fine = (int) (this.c.width * span * 2.6F);
        for (int i = 0; i < fine; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            // 颗粒本色是红调的（这种土本来就红），一半亮面一半暗缝
            int c = this.rng.nextFloat() < 0.55F ? FLESH_GRAIN : FLESH_GRAIN_DIM;
            this.c.blendDot(x, y, c, 0.18F + this.rng.nextFloat() * 0.20F);
        }
        int mid = (int) (this.c.width * span * 0.75F);
        for (int i = 0; i < mid; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float r = 1.0F + this.rng.nextFloat() * 1.2F;
            this.c.disc(x, y, r, FLESH_SOIL_MID, 0.45F);
            this.c.blendDot(x - 1, y - 1, 0xFF6A3028, 0.28F);     // 受光面
            this.c.blendDot(x + 1, y + 1, FLESH_GRAIN_DIM, 0.26F); // 缝
        }
        int coarse = (int) (this.c.width * span * 0.14F);
        for (int i = 0; i < coarse; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float r = 1.8F + this.rng.nextFloat() * 2.4F;
            this.c.disc((int) (x + 1.6F), (int) (y + 1.6F), r, 0xFF140608, 0.38F);  // 投影
            this.c.disc(x, y, r, this.rng.nextFloat() < 0.5F ? FLESH_GRAIN : FLESH_SOIL_MID, 0.80F);
            this.c.disc((int) (x - r * 0.35F), (int) (y - r * 0.35F),
                    Math.max(0.7F, r * 0.5F), 0xFF8A4038, 0.52F);                  // 高光
        }
        // ④ **肉质光泽**：局部暗红的湿润反光（土不会这么亮）
        int sheen = 8 + this.rng.nextInt(8);
        for (int i = 0; i < sheen; i++) {
            float px = this.rng.nextFloat() * this.c.width;
            float py = top + this.rng.nextFloat() * span;
            float r = 3.0F + this.rng.nextFloat() * 9.0F;
            // 一层很淡的红光晕 + 中间一个亮点
            this.c.disc((int) px, (int) py, r, FLESH_SOIL_TOP, 0.20F);
            this.c.disc((int) px, (int) py, r * 0.30F, FLESH_WET, 0.30F);
            this.c.blendDot((int) (px - r * 0.2F), (int) (py - r * 0.2F), 0xFF7A4440, 0.22F);
        }
        // ⑤ 孔洞：暗色的孔隙（让质地透气）
        int pores = (int) (this.c.width * span * 0.05F);
        for (int i = 0; i < pores; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            this.c.disc(x, y, 0.9F + this.rng.nextFloat() * 1.4F, 0xFF140608, 0.34F);
        }
        // ⑥ 深处的凝血块
        int pools = 10 + this.rng.nextInt(7);
        for (int i = 0; i < pools; i++) {
            float px = this.rng.nextFloat() * this.c.width;
            float py = top + span * (0.45F + this.rng.nextFloat() * 0.5F);
            float r = 5.0F + this.rng.nextFloat() * 16.0F;
            int lobes = 2 + this.rng.nextInt(3);
            for (int l = 0; l < lobes; l++) {
                this.c.disc((int) (px + (this.rng.nextFloat() - 0.5F) * r),
                        (int) (py + (this.rng.nextFloat() - 0.5F) * r * 0.7F),
                        r * (0.4F + this.rng.nextFloat() * 0.5F), 0xFF20080A, 0.30F);
            }
        }
    }

    /**
     * **最深处的血光** —— 几团极暗的红，像底下还压着什么东西。
     *
     * <p>整层都是黑的，只有这几处透出一点点红，反而比一片亮更有重量。</p>
     */
    private void fleshGlowPockets(float top, int bottom) {
        int n = 4 + this.rng.nextInt(3);
        for (int i = 0; i < n; i++) {
            float gx = this.rng.nextFloat() * this.c.width;
            float gy = top + this.rng.nextFloat() * Math.max(1.0F, bottom - top);
            float gr = 12.0F + this.rng.nextFloat() * 26.0F;
            this.c.disc((int) gx, (int) gy, gr, 0xFF3A0A10, 0.30F);
            this.c.disc((int) gx, (int) gy, gr * 0.55F, 0xFF5A1016, 0.26F);
            this.c.disc((int) gx, (int) gy, gr * 0.22F, 0xFF8A2028, 0.22F);
        }
    }

    /**
     * **深层**：暗红、粗纤维、光很少。
     *
     * <p>这里已经不像"肉"了，更像压实的组织 —— 所以纤维更粗、更暗，
     * 明暗差也拉开。</p>
     */
    private void fleshDeep(float top, int bottom) {
        float span = Math.max(1.0F, bottom - top);
        // 底：压暗（同样用"叠"不用"盖"，顶部让上面的肌肉透下来）
        for (int y = (int) top; y < bottom; y++) {
            float t = (y - top) / span;
            float op = Math.min(1.0F, t / 0.35F);
            op = op * op * (3.0F - 2.0F * op);
            for (int x = 0; x < this.c.width; x++) {
                this.c.blendDot(x, y, 0xFF20090C, op * 0.55F);
            }
        }
        // 粗纤维：更宽、更暗、明暗对比更强
        int coarse = (int) (span * 1.1F);
        for (int i = 0; i < coarse; i++) {
            int y = (int) top + this.rng.nextInt(Math.max(1, (int) span));
            int len = 30 + this.rng.nextInt(Math.max(10, (int) (this.c.width * 0.55F)));
            int x0 = this.rng.nextInt(Math.max(1, this.c.width - len));
            boolean light = this.rng.nextFloat() < 0.4F;
            int c = light ? 0xFF6E1A20 : 0xFF0E0406;
            int thick = 1 + this.rng.nextInt(2);
            for (int x = x0; x < x0 + len; x++) {
                for (int dy = 0; dy < thick; dy++) {
                    this.c.blendDot(x, y + dy, c, 0.2F + this.rng.nextFloat() * 0.14F);
                }
            }
        }
        // 几块压实的暗块
        for (int i = 0; i < 16; i++) {
            this.c.disc(this.rng.nextInt(Math.max(1, this.c.width)),
                    (int) (top + this.rng.nextFloat() * span),
                    6.0F + this.rng.nextFloat() * 16.0F, 0xFF0A0304, 0.32F);
        }
    }

    /**
     * **血管网**：粗的主血管 + 递归分叉出去的毛细血管。
     *
     * <p>主血管外圈暗、内芯亮（像真的在透血）；分叉出来的越来越细、越来越淡。</p>
     */
    private void fleshVessels(float top, float bottom) {
        float span = Math.max(1.0F, bottom - top);
        int mains = 4;
        for (int v = 0; v < mains; v++) {
            double x = this.rng.nextDouble() * this.c.width;
            double y = top + this.rng.nextDouble() * span;
            double ang = this.rng.nextDouble() * Math.PI * 2.0D;
            float wid = 2.6F + this.rng.nextFloat() * 1.6F;
            int steps = 160 + this.rng.nextInt(140);
            for (int s = 0; s < steps; s++) {
                ang += (this.rng.nextDouble() - 0.5D) * 0.20D;
                x += Math.cos(ang) * 1.2D;
                y += Math.sin(ang) * 1.2D;
                if (x < 0 || y < top || x >= this.c.width || y >= bottom) {
                    break;
                }
                // 外圈暗
                this.c.disc((int) x, (int) y, wid + 1.8F, 0xFF2A080C, 0.42F);
                // 内芯亮（血）
                this.c.blendDot((int) x, (int) y, 0xFFA82C32, 0.55F);
                this.c.blendDot((int) x, (int) y, 0xFFD25A60, 0.22F);
                // 每隔一段分一条细的出去
                if (s % 26 == 0) {
                    this.c.capillary(x, y, ang + (this.rng.nextBoolean() ? 0.9D : -0.9D),
                            top, bottom, 3);
                }
            }
        }
    }

    /**
     * **过渡层：土和血接在一起的那一段**。
     *
     * <p>之前这里是一条硬边（土画完了直接开始画肉），那也是"两个层不是一个精细度"
     * 最刺眼的地方。真实的交界是这样的：</p>
     * <ul>
     *   <li>边界<b>不规则</b> —— 土往下啃进肉里，肉往上顶进土里，犬牙交错</li>
     *   <li>土壤颗粒<b>散落在肉里</b>，越往下越少（不是突然消失）</li>
     *   <li>肉的纤维从土里<b>长出来</b> —— 贴着边界就开始了，只是被土盖住一半</li>
     *   <li>颜色互相渗透：土色往肉里褪成暗褐，血色往土里洇成暗红</li>
     * </ul>
     */
    /**
     * **过渡层 = 颜色渐变**。
     *
     * <p>就该是这样，别的都不加。我之前往这里塞了颗粒变化、血管爬升、
     * 犬牙边界、中段压暗 —— 那些都是多余的，反而把"渐变"这件事弄糊了：
     * 眼睛看到的是那些花样，而不是"土在变红"。</p>
     *
     * <p>所以这里现在只做一件事：</p>
     * <pre>
     *   上面   土的颜色      SOIL_GRAIN_A  (4A3626)
     *     ↓    平滑曲线（smoothstep）
     *   下面   血肉土壤的颜色 FLESH_SOIL_TOP (5E2626)
     * </pre>
     *
     * <p>唯一保留的技术处理：<b>极轻的逐像素抖动</b>。
     * 8 位色深下一条纯粹的渐变会出现看得见的横向色带（banding）——
     * 抖 ±2 就能把它打散，而肉眼完全看不出来。</p>
     */
    /**
     * **土壤过渡层** —— 它<b>首先还是土壤</b>。
     *
     * <h3>两件事是分开的</h3>
     * <p>「颜色在变」和「有没有纹理」是两件事，不能拿一个换另一个：</p>
     * <ul>
     *   <li><b>纹理</b>：颗粒、层理、石子 —— 全都在，密度和上面那一层一样
     *       （细砂 / 中粒 / 粗砾 三级，同一条做法）</li>
     *   <li><b>颜色</b>：从土褐（SOIL_GRAIN_A）渐变到血肉土壤的红褐（FLESH_SOIL_TOP）</li>
     * </ul>
     * <p>颗粒自己也跟着红化 —— 是「这种土本来就是这个颜色」，
     * 而不是「土上面蒙了一层红」。</p>
     *
     * <p>上一版我把纹理全删了、只留下颜色渐变，那就成了一层模糊的色块 ——
     * 那不是土壤，什么都不像。</p>
     */
    private void blendZone(float top, float bottom) {
        float span = Math.max(1.0F, bottom - top);
        // ① 底：颜色渐变（smoothstep + 极轻抖动打散色带）
        for (int y = (int) top; y < (int) bottom; y++) {
            float t = Math.max(0.0F, Math.min(1.0F, (y - top) / span));
            float k = t * t * (3.0F - 2.0F * t);
            int base = this.c.mix(SOIL_GRAIN_A, FLESH_SOIL_TOP, k);
            for (int x = 0; x < this.c.width; x++) {
                int j = this.rng.nextInt(5) - 2;
                this.c.putRaw(x, y, this.c.shift(base, j));
            }
        }
        // ② 细砂：密度和上面土壤层一样（width × span × 2.6）
        int fine = (int) (this.c.width * span * 2.6F);
        for (int i = 0; i < fine; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float k = (y - top) / span;
            // 砂粒本色也随深度红化
            int c = this.c.mix(this.c.mix(TreeCanvas.DIRT[4], TreeCanvas.DIRT[0], this.rng.nextFloat()),
                    FLESH_GRAIN, k * 0.9F);
            this.c.blendDot(x, y, c, 0.20F + this.rng.nextFloat() * 0.20F);
        }
        // ③ 中粒：受光面（左上）+ 缝（右下）
        int mid = (int) (this.c.width * span * 0.75F);
        for (int i = 0; i < mid; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float k = (y - top) / span;
            float r = 1.0F + this.rng.nextFloat() * 1.2F;
            this.c.disc(x, y, r, this.c.mix(TreeCanvas.DIRT[1], FLESH_SOIL_MID, k), 0.45F);
            this.c.blendDot(x - 1, y - 1, this.c.mix(TreeCanvas.DIRT[5], 0xFF8A4038, k), 0.30F);
            this.c.blendDot(x + 1, y + 1, this.c.mix(TreeCanvas.DIRT[0], FLESH_GRAIN_DIM, k), 0.26F);
        }
        // ④ 粗砾：投影 + 高光
        int coarse = (int) (this.c.width * span * 0.14F);
        for (int i = 0; i < coarse; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float k = (y - top) / span;
            float r = 1.8F + this.rng.nextFloat() * 2.4F;
            this.c.disc((int) (x + 1.6F), (int) (y + 1.6F), r,
                    this.c.mix(0xFF100A06, 0xFF140608, k), 0.38F);
            this.c.disc(x, y, r, this.c.mix(this.rng.nextFloat() < 0.4F ? TreeCanvas.DIRT[4] : TreeCanvas.DIRT[1],
                    FLESH_GRAIN, k), 0.82F);
            this.c.disc((int) (x - r * 0.35F), (int) (y - r * 0.35F),
                    Math.max(0.7F, r * 0.5F), this.c.mix(TreeCanvas.DIRT[5], 0xFF8A4038, k), 0.55F);
        }
        // ⑤ 层理：**和土壤层一样多（14 条）**。
        // 之前这里只有 4 条 —— 而那正是"土壤纹理"的另一半：
        // 颗粒给颗粒感，层理给"一层一层压出来的"横纹。
        // 少了层理，看着就只有砂粒没有土。颜色跟着一起红化。
        int strata = 14;
        for (int i = 0; i < strata; i++) {
            float baseY = top + span * (0.12F + 0.76F * i / (float) strata
                    + (this.rng.nextFloat() - 0.5F) * 0.05F);
            int th = 1 + this.rng.nextInt(3);
            float k = (baseY - top) / span;
            int c = this.c.mix(this.rng.nextBoolean() ? TreeCanvas.DIRT[1] : TreeCanvas.DIRT[3], FLESH_SOIL_DEEP, k);
            float alpha = 0.28F + this.rng.nextFloat() * 0.26F;
            for (int x = 0; x < this.c.width; x++) {
                if (this.rng.nextFloat() < 0.16F) {
                    continue;
                }
                float wave = (float) Math.sin(x * 0.03F + i * 1.7F) * 1.5F;
                for (int dy = 0; dy < th; dy++) {
                    this.c.blendDot(x, (int) (baseY + wave) + dy, c, alpha);
                }
            }
        }
    }

    /**
     * **血肉层** —— 土壤往下就是它。
     *
     * <p>暗红的肉，有肌束、有横着的肌纤维、有蜿蜒的血管、有血斑；
     * 和土壤交界的地方是一层"土里渗血"的过渡。树根扎进这里 ——
     * 这一层也就是<b>血肉晋升线</b>的所在（将来那条线的节点长在这片肉里）。</p>
     */
    private void fleshLayer(float top) {
        int bottom = this.c.height;
        float span = Math.max(1.0F, bottom - top);
        // （这里原来先铺了一层"暗底"，一整片 width×height 的循环。
        //  但下面 soakedSoil 的第一件事就是把整片覆盖掉 ——
        //  那层暗底从来没被人看见过，纯白算一遍。删掉。）
        // ==================================================================
        //  按深度分层 —— 越往下越暗、越粗、越少光。
        //  这不是"一层红底"，是一块有层次的肉：
        //      0.00 ~ 0.08  交界：土里渗血
        //      0.08 ~ 0.30  浅层：亮红的细肉，纤维密、有脂肪网
        //      0.30 ~ 0.65  中层：主血管网、成组的肌束、淤血
        //      0.65 ~ 0.88  深层：暗红、粗纤维、光很少
        //      0.88 ~ 1.00  最深处：近黑，只剩几团隐约的血光
        // ==================================================================
        // 过渡已经在外面作为独立的一段画过了（blendZone），这里不再重复。
        // **血浸的土** —— 这一层不是"一块肉"，是土被血浸透了。
        // **从这一层的最顶上开始画**（之前从 +10% 开始，于是最上面那一条
        // 没有任何东西覆盖，露出的只是下面那层"暗底"—— 就是那条没纹理的深红）。
        this.soakedSoil(top, bottom);
        this.fleshVessels(top + span * 0.10F, top + span * 0.72F);
        this.fleshDeep(top + span * 0.62F, bottom);
        this.fleshGlowPockets(top + span * 0.80F, bottom);
        return;
    }

    /**
     * 一层真正的土：有厚度、有层理、有石子、表层起伏、上面长着灵魂色的苔。
     */
    /**
     * **星光照在土上** —— 这是"树站在星光下"最直接的证据。
     *
     * <p>三件事：</p>
     * <ol>
     *   <li>土壤表面一条冷白的高光带（往下迅速衰减）</li>
     *   <li>贴着树根的一圈额外亮 —— 树干挡光，光在它脚边堆起来</li>
     *   <li>树冠在土上投一片椭圆阴影，<b>偏右下</b>（因为光来自左上）</li>
     * </ol>
     */
    private void starLightOnSoil(float cx, float trunkW, float canopyR) {
        int top = (int) this.soilTop;
        // ① 表面高光带
        for (int y = top; y < top + 14; y++) {
            float k = 1.0F - (y - top) / 14.0F;
            for (int x = 0; x < this.c.width; x++) {
                this.c.blendDot(x, y, TreeCanvas.STARLIGHT, k * 0.09F);
            }
        }
        // ② 树脚那一圈堆起来的光
        int footW = (int) Math.max(10.0F, trunkW * 4.0F);
        for (int x = (int) cx - footW; x < cx + footW; x++) {
            float d = Math.abs(x - cx) / (float) footW;
            float k = (1.0F - d) * 0.20F;
            for (int y = top; y < top + 20; y++) {
                this.c.blendDot(x, y, 0xFFE8F4FF,
                        k * (1.0F - (y - top) / 20.0F));
            }
        }
        // ③ 树冠的投影（偏右下）
        float shCx = cx + canopyR * 0.35F;
        float shCy = this.soilTop + canopyR * 0.12F;
        float shRx = canopyR * 0.95F;
        float shRy = Math.max(6.0F, canopyR * 0.22F);
        for (int y = (int) (shCy - shRy); y < shCy + shRy; y++) {
            for (int x = (int) (shCx - shRx); x < shCx + shRx; x++) {
                float dx = (x - shCx) / shRx;
                float dy = (y - shCy) / shRy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= 1.0F) {
                    continue;
                }
                this.c.blendDot(x, y, 0xFF05080C, (1.0F - d) * 0.40F);
            }
        }
    }

    private void soil(float soilH) {
        int top = (int) this.soilTop;
        int h = this.c.height;

        // 深土底
        for (int y = top; y < h; y++) {
            float t = (y - top) / Math.max(1.0F, soilH);
            int c = this.c.mix(TreeCanvas.DIRT[2], TreeCanvas.DIRT[0], Math.min(1.0F, t * 1.25F));
            int packed = TreeCanvas.abgr(c);
            for (int x = 0; x < this.c.width; x++) {
                this.c.putAbgr(x, y, packed);
            }
        }
        // 层理：沉积的横带。6 条太稀（看着像"几条脏线"），加到 14 条，
        // 并且每条有自己的厚度和断续 —— 这样才像一层一层压出来的。
        int strata = 14;
        for (int i = 0; i < strata; i++) {
            float baseY = top + soilH * (0.08F + 0.86F * i / (float) strata
                    + (this.rng.nextFloat() - 0.5F) * 0.04F);
            int th = 1 + this.rng.nextInt(4);
            int c = this.rng.nextBoolean() ? TreeCanvas.DIRT[1] : TreeCanvas.DIRT[3];
            float alpha = 0.30F + this.rng.nextFloat() * 0.30F;
            for (int x = 0; x < this.c.width; x++) {
                // 断续：一条层理不是从头连到尾的
                if (this.rng.nextFloat() < 0.16F) {
                    continue;
                }
                float wave = (float) Math.sin(x * 0.03F + i * 1.7F) * 1.6F;
                for (int dy = 0; dy < th; dy++) {
                    this.c.blendDot(x, (int) (baseY + wave) + dy, c, alpha);
                }
            }
        }
        // 石子
        int stones = 40 + this.c.width / 12;
        for (int i = 0; i < stones; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = top + 4 + this.rng.nextInt(Math.max(1, (int) soilH - 6));
            int r = 1 + this.rng.nextInt(3);
            int c = this.rng.nextFloat() < 0.4F ? TreeCanvas.DIRT[4] : TreeCanvas.DIRT[1];
            this.c.disc(x, y, r, c, 0.85F);
            this.c.disc(x - r / 3, y - r / 3, Math.max(0.6F, r * 0.4F), TreeCanvas.DIRT[5], 0.5F);
        }
        // ------------------------------------------------------------------
        //  **颗粒：做到纹理级** —— 和血肉那边一样，密度才是关键。
        //
        //  真实的土是无数颗粒挤在一起。之前这里只有一层单点、alpha 0.28，
        //  撒出来看着像"灰底上落了点灰"，不是土。
        //
        //  三级结构刻意和血肉对齐：
        //     ① 细砂（1px，数量最多）  ←→  肌纤维
        //     ② 中粒（带受光面）        ←→  肌束
        //     ③ 粗砾（带高光与投影）    ←→  层
        //  两边的最小单位、层次数都一样，放在一起才不会一个粗一个细。
        // ------------------------------------------------------------------
        // ① 细砂：极多，颜色在土色里随机取
        int fineGrains = (int) (this.c.width * soilH * 2.6F);
        for (int i = 0; i < fineGrains; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = top + this.rng.nextInt(Math.max(1, (int) soilH));
            int c = this.c.mix(TreeCanvas.DIRT[4], TreeCanvas.DIRT[0], this.rng.nextFloat());
            this.c.blendDot(x, y, c, 0.20F + this.rng.nextFloat() * 0.20F);
        }
        // ② 中粒：有受光面（左上亮、右下暗）
        int midGrains = (int) (this.c.width * soilH * 0.75F);
        for (int i = 0; i < midGrains; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = top + this.rng.nextInt(Math.max(1, (int) soilH));
            float r = 1.0F + this.rng.nextFloat() * 1.2F;
            this.c.disc(x, y, r, TreeCanvas.DIRT[1], 0.45F);
            this.c.blendDot(x - 1, y - 1, TreeCanvas.DIRT[5], 0.30F);
            this.c.blendDot(x + 1, y + 1, TreeCanvas.DIRT[0], 0.26F);
        }
        // ③ 粗砾：更大，有投影也有高光
        int coarseGrains = (int) (this.c.width * soilH * 0.14F);
        for (int i = 0; i < coarseGrains; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = top + this.rng.nextInt(Math.max(1, (int) soilH));
            float r = 1.8F + this.rng.nextFloat() * 2.4F;
            this.c.disc((int) (x + 1.6F), (int) (y + 1.6F), r, 0xFF100A06, 0.38F);   // 投影
            this.c.disc(x, y, r, this.rng.nextFloat() < 0.4F ? TreeCanvas.DIRT[4] : TreeCanvas.DIRT[1], 0.82F);
            this.c.disc((int) (x - r * 0.35F), (int) (y - r * 0.35F),
                    Math.max(0.7F, r * 0.5F), TreeCanvas.DIRT[5], 0.55F);                     // 受光面
        }
        // 表层起伏：用一串小丘把土面画出来（土色覆盖上面）
        int x2 = 0;
        while (x2 < this.c.width) {
            int hump = 8 + this.rng.nextInt(26);
            int up = this.rng.nextInt(5);
            for (int x = x2; x < Math.min(this.c.width, x2 + hump); x++) {
                float k = (x - x2) / (float) hump;
                int lift = (int) (Math.sin(k * Math.PI) * up);
                for (int y = top - lift; y < top - lift + 3; y++) {
                    this.c.blendDot(x, y, TreeCanvas.DIRT[3], 0.9F);
                }
                for (int y = top - lift + 3; y < Math.min(this.c.height, top - lift + 7); y++) {
                    this.c.blendDot(x, y, TreeCanvas.DIRT[4], 0.55F);
                }
            }
            x2 += hump;
        }
        // **三层界**：表土 / 心土 / 母质 —— 两条起伏的界线把它们分开
        for (int band = 1; band <= 2; band++) {
            float baseY = top + soilH * (band == 1 ? 0.30F : 0.68F);
            for (int x = 0; x < this.c.width; x++) {
                float wave = (float) Math.sin(x * 0.11F + band * 2.3F) * 2.4F
                        + (this.rng.nextFloat() - 0.5F) * 1.6F;
                int yy = (int) (baseY + wave);
                this.c.blendDot(x, yy, band == 1 ? TreeCanvas.DIRT[4] : TreeCanvas.DIRT[1], 0.34F);
                this.c.blendDot(x, yy + 1, band == 1 ? TreeCanvas.DIRT[3] : TreeCanvas.DIRT[0], 0.22F);
            }
        }
        // **有机质**：表土层里的小团块（腐殖那点黑褐色）
        int organics = this.c.width / 2;
        for (int i = 0; i < organics; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            int y = top + 2 + this.rng.nextInt(Math.max(1, (int) (soilH * 0.30F)));
            this.c.disc(x, y, 0.8F + this.rng.nextFloat() * 1.7F, 0xFF1A0E06, 0.4F);
        }

        // 苔：长在表层上的灵魂色短草
        for (int i = 0; i < this.c.width / 3; i++) {
            int x = this.rng.nextInt(Math.max(1, this.c.width));
            float hgt = 1.0F + this.rng.nextFloat() * 3.5F;
            int base = top - (int) (this.rng.nextFloat() * 4.0F);
            boolean lit = this.rng.nextFloat() < 0.22F;
            int c = lit ? TreeCanvas.MOSS_LIT : TreeCanvas.MOSS;
            for (int k = 0; k < hgt; k++) {
                this.c.blendDot(x, base - k, c, lit ? 0.85F : 0.6F);
            }
        }
    }

}
