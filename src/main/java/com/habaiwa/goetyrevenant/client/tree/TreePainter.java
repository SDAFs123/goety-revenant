package com.habaiwa.goetyrevenant.client.tree;

import java.util.List;
import java.util.ArrayList;

/**
 * **树的绘制** —— 主干、分枝、树冠、叶簇、树影，以及叶间的光点。
 *
 * <p>从 `TreeTexture` 抽出来的（那个文件原本什么都干）。</p>
 *
 * <h3>骨架照现实树来</h3>
 * <pre>
 *   主干    根部膨大、向上收细；约四成高度处分叉，并往一侧微偏（现实里的树很少正正直上）
 *   一级枝  从分叉点放射出去 3~5 根，每根再二级分叉，依此类推
 *   树冠    叶簇挂在各级枝的末端，中心密、外围疏
 *   树影    光从左上 → 主干右侧一道投影，让树"立"在空间里
 * </pre>
 *
 * <h3>阶数是连续插值出来的</h3>
 * <p>不是"啪"地换一张图 —— 主干高度、粗细、枝数、冠幅都在关键帧之间平滑过渡
 * （{@code TreeCanvas.rampCount} / {@code KEY}），所以生长动画看着是"一直在长"。</p>
 *
 * <h3>挂在树上的东西</h3>
 * <ul>
 *   <li><b>年轮节</b>（主干上的横向环）= 嵌入位 → 写进画布的 `slots`</li>
 *   <li><b>叶簇里的叶片</b> = 强化叶 → 写进画布的 `leaves`（含族色与档位）</li>
 * </ul>
 *
 * <p>用一次就丢。</p>
 */
public final class TreePainter {
    private final TreeCanvas c;
    /** 土壤表面（内容图坐标）—— 树干从这往上长。 */
    private final float soilTop;

    public TreePainter(TreeCanvas canvas, float soilTop) {
        this.c = canvas;
        this.soilTop = soilTop;
    }

    /** 画整棵树（阶数与树冠偏向已在画布里）。 */
    public void paint(float cx, float groundY, float trunkH, float trunkW) {
        this.tree(cx, groundY, trunkH, trunkW);
    }

    /**
     * **一棵现实的树** —— 完全重画。
     *
     * <h3>现实里的树长什么样</h3>
     * <pre>
     *   ① 根颈：入土那一圈向外扩（树不是插进土里的一根柱子）
     *   ② 主干：从地面到树高的四成左右，下粗上细、缓缓偏向一侧
     *   ③ 分叉：主干在那个高度【结束】，放射出 3~5 根主枝
     *   ④ 主枝：各自朝一个方向向外上方伸，长度接近主干的余量
     *   ⑤ 递归：每根主枝再分，长度 ×0.78、粗细 ×0.66、角度偏 ±30~50°
     *   ⑥ 分到第四级是枝梢，叶长在枝梢上
     * </pre>
     *
     * <h3>之前那棵错在哪</h3>
     * <p>它是"沿主干左右交替长枝"（{@code side = i % 2 == 0 ? 1 : -1}）——
     * 那是<b>针叶树</b>的画法，一根独干直上去、两边对称挂枝。
     * 阔叶乔木根本不是这个骨架，所以怎么调参数都不像。</p>
     */
    private void realTree(float cx, float groundY, float trunkH, float trunkW, int levels) {
        // 分叉点的位置：向一侧偏（现实里的树很少正正直上）
        float forkY = groundY - trunkH * TreeCanvas.FORK_T;
        float lean = (this.c.rng.nextFloat() - 0.5F) * trunkW * 2.2F;
        float forkX = cx + lean;

        // ① 主干：弯曲 + 根颈外扩
        this.trunkBent(cx, groundY, forkX, forkY, trunkW);

        // ② 主枝：3~5 根，从一个点上放射出去
        List<float[]> tips = new ArrayList<>();
        int n = 3 + this.c.rng.nextInt(3);
        float restH = trunkH * (1.0F - TreeCanvas.FORK_T);
        for (int i = 0; i < n; i++) {
            // 散布：-1 ~ 1（左到右），基准方向是"向上"（屏幕 y 向下，所以 -π/2）
            float spread = n == 1 ? 0.0F : (i / (float) (n - 1) - 0.5F) * 2.0F;
            float ang = (float) (-Math.PI / 2.0D) + spread * 0.95F
                    + (this.c.rng.nextFloat() - 0.5F) * 0.24F;
            // 长度：接近主干余量，各自不同（长短悬殊才有树形）
            float len = restH * (0.72F + this.c.rng.nextFloat() * 0.48F);
            float w = trunkW * (0.58F + this.c.rng.nextFloat() * 0.16F);
            this.branch(forkX, forkY, ang, len, w, 1, levels, tips);
        }
        // ③ 树冠：枝梢上长叶，中心密、外围疏
        this.canopy(tips, forkX, forkY - restH * 0.55F, restH * 0.95F);

        // ④ 环境光遮蔽：光从左上来 → 主干右侧一道投影（让树"立"在空间里）
        this.trunkShadow(cx, groundY, forkX, forkY, trunkH, trunkW);
    }

    /**
     * **弯曲的主干** —— 从根部到分叉点。
     *
     * <p>两条现实里的特征：</p>
     * <ul>
     *   <li><b>不是笔直的</b>：用一个二次曲线让它往一侧鼓出来</li>
     *   <li><b>根颈外扩</b>：入土那一圈明显更粗（树不是插进土里的柱子）</li>
     * </ul>
     */
    private void trunkBent(float x0, float y0, float x1, float y1, float w0) {
        int steps = Math.max(8, (int) Math.abs(y0 - y1));
        float bulgeSide = this.c.rng.nextBoolean() ? 1.0F : -1.0F;
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps;
            float x = x0 + (x1 - x0) * t
                    + (float) Math.sin(t * Math.PI) * w0 * 0.75F * bulgeSide;
            float y = y0 + (y1 - y0) * t;
            float w = w0 * (1.0F - t * 0.58F);
            // 根颈：最下面那一小段向外扩
            if (t < 0.09F) {
                w *= 1.0F + (0.09F - t) / 0.09F * 1.0F;
            }
            // 树皮：底色 + 左缘受光 + 右侧暗
            this.c.disc((int) x, (int) y, w, this.c.bark[1], 0.96F);
            this.c.disc((int) (x - w * 0.42F), (int) y, Math.max(1.0F, w * 0.34F),
                    this.c.bark[3], 0.34F);
            this.c.blendDot((int) (x - w * 0.55F), (int) y, TreeCanvas.BARK_LIGHT, 0.20F);
            this.c.blendDot((int) (x + w * 0.62F), (int) y, 0xFF180E08, 0.26F);
            // 树皮的竖纹
            if (s % 3 == 0) {
                this.c.blendDot((int) (x + (this.c.rng.nextFloat() - 0.5F) * w * 1.2F), (int) y,
                        0xFF2A1A10, 0.18F);
            }
        }
    }

    /**
     * **树冠** —— 叶长在枝梢上，但要成"团"而不是一根根孤立。
     *
     * <p>做法：以树冠中心为基准，给每个枝梢算一个"离中心多远"的权重 ——
     * 靠里的枝梢叶簇更大更密（那是真的枝多），靠外的小而疏（树冠边缘本来就透光）。
     * 最后再沿椭圆轮廓描一圈不规则的外缘，让树冠有形状。</p>
     */
    private void canopy(List<float[]> tips, float centerX, float centerY, float radius) {
        for (float[] tip : tips) {
            float dx = tip[0] - centerX;
            float dy = tip[1] - centerY;
            float d = (float) Math.sqrt(dx * dx + dy * dy) / Math.max(1.0F, radius);
            // 越靠中心，叶簇越大
            float k = Math.max(0.35F, 1.15F - d * 0.65F);
            float r = radius * 0.16F * k;
            this.leafCluster((int) tip[0], (int) tip[1], r);
            // 靠中心的多补一小簇（让树冠中间实、边缘透）
            if (d < 0.55F) {
                this.leafCluster((int) (tip[0] + (this.c.rng.nextFloat() - 0.5F) * r),
                        (int) (tip[1] + (this.c.rng.nextFloat() - 0.5F) * r), r * 0.62F);
            }
        }
    }

    /** 主干右侧的投影（光从左上来）。 */
    private void trunkShadow(float cx, float groundY, float forkX, float forkY,
                             float trunkH, float trunkW) {
        int occ = (int) Math.abs(groundY - forkY);
        for (int i = 0; i < occ; i++) {
            float t = i / (float) Math.max(1, occ);
            float y = groundY + (forkY - groundY) * t;
            float x = cx + (forkX - cx) * t + trunkW * 0.66F;
            float w = trunkW * (0.5F - t * 0.22F);
            this.c.disc((int) (x + w * 0.5F), (int) y, Math.max(1.0F, w),
                    0xFF0A0604, 0.30F);
        }
    }

    private void tree(float cx, float groundY, float trunkH, float trunkW) {
        // **第一阶还不是树** —— 是一棵刚钻出地面的幼苗。
        // 之前这里没有分支，stage 0 画的是一棵缩小版的大树（有树皮、有分枝），
        // 那当然不像幼苗：幼苗是另一种东西，细嫩的茎 + 两片摊开的子叶，
        // 没有树皮、没有分枝、也没有成树的深色叶。
        if (this.c.stage <= 0) {
            new SeedPainter(this.c, this.c.rng).sprout(cx, groundY, trunkH);
            return;
        }
        int levels = this.c.rampCount(TreeCanvas.LEVELS);
        // **完全重画**：换成现实树的骨架（主干分叉 + 主枝放射）。
        // 下面那一套旧逻辑（沿主干左右交替挂枝 = 针叶树的画法）不再使用，
        // 留在这里只是为了对照 —— 想回退把开关打开即可。
        this.realTree(cx, groundY, trunkH, trunkW, Math.max(1, levels));
    }

    /** 递归分枝：子枝半径 = 父枝 / √n（达芬奇的规则，看起来才对劲）。 */
    private void branch(float x, float y, float angle, float len, float w,
                        int level, int maxLevel, List<float[]> tips) {
        float ex = x + (float) Math.cos(angle) * len;
        float ey = y + (float) Math.sin(angle) * len;
        float ew = w * TreeCanvas.LENGTH_RATIO;
        this.c.limbShaded(x, y, ex, ey, w, ew * 0.72F, 2);

        if (level >= maxLevel || len < 6.0F) {
            tips.add(new float[]{ex, ey});
            return;
        }
        int kids = Math.min(TreeCanvas.KIDS[Math.min(level, TreeCanvas.KIDS.length - 1)], 3);
        float each = w / (float) Math.sqrt(kids + 1);
        // **分叉要不齐** —— 真实的大树没有两根一样长的枝。
        // 之前这里是"角度 0.42~0.72 + 长度 0.72~0.94"，
        // 每根枝几乎一样长、角度也匀 —— 那就是"看着像程序画的"的来源。
        // 现在角度 0.22~1.05、长度 0.52~0.98，长短悬殊出来才有树的形状。
        // 主枝继续往原方向走（比侧枝略短，符合真实的分叉）
        this.branch(ex, ey, angle + (this.c.rng.nextFloat() - 0.5F) * 0.26F,
                len * TreeCanvas.LENGTH_RATIO * (0.60F + this.c.rng.nextFloat() * 0.24F),
                each, level + 1, maxLevel, tips);
        for (int i = 0; i < kids; i++) {
            float spread = (0.22F + this.c.rng.nextFloat() * 0.83F) * (i % 2 == 0 ? 1.0F : -1.0F);
            float lenK = 0.52F + this.c.rng.nextFloat() * 0.46F;
            this.branch(ex, ey, angle + spread, len * TreeCanvas.LENGTH_RATIO * lenK,
                    each, level + 1, maxLevel, tips);
        }
    }

    private void leafCluster(float x, float y, float r) {
        int n = 5 + this.c.rng.nextInt(4);
        // 偏向那一侧的叶簇更大一点（不对称是"像真树"的关键之一）
        if (this.c.canopyBias != 0.0F) {
            float side = this.c.width > 0 ? (x - this.c.width / 2.0F) / (this.c.width / 2.0F) : 0.0F;
            r *= 1.0F + side * this.c.canopyBias * 0.22F;
        }
        for (int i = 0; i < n; i++) {
            double a = this.c.rng.nextDouble() * Math.PI * 2.0D;
            double d = this.c.rng.nextDouble() * r * 0.85D;
            float lx = x + (float) Math.cos(a) * (float) d;
            float ly = y + (float) Math.sin(a) * (float) d * 0.85F;
            float lr = r * (0.42F + this.c.rng.nextFloat() * 0.30F);
            int c = this.c.leaf[2 + this.c.rng.nextInt(TreeCanvas.LEAF.length - 3)];
            // **强化叶：把"这个槽选了什么"直接长在叶子上**，不用写字。
            // 哪一族 → 染成那一族的颜色；族内第几档 → 越亮、越大，
            // 第三档再给一圈光晕。扫一眼树冠就知道自己点到哪了。
            if (this.c.upgradeLeafCounter < TreeCanvas.MAX_UPGRADE_LEAF) {
                int slot = this.c.upgradeLeafCounter;
                int tier = this.choiceOf(slot);
                int accent = TreeCanvas.FAMILY_COLORS[slot % TreeCanvas.FAMILY_COLORS.length];
                if (tier > 0) {
                    c = this.c.mix(c, accent, Math.min(1.0F, 0.42F + tier * 0.19F));
                    lr *= 0.86F + tier * 0.12F;
                }
                this.leafShape(lx, ly, lr, c);
                if (tier >= 3) {
                    this.c.disc((int) lx, (int) ly, lr * 1.5F, accent, 0.22F);
                }
                this.registerLeaf((int) lx, (int) ly, (int) lr);
            } else {
                this.leafShape(lx, ly, lr, c);
            }
        }
        // 叶子底下的暗部，让簇看起来有厚度
        this.c.disc((int) x, (int) (y + r * 0.35F), r * 0.7F, this.c.leaf[0], 0.35F);
        // **星光的边**：光源在左上，所以左上那一缘亮一线、右下沉一圈。
        // 这一笔是整个树冠"立体"的来源 —— 没有它，叶子看着是贴纸。
        int rim = 14 + this.c.rng.nextInt(10);
        for (int i = 0; i < rim; i++) {
            double a = Math.PI * (1.02D + this.c.rng.nextDouble() * 0.62D);   // 左上 ~ 下端偏左
            float d = r * (0.70F + this.c.rng.nextFloat() * 0.34F);
            int px = (int) (x + Math.cos(a) * d);
            int py = (int) (y + Math.sin(a) * d * 0.85F);
            this.c.blendDot(px, py, TreeCanvas.STARLIGHT, 0.20F + this.c.rng.nextFloat() * 0.18F);
        }
        // 右下缘压暗
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * (0.05D + this.c.rng.nextDouble() * 0.45D);
            float d = r * (0.72F + this.c.rng.nextFloat() * 0.30F);
            this.c.blendDot((int) (x + Math.cos(a) * d), (int) (y + Math.sin(a) * d * 0.85F),
                    0xFF05080A, 0.16F);
        }
        // ------------------------------------------------------------------
        //  **透星的缝隙** —— 树冠不该是一块实心的黑。
        //
        //  真实的大树夜景是：树冠的轮廓映在星空上，而叶子的缝隙里透着星。
        //  这一步就是把叶簇上挖几个小洞（写全透明）——
        //  树层是透明的，洞那里会透出下一层（背景的星空）。
        //
        //  只在长成之后挖（阶数低的小树苗本来就稀疏，不需要）。
        // ------------------------------------------------------------------
        if (this.c.stage >= 3 && this.c.pixels != 0L) {
            int holes = 1 + this.c.rng.nextInt(3);
            for (int h = 0; h < holes; h++) {
                float hx = x + (this.c.rng.nextFloat() - 0.5F) * r * 1.10F;
                float hy = y + (this.c.rng.nextFloat() - 0.5F) * r * 0.95F;
                int rad = Math.max(1, (int) (r * (0.10F + this.c.rng.nextFloat() * 0.18F)));
                for (int dy = -rad; dy <= rad; dy++) {
                    for (int dx = -rad; dx <= rad; dx++) {
                        if (dx * dx + dy * dy > rad * rad) {
                            continue;
                        }
                        int px = (int) hx + dx;
                        int py = (int) hy + dy;
                        if (px < 0 || py < 0 || px >= this.c.width || py >= this.c.height) {
                            continue;
                        }
                        this.c.put(px, py, 0);          // 全透明 → 透出后面的星空
                    }
                }
            }
        }
    }

    /** 一片叶子：椭圆的，带一点叶尖。 */
    private void leafShape(float cx, float cy, float r, int color) {
        int rad = Math.max(2, (int) Math.ceil(r));
        for (int y = -rad; y <= rad; y++) {
            for (int x = -rad; x <= rad; x++) {
                float d = (float) Math.sqrt(x * x + y * y);
                // 叶尖：往左上尖一点
                float tip = x * -0.22F + y * -0.18F;
                if (d > r + tip) {
                    continue;
                }
                float edge = 1.0F - Math.max(0.0F, d - r * 0.68F) / Math.max(0.6F, r * 0.32F);
                int px = (int) cx + x;
                int py = (int) cy + y;
                int c = color;
                if (x * TreeCanvas.LIGHT_X + y * TreeCanvas.LIGHT_Y < -r * 0.35F) {
                    c = this.c.mix(color, 0xFF000000, 0.28F);   // 背光
                } else if (x * TreeCanvas.LIGHT_X + y * TreeCanvas.LIGHT_Y > r * 0.30F) {
                    c = this.c.mix(color, 0xFFFFFFFF, 0.16F);   // 受光
                }
                this.c.blendDot(px, py, c, Math.min(1.0F, edge + 0.35F));
            }
        }
        // **叶脉** —— 没有脉的叶子就是一块绿斑
        int vein = this.c.mix(color, 0xFF08160E, 0.5F);
        int litVein = this.c.mix(color, 0xFFE8FFE0, 0.28F);
        // 中脉（贯穿）
        for (int y = -rad; y <= rad; y++) {
            float k = 1.0F - Math.abs(y) / (float) Math.max(1, rad);
            this.c.blendDot((int) cx, (int) cy + y, vein, 0.34F * k);
        }
        // 侧脉（从中脉斜向两侧）
        int gap = Math.max(1, rad / 2);
        for (int i = -rad + 1; i <= rad - 1; i += gap) {
            for (int s = 1; s <= (int) (rad * 0.72F); s++) {
                float fade = 1.0F - s / (rad * 0.85F);
                if (fade <= 0) {
                    continue;
                }
                this.c.blendDot((int) (cx + s), (int) (cy + i + s * 0.42F), vein, 0.2F * fade);
                this.c.blendDot((int) (cx - s), (int) (cy + i + s * 0.42F), vein, 0.2F * fade);
            }
        }
        // 叶尖上的一点受光
        this.c.blendDot((int) cx, (int) (cy - rad * 0.7F), litVein, 0.3F);
    }

    /** 第 slot 个强化槽选了什么（0 = 空，1/2/3 = 该族的第几档）。 */
    private int choiceOf(int slot) {
        if (this.c.choices == null || slot < 0 || slot >= this.c.choices.length) {
            return 0;
        }
        return Math.max(0, Math.min(3, this.c.choices[slot]));
    }

    /** 记一片可以点的叶。 */
    private void registerLeaf(int x, int y, int r) {
        if (this.c.upgradeLeafCounter >= TreeCanvas.MAX_UPGRADE_LEAF) {
            return;
        }
        // 同上：坐标换算回最终尺寸
        this.c.leaves.add(new int[]{(int) (x / (float) this.c.ss), (int) (y / (float) this.c.ss),
                Math.max(4, (int) (r / (float) this.c.ss) + 3)});
        this.c.upgradeLeafCounter++;
    }

    /** 树冠里的灵魂光点（少量，飘着）。 */
    private void soulMotes() {
        int n = 10 + (int) (this.c.grow() * 26.0F);
        for (int i = 0; i < n; i++) {
            float x = this.c.rng.nextFloat() * this.c.width;
            float y = this.soilTop * (0.12F + this.c.rng.nextFloat() * 0.82F);
            float r = 0.8F + this.c.rng.nextFloat() * 1.8F;
            this.c.disc((int) x, (int) y, r, TreeCanvas.MOSS_LIT, 0.55F);
            this.c.disc((int) x, (int) y, r * 2.4F, TreeCanvas.MOSS, 0.16F);
        }
    }
}
