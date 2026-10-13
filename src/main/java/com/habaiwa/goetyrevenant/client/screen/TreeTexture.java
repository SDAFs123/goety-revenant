package com.habaiwa.goetyrevenant.client.screen;

import com.habaiwa.goetyrevenant.client.tree.SeedPainter;

import com.habaiwa.goetyrevenant.client.tree.TreeLayout;

import com.habaiwa.goetyrevenant.common.body.UpgradeSlots;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * **那棵树** —— 一整屏像素画的灵魂之树，一次画好、每帧只 blit 一次。
 *
 * <h3>它不是悬空的</h3>
 * <p>树长在<b>土里</b>：屏幕下面五分之一是一层有厚度、有层理、有石子的土壤，
 * 一条起伏的表土上长着灵魂色的苔。树根从主干底部散开、扎进去 —— 所以它是一棵
 * <b>站在地上的树</b>，不是飘着的一根木头。</p>
 *
 * <h3>四个阶段，一路长上去</h3>
 * <table>
 *   <tr><th>阶段</th><th>形态</th></tr>
 *   <tr><td><b>幼苗</b></td><td>刚破土的一段嫩茎 + 两片子叶，土面上还留着裂口</td></tr>
 *   <tr><td><b>小树</b></td><td>细主干、两根一级枝、几簇稀疏的叶</td></tr>
 *   <tr><td><b>成树</b></td><td>主干变粗、出现树皮纹与年轮节，四根一级枝再来二级分叉，树冠成形</td></tr>
 *   <tr><td><b>巨树</b></td><td>主干粗壮、六根一级枝到四级分叉，树冠茂密，树根把表土都拱开了</td></tr>
 * </table>
 * <p>阶段之间是<b>连续插值</b>的：不是"啪"地换一张图，而是每一级台阶都在长粗、长高、长密。</p>
 *
 * <h3>挂在树上的东西</h3>
 * <ul>
 *   <li><b>年轮节</b>（主干上的横向环）= 嵌入位，6 个</li>
 *   <li><b>叶簇里的叶片</b> = 强化叶，24 片</li>
 * </ul>
 *
 * <h3>为什么这样不卡</h3>
 * <p>整棵树只在<b>尺寸 / 序列 / 缩放 / 强化指纹</b>变化时重画一次，
 * 之后每帧只有一次 blit。</p>
 */
public final class TreeTexture extends com.habaiwa.goetyrevenant.client.tree.TreeCanvas {
    /** 日志器 —— 计时信息走它（System.out 在启动器里不一定进日志）。 */
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    /** 纹理名可以配 —— "种子"和"树"要同时存在（生长动画要把两张叠着画）。 */
    private final ResourceLocation tex;
    /** 构造时给的名字 —— 后台生成要用它再开一个临时实例。 */
    private final String texName;
    /** 这一层是不是"只有地层"。 */
    private final boolean terrainOnly;
    /** 画的是不是"裂开的种子"（名字里带 open）。 */
    private final boolean openSeed;

    public TreeTexture() {
        this("soul_tree");
    }

    public TreeTexture(String name) {
        this(name, false);
    }

    /**
     * @param terrainOnly <b>只画地层</b>（土壤 / 过渡 / 血肉土壤）——
     *                    这一层是<b>静态</b>的，只在画面尺寸变化时生成一次。
     *                    false = 只画树那一层（树 / 种子 / 根）。
     *
     * <p>为什么要拆：地层和树的变化频率完全不同。玩家换个嵌入槽，
     * 树要重画 —— 但土壤一个像素都不该动。之前是一整张图一起重画，
     * 于是每动一下都要把三层的颗粒重新撒一遍，白等好几秒。</p>
     */
    public TreeTexture(String name, boolean terrainOnly) {
        this.texName = name;
        this.terrainOnly = terrainOnly;
        this.openSeed = name.contains("open");
        this.tex = new ResourceLocation("goety_revenant", "dynamic/" + name);
    }

    // LEAF（叶色阶）在 TreeCanvas 里（protected）

    /** 缺氧的紫（淤血/深层）。 */
    private static final int MEAT_PURPLE = 0xFF2E141C;
    /** 脂肪：暗黄褐（不是亮黄白 —— 地下的脂肪也是暗的）。 */
    private static final int FAT = 0xFF6A5836;
    /** 筋膜：暗灰（不是亮灰白）。 */
    private static final int FASCIA = 0xFF544E4A;
    private static final int SOIL_GRAIN_B = 0xFF2A1E14;

    /**
     * **四族被动的代表色** —— 叶子靠它来说话，不用写字。
     *
     * <p>顺序和 {@code UpgradeType.Group} 一致：魂 / 战 / 连携 / 神识。
     * 一个槽选了哪一族，它对应的那片叶就染成那一族的颜色；
     * 族内选的是第几个（1/2/3）则表现为叶片<b>越来越亮、越来越大</b>，
     * 第三档还带一圈光晕。于是"选了什么都长在叶子上"。</p>
     */
    private static final int[] FAMILY_COLORS = {
            0xFF52E8DC,   // 魂：魂青
            0xFFC02030,   // 战：血红
            0xFFE08030,   // 连携：焰橙
            0xFF9060D0,   // 神识：幽紫
    };


    // KEY 在 TreeCanvas 里（protected）
    /** 主干高度占"地面到屏幕上沿"的比例。 */
    // ======================================================================
    //  **树在内容图上占多高** —— 这个比例同时决定了"看不看得全"和"细节够不够"。
    //
    //  树高 = (从土壤顶往上到内容图顶的那段) × 这个系数
    //        ≈ 0.33 × 系数
    //
    //  之前最大是 0.88 → 树高 ≈ 0.29 内容高，而一屏只有 0.263 ——
    //  【树比一屏还高】，所以打开时永远看不到完整的树。
    //  现在最大 0.46 → 树高 ≈ 0.15 内容高，占屏 57% —— 一屏装得下整棵树，
    //  而画布仍然是 3.8（空间够加东西，也够拖）。
    // ======================================================================
    private static final float[] TRUNK_H = {0.055F, 0.21F, 0.34F, 0.46F};
    /** 主干粗细系数。 */
    private static final float[] TRUNK_W = {0.22F, 0.45F, 0.72F, 1.00F};
    /** 一级枝数量。 */
    private static final int[] BRANCHES = {0, 2, 4, 6};
    /** 分叉层级。 */
    private static final int[] LEVELS = {0, 2, 3, 4};
    /** 叶簇数量。 */
    private static final int[] CLUSTERS = {2, 6, 11, 16};

    private static final int MAX_UPGRADE_LEAF = UpgradeSlots.COUNT;
    private static final float LENGTH_RATIO = 0.74F;

    /**
     * **超采样倍数** —— 整棵树按 2 倍分辨率画，画完再平均降回来。
     *
     * <p>这是"精细"的地基：枝干、叶片、石子全是圆和椭圆，
     * 在 1 倍分辨率下边缘必然是锯齿台阶；按 2 倍画再降采样，
     * 边缘就变成平滑过渡（等于免费的抗锯齿）。
     * 代价只是生成时多算几倍 —— 而这棵树一个会话只画一次。</p>
     */


    /**
     * **实际使用的超采样倍数** —— 按内容图的面积自适应。
     *
     * <p>超采样（2 倍画再降采样）是抗锯齿的地基，但它和"内容图比屏幕大 4.7 倍"
     * 叠在一起会爆：一个 1920×1080 的窗口，内容图 3456×2808，
     * 再乘 2 倍就是<b>3880 万像素</b> —— 一个 155 MB 的 NativeImage，
     * 光生成本身就要十几秒，界面上什么都看不见（甚至直接 OOM）。</p>
     *
     * <p>所以：图小的时候照旧 2 倍（抗锯齿），图大的时候降到 1 倍 ——
     * 宁可边缘略糙，也不能让界面打不开。</p>
     */
    // ss / SS_MAX 在 TreeCanvas 里（protected）

    /** 算出这次该用几倍超采样。 */
    private void pickSupersample() {
        long px = (long) this.contentW * this.contentH;
        this.ss = px > 1_100_000L ? 1 : SS_MAX;
        // （图放大之后几乎必然走 1 倍 —— 抗锯齿让位给"画得开"）
    }
    private static final int[] KIDS = {0, 3, 3, 2, 2};

    private DynamicTexture texture;
    /** 绘制尺寸（含超采样）—— build() 全程用它。 */
    /** 最终贴图尺寸（超采样之前）。 */
    private int drawW = -1;
    private int drawH = -1;
    private int stage = -2;
    /** 觉醒的序列序号（-1 = 还没觉醒，用中性色）。 */
    private int seqIndex = -1;
    /** 实际使用的树皮 / 叶子色阶（按序列染过色的）。 */
    // bark（树皮色阶）在 TreeCanvas 里（protected）
    // leaf / choices 在 TreeCanvas 里（protected）
    private long stamp = Long.MIN_VALUE;
    private float zoom = 1.0F;
    /**
     * **六个嵌入位各嵌了什么颜色的聚晶**（0 = 空）。
     *
     * <p>颜色由聚晶的派系（元素）决定 —— 于是不用任何文字，
     * 光看树干上那几个年轮是什么颜色，就知道里面嵌了什么。</p>
     */
    private int[] slotColors = new int[6];

    // slots / leaves 在 TreeCanvas 里（protected）—— 绘制产物由那一层统一持有

    private int upgradeLeafCounter = 0;

    // ======================================================================
    //  **请求号与结果交接** —— 修掉审查指出的竞态。
    //
    //  之前是"开线程 → 做完把结果赋给一个 volatile 字段"。volatile 只保证
    //  【看得见】，不保证【顺序对】：
    //
    //      A 先启动 → B 后启动 → B 先做完并显示 → A 后做完，把画面覆盖回旧状态
    //
    //  而且被覆盖掉的那个结果，它的 NativeImage 没人关闭（原生内存泄漏）。
    //
    //  现在每次请求发一个号；线程做完先核对"我还是最新的那个号吗"，
    //  不是的话就把自己的图像关掉、直接丢弃。
    //  交接本身用 AtomicReference 的 getAndSet —— "读取后清空"不会再丢东西。
    // ======================================================================
    /** 请求号：每次 ensure 递增，只有最新那个号的结果会被采纳。 */
    private final java.util.concurrent.atomic.AtomicLong requestSeq =
            new java.util.concurrent.atomic.AtomicLong();

    // ======================================================================
    //  **同时只允许一个生成线程** —— 顺带把中间的请求合并掉。
    //
    //  审查指出：窗口尺寸频繁变化时会连续开好几个线程，把运算搬到后台
    //  只是改善了主线程响应，并没有控制总 CPU 和内存负担。
    //
    //  现在这样：正在跑的时候再来请求，不新开线程，只把"还想要什么"记下来；
    //  跑完由 takePending 补一次 —— 所以拖窗口时中间那些尺寸根本不会真的生成，
    //  只有最后稳定下来的那个会被做出来。
    // ======================================================================
    // ----------------------------------------------------------------------
    //  **树层实际画到东西的包围盒** —— 先测量，再决定要不要真裁。
    //
    //  "裁掉透明区"能省多少内存，取决于树在内容图里占多大比例：
    //  占 30% 就值得改绘制映射，占 70% 就不划算还容易错位。
    //  所以先量出来（这一步只记录，不改任何绘制行为）。
    // ----------------------------------------------------------------------
    /** 有像素写入的范围（x0/y0 含，x1/y1 不含）；x1<=x0 表示还没写过。 */

    /** 有没有生成线程在跑。 */
    private final java.util.concurrent.atomic.AtomicBoolean busy =
            new java.util.concurrent.atomic.AtomicBoolean();
    /** 在跑期间收到的"还想要什么"（-1 = 没有排队的请求）。 */
    private int wantW = -1;
    private int wantH = -1;
    private int wantStage = 0;
    private int wantSeq = 0;
    private long wantStamp = 0L;
    /** 后台做好、等着主线程取走的那一份（原子交接）。 */
    private final java.util.concurrent.atomic.AtomicReference<TreeTexture> pendingRef =
            new java.util.concurrent.atomic.AtomicReference<>();
    /** 正在后台做的那一组参数（避免重复开线程）。 */
    private volatile int jobW = -1;
    private volatile int jobH = -1;
    private volatile int jobStage = 0;
    private volatile int jobSeq = 0;
    private volatile long jobStamp = 0L;

    private int viewW = -1;
    private int viewH = -1;
    /** 内容图尺寸（比视口大）。 */
    private int contentW = -1;
    private int contentH = -1;

    /** 土壤表面（内容图坐标）。 */
    private float soilTop;
    /** 土壤与血肉层的交界（内容图坐标）。 */
    private float fleshTop;
    /** 过渡层的顶（土壤的底）。 */
    private float blendTop;
    /** 根的节点（血肉晋升线的落点）：{x, y}。 */
    /**
     * **三条路 × 七阶的节点坐标**（最终尺寸，屏幕可用）。
     *
     * <p>魂树的根往下走会分成三条 —— 对应血肉线的三条路（异化 / 灵化 / 硅化）。
     * 每条路上有七个节点（一阶一个）。玩家走的是哪条，那条就亮起来；
     * 另外两条一直暗着 —— 它们是<b>没走的路</b>，看得见，但不会亮。</p>
     */
    // wayNodes / wayAlive / STAGES_PER_WAY 在 TreeCanvas 里（protected）

    public void ensure(int width, int height, int stage, int sequence, long stamp, float zoom) {
        // **zoom 不进缓存判断** —— 它只决定"看多近"，不该让内容重画一遍。
        // 之前它参与了，于是滚一下滚轮整张图（包括种子的大小）都会重算，看着就是"拉近视口种子也变大了"。
        // **缩放只影响"看多近"，不进生成**
        this.zoom = zoom;
        if (this.terrainOnly) {
            // **地层只看尺寸** —— 树的状态、嵌入槽、血肉线全都与它无关。
            // 于是它一辈子只生成一次（除非窗口大小变了）。
            if (this.image != null && this.viewW == width && this.viewH == height) {
                return;
            }
            if (this.jobW == width && this.jobH == height) {
                return;
            }
            this.jobW = width;
            this.jobH = height;
            this.jobStage = 0;
            this.jobSeq = 0;
            this.jobStamp = 0L;
            if (!this.busy.compareAndSet(false, true)) {
                // 已经有线程在跑 —— 只记下"还想要什么"，别再造一个
                this.wantW = width;
                this.wantH = height;
                this.wantStage = 0;
                this.wantSeq = 0;
                this.wantStamp = 0L;
                return;
            }
            final TreeTexture job = new TreeTexture(this.texName, true);
            final int w = width;
            final int h = height;
            final long mySeq = this.requestSeq.incrementAndGet();
            Thread worker = new Thread(() -> {
                try {
                    job.buildInto(w, h, 0, 0, 0L, this.zoom);
                    this.publish(job, mySeq);
                } catch (Throwable t) {
                    job.discard();
                    this.jobW = -1;
                } finally {
                    this.busy.set(false);   // 放行 —— 排队的那一次会在 takePending 里补上
                }
            }, "goety-revenant-terrain");
            worker.setDaemon(true);
            worker.start();
            return;
        }
        if (this.image != null && this.viewW == width && this.viewH == height
                && this.stage == stage && this.seqIndex == sequence
                && this.stamp == stamp) {
            return;
        }
        // 已经在为同一组参数生成了 —— 别重复开线程
        if (this.jobW == width && this.jobH == height && this.jobStage == stage
                && this.jobSeq == sequence && this.jobStamp == stamp) {
            return;
        }
        // ------------------------------------------------------------------
        //  **生成挪到后台线程**（和背景那边同一个思路）。
        //  这件事要几秒（上千万次颗粒操作），跑在主线程上就是"点开界面未响应"。
        //  它全是内存操作（填 NativeImage + 记槽位），不碰 GL，所以能在别的线程做。
        //  界面在这期间继续显示**上一张图**，做好了自动换 —— 不黑屏也不卡。
        // ------------------------------------------------------------------
        this.jobW = width;
        this.jobH = height;
        this.jobStage = stage;
        this.jobSeq = sequence;
        this.jobStamp = stamp;
        // 临时实例：在后台把图做出来，主线程再搬（两个实例注册的是同一个 id，
        // 但临时实例不注册 —— 注册只在 takePending 里做一次）
        if (!this.busy.compareAndSet(false, true)) {
            // 已经有线程在跑 —— 记下这一组参数，等它做完补一次
            this.wantW = width;
            this.wantH = height;
            this.wantStage = stage;
            this.wantSeq = sequence;
            this.wantStamp = stamp;
            return;
        }
        final TreeTexture job = new TreeTexture(this.texName, false);
        final int w = width;
        final int h = height;
        final long mySeq = this.requestSeq.incrementAndGet();
        Thread worker = new Thread(() -> {
            try {
                job.buildInto(w, h, stage, sequence, stamp, this.zoom);
                this.publish(job, mySeq);
            } catch (Throwable t) {
                job.discard();
                this.jobW = -1;
            } finally {
                this.busy.set(false);
            }
        }, "goety-revenant-tree");
        worker.setDaemon(true);
        worker.start();
    }

    /** 主线程每帧：后台做好一份就搬过来（这一句很快）。 */
    // ======================================================================
    //  **计时（诊断用）** —— 输出到日志，看 [回魂计时] 开头的行。
    //  优化的第一步是"知道时间花在哪"，不是猜。
    // ======================================================================
    private final java.util.List<String> timings = new java.util.ArrayList<>();
    private long markAt;

    /** 记一段（打印上一段到这一段之间的耗时）。 */
    private void mark(String what) {
        long now = System.nanoTime();
        if (this.markAt != 0L) {
            this.timings.add(what + "=" + ((now - this.markAt) / 1_000_000L) + "ms");
        }
        this.markAt = now;
    }

    /** **这一层现在什么状态** —— 界面角落那行诊断字用它。 */
    public String status(String name) {
        if (this.image == null) {
            return (this.jobW > 0 ? name + " 生成中…" : name + " 未开始");
        }
        if (this.jobW == this.viewW && this.jobH == this.viewH
                && this.stage == 0 && this.terrainOnly) {
            return name + " 就绪";
        }
        if (this.jobStage == this.stage && this.jobStamp == this.stamp
                && this.jobW == this.viewW && this.jobH == this.viewH) {
            return name + " 生成中…";
        }
        return name + " 就绪";
    }

    /**
     * **交付一个生成结果** —— 只有仍然是最新请求时才被采纳。
     *
     * <p>不是最新的（说明玩家又改了状态/尺寸，已经发了新请求）→ 把自己关掉丢弃。
     * 被顶掉的那一份也要关掉，否则原生内存会一直涨。</p>
     */
    private void publish(TreeTexture job, long seq) {
        if (this.requestSeq.get() != seq) {
            job.discard();          // 过期结果：关掉，别覆盖别人
            return;
        }
        TreeTexture old = this.pendingRef.getAndSet(job);
        if (old != null) {
            old.discard();          // 上一份还没被取走就被顶掉了：同样要关
        }
    }

    /**
     * **把这一层整个放掉** —— 图关掉、纹理注销、尺寸清掉。
     *
     * <p>和 discard() 的区别：那个是"丢掉一个还没用上的临时结果"，
     * 这个是"这一层现在不需要了，把它占的原生内存和显存都还回去"。
     * 清掉尺寸之后，下次真要用到它会自然重新生成 —— 状态是自洽的。</p>
     *
     * <p>用在那些只有一小段时间需要的层上：种子、裂开的种子。
     * 树和地层一直要用，不能这么放。</p>
     */
    public void release() {
        try {
            if (this.texture != null) {
                // 注销 + 关贴图。只 close 不注销的话，纹理管理器里会留一条死引用。
                Minecraft.getInstance().getTextureManager().release(this.tex);
                this.texture.close();
            }
        } catch (Throwable ignored) {
            // 已经没了就算了
        }
        this.texture = null;
        if (this.image != null) {
            this.image.close();
        }
        this.image = null;
        this.viewW = -1;
        this.viewH = -1;
        this.jobW = -1;
        this.jobH = -1;
    }

    /** **释放这一份持有的原生图像**（未注册到纹理管理器的那些）。 */
    private void discard() {
        if (this.image != null) {
            this.image.close();
            this.image = null;
        }
        if (this.texture != null) {
            this.texture.close();
            this.texture = null;
        }
    }

    private void takePending() {
        // **原子交换**：取走的同时清空，中间不会有写入被丢掉
        // （之前是 "读字段 → 再赋 null"，两步之间写进来的结果会消失）
        TreeTexture job = this.pendingRef.getAndSet(null);
        // 生成期间玩家又改了状态/尺寸的话，这里补一次（外层 ensure 的缓存判断
        // 会让"其实已经是对的了"这种情况直接返回，不会白做）
        if (this.wantW > 0) {
            int w = this.wantW;
            int h = this.wantH;
            int st = this.wantStage;
            int sq = this.wantSeq;
            long stamp = this.wantStamp;
            this.wantW = -1;
            this.ensure(w, h, st, sq, stamp, this.zoom);
        }
        if (job == null) {
            return;
        }
        // 搬生成结果（含槽位 / 叶子 / 三条路的节点）
        this.viewW = job.viewW;
        this.viewH = job.viewH;
        this.contentW = job.contentW;
        this.contentH = job.contentH;
        this.drawW = job.drawW;
        this.drawH = job.drawH;
        this.soilTop = job.soilTop;
        this.fleshTop = job.fleshTop;
        this.blendTop = job.blendTop;
        this.stage = job.stage;
        this.seqIndex = job.seqIndex;
        this.stamp = job.stamp;
        this.ss = job.ss;
        this.slots.clear();
        this.slots.addAll(job.slots);
        this.leaves.clear();
        this.leaves.addAll(job.leaves);
        for (int w2 = 0; w2 < 3; w2++) {
            for (int s2 = 0; s2 < STAGES_PER_WAY; s2++) {
                this.wayNodes[w2][s2][0] = job.wayNodes[w2][s2][0];
                this.wayNodes[w2][s2][1] = job.wayNodes[w2][s2][1];
            }
        }
        if (this.texture != null) {
            this.texture.close();
        }
        long upT = System.nanoTime();
        // **所有权转移**：图交接给 this 之后，job 就不能再持有它了。
        // 否则 job 万一被回收/丢弃时会把这张【已经在用的】图关掉 —— 界面直接黑掉。
        this.image = job.image;
        job.image = null;
        this.texture = new DynamicTexture(this.image);
        Minecraft.getInstance().getTextureManager().register(this.tex, this.texture);
        LOG.info("[REVENANT-T] 上传纹理 " + this.texName + " "
                + ((System.nanoTime() - upT) / 1_000_000L) + "ms  尺寸 "
                + this.image.getWidth() + "x" + this.image.getHeight()
                + "  内存 " + (this.image.getWidth() * (long) this.image.getHeight() * 4L / 1048576L) + "MB");
    }

    /** 后台线程里跑的那一半：从空白到一张成品图（不含纹理注册）。 */
    private void buildInto(int width, int height, int stage, int sequence, long stamp, float zoom) {
        this.viewW = width;
        this.viewH = height;
        // **内容图比视口大**：屏幕只是它的一个窗口，往下还能看到血肉层
        this.contentW = Math.max(1, (int) (width * TreeLayout.CONTENT_W));
        this.contentH = Math.max(1, (int) (height * TreeLayout.CONTENT_H));
        this.drawW = this.contentW;
        this.drawH = this.contentH;
        // 图大了就别超采样了（见 ss 的说明）
        this.pickSupersample();
        // 绘制坐标一律按 ss 倍存 —— build() 里所有计算都基于它
        this.width = this.contentW * this.ss;
        this.height = this.contentH * this.ss;
        this.stage = stage;
        this.seqIndex = sequence;
        this.stamp = stamp;
        this.zoom = zoom;
        this.buildPalette();
        if (width <= 0 || height <= 0) {
            return;
        }
        this.slots.clear();
        this.leaves.clear();
        this.upgradeLeafCounter = 0;
        this.rng = new Random(0x7EE0L + stage * 31L);

        NativeImage hi = new NativeImage(NativeImage.Format.RGBA,
                this.contentW * this.ss, this.contentH * this.ss, true);
        this.image = hi;
        // 拿到像素首地址 —— 之后所有读写都走内存（见 pixels 的说明）
        this.resolvePixels(hi);
        this.build();
        this.pixels = 0L;
        // 降采样：ss x ss 个像素取平均 —— 抗锯齿就在这一步发生
        // **只有降采样时才关原图。**
        // ss == 1 的时候 this.image 就是 hi 本身 —— 这时去 close(hi)
        // 等于把成品图释放掉了，后面建纹理就是
        // "IllegalStateException: Image is not allocated"。
        // （这个 bug 只在图大到超采样被关掉之后才会出现 —— 之前一直是 ss = 2，
        //   image 是降采样出来的新图，所以从没触发过。）
        if (this.ss > 1) {
            this.image = downsample(hi, this.contentW, this.contentH, this.ss);
            hi.close();
        } else {
            this.image = hi;
        }
        // 纹理注册留着主线程做（takePending）—— 这一步必须碰 GL
    }

    /** SS 倍图 → 原尺寸（每 SS×SS 个像素求平均）。 */
    private static NativeImage downsample(NativeImage src, int w, int h, int ss) {
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        int n = ss * ss;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                int a = 0;
                for (int dy = 0; dy < ss; dy++) {
                    for (int dx = 0; dx < ss; dx++) {
                        int p = src.getPixelRGBA(x * ss + dx, y * ss + dy);
                        r += p & 0xFF;
                        g += (p >> 8) & 0xFF;
                        b += (p >> 16) & 0xFF;
                        a += (p >>> 24) & 0xFF;
                    }
                }
                out.setPixelRGBA(x, y, ((a / n) << 24) | ((b / n) << 16) | ((g / n) << 8) | (r / n));
            }
        }
        return out;
    }

    /* ------------------------------------------------------------------ */
    /*  参数：四个阶段之间连续插值                                           */
    /* ------------------------------------------------------------------ */


    private float ramp(float[] keys) {
        float g = this.grow();
        for (int i = 0; i < KEY.length - 1; i++) {
            if (g <= KEY[i + 1] || i == KEY.length - 2) {
                float span = KEY[i + 1] - KEY[i];
                float t = span <= 0.0F ? 0.0F : (g - KEY[i]) / span;
                t = Math.max(0.0F, Math.min(1.0F, t));
                // 缓出：长得慢一点，更有"长"的感觉
                t = 1.0F - (1.0F - t) * (1.0F - t);
                return keys[i] + (keys[i + 1] - keys[i]) * t;
            }
        }
        return keys[keys.length - 1];
    }


    /**
     * **按序列给树染色** —— 七条序列的树不该长一个样。
     *
     * <p>做法不是整棵树刷成序列色（那就成色块了），而是把序列色
     * <p>序列只影响"长到哪一步"，不影响颜色。</p>
     */
    private void buildPalette() {
        // **树不随序列变样。**
        // 序列决定它**长到哪一步**（种子 / 幼苗 / 小树 / 成树 / 巨树），
        // 不决定它是什么颜色 —— 按序列染色是我自己加的戏，撤了。
        // bark 的默认值在 TreeCanvas（BARK），这里不需要再赋一遍
        this.leaf = LEAF;
    }

    /* ------------------------------------------------------------------ */
    /*  绘制                                                                */
    /* ------------------------------------------------------------------ */

    private void build() {
        float cx = this.width / 2.0F;
        // ==================================================================
        //  内容图的垂直分区（从上到下）—— **每层都拉长**，过渡层**独立成段**。
        //
        //    0.00 ~ 0.30   星空：往上看才是它（星点密集、银河、星云）
        //    0.30 ~ 0.50   夜空 + 树：树的背景，只有少量星星
        //    0.50 ~ 0.66   土壤层（16%，原来 12%）
        //    0.66 ~ 0.74   过渡层（8%，独立的一段，只有颜色渐变）
        //    0.74 ~ 1.00   血肉土壤（26%）
        // ==================================================================
        this.soilTop = this.height * TreeLayout.SOIL_LINE;
        float soilH = Math.max(40.0F, this.height * (TreeLayout.SOIL_BOTTOM - TreeLayout.SOIL_LINE));
        this.blendTop = this.height * TreeLayout.SOIL_BOTTOM;
        this.fleshTop = this.height * TreeLayout.FLESH_LINE;

        float groundY = this.soilTop + soilH * 0.16F;          // 主干从表层土里钻出来
        // **往上能长多高** —— 树冠要顶进星空区（内容图上部 30% 是星空）。
        // 树的背景是"少量星星的夜空"，树梢穿进上面那片密集的星空里。
        float avail = groundY - this.height * 0.03F;
        float trunkH = avail * this.ramp(TRUNK_H);
        float trunkW = Math.max(3.0F, this.height * 0.055F * this.ramp(TRUNK_W)) * this.zoom;

        this.rng = new Random(0x7EE0L + this.stage * 31L);
        // 每一棵树有自己的偏向（同一个存档里会长成同一棵，换树会不一样）
        this.canopyBias = (this.rng.nextFloat() - 0.5F) * 1.6F;
        // ------------------------------------------------------------------
        //  **地层**（土壤 / 过渡 / 血肉土壤）—— 静态。
        //  这一层只跟画面尺寸有关，和树的状态、嵌入槽、血肉线全都无关，
        //  所以它一辈子只生成一次（见 ensure 里 terrainOnly 的缓存判断）。
        // ------------------------------------------------------------------
        this.mark("分区");          // 到这里为止都是纯计算
        // 地层（土壤 / 过渡 / 血肉土壤）交给 TerrainPainter ——
        // 它只认画布原语和分区坐标，不认识纹理/缓存/视口
        new com.habaiwa.goetyrevenant.client.tree.TerrainPainter(
                this, this.rng, this.soilTop, this.blendTop, this.fleshTop)
                .paint(soilH, trunkW, trunkH);
        this.mark("地层");
        // 只做地层的话，到这里就够了 —— 下面全是"会变的那一层"
        if (this.terrainOnly) {
            LOG.info("[REVENANT-T] terrain: " + String.join(" ", this.timings));
            return;
        }
        // 上面的 0.00~0.42 是留给星空的 —— 树的轮廓会映在这片星上
        // **未觉醒 = 一粒种子**：没有干、没有枝、没有叶，
        // 槽位和强化叶都一个不留（它们要等树长出来才存在）。
        if (this.openSeed) {
            // 裂开的种子（生长动画中间那一段用它）
            new SeedPainter(this, this.rng).seedOpen(cx, groundY, trunkW);
            return;
        }
        if (this.stage < 0) {
            new SeedPainter(this, this.rng).seed(cx, groundY, trunkW);
            return;
        }
        // 根系交给 RootPainter（主根 / 侧根 / 细根 / 三条路），
        // 节点坐标它会写进画布的 wayNodes
        new com.habaiwa.goetyrevenant.client.tree.RootPainter(this, this.soilTop, this.fleshTop)
                .paint(cx, groundY, trunkW);
        this.mark("根");
        // 树交给 TreePainter（主干 / 分枝 / 树冠 / 叶簇 / 树影），
        // 嵌入位与强化叶它会写进画布的 slots / leaves
        new com.habaiwa.goetyrevenant.client.tree.TreePainter(this, this.soilTop)
                .paint(cx, groundY, trunkH, trunkW);
        this.mark("树");
        LOG.info("[REVENANT-T] tree: " + String.join(" ", this.timings)
                + "  |  " + this.inkReport());
    }

    /* ---- 纹理生命周期 -------------------------------------------------- */

    /** splitRoot 里用的深度跨度（避免每次重算）。 */
    private float depthSpanLocal = 1.0F;

    /* ---- ③ 主干 + 分枝 -------------------------------------------------- */


    /** 分叉点的高度比（树高的多少）。 */
    private static final float FORK_T = 0.42F;
    /** 每级长度衰减。 */
    private static final float LEN_STEP = 0.78F;
    /** 每级粗细衰减。 */
    private static final float WID_STEP = 0.66F;
    /** 分枝角度范围（弧度）。 */
    private static final float ANG_MIN = 0.48F;
    private static final float ANG_MAX = 0.92F;







    /** 一簇叶：几片叶挤在一起，不是单个圆点。 */
    /** 树冠整体的偏向（-1 ~ 1）：真实的大树总有一侧更茂密。 */
    private float canopyBias;





    /* ------------------------------------------------------------------ */
    /*  像素工具                                                            */
    /* ------------------------------------------------------------------ */



    // ======================================================================
    //  **像素读写走内存，不走 JNI**
    //
    //  getPixelRGBA / setPixelRGBA 每次调用都要过一次原生边界 —— 几十纳秒
    //  看着不多，但内容图放大之后颗粒是【上千万】级的
    //  （数量必须跟面积走，少了屏幕上的密度就降了 = 纹理精度就降了），
    //  于是总开销到了几十秒，界面直接未响应。
    //
    //  这里反射拿到像素首地址，之后用 MemoryUtil 直接读写。
    //  **视觉上完全一样**：颗粒的数量、位置、大小、颜色系数一个都没动。
    // ======================================================================
    /** 当前图像素首地址（0 = 没拿到，退回原路径）。 */

    /* ------------------------------------------------------------------ */
    /*  对外                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * **按视口画** —— 从内容图里取一块贴到屏幕上。
     *
     * @param viewX 视口在内容图里的左上角 X（最终尺寸的像素）
     * @param viewY 同上，Y
     */
    public void drawView(GuiGraphics graphics, float alpha, int viewX, int viewY) {
        drawView(graphics, 1.0F, alpha, viewX, viewY);
    }

    /**
     * 同上，但可以只画出下面一部分（{@code visible &lt; 1} = 生长中）。
     *
     * <p>关键：生长动画必须**也走视口**。之前它用的是旧的"整张图当屏幕"画法 ——
     * 内容图比屏幕大之后，那样画只会显示出图的左上角一小块，屏幕上就是空的。</p>
     */
    public void drawView(GuiGraphics graphics, float visible, float alpha, int viewX, int viewY) {
        // 主线程每帧取一次后台成果（没做好就是空操作）
        this.takePending();
        if (this.texture == null || this.viewW <= 0 || this.contentW <= 0) {
            return;
        }
        float a = Math.max(0.0F, Math.min(1.0F, alpha));
        if (a <= 0.01F) {
            return;
        }
        // **视口覆盖多大一块内容** —— 这一步才是缩放真正该做的事：
        // zoom 大 = 覆盖的区域小 = 看着近（内容是同一份，不会被重画）
        float z = Math.max(0.30F, Math.min(3.0F, this.zoom));
        int srcW = Math.max(32, (int) (this.viewW / z));
        int srcH = Math.max(32, (int) (this.viewH / z));
        // **以视口中心为基准缩** —— 之前是拿 viewX/viewY 当源左上角，
        // 于是放大时取的是视口【左上角】那一小块，画面就往右上角跑了。
        int centerX = viewX + this.viewW / 2;
        int centerY = viewY + this.viewH / 2;
        int vx = Math.max(0, Math.min(this.contentW - srcW, centerX - srcW / 2));
        int vy = Math.max(0, Math.min(this.contentH - srcH, centerY - srcH / 2));
        float v = Math.max(0.0F, Math.min(1.0F, visible));
        int showH = (int) (this.viewH * v);
        if (showH <= 0) {
            return;
        }
        // 从下往上长：只显示靠下的那一段（生长动画用）
        int yOff = this.viewH - showH;
        int srcShowH = Math.max(1, (int) (srcH * v));
        int srcY = vy + (srcH - srcShowH);
        if (a < 0.99F) {
            graphics.setColor(1.0F, 1.0F, 1.0F, a);
        }
        // blit(纹理, 目标x, 目标y, 目标宽, 目标高, 源左上x, 源左上y, 源宽, 源高, 纹理总宽, 总高)
        graphics.blit(this.tex, 0, yOff, this.viewW, showH,
                (float) vx, (float) srcY, srcW, srcShowH, this.contentW, this.contentH);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 内容图尺寸（界面要拿它算平移范围）。 */
    public int contentWidth() {
        return this.contentW;
    }

    public int contentHeight() {
        return this.contentH;
    }

    public int viewWidth() {
        return this.viewW;
    }

    public int viewHeight() {
        return this.viewH;
    }

    /** 有几条路（血肉线的三条）。 */
    public int wayCount() {
        return this.wayNodes.length;
    }

    /** 每条路上有几个节点（= 血肉线的阶数）。 */
    public int wayStageCount() {
        return STAGES_PER_WAY;
    }

    /**
     * 第 way 条路上第 stage 阶的节点坐标（最终尺寸；null = 那条路还没长出来）。
     *
     * @param way   0 / 1 / 2
     * @param stage 0 起
     */
    public int[] wayNodeAt(int way, int stage) {
        if (way < 0 || way >= this.wayNodes.length
                || stage < 0 || stage >= STAGES_PER_WAY) {
            return null;
        }
        int[] n = this.wayNodes[way][stage];
        return n[0] == 0 && n[1] == 0 ? null : n;
    }

    /** 一次 blit。visible &lt; 1 时从下往上裁剪（生长动画）。 */
    public void draw(GuiGraphics graphics, float visible) {
        this.draw(graphics, visible, 1.0F);
    }

    /**
     * 同上，但可以带透明度 —— 生长动画要把"种子"和"树苗"两张叠着画
     * （一张淡出、一张长出来）。
     */
    public void draw(GuiGraphics graphics, float visible, float alpha) {
        if (this.texture == null) {
            return;
        }
        visible = Math.max(0.0F, Math.min(1.0F, visible));
        int w = this.drawW;
        int full = this.drawH;
        int h = (int) (full * visible);
        if (h <= 0 || w <= 0) {
            return;
        }
        // 从下往上长：裁掉上面那截
        int srcY = full - h;
        float a = Math.max(0.0F, Math.min(1.0F, alpha));
        if (a <= 0.01F) {
            return;
        }
        if (a < 0.99F) {
            graphics.setColor(1.0F, 1.0F, 1.0F, a);
        }
        graphics.blit(this.tex, 0, srcY, 0.0F, srcY, w, h, w, full);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void setChoices(int[] choices) {
        this.choices = choices;
    }

    /** 六个嵌入位各嵌了什么颜色的聚晶（0 = 空）。 */
    public void setSlotColors(int[] colors) {
        this.slotColors = colors == null ? new int[6] : colors;
    }

    public int slotCount() {
        return this.slots.size();
    }

    public int[] slotAt(int i) {
        return i >= 0 && i < this.slots.size() ? this.slots.get(i) : null;
    }

    public int leafCount() {
        return this.leaves.size();
    }

    public int[] leafAt(int i) {
        return i >= 0 && i < this.leaves.size() ? this.leaves.get(i) : null;
    }

    /** 鼠标附近的那片叶（没有就 -1）。 */
    public int leafNear(double mx, double my) {
        for (int i = 0; i < this.leaves.size(); i++) {
            int[] l = this.leaves.get(i);
            double dx = mx - l[0];
            double dy = my - l[1];
            if (dx * dx + dy * dy <= (double) l[2] * l[2]) {
                return i;
            }
        }
        return -1;
    }

    /** 重新生成（下一次 ensure 时生效）。 */
    public void invalidate() {
        this.stage = -2;
    }
}
