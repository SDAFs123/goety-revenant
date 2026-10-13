package com.habaiwa.goetyrevenant.client.fx;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * **一小块可平铺的肉纹** —— 给提示框当底用。
 *
 * <h3>为什么不做成"纯色 + 几道线"</h3>
 * <p>提示框的底如果只是一块暗红加深浅横线，看着就是个色块 —— 那不叫肉。
 * 真的肉纹要有三样东西（和界面上那片血肉土壤是同一套做法）：</p>
 * <ul>
 *   <li><b>颗粒</b>：细密的明暗点，肉不是平的</li>
 *   <li><b>血管</b>：几条暗色的蜿蜒线，外圈暗、内芯透一点亮</li>
 *   <li><b>湿润</b>：极少量偏亮的点（肉是湿的）</li>
 * </ul>
 *
 * <h3>为什么是"平铺一张小图"而不是直接画点</h3>
 * <p>提示框是<b>每帧</b>都要画的。每帧撒几百个点虽然不算慢，但没必要 ——
 * 生成一张 64×64 的小图（一次），之后按框的大小把它平铺上去，
 * 一个 200×100 的框只要 8 次 blit 就铺满了。</p>
 *
 * <p>尺寸取 64 是有意的：它必须能<b>无缝接上自己</b>（平铺时四边要对齐）。
 * 所以里面的元素都避开贴图边缘的 2 像素，免得出现一条条接缝。</p>
 */
public final class FleshTexture {
    private static final ResourceLocation TEX =
            new ResourceLocation("goety_revenant", "dynamic/flesh_tooltip_bg");

    /** 平铺单元的大小。 */
    public static final int TILE = 64;

    private static boolean built;

    private FleshTexture() {
    }

    /** 第一次用的时候生成（之后一直复用）。 */
    public static void ensure() {
        if (built) {
            return;
        }
        built = true;
        try {
            NativeImage img = build(TILE);
            Minecraft.getInstance().getTextureManager()
                    .register(TEX, new DynamicTexture(img));
        } catch (Throwable ignored) {
            // 生成失败就让调用方退回纯色底
        }
    }

    public static ResourceLocation texture() {
        return TEX;
    }

    private static NativeImage build(int n) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, n, n, false);
        Random rng = new Random(0x51E55EEDL);

        // ① 底：暗红（和血肉土壤同一个调子）
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float t = y / (float) n;
                int c = mix(0xFF150607, 0xFF1E0A0C, t);
                img.setPixelRGBA(x, y, abgr(c));
            }
        }
        // ② 颗粒：细密的明暗点（避开边缘 2 像素，免得平铺出接缝）
        int grains = n * n / 5;
        for (int i = 0; i < grains; i++) {
            int x = 2 + rng.nextInt(n - 4);
            int y = 2 + rng.nextInt(n - 4);
            boolean light = rng.nextFloat() < 0.42F;
            int c = light ? 0xFF3A1014 : 0xFF0C0304;
            blend(img, x, y, c, 0.16F + rng.nextFloat() * 0.22F);
        }
        // ③ 血管：几条短的蜿蜒线，外圈暗、内芯透一点亮
        for (int v = 0; v < 3; v++) {
            float vx = 4 + rng.nextFloat() * (n - 8);
            float vy = 4 + rng.nextFloat() * (n - 8);
            double ang = rng.nextDouble() * Math.PI * 2.0D;
            for (int s = 0; s < 14 + rng.nextInt(10); s++) {
                ang += (rng.nextDouble() - 0.5D) * 0.5D;
                vx += (float) Math.cos(ang) * 1.4F;
                vy += (float) Math.sin(ang) * 1.4F;
                // 贴图内循环（这样平铺时线不会断），并避开边缘
                int ix = 2 + (int) (((vx % (n - 4)) + (n - 4)) % (n - 4));
                int iy = 2 + (int) (((vy % (n - 4)) + (n - 4)) % (n - 4));
                disc(img, ix, iy, 1.6F, 0xFF2A080C, 0.42F);
                blend(img, ix, iy, 0xFF6A1E24, 0.30F);
            }
        }
        // ④ 湿润：极少量偏亮的点
        for (int i = 0; i < 5; i++) {
            int x = 3 + rng.nextInt(n - 6);
            int y = 3 + rng.nextInt(n - 6);
            blend(img, x, y, 0xFF8A464A, 0.22F);
            blend(img, x, y + 1, 0xFF5A2A2E, 0.12F);
        }
        return img;
    }

    /* ------------------------------------------------------------------ */

    private static void disc(NativeImage img, int cx, int cy, float r, int argb, float k) {
        int rad = Math.max(1, (int) Math.ceil(r));
        for (int y = cy - rad; y <= cy + rad; y++) {
            for (int x = cx - rad; x <= cx + rad; x++) {
                float dx = x - cx;
                float dy = y - cy;
                if (dx * dx + dy * dy > r * r) {
                    continue;
                }
                blend(img, x, y, argb, k);
            }
        }
    }

    private static void blend(NativeImage img, int x, int y, int argb, float k) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight() || k <= 0.002F) {
            return;
        }
        k = Math.min(1.0F, k);
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
