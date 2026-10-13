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
