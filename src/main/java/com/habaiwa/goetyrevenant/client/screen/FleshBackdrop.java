package com.habaiwa.goetyrevenant.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 七窍界面的**血肉背景**。
 *
 * <h3>画什么</h3>
 * <ol>
 *   <li><b>底层</b>：中间亮、四周烂掉的暗红肉色渐变</li>
 *   <li><b>肌纤维</b>：一两百条长短不一的横纹，把"肉"画出纹理</li>
 *   <li><b>腐烂斑</b>：几块发黑的坏死区，边缘碎掉</li>
 *   <li><b>主血管</b>：几条从屏幕边缘钻进来、在肉里蜿蜒的粗血管，<b>跟着心跳一明一暗</b></li>
 *   <li><b>缝合</b>：四个角各一道把整块皮缝在屏幕上的粗缝线</li>
 *   <li><b>撕裂边</b>：四周不规则的肉边，不是干净的直角</li>
 * </ol>
 *
 * <h3>为什么这样不卡</h3>
 * <p>整张背景<b>只在窗口尺寸变化时生成一次</b>，生成结果是一张"矩形清单"。
 * 之后每帧只是照着清单发几百次 {@code fill()}，不做任何三角函数或随机数。
 * （思路来自已经删除的七窍人体图界面里那套伤口缓存。）</p>
 */
final class FleshBackdrop {
    private static final int VOID = 0xF00A0406;
    private static final int FLESH_DARK = 0xFF3A0D10;
    private static final int FLESH = 0xFF6B1E22;
    private static final int FLESH_LIGHT = 0xFF8A2A2E;
    private static final int BLOOD = 0xFFB01B22;
    private static final int BLOOD_DARK = 0xFF6A0A10;
    private static final int SCAB = 0xFF2A0508;
    private static final int TENDON = 0xFF7A4A3C;

    private int width = -1;
    private int height = -1;
    /** 静态层：{x0,y0,x1,y1,argb}。 */
    private int[][] statics = new int[0][];
    /** 主血管：{x0,y0,x1,y1,相位}。 */
    private int[][] vessels = new int[0][];

    void draw(GuiGraphics graphics, int w, int h, float beat) {
        if (w != this.width || h != this.height) {
            build(w, h);
        }
        for (int[] op : this.statics) {
            graphics.fill(op[0], op[1], op[2], op[3], op[4]);
        }
        // 血管：跟着心跳明暗。相位不同，所以不是一起亮，而是"血一波一波淌过去"
        for (int[] v : this.vessels) {
            float wave = 0.5F + 0.5F * (float) Math.sin(beat * 6.28318F + v[4] / 100.0F);
            int core = ((int) (0x30 + 0x55 * wave) << 24) | (BLOOD & 0xFFFFFF);
            graphics.fill(v[0], v[1], v[2], v[3], core);
        }
    }

    /* ------------------------------------------------------------------ */

    private void build(int w, int h) {
        this.width = w;
        this.height = h;
        Random rng = new Random(w * 31L + h);
        List<int[]> ops = new ArrayList<>(2048);
        List<int[]> veins = new ArrayList<>(512);

        // 1) 底：整屏黑洞 + 逐行肉色渐变
        ops.add(new int[]{0, 0, w, h, VOID});
        for (int y = 0; y < h; y += 2) {
            float t = y / (float) h;
            // 中间偏上最亮的"腹腔中心"，上下两头烂掉
            float v = 1.0F - Math.abs(t - 0.42F) * 1.7F;
            v = Math.max(0.0F, Math.min(1.0F, v));
            int color = lerp(SCAB, lerp(FLESH_DARK, FLESH, 0.7F), v);
            ops.add(new int[]{0, y, w, y + 2, (0xF2 << 24) | (color & 0xFFFFFF)});
        }

        // 2) 肌纤维：长短不一的横纹
        int fibers = Math.max(120, (w * h) / 5200);
        for (int i = 0; i < fibers; i++) {
            int y = rng.nextInt(Math.max(1, h));
            int x = rng.nextInt(Math.max(1, w));
            int len = 12 + rng.nextInt(Math.max(8, w / 4));
            boolean dark = rng.nextBoolean();
            int base = dark ? FLESH_DARK : FLESH_LIGHT;
            int alpha = 0x18 + rng.nextInt(0x2A);
            ops.add(new int[]{x, y, Math.min(w, x + len), y + 1, (alpha << 24) | (base & 0xFFFFFF)});
        }

        // 3) 腐烂斑：小块坏死，用逐行段拼出毛边
        for (int i = 0; i < 7; i++) {
            int cx = rng.nextInt(Math.max(1, w));
            int cy = rng.nextInt(Math.max(1, h));
            int r = 14 + rng.nextInt(34);
            for (int dy = -r; dy <= r; dy += 1) {
                int half = (int) Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy));
                half = (int) (half * (0.55F + rng.nextFloat() * 0.45F));
                if (half <= 0) {
                    continue;
                }
                int y = cy + dy;
                if (y < 0 || y >= h) {
                    continue;
                }
                ops.add(new int[]{Math.max(0, cx - half), y, Math.min(w, cx + half), y + 1,
                        (0x40 << 24) | (SCAB & 0xFFFFFF)});
            }
        }

        // 4) 主血管：从四条边钻进来，蜿蜒着走
        int veinCount = 9;
        for (int i = 0; i < veinCount; i++) {
            float x = rng.nextInt(Math.max(1, w));
            float y = rng.nextInt(Math.max(1, h));
            double dir = rng.nextDouble() * Math.PI * 2.0D;
            int steps = 22 + rng.nextInt(22);
            int phase100 = rng.nextInt(628);
            for (int s = 0; s < steps; s++) {
                double px = x;
                double py = y;
                dir += (rng.nextDouble() - 0.5D) * 1.1D;
                x += (float) (Math.cos(dir) * 9.0D);
                y += (float) (Math.sin(dir) * 9.0D);
                int x0 = (int) Math.min(px, x);
                int y0 = (int) Math.min(py, y);
                int x1 = (int) Math.max(px, x);
                int y1 = (int) Math.max(py, y);
                // 血管只画在屏幕内，粗细 3 像素
                if (x1 < 0 || x0 > w || y1 < 0 || y0 > h) {
                    continue;
                }
                int rx0 = Math.max(0, x0);
                int ry0 = Math.max(0, y0);
                int rx1 = Math.min(w, x1 + 3);
                int ry1 = Math.min(h, y1 + 3);
                if (rx1 <= rx0 || ry1 <= ry0) {
                    continue;
                }
                // 暗色血管壁（静态）
                ops.add(new int[]{rx0, ry0, rx1, ry1, (0x88 << 24) | (FLESH_DARK & 0xFFFFFF)});
                // 亮的血心（动态，单独存）
                veins.add(new int[]{rx0 + 1, ry0 + 1, Math.max(rx0 + 1, rx1 - 1), Math.max(ry0 + 1, ry1 - 1),
                        phase100});
            }
        }

        // 5) 中央微光：身体背后一团暗红的光 —— 不然中间一大块黑得很空
        int glowCx = w / 2;
        int glowCy = (int) (h * 0.40F);
        for (int band = 0; band < 4; band++) {
            int R = Math.min(w, h) / 3 + band * (Math.min(w, h) / 9);
            int R2 = R + 1;
            int alpha = Math.max(4, 0x16 - band * 5);
            for (int dy = -R; dy <= R; dy += 2) {
                int half = (int) Math.sqrt(Math.max(0, (double) R2 * R2 - (double) dy * dy));
                int y = glowCy + dy;
                if (y < 0 || y >= h) {
                    continue;
                }
                ops.add(new int[]{Math.max(0, glowCx - half), y, Math.min(w, glowCx + half), y + 2,
                        (alpha << 24) | (FLESH & 0xFFFFFF)});
            }
        }

        // 6) 血点子：密密的小黑点，干掉的旧血
        for (int i = 0; i < 220; i++) {
            int x = rng.nextInt(Math.max(1, w));
            int y = 6 + rng.nextInt(Math.max(1, h - 12));
            int sz = 1 + rng.nextInt(2);
            int alpha = 0x22 + rng.nextInt(0x40);
            ops.add(new int[]{x, y, Math.min(w, x + sz), y + 1, (alpha << 24) | (SCAB & 0xFFFFFF)});
        }

        // 7) 从皮缘淌下来的血痕
        for (int i = 0; i < 14; i++) {
            int x = rng.nextInt(Math.max(1, w));
            int len = 24 + rng.nextInt(Math.max(20, h / 3));
            int thickness = 2 + rng.nextInt(3);
            ops.add(new int[]{x, 0, Math.min(w, x + thickness), len, (0x70 << 24) | (BLOOD_DARK & 0xFFFFFF)});
        }

        // 8) 底部积血
        int poolH = Math.min(h / 6, 90);
        for (int y = h - poolH; y < h; y += 2) {
            float t = (y - (h - poolH)) / (float) poolH;
            int alpha = (int) (0x30 + 0x50 * t);
            ops.add(new int[]{0, y, w, y + 2, (alpha << 24) | (BLOOD_DARK & 0xFFFFFF)});
        }

        // 9) 四角缝合：把这块"皮"钉在屏幕上
        int corner = Math.min(w, h) / 9;
        for (int c = 0; c < 4; c++) {
            int ox = (c == 0 || c == 2) ? 0 : w - corner;
            int oy = (c < 2) ? 0 : h - corner;
            for (int i = 4; i < corner; i += 7) {
                int j = corner - i;
                ops.add(new int[]{ox + i, oy + j, ox + i + 4, oy + j + 2, TENDON});
                ops.add(new int[]{ox + j, oy + i, ox + j + 2, oy + i + 4, TENDON});
            }
        }

        // 6) 撕裂边：四周不规则的肉边
        for (int x = 0; x < w; x += 3) {
            int top = 3 + rng.nextInt(9);
            int bottom = 3 + rng.nextInt(9);
            ops.add(new int[]{x, 0, Math.min(w, x + 3), top, (0xD0 << 24) | (SCAB & 0xFFFFFF)});
            ops.add(new int[]{x, h - bottom, Math.min(w, x + 3), h, (0xD0 << 24) | (SCAB & 0xFFFFFF)});
        }
        for (int y = 0; y < h; y += 3) {
            int left = 3 + rng.nextInt(9);
            int right = 3 + rng.nextInt(9);
            ops.add(new int[]{0, y, left, Math.min(h, y + 3), (0xD0 << 24) | (SCAB & 0xFFFFFF)});
            ops.add(new int[]{w - right, y, w, Math.min(h, y + 3), (0xD0 << 24) | (SCAB & 0xFFFFFF)});
        }

        this.statics = ops.toArray(new int[0][]);
        this.vessels = veins.toArray(new int[0][]);
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
