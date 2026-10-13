package com.habaiwa.goetyrevenant.client.tree;

/**
 * **树根的绘制** —— 从树基往下渗透，越深越不像木头，最后完全是血管。
 *
 * <p>从 `TreeTexture` 抽出来的。</p>
 *
 * <h3>按现实根系的结构</h3>
 * <pre>
 *   主根   垂直向下，直径沿深度递减
 *   侧根   前段近水平外扩 → 之后逐渐下弯（"向地性"给的那条弧线）
 *   细根   从侧根末端分出，短、细、多
 *   根毛   根尖后一小段密集的细毛
 * </pre>
 *
 * <h3>三条血肉线各有性格</h3>
 * <p>半路上根会分成三支 —— 对应血肉线的三条路。看一眼根须就知道这个人往哪条路走了：</p>
 * <ul>
 *   <li><b>异化</b>（向外、增殖）根须多、粗、分叉密</li>
 *   <li><b>灵化</b>（向内、轻）根须细、长、弯曲，像飘着的丝</li>
 *   <li><b>硅化</b>（无机、硬）根须短、直、少分叉</li>
 * </ul>
 *
 * <p>节点坐标写进画布的 `wayNodes` —— 界面之后靠它画三条路的节点与连线。</p>
 */
public final class RootPainter {
    /** 根往下能扎多深的跨度（画的时候算一次，供后续步骤用）。 */
    private float depthSpanLocal = 1.0F;
    private final TreeCanvas c;
    /** 土壤表面（内容图坐标）—— 根从这往下扎。 */
    private final float soilTop;
    /** 过渡层底 = 血肉土壤顶 —— 根进到这一层就开始"变成血管"。 */
    private final float fleshTop;

    public RootPainter(TreeCanvas canvas, float soilTop, float fleshTop) {
        this.c = canvas;
        this.soilTop = soilTop;
        this.fleshTop = fleshTop;
    }

    /** 画整棵根系（主根 → 侧根 → 细根 → 三路分叉）。 */
    public void paint(float cx, float groundY, float trunkW) {
        this.roots(cx, groundY, trunkW);
    }

    // ======================================================================
    //  **三条血肉线各自的根须性格**
    //
    //  三条路是三种"不再是人了"，根系也该是三种样子 —— 这比在界面上写名字
    //  更能说明问题：看一眼根须就知道这个人往哪条路走了。
    //
    //      异化（向外、增殖）  根须多、粗、分叉密 —— 一路向外扩张
    //      灵化（向内、轻）    根须细、长、弯曲    —— 像飘着的丝
    //      硅化（无机、硬）    根须短、直、少分叉  —— 像晶体，不弯
    // ======================================================================
    /** 每条路发几条侧根。 */
    private static final int[] WAY_LATERALS = {16, 10, 8};
    /** 每条路侧根的弯曲程度（越大越飘/越弯，越小越直）。 */
    private static final float[] WAY_CURL = {0.85F, 1.45F, 0.25F};
    /** 每条路根须的粗细倍率。 */
    private static final float[] WAY_THICK = {1.25F, 0.70F, 0.95F};
    /** 每条路的细根密度倍率（末端那一撮）。 */
    private static final float[] WAY_FINE = {1.50F, 1.00F, 0.70F};
    /** 三条路的颜色（和 FleshLine 那套对得上）。 */
    private static final int[] WAY_TINT = {0xFF4A1418, 0xFF2A2A34, 0xFF343038};


    /** 树根：从主干底部散开、扎进土里；长得越大，根越粗、越把土拱开。 */
    /**
     * **魂树的根** —— 从树基往下渗透，越深越不像木头，最后完全是血管；
     * 半路上分成三条，就是血肉线的三条路。
     *
     * <h3>沿路的变化（这是"渗透"的实质）</h3>
     * <pre>
     *   土壤里（深 0.00~0.22）  木质：树皮的颜色，有受光面
     *   土壤下缘（深 0.22~0.46）**树皮裂开**，里面透出肉 —— 半木半肉
     *   血肉上部（深 0.46~0.72）**完全肉质**：和周围的肌肉同质感、有湿润高光
     *   血肉深处（深 0.72~1.00）**血管**：外圈暗、内芯亮，并且开始分叉出毛细血管
     * </pre>
     *
     * <p>所以"根"和"血管"不是两样东西 —— 是<b>同一条东西在不同深度上的样子</b>。
     * 走到这片肉的深处，你已经分不清哪里是根、哪里是血管了。</p>
     */
    private void roots(float cx, float groundY, float trunkW) {
        float g = this.c.grow();
        float total = Math.max(40.0F, this.c.height - groundY);
        float depthSpan = Math.max(1.0F, this.c.height - this.soilTop);

        // ==================================================================
        //  **主根（taproot）** —— 真实树的第一条根：从树干基部【垂直向下】，
        //  在土里轻微摆动（不是笔直，也不是斜着拉到底），越深越细。
        // ==================================================================
        int steps = (int) (total / 2.0F);
        float prevX = cx;
        float prevY = groundY - 2.0F;
        boolean branched = false;
        for (int s = 1; s <= steps; s++) {
            float t = s / (float) steps;
            float y = groundY - 2.0F + total * t;
            // 摆动：两个不同频率的正弦，幅度随深度略增 ——
            // 看着是"顺着土的缝往下扎"，而不是一条铅垂线
            float sway = (float) (Math.sin(t * 5.2D) * 0.55D + Math.sin(t * 13.0D + 1.2D) * 0.22D)
                    * trunkW * (0.5F + t * 0.9F);
            float x = cx + sway;
            float w = Math.max(1.3F, trunkW * 0.40F * (1.0F - t * 0.72F));
            float depth = (y - this.soilTop) / depthSpan;
            this.rootSegment(x, y, w, depth, prevX, prevY);

            // **侧根**：从主根的不同深度发出去（水平外扩、再下垂）
            if (s > steps * 0.12F && s % 11 == 0) {
                float side = (this.c.rng.nextBoolean() ? 1.0F : -1.0F);
                this.lateral(x, y, side, w * 0.62F, depth, 1);
            }
            // **分叉**：走到血肉层上部时，主根分成三支 ——
            // 这才是"三条路"该有的样子：一棵树先从一根长下来，再分开。
            // 之前是三条从基部并排斜着画下去，那不像树，像三根钉子。
            if (!branched && depth > 0.58F) {
                branched = true;
                for (int w2 = 0; w2 < 3; w2++) {
                    float dir = (w2 - 1) * 0.85F;      // 左 / 中 / 右
                    this.splitRoot(x, y, dir, w * 0.70F, depth, w2);
                }
            }
            prevX = x;
            prevY = y;
        }

        // ---- 土壤里的须根（只在土里，木质的细根）----
        int fine = 8 + Math.round(g * 10.0F);
        for (int i = 0; i < fine; i++) {
            float side = (i % 2 == 0) ? 1.0F : -1.0F;
            float spread = 0.3F + (i / (float) fine) * 1.8F;
            float len = trunkW * (1.4F + this.c.rng.nextFloat() * 1.3F) * (0.7F + g);
            float sx = cx + side * trunkW * 0.20F;
            float sy = groundY - 1.0F;
            float maxY = this.soilTop + (this.fleshTop - this.soilTop) * 0.9F;
            float ey = Math.min(maxY, sy + len * (0.7F + g * 0.5F));
            this.c.limbShaded(sx, sy, sx + side * len * spread, ey,
                    Math.max(1.4F, trunkW * 0.26F), 0.8F, 1);
        }

        // ---- 血肉里的血管分叉（从三条主路的深处散出去）----
        int vb = 10 + Math.round(g * 12.0F);
        for (int i = 0; i < vb; i++) {
            int way = i % 3;
            int stage = 4 + (i % 3);
            int[] node = this.c.wayNodes[way][Math.min(TreeCanvas.STAGES_PER_WAY - 1, stage)];
            if (node == null || node[0] == 0) {
                continue;
            }
            float nx = node[0] * this.c.ss;
            float ny = node[1] * this.c.ss;
            double ang = this.c.rng.nextDouble() * Math.PI * 2.0D;
            this.c.capillary(nx, ny, ang, this.fleshTop, this.c.height, 2);
        }

        // 旧的那套"随机方向支根"不再画了 —— 新根系里主根、侧根、三支都是
        // 按真实树的结构走的，再叠一层随机方向的粗根只会显得乱。
    }

    /**
     * **一段根** —— 按深度决定它现在是什么。
     *
     * <pre>
     *   深 0.00~0.22  木质：树皮的颜色 + 受光面
     *   深 0.22~0.46  树皮裂开，里面透出肉
     *   深 0.46~0.72  完全肉质
     *   深 0.72~1.00  血管：外圈暗、内芯亮
     * </pre>
     */
    private void rootSegment(float x, float y, float w, float depth, float fromX, float fromY) {
        if (depth < 0.22F) {
            this.c.disc((int) x, (int) y, w, this.c.bark[1], 0.95F);
            this.c.blendDot((int) (x - w * 0.5F), (int) y, TreeCanvas.BARK_LIGHT, 0.22F);
        } else if (depth < 0.46F) {
            float k = (depth - 0.22F) / 0.24F;
            this.c.disc((int) x, (int) y, w, this.c.bark[1], 0.9F);
            this.c.disc((int) x, (int) y, w * 0.72F, TreeCanvas.MEAT_DEEP, 0.20F + k * 0.55F);
            if (this.c.rng.nextInt(5) == 0) {
                this.c.blendDot((int) x, (int) y, 0xFF160608, 0.32F);
            }
        } else if (depth < 0.72F) {
            this.c.disc((int) x, (int) y, w * 0.96F, TreeCanvas.MEAT_DEEP, 0.85F);
            this.c.blendDot((int) (x - w * 0.35F), (int) y, TreeCanvas.MEAT_FRESH, 0.28F);
            this.c.blendDot((int) x, (int) (y + w * 0.3F), 0xFF20080C, 0.20F);
            if (this.c.rng.nextInt(7) == 0) {
                this.c.blendDot((int) x, (int) y, TreeCanvas.WET, 0.12F);
            }
        } else {
            this.c.disc((int) x, (int) y, w + 1.5F, 0xFF2A080C, 0.55F);
            this.c.disc((int) x, (int) y, w, TreeCanvas.MEAT_FRESH, 0.60F);
            this.c.blendDot((int) x, (int) y, 0xFFD25A60, 0.34F);
            // 走到血管这一段就开始散毛细血管
            if (this.c.rng.nextInt(26) == 0) {
                this.c.capillary(x, y, this.c.rng.nextDouble() * Math.PI * 2.0D,
                        this.fleshTop, this.c.height, 2);
            }
        }
    }

    /**
     * **按性格长的侧根** —— 先水平外扩、再向下弯（现实的规律），
     * 弯曲程度和粗细按这条路走。
     *
     * <p>异化的根须又粗又爱分叉；灵化的又细又飘（几乎像丝）；
     * 硅化的短直、拐角硬（像晶体而不是肉）。</p>
     */
    private void lateralStyled(float x, float y, float side, float w, float depth, int way) {
        int idx = Math.min(way, 2);
        float curl = WAY_CURL[idx];
        float thick = WAY_THICK[idx];
        depthSpanLocal = Math.max(1.0F, this.c.height - this.soilTop);

        float px = x;
        float py = y;
        float dirX = side * 1.0F;
        float dirY = 0.10F;
        int steps = 22 + this.c.rng.nextInt(20);
        for (int s = 0; s < steps; s++) {
            // **向地性**：竖直分量持续增加 —— 侧根先平后垂，这就是那个弧
            dirY += 0.026F / curl;
            dirX *= (1.0F - 0.012F * curl);
            // 灵化的会额外飘（正弦扰动）；硅化的几乎是直线
            float wobble = curl > 1.0F
                    ? (float) Math.sin(s * 0.42F + x * 0.03F) * 0.10F * curl
                    : 0.0F;
            px += (dirX + wobble) * 1.6F;
            py += dirY * 1.6F;
            if (px < 3.0F || px > this.c.width - 3.0F || py >= this.c.height - 2.0F) {
                break;
            }
            float d = (py - this.soilTop) / depthSpanLocal;
            float ww = Math.max(0.7F, w * thick * (1.0F - s / (float) steps * 0.82F));
            this.rootSegment(px, py, ww, d, 0, 0);
        }
        // **末端一撮细根**（现实中根尖分出的那些短根）
        this.fineRoots(px, py, dirX, dirY, WAY_FINE[idx], way);
    }

    /**
     * **细根 + 根毛** —— 根尖那一撮。
     *
     * <p>现实中一棵树的根尖有几百万条，这里按密度倍率给一小撮：
     * 短（6~20 像素）、细（<1 像素宽）、方向散开但偏母根方向。</p>
     */
    private void fineRoots(float x, float y, float dirX, float dirY, float density, int way) {
        int idx = Math.min(way, 2);
        int count = (int) ((6 + this.c.rng.nextInt(9)) * density);
        float baseAng = (float) Math.atan2(dirY, dirX);
        for (int i = 0; i < count; i++) {
            // 方向：母根方向 ± 散开（硅化的散得小，因为不弯）
            float spreadK = idx == 2 ? 0.55F : 1.15F;
            float ang = baseAng + (this.c.rng.nextFloat() - 0.5F) * spreadK;
            float len = (5.0F + this.c.rng.nextFloat() * 14.0F) * (0.6F + density * 0.4F);
            float fx = x;
            float fy = y;
            for (int s = 0; s < (int) len; s++) {
                ang += (this.c.rng.nextFloat() - 0.5F) * (idx == 2 ? 0.06F : 0.20F);
                fx += (float) Math.cos(ang);
                fy += (float) Math.sin(ang) * 1.15F;   // 略微向下
                if (fy >= this.c.height - 1.0F || fx < 1.0F || fx > this.c.width - 1.0F) {
                    break;
                }
                float d = (fy - this.soilTop) / depthSpanLocal;
                this.rootSegment(fx, fy, Math.max(0.6F, 0.9F * WAY_THICK[idx]), d, 0, 0);
            }
            // **根毛**：细根末端后面那一小段密集的短毛（1 像素）
            if (this.c.rng.nextBoolean()) {
                int hairs = 3 + this.c.rng.nextInt(6);
                for (int h = 0; h < hairs; h++) {
                    float ha = ang + (this.c.rng.nextFloat() - 0.5F) * 1.6F;
                    float hx = fx + (float) Math.cos(ha) * (1 + this.c.rng.nextInt(3));
                    float hy = fy + (float) Math.sin(ha) * (1 + this.c.rng.nextInt(3));
                    float hd = (hy - this.soilTop) / depthSpanLocal;
                    this.rootSegment(hx, hy, 0.55F, hd, 0, 0);
                }
            }
        }
    }

    /**
     * **侧根** —— 从主根发出去，先<b>水平外扩</b>、再<b>向下弯</b>。
     *
     * <p>这个"先平后垂"的弧度是树根最认得出的特征。画成斜直线的话，
     * 看着就像插进去几根棍子。</p>
     */
    private void lateral(float x, float y, float side, float w, float depth, int level) {
        if (level <= 0 || w < 0.7F) {
            return;
        }
        float px = x;
        float py = y;
        float dirX = side * 1.0F;
        float dirY = 0.08F;
        int steps = 26 + this.c.rng.nextInt(22);
        float depthSpan = Math.max(1.0F, this.c.height - this.soilTop);
        for (int s = 0; s < steps; s++) {
            dirY += 0.030F;          // 逐渐下垂
            dirX *= 0.982F;          // 水平速度衰减
            px += dirX * 1.7F;
            py += dirY * 1.7F;
            if (px < 4.0F || px > this.c.width - 4.0F || py >= this.c.height - 2.0F) {
                break;
            }
            float d = (py - this.soilTop) / depthSpan;
            float ww = w * (1.0F - s / (float) steps * 0.8F);
            this.rootSegment(px, py, Math.max(0.8F, ww), d, 0, 0);
        }
        // 末端再分一条细的
        if (this.c.rng.nextBoolean()) {
            this.lateral(px, py, side * (this.c.rng.nextBoolean() ? 1.0F : -1.0F),
                    w * 0.5F, depth, level - 1);
        }
    }

    /**
     * **主根分出来的三支** —— 就是血肉线的三条路。
     *
     * @param dir -0.85 / 0 / +0.85（左 / 中 / 右）
     */
    private void splitRoot(float x, float y, float dir, float w, float depth, int way) {
        float depthSpan = Math.max(1.0F, this.c.height - this.soilTop);
        // 终点：左支往左下、中支往最深处、右支往右下
        float tx = x + this.c.width * dir * 0.30F;
        float ty = this.c.height * (way == 1 ? 0.98F : 0.90F);
        tx = Math.max(10.0F, Math.min(this.c.width - 10.0F, tx));
        int steps = (int) Math.max(30.0F, Math.hypot(tx - x, ty - y) / 2.0F);
        float px = x;
        float py = y;
        float dirX = dir * 0.6F;
        float dirY = 1.0F;
        int nextStage = 0;
        for (int s = 0; s <= steps; s++) {
            float t = s / (float) steps;
            // 往终点收，但只收一点点（收多了就是直线）——
            // 加上自身的摆动，出来是一条弯的根
            dirX = dirX * 0.94F + (dir * 0.9F) * 0.06F;
            dirY = dirY * 0.96F + 1.0F * 0.04F;
            float wob = (float) Math.sin(t * 9.0D + way * 2.1D) * 0.30F;
            px += (dirX + wob) * 1.8F;
            py += dirY * 1.8F;
            if (py >= this.c.height - 2.0F || px < 4.0F || px > this.c.width - 4.0F) {
                break;
            }
            float d = (py - this.soilTop) / depthSpan;
            float ww = Math.max(1.0F, w * (1.0F - t * 0.6F));
            this.rootSegment(px, py, ww, d, 0, 0);

            // **侧根**：按这条路的性格发（异化多、灵化飘、硅化直）
            int laterals = WAY_LATERALS[Math.min(way, 2)];
            float step = Math.max(1.0F, steps / (float) laterals);
            if (s > steps * 0.15F && Math.abs(s % step) < 1.0F) {
                float side = (this.c.rng.nextBoolean() ? 1.0F : -1.0F);
                this.lateralStyled(px, py, side, ww * 0.58F, d, way);
            }

            // 七个节点均分在这一支上
            if (nextStage < TreeCanvas.STAGES_PER_WAY) {
                float want = (nextStage + 1) / (float) (TreeCanvas.STAGES_PER_WAY + 1);
                if (t >= want) {
                    this.c.wayNodes[way][nextStage][0] = (int) (px / this.c.ss);
                    this.c.wayNodes[way][nextStage][1] = (int) (py / this.c.ss);
                    this.c.wayAlive[way] = true;
                    nextStage++;
                }
            }
        }
    }

}
