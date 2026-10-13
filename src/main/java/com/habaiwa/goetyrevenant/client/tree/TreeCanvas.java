package com.habaiwa.goetyrevenant.client.tree;

import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * **像素画布** —— 魂树内容图的绘制底座。
 *
 * <p>从 `TreeTexture` 里抽出来的第一层（那个文件原本 2800 多行，什么都干）。</p>
 *
 * <h3>为什么像素读写要走内存</h3>
 * <p>原版 `NativeImage.get/setPixelRGBA` 每次调用都要过一次原生边界（几十纳秒）。
 * 看着不多，但内容图放大之后颗粒是<b>上千万</b>级的（数量必须跟面积走，
 * 少了屏幕上的密度就降），总开销到了几十秒，界面直接未响应。</p>
 *
 * <p>所以这里反射拿到像素首地址，之后用 `MemoryUtil` 直接读写 ——
 * <b>视觉上完全一样</b>，只是不再每次过 JNI。拿不到指针时自动退回原路径。</p>
 *
 * <h3>坐标系</h3>
 * <p>所有坐标都是<b>内容图像素坐标</b>，左上为原点。内容图比屏幕大，
 * 视口变换由 `TreeTexture.drawView` 负责，这一层只认内容图坐标。</p>
 *
 * <h3>颜色打包</h3>
 * <p>对外一律用 `0xAARRGGBB`（和原版直觉一致），内部存的是 **ABGR**
 * —— `NativeImage` 的格式就是这样，所以 `put`/`get` 面对的是 ABGR，
 * 转换在 `abgr()` 里做。</p>
 */
public abstract class TreeCanvas {
    /** 当前图像素首地址（0 = 没拿到，退回原路径）。 */
    protected long pixels;
    /** 每行多少像素。 */
    protected int stride;

    /** 画布宽高（内容图像素）。 */
    protected int width = -1;
    protected int height = -1;

    /** 像素载体。子类负责在尺寸变化时重建它并调用 `resolvePixels`。 */
    protected NativeImage image;

    // ----------------------------------------------------------------------
    //  有内容范围的测量 —— 用来判断"裁掉透明区"值不值得做
    // ----------------------------------------------------------------------
    protected int inkX0 = Integer.MAX_VALUE;
    protected int inkY0 = Integer.MAX_VALUE;
    protected int inkX1 = Integer.MIN_VALUE;
    protected int inkY1 = Integer.MIN_VALUE;

    /** 记一下"这个像素被写过"。 */
    protected void ink(int x, int y) {
        if (x < this.inkX0) {
            this.inkX0 = x;
        }
        if (y < this.inkY0) {
            this.inkY0 = y;
        }
        if (x + 1 > this.inkX1) {
            this.inkX1 = x + 1;
        }
        if (y + 1 > this.inkY1) {
            this.inkY1 = y + 1;
        }
    }

    /** 反射拿像素首地址；失败就退回 JNI 路径（功能不受影响）。 */
    protected void resolvePixels(NativeImage img) {
        this.pixels = 0L;
        if (img == null) {
            return;
        }
        try {
            java.lang.reflect.Field f = NativeImage.class.getDeclaredField("pixels");
            f.setAccessible(true);
            this.pixels = (long) f.get(img);
            this.stride = img.getWidth();
        } catch (Throwable t) {
            this.pixels = 0L;
        }
    }

    /** 直接铺一个像素（ABGR）。 */
    protected void put(int x, int y, int abgr) {
        // 顺手记一下有内容的范围（"裁透明区"值不值得做的依据）。
        // 这里是所有写入的最底层，几次整数比较，JIT 会优化掉。
        this.ink(x, y);
        org.lwjgl.system.MemoryUtil.memPutInt(
                this.pixels + ((long) y * this.stride + x) * 4L, abgr);
    }

    protected int get(int x, int y) {
        return org.lwjgl.system.MemoryUtil.memGetInt(
                this.pixels + ((long) y * this.stride + x) * 4L);
    }

    /** 同一个用途，但值已经是 ABGR（各层的底都是这么算出来的）。 */
    protected void putAbgr(int x, int y, int abgrValue) {
        if (this.pixels != 0L) {
            this.put(x, y, abgrValue);
        } else {
            this.image.setPixelRGBA(x, y, abgrValue);
        }
    }

    /** 直接铺一个像素（不走混合，用于底色那种整片填充）。 */
    protected void putRaw(int x, int y, int argb) {
        int abgr = abgr(argb);
        if (this.pixels != 0L) {
            this.put(x, y, abgr);
        } else {
            this.image.setPixelRGBA(x, y, abgr);
        }
    }

    /** 一个柔边圆点（中心亮、边缘淡）。 */
    protected void disc(int cx, int cy, float r, int color, float strength) {
        int rad = Math.max(1, (int) Math.ceil(r));
        for (int y = cy - rad; y <= cy + rad; y++) {
            for (int x = cx - rad; x <= cx + rad; x++) {
                float dx = x - cx;
                float dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > r) {
                    continue;
                }
                float k = (1.0F - d / Math.max(0.6F, r)) * 0.55F + 0.55F;
                this.blendDot(x, y, color, k * strength);
            }
        }
    }

    /** 把一个颜色按比例混到某个像素上（越界的直接忽略）。 */
    protected void blendDot(int x, int y, int color, float k) {
        if (x < 0 || y < 0 || x >= this.width || y >= this.height) {
            return;
        }
        k = Math.max(0.0F, Math.min(1.0F, k));
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;
        int ca = (color >>> 24) & 0xFF;
        if (this.pixels != 0L) {
            // 快路径：直接读内存里的像素（存的是 ABGR，低字节是 R）
            int base = this.get(x, y);
            int br = base & 0xFF;
            int bg = (base >> 8) & 0xFF;
            int bb = (base >> 16) & 0xFF;
            int ba = (base >>> 24) & 0xFF;
            int nr = (int) (br + (cr - br) * k);
            int ng = (int) (bg + (cg - bg) * k);
            int nb = (int) (bb + (cb - bb) * k);
            int na = (int) Math.max(ba, ca * k);
            this.put(x, y, (na << 24) | (nb << 16) | (ng << 8) | nr);
            return;
        }
        // 原路径（拿不到指针时）
        int base = this.image.getPixelRGBA(x, y);
        int br = base & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = (base >> 16) & 0xFF;
        int ba = (base >>> 24) & 0xFF;
        int nr = (int) (br + (cr - br) * k);
        int ng = (int) (bg + (cg - bg) * k);
        int nb = (int) (bb + (cb - bb) * k);
        int na = (int) Math.max(ba, ca * k);
        this.image.setPixelRGBA(x, y, (na << 24) | (nb << 16) | (ng << 8) | nr);
    }

    /** 两个 ABGR 色按 t 线性混合。 */
    protected int mix(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int ar = a & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = (a >> 16) & 0xFF;
        int br = b & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = (b >> 16) & 0xFF;
        return 0xFF000000
                | ((int) (ab + (bb - ab) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ar + (br - ar) * t);
    }

    /** 0xAARRGGBB → NativeImage 要的 ABGR 打包。 */
    protected static int abgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** 有内容的那块占整张图的百分比（诊断用）。 */
    public String inkReport() {
        if (this.inkX1 <= this.inkX0) {
            return "（还没画东西）";
        }
        long full = (long) this.width * this.height;
        long used = (long) (this.inkX1 - this.inkX0) * (this.inkY1 - this.inkY0);
        return String.format("内容区 %dx%d = 整张的 %.1f%%",
                this.inkX1 - this.inkX0, this.inkY1 - this.inkY0, used * 100.0D / full);
    }

    /* ------------------------------------------------------------------ */
    /*  公共配色（两个画笔共用，放这里免得各抄一份）                          */
    /* ------------------------------------------------------------------ */
    /** 土色阶（深 → 浅）。 */
    protected static final int[] DIRT = {
            0xFF160E0A, 0xFF241611, 0xFF31201A, 0xFF3E2A21, 0xFF4C352A, 0xFF5A4033};

    /* ------------------------------------------------------------------ */
    /*  共用状态与工具（多个画笔都要用，放这层免得各抄一份）                    */
    /* ------------------------------------------------------------------ */
    /** 绘制用的随机源 —— 每个内容图一份，保证同一存档同一棵树长得一样。 */
    protected Random rng;

    /** 当前阶数（决定树长多大）—— 树、根、种子都要读它。 */
    protected int stage = -2;

    /** 叶色阶（暗 → 亮）。 */
    protected static final int[] LEAF = {
            0xFF0C2A22, 0xFF10402F, 0xFF16543B, 0xFF1D6A49, 0xFF278059,
            0xFF34966B, 0xFF45AC80, 0xFF5CC197};
    /** 实际使用的叶子色阶（按序列染过色的那种；现在不染，等于 LEAF）。 */
    protected int[] leaf = LEAF;
    /** 嵌入槽的选择状态（界面传进来，决定哪片叶子是亮的）。 */
    protected int[] choices;
    /** 实际使用的树皮色阶。 */
    protected int[] bark;
    /** **湿润高光**：压到暗红灰（地下的湿，只是"不干"，不是"反光"）。 */
    protected static final int WET = 0xFF7A4E52;

    /** 光源方向（左上那颗亮星）—— 枝干与土面的受光面都按它算。 */
    protected static final float LIGHT_X = -0.62F;
    protected static final float LIGHT_Y = -0.78F;
    /** 树皮的高光色。 */
    protected static final int BARK_LIGHT = 0xFFA8C4D8;

    // ==== 血肉层用到的色（按解剖学的真实层次取色，不是一支红刷到底）====
    // ======================================================================
    //  **血肉的色，全部按"地下无光"来取。**
    //
    //  我第一版画的是**手术台上的新鲜肉**：鲜红、油亮、高饱和。那是**有强光**的样子。
    //  而这里是土壤底下 —— 没有光。真实的、无光环境里的血肉：
    //
    //      · 深暗的红褐，明度只有 15~30%（接近黑）
    //      · 偏紫、偏褐（血氧化了、缺氧了）
    //      · 饱和度低 —— 不是"红"，是"暗红里带褐"
    //
    //  **更关键的一条**：明度必须和土壤（约 0x4A3626，R≈74）落在同一个区间。
    //  差一倍的两层摆在一起，永远像拼贴 —— 加多厚的过渡带都救不回来。
    //  所以下面这些值的红通道全部压在 60~90 之间，和土一个量级。
    // ======================================================================
    /** 肌肉（"鲜"也只是暗红 —— 地下没有光）。R≈74，和土壤同级。 */
    protected static final int MEAT_FRESH = 0xFF4A1418;
    /** 深一点的肌肉。 */
    protected static final int MEAT_DEEP = 0xFF361014;
    /** 压实的组织。 */
    protected static final int MEAT_DARK = 0xFF240A0E;

    /** 树皮的默认色阶。 */
    /** 树皮色阶（暗 → 亮）。 */
    protected static final int[] BARK = {
            0xFF120E10, 0xFF1E1614, 0xFF2B1F1A, 0xFF392821, 0xFF473226,
            0xFF553D2E, 0xFF634836, 0xFF71523E, 0xFF7F5C46};

    /** 长到哪一步了（0~1 连续插值）。 */
    protected float grow() {
        return Math.max(0.0F, Math.min(1.0F, this.stage / 7.0F));
    }

    /**
     * **关键帧位置** —— 树的参数在它们之间连续插值。
     *
     * <p>四个阶段：幼苗 / 小树 / 成树 / 巨树。注意这里是【位置】不是阶数 ——
     * {@code ramp()} 与 {@code rampCount()} 拿当前 grow() 在这条线上找区间。</p>
     */
    protected static final float[] KEY = {0.0F, 1.0F / 3.0F, 2.0F / 3.0F, 1.0F};

    /** 按阶数在关键帧之间平滑插值取整数（树的各级参数都靠它连续变化）。 */
    protected int rampCount(int[] keys) {
        float g = this.grow();
        for (int i = 0; i < KEY.length - 1; i++) {
            if (g <= KEY[i + 1] || i == KEY.length - 2) {
                float span = KEY[i + 1] - KEY[i];
                float t = span <= 0.0F ? 0.0F : (g - KEY[i]) / span;
                t = Math.max(0.0F, Math.min(1.0F, t));
                t = 1.0F - (1.0F - t) * (1.0F - t);
                return Math.round(keys[i] + (keys[i + 1] - keys[i]) * t);
            }
        }
        return keys[keys.length - 1];
    }

    /** **一条带阴影的枝干** —— 树和根共用（根其实也是枝，只是一路向下）。 */
    /** 带明暗的一段粗线（树枝、树根都用它）。 */
    protected void limbShaded(float x0, float y0, float x1, float y1,
                            float w0, float w1, int stepsPerPixel) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = Math.max(2, (int) (len * Math.max(1, stepsPerPixel)));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float x = x0 + dx * t;
            float y = y0 + dy * t;
            float w = w0 + (w1 - w0) * t;
            int shade = (int) (t * 2.0F);
            this.disc((int) x, (int) y, w * 0.5F, this.bark[Math.min(BARK.length - 1, 2 + shade)], 0.95F);
            // 受光侧高光
            float nx = -dy / Math.max(1.0F, len);
            float ny = dx / Math.max(1.0F, len);
            float dot = nx * LIGHT_X + ny * LIGHT_Y;
            float side = dot > 0.0F ? 1.0F : -1.0F;
            this.disc((int) (x + nx * side * w * 0.28F), (int) (y + ny * side * w * 0.28F),
                    Math.max(0.7F, w * 0.22F), BARK_LIGHT, 0.28F);
            // 背光侧压暗
            this.disc((int) (x - nx * side * w * 0.34F), (int) (y - ny * side * w * 0.34F),
                    Math.max(0.7F, w * 0.24F), this.bark[0], 0.35F);
        }
    }

    // ----------------------------------------------------------------------
    //  超采样
    // ----------------------------------------------------------------------
    /** 超采样上限（图小的时候用它抗锯齿；图大到爆内存时降到 1）。 */
    protected static final int SS_MAX = 2;
    /** 这次实际用几倍超采样。 */
    protected int ss = SS_MAX;

    // ----------------------------------------------------------------------
    //  **绘制产物** —— 画的时候往这里填，之后界面读它们做交互
    //  （悬停高亮、点选槽位、点击叶片）。
    //  放在画布这一层，是为了让各画笔都能往同一个容器里写；
    //  之前它们散在 TreeTexture 里，任何一块都搬不动。
    // ----------------------------------------------------------------------
    /** 嵌入位（主干上的年轮节）：{x, y}。 */
    protected final java.util.List<int[]> slots = new java.util.ArrayList<>(8);
    /** 强化叶：{x, y, r} —— 前几个是嵌入位旁的年轮叶，之后是树冠里的叶片。 */
    protected final java.util.List<int[]> leaves = new java.util.ArrayList<>(
            com.habaiwa.goetyrevenant.common.body.UpgradeSlots.COUNT + 8);
    /** 三条路各自七阶 —— 阶数由血肉线那边定义，这里只引用。 */
    protected static final int STAGES_PER_WAY =
            com.habaiwa.goetyrevenant.common.flesh.FleshLine.STAGES;
    /** **三条路 × 七阶的节点坐标**（{x, y}）。 */
    protected final int[][][] wayNodes = new int[3][STAGES_PER_WAY][2];
    /** 每条路有没有被走到（决定画不画它的细节）。 */
    protected final boolean[] wayAlive = new boolean[3];

    /** 星光的颜色（照在土面上、叶簇缝隙里透出来的那种光）。 */
    protected static final int STARLIGHT = 0xFFCFE8FF;
    /** 苔藓色（根须上、土面上的绿）。 */
    protected static final int MOSS = 0xFF2E8C78;
    /** 苔藓的亮面。 */
    protected static final int MOSS_LIT = 0xFF52E8DC;

    /** 把一个 ARGB 的每个通道整体加减 d（用于颗粒的明暗面）。 */
    /** 整体加减一个亮度（抖动用）。 */
    protected int shift(int argb, int d) {
        int r = Math.max(0, Math.min(255, ((argb >> 16) & 0xFF) + d));
        int g = Math.max(0, Math.min(255, ((argb >> 8) & 0xFF) + d));
        int b = Math.max(0, Math.min(255, (argb & 0xFF) + d));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    /** 递归分叉的毛细血管。 */
    protected void capillary(double x, double y, double ang, float top, float bottom, int depth) {
        if (depth <= 0) {
            return;
        }
        int steps = 24 + this.rng.nextInt(30);
        float wid = 0.9F + depth * 0.35F;
        for (int s = 0; s < steps; s++) {
            ang += (this.rng.nextDouble() - 0.5D) * 0.35D;
            x += Math.cos(ang) * 1.1D;
            y += Math.sin(ang) * 1.1D;
            if (x < 0 || y < top || x >= this.width || y >= bottom) {
                return;
            }
            this.blendDot((int) x, (int) y, 0xFF7A1E24, 0.3F + depth * 0.06F);
            if (s % 14 == 0 && this.rng.nextFloat() < 0.6F) {
                this.capillary(x, y, ang + (this.rng.nextBoolean() ? 0.8D : -0.8D),
                        top, bottom, depth - 1);
            }
        }
    }
}
