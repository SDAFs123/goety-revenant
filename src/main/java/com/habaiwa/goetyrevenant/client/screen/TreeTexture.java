package com.habaiwa.goetyrevenant.client.screen;

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
public final class TreeTexture {
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

    /** 树皮色阶（暗 → 亮）。 */
    private static final int[] BARK = {
            0xFF120E10, 0xFF1E1614, 0xFF2B1F1A, 0xFF392821, 0xFF473226,
            0xFF553D2E, 0xFF634836, 0xFF71523E, 0xFF7F5C46};
    /** 叶色阶（暗 → 亮）。 */
    private static final int[] LEAF = {
            0xFF0C2A22, 0xFF10402F, 0xFF16543B, 0xFF1D6A49, 0xFF278059,
            0xFF34966B, 0xFF45AC80, 0xFF5CC197};
    /** 土色阶（深 → 浅）。 */
    private static final int[] DIRT = {
            0xFF160E0A, 0xFF241611, 0xFF31201A, 0xFF3E2A21, 0xFF4C352A, 0xFF5A4033};
    /** 苔（灵魂色）。 */
    private static final int MOSS = 0xFF2E8C78;
    private static final int MOSS_LIT = 0xFF52E8DC;
    /** 木纹高光。 */
    /**
     * **受光面的颜色** —— 星光不是阳光，所以不是暖黄，是冷白偏青。
     * 主光源在左上（就是星空里那颗最亮的星的方向）。
     */
    private static final int BARK_LIGHT = 0xFFA8C4D8;

    /** 星光本体色（高光、叶缘、土面都从它派生）。 */
    private static final int STARLIGHT = 0xFFCFE8FF;

    // ==== 幼苗（子叶期）用色：嫩绿偏黄，和成树的树皮/深叶完全是两回事 ====
    /** 胚轴（从土里钻出来那截）：浅黄绿。 */
    private static final int SPROUT_STEM = 0xFF8A9A56;
    private static final int SPROUT_STEM_LIT = 0xFFBCCB7E;
    /** 子叶：嫩绿。 */
    private static final int SPROUT_LEAF = 0xFF6E9E4C;
    private static final int SPROUT_LEAF_LIT = 0xFF9ECB72;
    private static final int SPROUT_LEAF_DARK = 0xFF3C6530;

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
    private static final int MEAT_FRESH = 0xFF4A1418;
    /** 深一点的肌肉。 */
    private static final int MEAT_DEEP = 0xFF361014;
    /** 压实的组织。 */
    private static final int MEAT_DARK = 0xFF240A0E;
    /** 缺氧的紫（淤血/深层）。 */
    private static final int MEAT_PURPLE = 0xFF2E141C;
    /** 脂肪：暗黄褐（不是亮黄白 —— 地下的脂肪也是暗的）。 */
    private static final int FAT = 0xFF6A5836;
    /** 筋膜：暗灰（不是亮灰白）。 */
    private static final int FASCIA = 0xFF544E4A;
    /** **湿润高光**：压到暗红灰（地下的湿，只是"不干"，不是"反光"）。 */
    private static final int WET = 0xFF7A4E52;
    /** 土壤颗粒的两种土色（过渡层要用它和肉混）。 */
    private static final int SOIL_GRAIN_A = 0xFF4A3626;
    private static final int SOIL_GRAIN_B = 0xFF2A1E14;

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

    private static final float LIGHT_X = -0.62F;
    private static final float LIGHT_Y = -0.78F;

    /** 四个阶段的参数（之间连续插值）。 */
    private static final float[] KEY = {0.0F, 1.0F / 3.0F, 2.0F / 3.0F, 1.0F};
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
    private static final int SS_MAX = 2;

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
    private int ss = SS_MAX;

    /** 算出这次该用几倍超采样。 */
    private void pickSupersample() {
        long px = (long) this.contentW * this.contentH;
        this.ss = px > 1_100_000L ? 1 : SS_MAX;
        // （图放大之后几乎必然走 1 倍 —— 抗锯齿让位给"画得开"）
    }
    private static final int[] KIDS = {0, 3, 3, 2, 2};

    private NativeImage image;
    private DynamicTexture texture;
    /** 绘制尺寸（含超采样）—— build() 全程用它。 */
    private int width = -1;
    private int height = -1;
    /** 最终贴图尺寸（超采样之前）。 */
    private int drawW = -1;
    private int drawH = -1;
    private int stage = -2;
    /** 觉醒的序列序号（-1 = 还没觉醒，用中性色）。 */
    private int seqIndex = -1;
    /** 实际使用的树皮 / 叶子色阶（按序列染过色的）。 */
    private int[] bark = BARK;
    private int[] leaf = LEAF;
    private long stamp = Long.MIN_VALUE;
    private float zoom = 1.0F;
    private int[] choices;
    /**
     * **六个嵌入位各嵌了什么颜色的聚晶**（0 = 空）。
     *
     * <p>颜色由聚晶的派系（元素）决定 —— 于是不用任何文字，
     * 光看树干上那几个年轮是什么颜色，就知道里面嵌了什么。</p>
     */
    private int[] slotColors = new int[6];

    /** 嵌入位（主干上的年轮节）：{x, y}。 */
    private final List<int[]> slots = new ArrayList<>(8);
    /** 强化叶：{x, y, r} —— 前几个是嵌入位旁的年轮叶，之后是树冠里的叶片。 */
    private final List<int[]> leaves = new ArrayList<>(MAX_UPGRADE_LEAF + 8);

    private Random rng;
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
    private int inkX0 = Integer.MAX_VALUE;
    private int inkY0 = Integer.MAX_VALUE;
    private int inkX1 = Integer.MIN_VALUE;
    private int inkY1 = Integer.MIN_VALUE;

    /** 记一下"这个像素被写过"。 */
    private void ink(int x, int y) {
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
    private static final int STAGES_PER_WAY =
            com.habaiwa.goetyrevenant.common.flesh.FleshLine.STAGES;
    private final int[][][] wayNodes = new int[3][STAGES_PER_WAY][2];
    /** 每条路有没有被走到（用来决定画不画它的细节）。 */
    private final boolean[] wayAlive = new boolean[3];

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

    /** 生长值 0~1（0 = 幼苗，1 = 巨树）。 */
    private float grow() {
        return Math.max(0.0F, Math.min(1.0F, this.stage / 7.0F));
    }

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

    private int rampCount(int[] keys) {
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
        this.bark = BARK;
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
        this.soil(soilH);
        this.mark("土壤");
        // **星光照在土上**（光源在左上那颗亮星的方向）
        this.starLightOnSoil(cx, trunkW, trunkH * 0.42F);
        // 过渡层：独立的一段（土壤底 → 血肉顶），只有颜色渐变
        this.blendZone(this.blendTop, this.fleshTop);
        this.mark("过渡");
        // 血肉土壤：从过渡层底一直到内容图底
        this.fleshLayer(this.fleshTop);
        this.mark("血肉");
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
            this.seedOpen(cx, groundY, trunkW);
            return;
        }
        if (this.stage < 0) {
            this.seed(cx, groundY, trunkW);
            return;
        }
        this.roots(cx, groundY, trunkW);
        this.mark("根");
        this.tree(cx, groundY, trunkH, trunkW);
        this.mark("树");
        this.soulMotes();
        this.mark("叶");
        LOG.info("[REVENANT-T] tree: " + String.join(" ", this.timings)
                + "  |  " + this.inkReport());
    }

    /* ---- ① 土壤 -------------------------------------------------------- */

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
            for (int x = 0; x < this.width; x++) {
                this.blendDot(x, y, STARLIGHT, k * 0.09F);
            }
        }
        // ② 树脚那一圈堆起来的光
        int footW = (int) Math.max(10.0F, trunkW * 4.0F);
        for (int x = (int) cx - footW; x < cx + footW; x++) {
            float d = Math.abs(x - cx) / (float) footW;
            float k = (1.0F - d) * 0.20F;
            for (int y = top; y < top + 20; y++) {
                this.blendDot(x, y, 0xFFE8F4FF,
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
                this.blendDot(x, y, 0xFF05080C, (1.0F - d) * 0.40F);
            }
        }
    }

    private void soil(float soilH) {
        int top = (int) this.soilTop;
        int h = this.height;

        // 深土底
        for (int y = top; y < h; y++) {
            float t = (y - top) / Math.max(1.0F, soilH);
            int c = this.mix(DIRT[2], DIRT[0], Math.min(1.0F, t * 1.25F));
            int packed = abgr(c);
            for (int x = 0; x < this.width; x++) {
                this.putAbgr(x, y, packed);
            }
        }
        // 层理：沉积的横带。6 条太稀（看着像"几条脏线"），加到 14 条，
        // 并且每条有自己的厚度和断续 —— 这样才像一层一层压出来的。
        int strata = 14;
        for (int i = 0; i < strata; i++) {
            float baseY = top + soilH * (0.08F + 0.86F * i / (float) strata
                    + (this.rng.nextFloat() - 0.5F) * 0.04F);
            int th = 1 + this.rng.nextInt(4);
            int c = this.rng.nextBoolean() ? DIRT[1] : DIRT[3];
            float alpha = 0.30F + this.rng.nextFloat() * 0.30F;
            for (int x = 0; x < this.width; x++) {
                // 断续：一条层理不是从头连到尾的
                if (this.rng.nextFloat() < 0.16F) {
                    continue;
                }
                float wave = (float) Math.sin(x * 0.03F + i * 1.7F) * 1.6F;
                for (int dy = 0; dy < th; dy++) {
                    this.blendDot(x, (int) (baseY + wave) + dy, c, alpha);
                }
            }
        }
        // 石子
        int stones = 40 + this.width / 12;
        for (int i = 0; i < stones; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = top + 4 + this.rng.nextInt(Math.max(1, (int) soilH - 6));
            int r = 1 + this.rng.nextInt(3);
            int c = this.rng.nextFloat() < 0.4F ? DIRT[4] : DIRT[1];
            this.disc(x, y, r, c, 0.85F);
            this.disc(x - r / 3, y - r / 3, Math.max(0.6F, r * 0.4F), DIRT[5], 0.5F);
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
        int fineGrains = (int) (this.width * soilH * 2.6F);
        for (int i = 0; i < fineGrains; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = top + this.rng.nextInt(Math.max(1, (int) soilH));
            int c = this.mix(DIRT[4], DIRT[0], this.rng.nextFloat());
            this.blendDot(x, y, c, 0.20F + this.rng.nextFloat() * 0.20F);
        }
        // ② 中粒：有受光面（左上亮、右下暗）
        int midGrains = (int) (this.width * soilH * 0.75F);
        for (int i = 0; i < midGrains; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = top + this.rng.nextInt(Math.max(1, (int) soilH));
            float r = 1.0F + this.rng.nextFloat() * 1.2F;
            this.disc(x, y, r, DIRT[1], 0.45F);
            this.blendDot(x - 1, y - 1, DIRT[5], 0.30F);
            this.blendDot(x + 1, y + 1, DIRT[0], 0.26F);
        }
        // ③ 粗砾：更大，有投影也有高光
        int coarseGrains = (int) (this.width * soilH * 0.14F);
        for (int i = 0; i < coarseGrains; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = top + this.rng.nextInt(Math.max(1, (int) soilH));
            float r = 1.8F + this.rng.nextFloat() * 2.4F;
            this.disc((int) (x + 1.6F), (int) (y + 1.6F), r, 0xFF100A06, 0.38F);   // 投影
            this.disc(x, y, r, this.rng.nextFloat() < 0.4F ? DIRT[4] : DIRT[1], 0.82F);
            this.disc((int) (x - r * 0.35F), (int) (y - r * 0.35F),
                    Math.max(0.7F, r * 0.5F), DIRT[5], 0.55F);                     // 受光面
        }
        // 表层起伏：用一串小丘把土面画出来（土色覆盖上面）
        int x2 = 0;
        while (x2 < this.width) {
            int hump = 8 + this.rng.nextInt(26);
            int up = this.rng.nextInt(5);
            for (int x = x2; x < Math.min(this.width, x2 + hump); x++) {
                float k = (x - x2) / (float) hump;
                int lift = (int) (Math.sin(k * Math.PI) * up);
                for (int y = top - lift; y < top - lift + 3; y++) {
                    this.blendDot(x, y, DIRT[3], 0.9F);
                }
                for (int y = top - lift + 3; y < Math.min(this.height, top - lift + 7); y++) {
                    this.blendDot(x, y, DIRT[4], 0.55F);
                }
            }
            x2 += hump;
        }
        // **三层界**：表土 / 心土 / 母质 —— 两条起伏的界线把它们分开
        for (int band = 1; band <= 2; band++) {
            float baseY = top + soilH * (band == 1 ? 0.30F : 0.68F);
            for (int x = 0; x < this.width; x++) {
                float wave = (float) Math.sin(x * 0.11F + band * 2.3F) * 2.4F
                        + (this.rng.nextFloat() - 0.5F) * 1.6F;
                int yy = (int) (baseY + wave);
                this.blendDot(x, yy, band == 1 ? DIRT[4] : DIRT[1], 0.34F);
                this.blendDot(x, yy + 1, band == 1 ? DIRT[3] : DIRT[0], 0.22F);
            }
        }
        // **有机质**：表土层里的小团块（腐殖那点黑褐色）
        int organics = this.width / 2;
        for (int i = 0; i < organics; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = top + 2 + this.rng.nextInt(Math.max(1, (int) (soilH * 0.30F)));
            this.disc(x, y, 0.8F + this.rng.nextFloat() * 1.7F, 0xFF1A0E06, 0.4F);
        }

        // 苔：长在表层上的灵魂色短草
        for (int i = 0; i < this.width / 3; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            float hgt = 1.0F + this.rng.nextFloat() * 3.5F;
            int base = top - (int) (this.rng.nextFloat() * 4.0F);
            boolean lit = this.rng.nextFloat() < 0.22F;
            int c = lit ? MOSS_LIT : MOSS;
            for (int k = 0; k < hgt; k++) {
                this.blendDot(x, base - k, c, lit ? 0.85F : 0.6F);
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
    private void sprout(float cx, float groundY, float trunkH) {
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
            this.disc((int) (x + ww * 0.35F), (int) y, ww, SPROUT_STEM, 0.95F);
            this.blendDot((int) (x - ww * 0.45F), (int) y, SPROUT_STEM_LIT, 0.45F);
            this.blendDot((int) (x + ww * 0.75F), (int) y, SPROUT_LEAF_DARK, 0.30F);
        }
        float topX = cx + (float) Math.sin(1.6D) * bend;
        float topY = groundY - h;

        // ---- 顶芽：子叶之间那个小尖 ----
        for (int i = 0; i < 4; i++) {
            this.blendDot((int) topX, (int) (topY - i), SPROUT_LEAF_LIT, 0.55F - i * 0.10F);
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
                    this.blendDot(x, y, c, 0.88F);
                }
            }
            // 中脉：一条从叶基到叶尖的深色线
            for (int i = 2; i < (int) leafLen - 1; i++) {
                this.blendDot(lx0 + (int) (side * i), ly0 + 1, SPROUT_LEAF_DARK, 0.40F);
            }
            // 叶尖一枚小小的亮
            this.blendDot(lx0 + (int) (side * (leafLen - 1.0F)), ly0 + 1,
                    SPROUT_LEAF_LIT, 0.6F);
        }

        // ---- 土面那一圈：胚轴刚顶出来的痕迹 ----
        for (int i = 0; i < 10; i++) {
            int x = (int) (cx + (this.rng.nextFloat() - 0.5F) * (w * 6.0F));
            this.blendDot(x, (int) groundY, 0xFF3A2A1C, 0.35F);
        }
        // 子叶还小，这一阶没有叶簇、也没有可点的槽位
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
    private void seed(float cx, float groundY, float trunkW) {
        // 小得多：之前 trunkW × 2.0 在屏幕上约 119 像素，太大 ——
        // 种子是"一粒"，不该占掉半个画面。现在约 45 像素。
        float r = Math.max(5.0F, trunkW * 0.75F);
        float ry = r * 1.42F;                     // 卵形：略长
        float sy = groundY - ry * 0.60F;          // 身体中心（露出大半）
        // 土面的压痕
        this.disc((int) cx, (int) (groundY + 1.0F), r * 1.5F, 0xFF0A0606, 0.42F);
        this.disc((int) cx, (int) (groundY + 2.0F), r * 1.1F, 0xFF120A08, 0.30F);
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
                int c = this.mix(0xFF2A1608, 0xFF7A4A22, Math.max(0.0F, bulge));
                c = this.mix(c, 0xFF8E5228, Math.max(0.0F, bulge - 0.45F) * 0.8F);
                // 受光（左上）
                float lit = -(nx * 0.66F + ny * 0.75F);
                if (lit > 0.30F) {
                    c = this.mix(c, 0xFFB8823E, (lit - 0.30F) * 0.60F);
                } else if (lit < -0.35F) {
                    c = this.mix(c, 0xFF120804, (-lit - 0.35F) * 0.65F);
                }
                this.blendDot((int) (cx + x), (int) (sy + y), c, 0.97F);
            }
        }
        // 身体上的高光（光滑物体的标志）
        this.disc((int) (cx - r * 0.32F), (int) (sy - ry * 0.18F), r * 0.34F,
                0xFFE0B470, 0.28F);
        this.disc((int) (cx - r * 0.34F), (int) (sy - ry * 0.22F), r * 0.17F,
                0xFFFFE0A8, 0.42F);
        // 底端的果脐（那个小尖）
        for (int i = 0; i < 4; i++) {
            this.blendDot((int) cx, (int) (sy + ry * (0.98F + i * 0.06F)),
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
                int c = this.mix(0xFF241A10, 0xFF54402A, Math.max(0.0F, bulge) * 0.8F);
                float lit = -(nx * 0.66F + ((y - sy) / ry) * 0.75F);
                if (lit > 0.30F) {
                    c = this.mix(c, 0xFF7A6244, (lit - 0.30F) * 0.55F);
                }
                this.blendDot((int) (cx + x), (int) y, c, 0.97F);
            }
        }
        // **鳞片状的纹**（壳斗的招牌）—— 横向几道 + 纵向几道，交叉成小块
        for (int k = 0; k < 5; k++) {
            float yy = capTop + (capBottom - capTop) * (k + 0.5F) / 5.0F;
            float t2 = (yy - capTop) / Math.max(1.0F, capBottom - capTop);
            float half = r * (0.55F + t2 * 0.55F);
            for (int x = (int) -half; x <= (int) half; x++) {
                this.blendDot((int) (cx + x), (int) yy, 0xFF160E06, 0.30F);
                this.blendDot((int) (cx + x), (int) yy + 1, 0xFF6A5438, 0.16F);
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
                this.blendDot((int) (cx + x), (int) y, 0xFF160E06, 0.22F);
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
            this.blendDot((int) (cx + x), yy, 0xFF0E0804, 0.55F);
            this.blendDot((int) (cx + x), yy + 1, 0xFF3A2A18, 0.30F);
        }
        // 帽顶的短柄
        for (int y = 0; y < 4; y++) {
            this.blendDot((int) cx, (int) (capTop - y), 0xFF3E2E1C, 0.7F - y * 0.14F);
        }
        // 一点魂光（它是有灵性的种子）
        this.disc((int) cx, (int) (sy - ry * 0.05F), r * 0.70F, 0xFF52E8DC, 0.11F);
        this.disc((int) (cx - r * 0.10F), (int) (sy + ry * 0.15F), r * 0.32F,
                0xFFB8FFF4, 0.16F);
        // 底部埋进土里
        for (int x = (int) (-r * 1.1F); x <= r * 1.1F; x++) {
            float t = Math.abs(x) / (r * 1.1F);
            int depth = (int) (2.0F + (1.0F - t) * 3.0F);
            for (int dy = 0; dy < depth; dy++) {
                this.blendDot((int) (cx + x), (int) (groundY + dy), DIRT[4], 0.55F - dy * 0.13F);
            }
        }
    }
    /**
     * **裂开的种子** —— 种皮从种脐裂开，两半各向外让开一点。
     *
     * <p>生长动画要靠这张图来完成"破开"那一下：完整种子淡出、这张淡入，
     * 中间露出一道缝，然后幼苗从那道缝里顶出来。</p>
     */
    private void seedOpen(float cx, float groundY, float trunkW) {
        float r = Math.max(5.0F, trunkW * 0.75F);
        float ry = r * 1.42F;
        float sy = groundY - ry * 0.60F;
        float split = r * 0.22F;                  // 两半让开的距离
        // 土面的压痕（和完整种子一致）
        this.disc((int) cx, (int) (groundY + 1.0F), r * 1.5F, 0xFF0A0606, 0.40F);
        // --- 缝里的暗（先铺，两半压在上面）---
        for (int y = (int) (-ry * 1.1F); y <= (int) (ry * 1.0F); y++) {
            float t = (y + ry * 1.1F) / (ry * 2.1F);
            float w = r * (0.10F + 0.10F * (float) Math.sin(t * Math.PI));
            for (int x = (int) -w; x <= w; x++) {
                this.blendDot((int) (cx + x), (int) (sy + y), 0xFF0A0406, 0.85F);
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
                    int c = this.mix(0xFF251608, 0xFF7A5230, bulge);
                    // 裂口那一圈是新鲜的（偏亮偏红）
                    float nearSplit = 1.0F - Math.min(1.0F, Math.abs(x) / (r * 0.55F));
                    c = this.mix(c, 0xFF9A5030, nearSplit * 0.55F);
                    float lit = -(nx * 0.68F + ny * 0.74F);
                    if (lit > 0.25F) {
                        c = this.mix(c, 0xFFC09A5E, (lit - 0.25F) * 0.50F);
                    }
                    this.blendDot((int) (cx + x + side * split), (int) (sy + y), c, 0.97F);
                }
            }
            // 另一半的高光跟着挪
            if (side < 0) {
                this.disc((int) (cx - r * 0.34F + side * split), (int) (sy - ry * 0.36F),
                        r * 0.26F, 0xFFE8D2A8, 0.30F);
            }
        }
        // --- 缝里透出的魂光（芽还没出来，先亮起来）---
        for (int y = (int) (-ry * 0.9F); y <= (int) (ry * 0.8F); y++) {
            float t = (y + ry * 0.9F) / (ry * 1.7F);
            if (t < 0.35F) {
                continue;
            }
            this.blendDot((int) cx, (int) y + (int) sy, 0xFF52E8DC, (t - 0.35F) * 0.5F);
        }
        // --- 底部埋进土里 ---
        for (int x = (int) (-r * 1.2F); x <= r * 1.2F; x++) {
            float t = Math.abs(x) / (r * 1.2F);
            int depth = (int) (3.0F + (1.0F - t) * 3.0F);
            for (int dy = 0; dy < depth; dy++) {
                this.blendDot((int) (cx + x), (int) (groundY + dy), DIRT[4], 0.55F - dy * 0.12F);
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
        int bottom = this.height;
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
            int base = this.mix(SOIL_GRAIN_A, FLESH_SOIL_TOP, k);
            for (int x = 0; x < this.width; x++) {
                int j = this.rng.nextInt(5) - 2;
                this.putRaw(x, y, this.shift(base, j));
            }
        }
        // ② 细砂：密度和上面土壤层一样（width × span × 2.6）
        int fine = (int) (this.width * span * 2.6F);
        for (int i = 0; i < fine; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float k = (y - top) / span;
            // 砂粒本色也随深度红化
            int c = this.mix(this.mix(DIRT[4], DIRT[0], this.rng.nextFloat()),
                    FLESH_GRAIN, k * 0.9F);
            this.blendDot(x, y, c, 0.20F + this.rng.nextFloat() * 0.20F);
        }
        // ③ 中粒：受光面（左上）+ 缝（右下）
        int mid = (int) (this.width * span * 0.75F);
        for (int i = 0; i < mid; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float k = (y - top) / span;
            float r = 1.0F + this.rng.nextFloat() * 1.2F;
            this.disc(x, y, r, this.mix(DIRT[1], FLESH_SOIL_MID, k), 0.45F);
            this.blendDot(x - 1, y - 1, this.mix(DIRT[5], 0xFF8A4038, k), 0.30F);
            this.blendDot(x + 1, y + 1, this.mix(DIRT[0], FLESH_GRAIN_DIM, k), 0.26F);
        }
        // ④ 粗砾：投影 + 高光
        int coarse = (int) (this.width * span * 0.14F);
        for (int i = 0; i < coarse; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float k = (y - top) / span;
            float r = 1.8F + this.rng.nextFloat() * 2.4F;
            this.disc((int) (x + 1.6F), (int) (y + 1.6F), r,
                    this.mix(0xFF100A06, 0xFF140608, k), 0.38F);
            this.disc(x, y, r, this.mix(this.rng.nextFloat() < 0.4F ? DIRT[4] : DIRT[1],
                    FLESH_GRAIN, k), 0.82F);
            this.disc((int) (x - r * 0.35F), (int) (y - r * 0.35F),
                    Math.max(0.7F, r * 0.5F), this.mix(DIRT[5], 0xFF8A4038, k), 0.55F);
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
            int c = this.mix(this.rng.nextBoolean() ? DIRT[1] : DIRT[3], FLESH_SOIL_DEEP, k);
            float alpha = 0.28F + this.rng.nextFloat() * 0.26F;
            for (int x = 0; x < this.width; x++) {
                if (this.rng.nextFloat() < 0.16F) {
                    continue;
                }
                float wave = (float) Math.sin(x * 0.03F + i * 1.7F) * 1.5F;
                for (int dy = 0; dy < th; dy++) {
                    this.blendDot(x, (int) (baseY + wave) + dy, c, alpha);
                }
            }
        }
    }
    /** 整体加减一个亮度（抖动用）。 */
    private int shift(int argb, int d) {
        int r = Math.max(0, Math.min(255, ((argb >> 16) & 0xFF) + d));
        int g = Math.max(0, Math.min(255, ((argb >> 8) & 0xFF) + d));
        int b = Math.max(0, Math.min(255, (argb & 0xFF) + d));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
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
                c = this.mix(FLESH_SOIL_TOP, FLESH_SOIL_MID, t * 2.0F);
            } else {
                c = this.mix(FLESH_SOIL_MID, FLESH_SOIL_DEEP, (t - 0.5F) * 2.0F);
            }
            // 逐像素抖 ±2：打散色带，同时让"底"本身也带一点颗粒感 ——
            // 就算哪里被颗粒漏过去，也不是一块平色
            for (int x = 0; x < this.width; x++) {
                int j = this.rng.nextInt(7) - 3;
                this.putRaw(x, y, this.shift(c, j));
            }
        }
        // ② **血管脉络** —— 猩红之地最认得出的东西。
        // 主脉往下走，沿途分叉出细的（递归），而不是一条条平行线。
        int mains = 12 + this.rng.nextInt(8);
        for (int i = 0; i < mains; i++) {
            float vx = this.rng.nextFloat() * this.width;
            float vy = top - span * 0.25F + this.rng.nextFloat() * span * 0.35F;
            double ang = Math.PI / 2.0D + (this.rng.nextDouble() - 0.5D) * 1.1D;
            int steps = (int) (span * (0.55F + this.rng.nextFloat() * 0.6F));
            float wid = 1.6F + this.rng.nextFloat() * 1.4F;
            for (int s = 0; s < steps; s++) {
                ang += (this.rng.nextDouble() - 0.5D) * 0.22D;
                vx += (float) Math.cos(ang) * 1.25F;
                vy += (float) Math.sin(ang) * 1.25F;
                if (vy < top || vy >= bottom || vx < 0 || vx >= this.width) {
                    break;
                }
                // 外圈暗、内芯亮一点 —— 像血在管子里
                this.disc((int) vx, (int) vy, wid + 1.6F, FLESH_VEIN, 0.55F);
                this.disc((int) vx, (int) vy, wid, 0xFF3E1216, 0.60F);
                this.blendDot((int) vx, (int) vy, FLESH_VEIN_LIT, 0.26F);
                // 沿途分细的（递归毛细血管）
                if (s % 22 == 0) {
                    this.capillary(vx, vy, ang + (this.rng.nextBoolean() ? 0.95D : -0.95D),
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
        int fine = (int) (this.width * span * 2.6F);
        for (int i = 0; i < fine; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            // 颗粒本色是红调的（这种土本来就红），一半亮面一半暗缝
            int c = this.rng.nextFloat() < 0.55F ? FLESH_GRAIN : FLESH_GRAIN_DIM;
            this.blendDot(x, y, c, 0.18F + this.rng.nextFloat() * 0.20F);
        }
        int mid = (int) (this.width * span * 0.75F);
        for (int i = 0; i < mid; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float r = 1.0F + this.rng.nextFloat() * 1.2F;
            this.disc(x, y, r, FLESH_SOIL_MID, 0.45F);
            this.blendDot(x - 1, y - 1, 0xFF6A3028, 0.28F);     // 受光面
            this.blendDot(x + 1, y + 1, FLESH_GRAIN_DIM, 0.26F); // 缝
        }
        int coarse = (int) (this.width * span * 0.14F);
        for (int i = 0; i < coarse; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            float r = 1.8F + this.rng.nextFloat() * 2.4F;
            this.disc((int) (x + 1.6F), (int) (y + 1.6F), r, 0xFF140608, 0.38F);  // 投影
            this.disc(x, y, r, this.rng.nextFloat() < 0.5F ? FLESH_GRAIN : FLESH_SOIL_MID, 0.80F);
            this.disc((int) (x - r * 0.35F), (int) (y - r * 0.35F),
                    Math.max(0.7F, r * 0.5F), 0xFF8A4038, 0.52F);                  // 高光
        }
        // ④ **肉质光泽**：局部暗红的湿润反光（土不会这么亮）
        int sheen = 8 + this.rng.nextInt(8);
        for (int i = 0; i < sheen; i++) {
            float px = this.rng.nextFloat() * this.width;
            float py = top + this.rng.nextFloat() * span;
            float r = 3.0F + this.rng.nextFloat() * 9.0F;
            // 一层很淡的红光晕 + 中间一个亮点
            this.disc((int) px, (int) py, r, FLESH_SOIL_TOP, 0.20F);
            this.disc((int) px, (int) py, r * 0.30F, FLESH_WET, 0.30F);
            this.blendDot((int) (px - r * 0.2F), (int) (py - r * 0.2F), 0xFF7A4440, 0.22F);
        }
        // ⑤ 孔洞：暗色的孔隙（让质地透气）
        int pores = (int) (this.width * span * 0.05F);
        for (int i = 0; i < pores; i++) {
            int x = this.rng.nextInt(Math.max(1, this.width));
            int y = (int) (top + this.rng.nextFloat() * span);
            this.disc(x, y, 0.9F + this.rng.nextFloat() * 1.4F, 0xFF140608, 0.34F);
        }
        // ⑥ 深处的凝血块
        int pools = 10 + this.rng.nextInt(7);
        for (int i = 0; i < pools; i++) {
            float px = this.rng.nextFloat() * this.width;
            float py = top + span * (0.45F + this.rng.nextFloat() * 0.5F);
            float r = 5.0F + this.rng.nextFloat() * 16.0F;
            int lobes = 2 + this.rng.nextInt(3);
            for (int l = 0; l < lobes; l++) {
                this.disc((int) (px + (this.rng.nextFloat() - 0.5F) * r),
                        (int) (py + (this.rng.nextFloat() - 0.5F) * r * 0.7F),
                        r * (0.4F + this.rng.nextFloat() * 0.5F), 0xFF20080A, 0.30F);
            }
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
            double x = this.rng.nextDouble() * this.width;
            double y = top + this.rng.nextDouble() * span;
            double ang = this.rng.nextDouble() * Math.PI * 2.0D;
            float wid = 2.6F + this.rng.nextFloat() * 1.6F;
            int steps = 160 + this.rng.nextInt(140);
            for (int s = 0; s < steps; s++) {
                ang += (this.rng.nextDouble() - 0.5D) * 0.20D;
                x += Math.cos(ang) * 1.2D;
                y += Math.sin(ang) * 1.2D;
                if (x < 0 || y < top || x >= this.width || y >= bottom) {
                    break;
                }
                // 外圈暗
                this.disc((int) x, (int) y, wid + 1.8F, 0xFF2A080C, 0.42F);
                // 内芯亮（血）
                this.blendDot((int) x, (int) y, 0xFFA82C32, 0.55F);
                this.blendDot((int) x, (int) y, 0xFFD25A60, 0.22F);
                // 每隔一段分一条细的出去
                if (s % 26 == 0) {
                    this.capillary(x, y, ang + (this.rng.nextBoolean() ? 0.9D : -0.9D),
                            top, bottom, 3);
                }
            }
        }
    }

    /** 递归分叉的毛细血管。 */
    private void capillary(double x, double y, double ang, float top, float bottom, int depth) {
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
            for (int x = 0; x < this.width; x++) {
                this.blendDot(x, y, 0xFF20090C, op * 0.55F);
            }
        }
        // 粗纤维：更宽、更暗、明暗对比更强
        int coarse = (int) (span * 1.1F);
        for (int i = 0; i < coarse; i++) {
            int y = (int) top + this.rng.nextInt(Math.max(1, (int) span));
            int len = 30 + this.rng.nextInt(Math.max(10, (int) (this.width * 0.55F)));
            int x0 = this.rng.nextInt(Math.max(1, this.width - len));
            boolean light = this.rng.nextFloat() < 0.4F;
            int c = light ? 0xFF6E1A20 : 0xFF0E0406;
            int thick = 1 + this.rng.nextInt(2);
            for (int x = x0; x < x0 + len; x++) {
                for (int dy = 0; dy < thick; dy++) {
                    this.blendDot(x, y + dy, c, 0.2F + this.rng.nextFloat() * 0.14F);
                }
            }
        }
        // 几块压实的暗块
        for (int i = 0; i < 16; i++) {
            this.disc(this.rng.nextInt(Math.max(1, this.width)),
                    (int) (top + this.rng.nextFloat() * span),
                    6.0F + this.rng.nextFloat() * 16.0F, 0xFF0A0304, 0.32F);
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
            float gx = this.rng.nextFloat() * this.width;
            float gy = top + this.rng.nextFloat() * Math.max(1.0F, bottom - top);
            float gr = 12.0F + this.rng.nextFloat() * 26.0F;
            this.disc((int) gx, (int) gy, gr, 0xFF3A0A10, 0.30F);
            this.disc((int) gx, (int) gy, gr * 0.55F, 0xFF5A1016, 0.26F);
            this.disc((int) gx, (int) gy, gr * 0.22F, 0xFF8A2028, 0.22F);
        }
    }

    /* ---- ② 树根 -------------------------------------------------------- */

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
        float g = this.grow();
        float total = Math.max(40.0F, this.height - groundY);
        float depthSpan = Math.max(1.0F, this.height - this.soilTop);

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
                float side = (this.rng.nextBoolean() ? 1.0F : -1.0F);
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
            float len = trunkW * (1.4F + this.rng.nextFloat() * 1.3F) * (0.7F + g);
            float sx = cx + side * trunkW * 0.20F;
            float sy = groundY - 1.0F;
            float maxY = this.soilTop + (this.fleshTop - this.soilTop) * 0.9F;
            float ey = Math.min(maxY, sy + len * (0.7F + g * 0.5F));
            this.limbShaded(sx, sy, sx + side * len * spread, ey,
                    Math.max(1.4F, trunkW * 0.26F), 0.8F, 1);
        }

        // ---- 血肉里的血管分叉（从三条主路的深处散出去）----
        int vb = 10 + Math.round(g * 12.0F);
        for (int i = 0; i < vb; i++) {
            int way = i % 3;
            int stage = 4 + (i % 3);
            int[] node = this.wayNodes[way][Math.min(STAGES_PER_WAY - 1, stage)];
            if (node == null || node[0] == 0) {
                continue;
            }
            float nx = node[0] * this.ss;
            float ny = node[1] * this.ss;
            double ang = this.rng.nextDouble() * Math.PI * 2.0D;
            this.capillary(nx, ny, ang, this.fleshTop, this.height, 2);
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
            this.disc((int) x, (int) y, w, this.bark[1], 0.95F);
            this.blendDot((int) (x - w * 0.5F), (int) y, BARK_LIGHT, 0.22F);
        } else if (depth < 0.46F) {
            float k = (depth - 0.22F) / 0.24F;
            this.disc((int) x, (int) y, w, this.bark[1], 0.9F);
            this.disc((int) x, (int) y, w * 0.72F, MEAT_DEEP, 0.20F + k * 0.55F);
            if (this.rng.nextInt(5) == 0) {
                this.blendDot((int) x, (int) y, 0xFF160608, 0.32F);
            }
        } else if (depth < 0.72F) {
            this.disc((int) x, (int) y, w * 0.96F, MEAT_DEEP, 0.85F);
            this.blendDot((int) (x - w * 0.35F), (int) y, MEAT_FRESH, 0.28F);
            this.blendDot((int) x, (int) (y + w * 0.3F), 0xFF20080C, 0.20F);
            if (this.rng.nextInt(7) == 0) {
                this.blendDot((int) x, (int) y, WET, 0.12F);
            }
        } else {
            this.disc((int) x, (int) y, w + 1.5F, 0xFF2A080C, 0.55F);
            this.disc((int) x, (int) y, w, MEAT_FRESH, 0.60F);
            this.blendDot((int) x, (int) y, 0xFFD25A60, 0.34F);
            // 走到血管这一段就开始散毛细血管
            if (this.rng.nextInt(26) == 0) {
                this.capillary(x, y, this.rng.nextDouble() * Math.PI * 2.0D,
                        this.fleshTop, this.height, 2);
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
        depthSpanLocal = Math.max(1.0F, this.height - this.soilTop);

        float px = x;
        float py = y;
        float dirX = side * 1.0F;
        float dirY = 0.10F;
        int steps = 22 + this.rng.nextInt(20);
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
            if (px < 3.0F || px > this.width - 3.0F || py >= this.height - 2.0F) {
                break;
            }
            float d = (py - this.soilTop) / depthSpanLocal;
            float ww = Math.max(0.7F, w * thick * (1.0F - s / (float) steps * 0.82F));
            this.rootSegment(px, py, ww, d, 0, 0);
        }
        // **末端一撮细根**（现实中根尖分出的那些短根）
        this.fineRoots(px, py, dirX, dirY, WAY_FINE[idx], way);
    }

    /** splitRoot 里用的深度跨度（避免每次重算）。 */
    private float depthSpanLocal = 1.0F;

    /**
     * **细根 + 根毛** —— 根尖那一撮。
     *
     * <p>现实中一棵树的根尖有几百万条，这里按密度倍率给一小撮：
     * 短（6~20 像素）、细（<1 像素宽）、方向散开但偏母根方向。</p>
     */
    private void fineRoots(float x, float y, float dirX, float dirY, float density, int way) {
        int idx = Math.min(way, 2);
        int count = (int) ((6 + this.rng.nextInt(9)) * density);
        float baseAng = (float) Math.atan2(dirY, dirX);
        for (int i = 0; i < count; i++) {
            // 方向：母根方向 ± 散开（硅化的散得小，因为不弯）
            float spreadK = idx == 2 ? 0.55F : 1.15F;
            float ang = baseAng + (this.rng.nextFloat() - 0.5F) * spreadK;
            float len = (5.0F + this.rng.nextFloat() * 14.0F) * (0.6F + density * 0.4F);
            float fx = x;
            float fy = y;
            for (int s = 0; s < (int) len; s++) {
                ang += (this.rng.nextFloat() - 0.5F) * (idx == 2 ? 0.06F : 0.20F);
                fx += (float) Math.cos(ang);
                fy += (float) Math.sin(ang) * 1.15F;   // 略微向下
                if (fy >= this.height - 1.0F || fx < 1.0F || fx > this.width - 1.0F) {
                    break;
                }
                float d = (fy - this.soilTop) / depthSpanLocal;
                this.rootSegment(fx, fy, Math.max(0.6F, 0.9F * WAY_THICK[idx]), d, 0, 0);
            }
            // **根毛**：细根末端后面那一小段密集的短毛（1 像素）
            if (this.rng.nextBoolean()) {
                int hairs = 3 + this.rng.nextInt(6);
                for (int h = 0; h < hairs; h++) {
                    float ha = ang + (this.rng.nextFloat() - 0.5F) * 1.6F;
                    float hx = fx + (float) Math.cos(ha) * (1 + this.rng.nextInt(3));
                    float hy = fy + (float) Math.sin(ha) * (1 + this.rng.nextInt(3));
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
        int steps = 26 + this.rng.nextInt(22);
        float depthSpan = Math.max(1.0F, this.height - this.soilTop);
        for (int s = 0; s < steps; s++) {
            dirY += 0.030F;          // 逐渐下垂
            dirX *= 0.982F;          // 水平速度衰减
            px += dirX * 1.7F;
            py += dirY * 1.7F;
            if (px < 4.0F || px > this.width - 4.0F || py >= this.height - 2.0F) {
                break;
            }
            float d = (py - this.soilTop) / depthSpan;
            float ww = w * (1.0F - s / (float) steps * 0.8F);
            this.rootSegment(px, py, Math.max(0.8F, ww), d, 0, 0);
        }
        // 末端再分一条细的
        if (this.rng.nextBoolean()) {
            this.lateral(px, py, side * (this.rng.nextBoolean() ? 1.0F : -1.0F),
                    w * 0.5F, depth, level - 1);
        }
    }

    /**
     * **主根分出来的三支** —— 就是血肉线的三条路。
     *
     * @param dir -0.85 / 0 / +0.85（左 / 中 / 右）
     */
    private void splitRoot(float x, float y, float dir, float w, float depth, int way) {
        float depthSpan = Math.max(1.0F, this.height - this.soilTop);
        // 终点：左支往左下、中支往最深处、右支往右下
        float tx = x + this.width * dir * 0.30F;
        float ty = this.height * (way == 1 ? 0.98F : 0.90F);
        tx = Math.max(10.0F, Math.min(this.width - 10.0F, tx));
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
            if (py >= this.height - 2.0F || px < 4.0F || px > this.width - 4.0F) {
                break;
            }
            float d = (py - this.soilTop) / depthSpan;
            float ww = Math.max(1.0F, w * (1.0F - t * 0.6F));
            this.rootSegment(px, py, ww, d, 0, 0);

            // **侧根**：按这条路的性格发（异化多、灵化飘、硅化直）
            int laterals = WAY_LATERALS[Math.min(way, 2)];
            float step = Math.max(1.0F, steps / (float) laterals);
            if (s > steps * 0.15F && Math.abs(s % step) < 1.0F) {
                float side = (this.rng.nextBoolean() ? 1.0F : -1.0F);
                this.lateralStyled(px, py, side, ww * 0.58F, d, way);
            }

            // 七个节点均分在这一支上
            if (nextStage < STAGES_PER_WAY) {
                float want = (nextStage + 1) / (float) (STAGES_PER_WAY + 1);
                if (t >= want) {
                    this.wayNodes[way][nextStage][0] = (int) (px / this.ss);
                    this.wayNodes[way][nextStage][1] = (int) (py / this.ss);
                    this.wayAlive[way] = true;
                    nextStage++;
                }
            }
        }
    }

    /* ---- ③ 主干 + 分枝 -------------------------------------------------- */


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

    /** 分叉点的高度比（树高的多少）。 */
    private static final float FORK_T = 0.42F;
    /** 每级长度衰减。 */
    private static final float LEN_STEP = 0.78F;
    /** 每级粗细衰减。 */
    private static final float WID_STEP = 0.66F;
    /** 分枝角度范围（弧度）。 */
    private static final float ANG_MIN = 0.48F;
    private static final float ANG_MAX = 0.92F;

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
        float forkY = groundY - trunkH * FORK_T;
        float lean = (this.rng.nextFloat() - 0.5F) * trunkW * 2.2F;
        float forkX = cx + lean;

        // ① 主干：弯曲 + 根颈外扩
        this.trunkBent(cx, groundY, forkX, forkY, trunkW);

        // ② 主枝：3~5 根，从一个点上放射出去
        List<float[]> tips = new ArrayList<>();
        int n = 3 + this.rng.nextInt(3);
        float restH = trunkH * (1.0F - FORK_T);
        for (int i = 0; i < n; i++) {
            // 散布：-1 ~ 1（左到右），基准方向是"向上"（屏幕 y 向下，所以 -π/2）
            float spread = n == 1 ? 0.0F : (i / (float) (n - 1) - 0.5F) * 2.0F;
            float ang = (float) (-Math.PI / 2.0D) + spread * 0.95F
                    + (this.rng.nextFloat() - 0.5F) * 0.24F;
            // 长度：接近主干余量，各自不同（长短悬殊才有树形）
            float len = restH * (0.72F + this.rng.nextFloat() * 0.48F);
            float w = trunkW * (0.58F + this.rng.nextFloat() * 0.16F);
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
        float bulgeSide = this.rng.nextBoolean() ? 1.0F : -1.0F;
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
            this.disc((int) x, (int) y, w, this.bark[1], 0.96F);
            this.disc((int) (x - w * 0.42F), (int) y, Math.max(1.0F, w * 0.34F),
                    this.bark[3], 0.34F);
            this.blendDot((int) (x - w * 0.55F), (int) y, BARK_LIGHT, 0.20F);
            this.blendDot((int) (x + w * 0.62F), (int) y, 0xFF180E08, 0.26F);
            // 树皮的竖纹
            if (s % 3 == 0) {
                this.blendDot((int) (x + (this.rng.nextFloat() - 0.5F) * w * 1.2F), (int) y,
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
                this.leafCluster((int) (tip[0] + (this.rng.nextFloat() - 0.5F) * r),
                        (int) (tip[1] + (this.rng.nextFloat() - 0.5F) * r), r * 0.62F);
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
            this.disc((int) (x + w * 0.5F), (int) y, Math.max(1.0F, w),
                    0xFF0A0604, 0.30F);
        }
    }

    private void tree(float cx, float groundY, float trunkH, float trunkW) {
        // **第一阶还不是树** —— 是一棵刚钻出地面的幼苗。
        // 之前这里没有分支，stage 0 画的是一棵缩小版的大树（有树皮、有分枝），
        // 那当然不像幼苗：幼苗是另一种东西，细嫩的茎 + 两片摊开的子叶，
        // 没有树皮、没有分枝、也没有成树的深色叶。
        if (this.stage <= 0) {
            this.sprout(cx, groundY, trunkH);
            return;
        }
        int levels = this.rampCount(LEVELS);
        // **完全重画**：换成现实树的骨架（主干分叉 + 主枝放射）。
        // 下面那一套旧逻辑（沿主干左右交替挂枝 = 针叶树的画法）不再使用，
        // 留在这里只是为了对照 —— 想回退把开关打开即可。
        this.realTree(cx, groundY, trunkH, trunkW, Math.max(1, levels));
    }

    /** 幼苗：破土的一段嫩茎 + 两片子叶，土面上还留着裂口。 */
    private void sprout(float cx, float groundY, float trunkH, float trunkW) {
        float topY = groundY - Math.max(6.0F, trunkH);
        float w = Math.max(1.4F, trunkW * 0.5F);
        this.limbShaded(cx, groundY, cx, topY, w, w * 0.6F, 1);
        // 土面裂口
        for (int i = 0; i < 5; i++) {
            float a = (float) (-Math.PI + this.rng.nextFloat() * Math.PI * 2.0F);
            this.limbShaded(cx, groundY, cx + (float) Math.cos(a) * 5.0F,
                    groundY + (float) Math.sin(a) * 2.0F, 1.2F, 0.5F, 1);
        }
        // 两片子叶
        int lit = this.mix(this.leaf[5], this.leaf[7], 0.35F);
        this.leafShape(cx - w * 2.0F, topY - 1.0F, Math.max(3.0F, w * 2.4F), this.leaf[3]);
        this.leafShape(cx + w * 2.0F, topY - 2.0F, Math.max(3.0F, w * 2.4F), lit);
        // 顶芽
        this.disc((int) cx, (int) (topY - 2.0F), Math.max(1.5F, w), this.leaf[4], 0.9F);
        // 这两片就是最初的强化叶
        this.registerLeaf((int) (cx - w * 2.0F), (int) (topY - 1.0F), Math.max(3, (int) w));
        this.registerLeaf((int) (cx + w * 2.0F), (int) (topY - 2.0F), Math.max(3, (int) w));
        this.slots.add(new int[]{(int) (cx / this.ss), (int) ((groundY - trunkH * 0.5F) / this.ss)});
    }

    /** 递归分枝：子枝半径 = 父枝 / √n（达芬奇的规则，看起来才对劲）。 */
    private void branch(float x, float y, float angle, float len, float w,
                        int level, int maxLevel, List<float[]> tips) {
        float ex = x + (float) Math.cos(angle) * len;
        float ey = y + (float) Math.sin(angle) * len;
        float ew = w * LENGTH_RATIO;
        this.limbShaded(x, y, ex, ey, w, ew * 0.72F, 2);

        if (level >= maxLevel || len < 6.0F) {
            tips.add(new float[]{ex, ey});
            return;
        }
        int kids = Math.min(KIDS[Math.min(level, KIDS.length - 1)], 3);
        float each = w / (float) Math.sqrt(kids + 1);
        // **分叉要不齐** —— 真实的大树没有两根一样长的枝。
        // 之前这里是"角度 0.42~0.72 + 长度 0.72~0.94"，
        // 每根枝几乎一样长、角度也匀 —— 那就是"看着像程序画的"的来源。
        // 现在角度 0.22~1.05、长度 0.52~0.98，长短悬殊出来才有树的形状。
        // 主枝继续往原方向走（比侧枝略短，符合真实的分叉）
        this.branch(ex, ey, angle + (this.rng.nextFloat() - 0.5F) * 0.26F,
                len * LENGTH_RATIO * (0.60F + this.rng.nextFloat() * 0.24F),
                each, level + 1, maxLevel, tips);
        for (int i = 0; i < kids; i++) {
            float spread = (0.22F + this.rng.nextFloat() * 0.83F) * (i % 2 == 0 ? 1.0F : -1.0F);
            float lenK = 0.52F + this.rng.nextFloat() * 0.46F;
            this.branch(ex, ey, angle + spread, len * LENGTH_RATIO * lenK,
                    each, level + 1, maxLevel, tips);
        }
    }

    /** 一簇叶：几片叶挤在一起，不是单个圆点。 */
    /** 树冠整体的偏向（-1 ~ 1）：真实的大树总有一侧更茂密。 */
    private float canopyBias;

    private void leafCluster(float x, float y, float r) {
        int n = 5 + this.rng.nextInt(4);
        // 偏向那一侧的叶簇更大一点（不对称是"像真树"的关键之一）
        if (this.canopyBias != 0.0F) {
            float side = this.width > 0 ? (x - this.width / 2.0F) / (this.width / 2.0F) : 0.0F;
            r *= 1.0F + side * this.canopyBias * 0.22F;
        }
        for (int i = 0; i < n; i++) {
            double a = this.rng.nextDouble() * Math.PI * 2.0D;
            double d = this.rng.nextDouble() * r * 0.85D;
            float lx = x + (float) Math.cos(a) * (float) d;
            float ly = y + (float) Math.sin(a) * (float) d * 0.85F;
            float lr = r * (0.42F + this.rng.nextFloat() * 0.30F);
            int c = this.leaf[2 + this.rng.nextInt(LEAF.length - 3)];
            // **强化叶：把"这个槽选了什么"直接长在叶子上**，不用写字。
            // 哪一族 → 染成那一族的颜色；族内第几档 → 越亮、越大，
            // 第三档再给一圈光晕。扫一眼树冠就知道自己点到哪了。
            if (this.upgradeLeafCounter < MAX_UPGRADE_LEAF) {
                int slot = this.upgradeLeafCounter;
                int tier = this.choiceOf(slot);
                int accent = FAMILY_COLORS[slot % FAMILY_COLORS.length];
                if (tier > 0) {
                    c = mix(c, accent, Math.min(1.0F, 0.42F + tier * 0.19F));
                    lr *= 0.86F + tier * 0.12F;
                }
                this.leafShape(lx, ly, lr, c);
                if (tier >= 3) {
                    this.disc((int) lx, (int) ly, lr * 1.5F, accent, 0.22F);
                }
                this.registerLeaf((int) lx, (int) ly, (int) lr);
            } else {
                this.leafShape(lx, ly, lr, c);
            }
        }
        // 叶子底下的暗部，让簇看起来有厚度
        this.disc((int) x, (int) (y + r * 0.35F), r * 0.7F, this.leaf[0], 0.35F);
        // **星光的边**：光源在左上，所以左上那一缘亮一线、右下沉一圈。
        // 这一笔是整个树冠"立体"的来源 —— 没有它，叶子看着是贴纸。
        int rim = 14 + this.rng.nextInt(10);
        for (int i = 0; i < rim; i++) {
            double a = Math.PI * (1.02D + this.rng.nextDouble() * 0.62D);   // 左上 ~ 下端偏左
            float d = r * (0.70F + this.rng.nextFloat() * 0.34F);
            int px = (int) (x + Math.cos(a) * d);
            int py = (int) (y + Math.sin(a) * d * 0.85F);
            this.blendDot(px, py, STARLIGHT, 0.20F + this.rng.nextFloat() * 0.18F);
        }
        // 右下缘压暗
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * (0.05D + this.rng.nextDouble() * 0.45D);
            float d = r * (0.72F + this.rng.nextFloat() * 0.30F);
            this.blendDot((int) (x + Math.cos(a) * d), (int) (y + Math.sin(a) * d * 0.85F),
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
        if (this.stage >= 3 && this.pixels != 0L) {
            int holes = 1 + this.rng.nextInt(3);
            for (int h = 0; h < holes; h++) {
                float hx = x + (this.rng.nextFloat() - 0.5F) * r * 1.10F;
                float hy = y + (this.rng.nextFloat() - 0.5F) * r * 0.95F;
                int rad = Math.max(1, (int) (r * (0.10F + this.rng.nextFloat() * 0.18F)));
                for (int dy = -rad; dy <= rad; dy++) {
                    for (int dx = -rad; dx <= rad; dx++) {
                        if (dx * dx + dy * dy > rad * rad) {
                            continue;
                        }
                        int px = (int) hx + dx;
                        int py = (int) hy + dy;
                        if (px < 0 || py < 0 || px >= this.width || py >= this.height) {
                            continue;
                        }
                        this.put(px, py, 0);          // 全透明 → 透出后面的星空
                    }
                }
            }
        }
    }

    /** 第 slot 个强化槽选了什么（0 = 空，1/2/3 = 该族的第几档）。 */
    private int choiceOf(int slot) {
        if (this.choices == null || slot < 0 || slot >= this.choices.length) {
            return 0;
        }
        return Math.max(0, Math.min(3, this.choices[slot]));
    }

    /** 记一片可以点的叶。 */
    private void registerLeaf(int x, int y, int r) {
        if (this.upgradeLeafCounter >= MAX_UPGRADE_LEAF) {
            return;
        }
        // 同上：坐标换算回最终尺寸
        this.leaves.add(new int[]{(int) (x / (float) this.ss), (int) (y / (float) this.ss),
                Math.max(4, (int) (r / (float) this.ss) + 3)});
        this.upgradeLeafCounter++;
    }

    /** 树冠里的灵魂光点（少量，飘着）。 */
    private void soulMotes() {
        int n = 10 + (int) (this.grow() * 26.0F);
        for (int i = 0; i < n; i++) {
            float x = this.rng.nextFloat() * this.width;
            float y = this.soilTop * (0.12F + this.rng.nextFloat() * 0.82F);
            float r = 0.8F + this.rng.nextFloat() * 1.8F;
            this.disc((int) x, (int) y, r, MOSS_LIT, 0.55F);
            this.disc((int) x, (int) y, r * 2.4F, MOSS, 0.16F);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  像素工具                                                            */
    /* ------------------------------------------------------------------ */

    /** 带明暗的一段粗线（树枝、树根都用它）。 */
    private void limbShaded(float x0, float y0, float x1, float y1,
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
                if (x * LIGHT_X + y * LIGHT_Y < -r * 0.35F) {
                    c = this.mix(color, 0xFF000000, 0.28F);   // 背光
                } else if (x * LIGHT_X + y * LIGHT_Y > r * 0.30F) {
                    c = this.mix(color, 0xFFFFFFFF, 0.16F);   // 受光
                }
                this.blendDot(px, py, c, Math.min(1.0F, edge + 0.35F));
            }
        }
        // **叶脉** —— 没有脉的叶子就是一块绿斑
        int vein = this.mix(color, 0xFF08160E, 0.5F);
        int litVein = this.mix(color, 0xFFE8FFE0, 0.28F);
        // 中脉（贯穿）
        for (int y = -rad; y <= rad; y++) {
            float k = 1.0F - Math.abs(y) / (float) Math.max(1, rad);
            this.blendDot((int) cx, (int) cy + y, vein, 0.34F * k);
        }
        // 侧脉（从中脉斜向两侧）
        int gap = Math.max(1, rad / 2);
        for (int i = -rad + 1; i <= rad - 1; i += gap) {
            for (int s = 1; s <= (int) (rad * 0.72F); s++) {
                float fade = 1.0F - s / (rad * 0.85F);
                if (fade <= 0) {
                    continue;
                }
                this.blendDot((int) (cx + s), (int) (cy + i + s * 0.42F), vein, 0.2F * fade);
                this.blendDot((int) (cx - s), (int) (cy + i + s * 0.42F), vein, 0.2F * fade);
            }
        }
        // 叶尖上的一点受光
        this.blendDot((int) cx, (int) (cy - rad * 0.7F), litVein, 0.3F);
    }

    /** 同一个用途，但值已经是 ABGR（各层的底都是这么算出来的）。 */
    private void putAbgr(int x, int y, int abgrValue) {
        if (this.pixels != 0L) {
            this.put(x, y, abgrValue);
        } else {
            this.image.setPixelRGBA(x, y, abgrValue);
        }
    }

    /** 直接铺一个像素（不走混合，用于底色那种整片填充）。 */
    private void putRaw(int x, int y, int argb) {
        int abgr = abgr(argb);
        if (this.pixels != 0L) {
            this.put(x, y, abgr);
        } else {
            this.image.setPixelRGBA(x, y, abgr);
        }
    }

    private void disc(int cx, int cy, float r, int color, float strength) {
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
    private long pixels;
    private int stride;

    private void resolvePixels(NativeImage img) {
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
            this.pixels = 0L;   // 拿不到就走原路，功能不受影响
        }
    }

    private void put(int x, int y, int abgr) {
        // 顺手记一下有内容的范围（"裁透明区"值不值得做的依据）。
        // 这里是所有写入的最底层，加 4 次整数比较，JIT 会把它优化掉。
        this.ink(x, y);
        org.lwjgl.system.MemoryUtil.memPutInt(
                this.pixels + ((long) y * this.stride + x) * 4L, abgr);
    }

    private int get(int x, int y) {
        return org.lwjgl.system.MemoryUtil.memGetInt(
                this.pixels + ((long) y * this.stride + x) * 4L);
    }

    private void blendDot(int x, int y, int color, float k) {
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

    private int mix(int a, int b, float t) {
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

    /** 0xAARRGGBB → NativeImage.setPixelRGBA 要的 ABGR 打包。 */
    private static int abgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

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
