package com.habaiwa.goetyrevenant.client.tree;

import java.util.Random;

/**
 * **种子与幼苗的绘制** —— 一粒种子、它裂开、苗从缝里顶出来。
 *
 * <p>从 `TreeTexture` 抽出来的。</p>
 *
 * <h3>形态是橡子，不是椭圆</h3>
 * <p>现实里最认得出的树种是橡子，它由<b>两个截然不同的部分</b>组成
 * —— 这才是它一眼能被认出来的原因：</p>
 * <pre>
 *      ╭─╮       ① 帽（壳斗）：盖在上部，粗糙，有鳞片状交叉纹，顶上短柄
 *     ╭┤▓▓├╮
 *     ││  ││     ② 身体（坚果）：光滑、栗色、【上宽下尖】的卵形，
 *      ╲  ╱         左上一处高光，底端小尖（果脐）
 *       ╲╱
 * </pre>
 *
 * <h3>裂开与顶出</h3>
 * <p>`seedOpen` 单独画"种皮从种脐裂开、两半各让开一点 + 缝里透魂光"，
 * 生长动画靠"完整种子淡出 / 裂开种子淡入 / 苗从下往上裁出"三段叠出那个动作。</p>
 *
 * <p>用一次就丢，不保留状态。</p>
 */
public final class SeedPainter {
    private static final int SPROUT_STEM_LIT = 0xFFBCCB7E;
    // ==== 幼苗（子叶期）用色：嫩绿偏黄，和成树的树皮/深叶完全是两回事 ====
    /** 胚轴（从土里钻出来那截）：浅黄绿。 */
    private static final int SPROUT_STEM = 0xFF8A9A56;
    private static final int SPROUT_LEAF_LIT = 0xFF9ECB72;
    private static final int SPROUT_LEAF_DARK = 0xFF3C6530;
    /** 子叶：嫩绿。 */
    private static final int SPROUT_LEAF = 0xFF6E9E4C;

    private final TreeCanvas c;
    private final Random rng;

    public SeedPainter(TreeCanvas canvas, Random rng) {
        this.c = canvas;
        this.rng = rng;
    }

    public void seed(float cx, float groundY, float trunkW) {
        this.seedImpl(cx, groundY, trunkW);
    }

    public void seedOpen(float cx, float groundY, float trunkW) {
        this.seedOpenImpl(cx, groundY, trunkW);
    }

    public void sprout(float cx, float groundY, float trunkH) {
        this.sproutImpl(cx, groundY, trunkH);
    }

    /**
     * **一粒种子** —— 重画过。
     *
     * <p>之前那颗的问题：太小（{@code trunkW × 1.15}，几乎看不清）、太瘦、
     * 只有几道横纹。真实种子的样子是：</p>
     * <ul>
     *   <li><b>饱满</b>：鼓的，不是瘦长的椭圆</li>
     *   <li><b>竖纹</b>：种皮的纹路是从种脐往外发的【竖】的（之前画成横的）</li>
     *   <li><b>一处明确的高光</b>：种皮是光滑的，左上有一点亮</li>
     *   <li><b>一半埋在土里</b>：土面有一圈压痕</li>
     * </ul>
     */
    /**
     * **一粒树的种子 —— 橡子**。完全重画。
     *
     * <p>之前那几版都不是种子：一个上下对称的椭圆加上竖纹和高光，
     * 看着像颗豆子或者石头。现实里最认得出的树种是橡子，
     * 它由<b>两个截然不同的部分</b>组成 —— 这才是它一眼能被认出来的原因：</p>
     *
     * <pre>
     *      ╭─╮       ① 帽（壳斗）：盖在上部四分之一到三分之一，
     *     ╭┤▓▓├╮         粗糙，有【鳞片状的交叉纹】，顶上还有个短柄
     *     ││  ││
     *     ││  ││     ② 身体（坚果）：光滑、有光泽、栗色，
     *      ╲  ╱          【上宽下尖】的卵形，左上一处明确的高光，
     *       ╲╱           底端一个小尖（果脐）
     * </pre>
     */
    private void seedImpl(float cx, float groundY, float trunkW) {
        // 小得多：之前 trunkW × 2.0 在屏幕上约 119 像素，太大 ——
        // 种子是"一粒"，不该占掉半个画面。现在约 45 像素。
        float r = Math.max(5.0F, trunkW * 0.75F);
        float ry = r * 1.42F;                     // 卵形：略长
        float sy = groundY - ry * 0.60F;          // 身体中心（露出大半）
        // 土面的压痕
        this.c.disc((int) cx, (int) (groundY + 1.0F), r * 1.5F, 0xFF0A0606, 0.42F);
        this.c.disc((int) cx, (int) (groundY + 2.0F), r * 1.1F, 0xFF120A08, 0.30F);
        // ---------------- ② 身体：上宽下尖的卵形 ----------------
        for (int y = (int) -ry; y <= (int) (ry * 1.05F); y++) {
            float ny = y / ry;
            // 椭圆的半宽
            float half = (float) Math.sqrt(Math.max(0.0F, 1.0F - ny * ny));
            // **下半收窄** —— 让它变成尖头的卵形，而不是对称的椭圆
            if (ny > 0.0F) {
                half *= 1.0F - ny * ny * 0.45F;
            }
            half *= r;
            for (int x = (int) -half; x <= (int) half; x++) {
                float nx = x / Math.max(1.0F, half);
                // 球面感：中心亮、边缘暗
                float bulge = 1.0F - Math.abs(nx) * Math.abs(nx) * 0.75F
                        - Math.abs(ny) * Math.abs(ny) * 0.45F;
                int c = this.c.mix(0xFF2A1608, 0xFF7A4A22, Math.max(0.0F, bulge));
                c = this.c.mix(c, 0xFF8E5228, Math.max(0.0F, bulge - 0.45F) * 0.8F);
                // 受光（左上）
                float lit = -(nx * 0.66F + ny * 0.75F);
                if (lit > 0.30F) {
                    c = this.c.mix(c, 0xFFB8823E, (lit - 0.30F) * 0.60F);
                } else if (lit < -0.35F) {
                    c = this.c.mix(c, 0xFF120804, (-lit - 0.35F) * 0.65F);
                }
                this.c.blendDot((int) (cx + x), (int) (sy + y), c, 0.97F);
            }
        }
        // 身体上的高光（光滑物体的标志）
        this.c.disc((int) (cx - r * 0.32F), (int) (sy - ry * 0.18F), r * 0.34F,
                0xFFE0B470, 0.28F);
        this.c.disc((int) (cx - r * 0.34F), (int) (sy - ry * 0.22F), r * 0.17F,
                0xFFFFE0A8, 0.42F);
        // 底端的果脐（那个小尖）
        for (int i = 0; i < 4; i++) {
            this.c.blendDot((int) cx, (int) (sy + ry * (0.98F + i * 0.06F)),
                    0xFF1A0C04, 0.55F - i * 0.12F);
        }
        // ---------------- ① 帽（壳斗）：盖在上部 ----------------
        float capBottom = sy - ry * 0.44F;         // 帽子的下沿
        float capTop = sy - ry * 1.10F;            // 帽顶
        for (int y = (int) capTop; y <= capBottom; y++) {
            float t = (y - capTop) / Math.max(1.0F, capBottom - capTop);
            // 帽子：上窄下宽的杯形（盖住身体的上端）
            float half = r * (0.55F + t * 0.55F);
            // 帽子外沿比身体略宽一点 —— 像真的壳斗那样"扣"在上面
            for (int x = (int) -half; x <= (int) half; x++) {
                float nx = x / Math.max(1.0F, half);
                // 帽子的底色：暗灰褐（比身体暗、比身体糙）
                float bulge = 1.0F - Math.abs(nx) * Math.abs(nx) * 0.7F;
                int c = this.c.mix(0xFF241A10, 0xFF54402A, Math.max(0.0F, bulge) * 0.8F);
                float lit = -(nx * 0.66F + ((y - sy) / ry) * 0.75F);
                if (lit > 0.30F) {
                    c = this.c.mix(c, 0xFF7A6244, (lit - 0.30F) * 0.55F);
                }
                this.c.blendDot((int) (cx + x), (int) y, c, 0.97F);
            }
        }
        // **鳞片状的纹**（壳斗的招牌）—— 横向几道 + 纵向几道，交叉成小块
        for (int k = 0; k < 5; k++) {
            float yy = capTop + (capBottom - capTop) * (k + 0.5F) / 5.0F;
            float t2 = (yy - capTop) / Math.max(1.0F, capBottom - capTop);
            float half = r * (0.55F + t2 * 0.55F);
            for (int x = (int) -half; x <= (int) half; x++) {
                this.c.blendDot((int) (cx + x), (int) yy, 0xFF160E06, 0.30F);
                this.c.blendDot((int) (cx + x), (int) yy + 1, 0xFF6A5438, 0.16F);
            }
        }
        int ribs = 7;
        for (int i = 0; i < ribs; i++) {
            float kk = i / (float) (ribs - 1) - 0.5F;
            for (int y = (int) capTop; y <= capBottom; y++) {
                float t2 = (y - capTop) / Math.max(1.0F, capBottom - capTop);
                float half = r * (0.55F + t2 * 0.55F);
                float x = kk * half * 1.85F;
                if (Math.abs(x) > half) {
                    continue;
                }
                this.c.blendDot((int) (cx + x), (int) y, 0xFF160E06, 0.22F);
            }
        }
        // **帽沿**：一圈不规则的下边（不是一条平线）
        for (int x = (int) (-r * 1.15F); x <= (int) (r * 1.15F); x++) {
            float t = Math.abs(x) / (r * 1.15F);
            if (t > 1.0F) {
                continue;
            }
            float wave = (float) Math.sin(x * 0.7F) * 1.2F + (this.rng.nextFloat() - 0.5F);
            int yy = (int) (capBottom + wave);
            this.c.blendDot((int) (cx + x), yy, 0xFF0E0804, 0.55F);
            this.c.blendDot((int) (cx + x), yy + 1, 0xFF3A2A18, 0.30F);
        }
        // 帽顶的短柄
        for (int y = 0; y < 4; y++) {
            this.c.blendDot((int) cx, (int) (capTop - y), 0xFF3E2E1C, 0.7F - y * 0.14F);
        }
        // 一点魂光（它是有灵性的种子）
        this.c.disc((int) cx, (int) (sy - ry * 0.05F), r * 0.70F, 0xFF52E8DC, 0.11F);
        this.c.disc((int) (cx - r * 0.10F), (int) (sy + ry * 0.15F), r * 0.32F,
                0xFFB8FFF4, 0.16F);
        // 底部埋进土里
        for (int x = (int) (-r * 1.1F); x <= r * 1.1F; x++) {
            float t = Math.abs(x) / (r * 1.1F);
            int depth = (int) (2.0F + (1.0F - t) * 3.0F);
            for (int dy = 0; dy < depth; dy++) {
                this.c.blendDot((int) (cx + x), (int) (groundY + dy), TreeCanvas.DIRT[4], 0.55F - dy * 0.13F);
            }
        }
    }
    /**
     * **裂开的种子** —— 种皮从种脐裂开，两半各向外让开一点。
     *
     * <p>生长动画要靠这张图来完成"破开"那一下：完整种子淡出、这张淡入，
     * 中间露出一道缝，然后幼苗从那道缝里顶出来。</p>
     */
    private void seedOpenImpl(float cx, float groundY, float trunkW) {
        float r = Math.max(5.0F, trunkW * 0.75F);
        float ry = r * 1.42F;
        float sy = groundY - ry * 0.60F;
        float split = r * 0.22F;                  // 两半让开的距离
        // 土面的压痕（和完整种子一致）
        this.c.disc((int) cx, (int) (groundY + 1.0F), r * 1.5F, 0xFF0A0606, 0.40F);
        // --- 缝里的暗（先铺，两半压在上面）---
        for (int y = (int) (-ry * 1.1F); y <= (int) (ry * 1.0F); y++) {
            float t = (y + ry * 1.1F) / (ry * 2.1F);
            float w = r * (0.10F + 0.10F * (float) Math.sin(t * Math.PI));
            for (int x = (int) -w; x <= w; x++) {
                this.c.blendDot((int) (cx + x), (int) (sy + y), 0xFF0A0406, 0.85F);
            }
        }
        // --- 左右两半（各偏出去 split，外侧保持椭圆的弧）---
        for (int side = -1; side <= 1; side += 2) {
            for (int y = (int) (-ry * 1.6F); y <= (int) (ry * 1.6F); y++) {
                for (int x = (int) (-r * 1.6F); x <= (int) (r * 1.6F); x++) {
                    // 这一半只画属于自己那一侧、且让开缝的部分
                    if (side > 0 ? (x < split * 0.55F) : (x > -split * 0.55F)) {
                        continue;
                    }
                    float nx = x / r;
                    float ny = y / ry;
                    float d2 = nx * nx + ny * ny;
                    if (d2 > 1.0F) {
                        continue;
                    }
                    float bulge = 1.0F - (float) Math.sqrt(d2);
                    int c = this.c.mix(0xFF251608, 0xFF7A5230, bulge);
                    // 裂口那一圈是新鲜的（偏亮偏红）
                    float nearSplit = 1.0F - Math.min(1.0F, Math.abs(x) / (r * 0.55F));
                    c = this.c.mix(c, 0xFF9A5030, nearSplit * 0.55F);
                    float lit = -(nx * 0.68F + ny * 0.74F);
                    if (lit > 0.25F) {
                        c = this.c.mix(c, 0xFFC09A5E, (lit - 0.25F) * 0.50F);
                    }
                    this.c.blendDot((int) (cx + x + side * split), (int) (sy + y), c, 0.97F);
                }
            }
            // 另一半的高光跟着挪
            if (side < 0) {
                this.c.disc((int) (cx - r * 0.34F + side * split), (int) (sy - ry * 0.36F),
                        r * 0.26F, 0xFFE8D2A8, 0.30F);
            }
        }
        // --- 缝里透出的魂光（芽还没出来，先亮起来）---
        for (int y = (int) (-ry * 0.9F); y <= (int) (ry * 0.8F); y++) {
            float t = (y + ry * 0.9F) / (ry * 1.7F);
            if (t < 0.35F) {
                continue;
            }
            this.c.blendDot((int) cx, (int) y + (int) sy, 0xFF52E8DC, (t - 0.35F) * 0.5F);
        }
        // --- 底部埋进土里 ---
        for (int x = (int) (-r * 1.2F); x <= r * 1.2F; x++) {
            float t = Math.abs(x) / (r * 1.2F);
            int depth = (int) (3.0F + (1.0F - t) * 3.0F);
            for (int dy = 0; dy < depth; dy++) {
                this.c.blendDot((int) (cx + x), (int) (groundY + dy), TreeCanvas.DIRT[4], 0.55F - dy * 0.12F);
            }
        }
    }
    /**
     * **一粒种子** —— 未觉醒时魂树界面里唯一的东西。
     *
     * <p>半个埋在土里，种皮有纹理和一道种脐，朝光的那面亮一些；
     * 周围有几道土裂（它快出来了），最上面一点魂光 —— 毕竟是灵魂的种子。</p>
     */
    /**
     * **幼苗（子叶期）** —— 真正的样子。
     *
     * <pre>
     *        ╱╲        ← 两片子叶（左右摊开，圆润，各有一条中脉）
     *        │         ← 顶芽（子叶之间一个很小的尖，将来从这里长真叶）
     *        │         ← 胚轴：细、浅黄绿、略微弯（不是笔直的杆）
     *   ═════╪═════    ← 土面
     * </pre>
     *
     * <p>和成树的区别在<b>质感</b>而不只是大小：这里没有树皮、
     * 没有分枝、没有深色叶 —— 全是嫩的颜色和圆润的形状。</p>
     */
    private void sproutImpl(float cx, float groundY, float trunkH) {
        // 高度：幼苗不该像树那么高（树干高的三成左右），再钳一个下限
        float h = Math.max(26.0F, trunkH * 0.34F);
        float w = Math.max(2.0F, h * 0.045F);           // 茎很细
        float bend = w * 5.0F;                          // 微微弯向一边

        // ---- 胚轴 ----
        int steps = (int) h;
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps;
            float y = groundY - t * h;
            // 越往上越往一边偏一点（幼苗都朝光歪）
            float x = cx + (float) Math.sin(t * 1.6D) * bend;
            float ww = w * (1.0F - t * 0.25F);
            // 左缘受光（星光的边）
            this.c.disc((int) (x + ww * 0.35F), (int) y, ww, SPROUT_STEM, 0.95F);
            this.c.blendDot((int) (x - ww * 0.45F), (int) y, SPROUT_STEM_LIT, 0.45F);
            this.c.blendDot((int) (x + ww * 0.75F), (int) y, SPROUT_LEAF_DARK, 0.30F);
        }
        float topX = cx + (float) Math.sin(1.6D) * bend;
        float topY = groundY - h;

        // ---- 顶芽：子叶之间那个小尖 ----
        for (int i = 0; i < 4; i++) {
            this.c.blendDot((int) topX, (int) (topY - i), SPROUT_LEAF_LIT, 0.55F - i * 0.10F);
        }

        // ---- 两片子叶：左右各一片，圆润、摊开 ----
        float leafLen = Math.max(7.0F, h * 0.30F);
        for (int side = -1; side <= 1; side += 2) {
            // 子叶本体：一片横向拉长的椭圆
            int lx0 = (int) topX;
            int ly0 = (int) (topY + 1.0F);
            for (int i = 0; i < (int) leafLen; i++) {
                float t = i / leafLen;
                // 厚度：中间最厚，两端收
                float thick = (float) Math.sin(t * Math.PI) * leafLen * 0.34F;
                int x = lx0 + (int) (side * i);
                for (int dy = 0; dy < (int) thick; dy++) {
                    int y = ly0 + dy - (int) (thick * 0.4F);
                    // 上缘亮（受星光）、下缘暗
                    float up = 1.0F - dy / Math.max(1.0F, thick);
                    int c = up > 0.55F ? SPROUT_LEAF_LIT
                            : (up > 0.22F ? SPROUT_LEAF : SPROUT_LEAF_DARK);
                    this.c.blendDot(x, y, c, 0.88F);
                }
            }
            // 中脉：一条从叶基到叶尖的深色线
            for (int i = 2; i < (int) leafLen - 1; i++) {
                this.c.blendDot(lx0 + (int) (side * i), ly0 + 1, SPROUT_LEAF_DARK, 0.40F);
            }
            // 叶尖一枚小小的亮
            this.c.blendDot(lx0 + (int) (side * (leafLen - 1.0F)), ly0 + 1,
                    SPROUT_LEAF_LIT, 0.6F);
        }

        // ---- 土面那一圈：胚轴刚顶出来的痕迹 ----
        for (int i = 0; i < 10; i++) {
            int x = (int) (cx + (this.rng.nextFloat() - 0.5F) * (w * 6.0F));
            this.c.blendDot(x, (int) groundY, 0xFF3A2A1C, 0.35F);
        }
        // 子叶还小，这一阶没有叶簇、也没有可点的槽位
    }
}
