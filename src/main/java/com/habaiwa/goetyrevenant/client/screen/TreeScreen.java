package com.habaiwa.goetyrevenant.client.screen;

import com.habaiwa.goetyrevenant.client.BodyClientState;
import com.habaiwa.goetyrevenant.common.body.UpgradeType;
import com.habaiwa.goetyrevenant.common.network.BodyActionMessage;
import com.habaiwa.goetyrevenant.common.network.BodyStateMessage;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import com.habaiwa.goetyrevenant.common.tree.SoulTree;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * **魂树界面** —— 一片灵魂空间里的一棵树。
 *
 * <h3>树本身是一张纹理</h3>
 * <p>树（主干 / 枝 / 叶 / 年轮）由 {@link TreeTexture} 用像素画出来，绘制时一次 blit ——
 * 所以它是连续的、平滑的，不是一堆积木；每帧也只有一次绘制。</p>
 *
 * <h3>跳井之前什么都看不到</h3>
 * <p>树是灵魂的形态，没跳进哭泣之井的人只有一片漆黑的虚空和一行提示。</p>
 */
public class TreeScreen extends Screen {
    /** 日志器 —— 计时信息走它（System.out 在启动器里不一定进日志）。 */
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    // ======================================================================
    //  【留给以后的结构说明】
    //
    //  这个界面之后的定位是"**一个界面干很多事**"，不是单一功能页。
    //  现在它已经具备了承载这件事的地基：
    //
    //    · 它是**一张比屏幕大的内容图**（1.8 × 2.6 倍），可以上下左右平移 ——
    //      所以空间是够的，往上、往下、往左右都有地方放新东西。
    //    · 内容图的分区是清楚的：
    //          0.00 ~ 0.50   树（地上）—— 魂：序列、聚晶、强化叶
    //          0.50 ~ 0.64   土壤
    //          0.64 ~ 1.00   **血肉层** —— 血肉线三条路的根与节点
    //      往后再加区域，按比例切新的层带即可（别忘了同步 contentHeight）。
    //    · 交互坐标有统一换算（屏幕 ↔ 内容图，见 pickSlot / rootNodeAt 的用法），
    //      新增可点区域照抄那套就行。
    //
    //  以后要往里放的东西（已知）：
    //    · 血肉线三条路的每个阶段（异化 / 灵化 / 硅化）
    //    · 绽放（花）与成源（果）—— 尚未设计
    //    · 三我聚晶 —— 六阶，灵环中央
    // ======================================================================

    private static final int BONE_DIM = 0xFF9FB2B8;
    private static final int SOUL_BRIGHT = 0xFF52E8DC;
    private static final int GOLD = 0xFFD8B24A;

    /**
     * **生长动画的总时长**。
     *
     * <p>一个玩家一辈子只播一次（觉醒那一刻），所以放慢是值得的 ——
     * 1.9 秒太赶，还没看清就从种子长成苗了。现在五秒，节奏也重排过。</p>
     */
    private static final long GROW_MS = 5000L;
    private static final long RIPPLE_MS = 700L;

    private final SoulBackdrop backdrop = BACKDROP_CACHE;
    // ======================================================================
    //  **这三张图跨界面实例复用**（静态）。
    //
    //  之前它们是普通字段 —— 每 new 一次 TreeScreen 就新建一个空对象，
    //  上一张图白白扔掉，下次打开又从头生成。于是"每次开界面都要等加载"，
    //  而其实那张图根本没变。
    //
    //  改成静态之后：只有**第一次**要等，之后打开是瞬时的。
    //  状态变化（阶数、嵌入槽、血肉线）会让 stamp 变 → 自动重新生成，
    //  不需要手动清缓存。
    // ======================================================================
    // **地层**（土壤/过渡/血肉土壤）—— 静态，只在尺寸变时生成一次
    private static final TreeTexture TERRAIN_CACHE = new TreeTexture("soul_terrain", true);
    // **树那一层**（树/种子/根）—— 状态变了才重做
    private static final TreeTexture TREE_CACHE = new TreeTexture();
    private static final TreeTexture SEED_CACHE = new TreeTexture("soul_seed");
    /** 裂开的种子 —— 生长动画中间那一段用它（种子"破开"是这个动画的重点）。 */
    private static final TreeTexture SEED_OPEN_CACHE = new TreeTexture("soul_seed_open");
    private static final SoulBackdrop BACKDROP_CACHE = new SoulBackdrop();

    private final TreeTexture terrain = TERRAIN_CACHE;
    private final TreeTexture tree = TREE_CACHE;
    /** 固定的"一粒种子" —— 生长动画要和树苗叠着画，所以单独一张纹理。 */
    private final TreeTexture seed = SEED_CACHE;
    private final TreeTexture seedOpen = SEED_OPEN_CACHE;
    private final List<long[]> ripples = new ArrayList<>();

    private long openedAt = 0L;
    private boolean playGrowth = true;
    /** 生长动画的那句提示只报一次。 */
    private boolean growthAnnounced = false;
    /** 打过的帧数（前 20 帧报告渲染耗时）。 */
    private int frameCount = 0;
    /** 生长动画演完之后，两张种子图是不是已经放掉了（只放一次）。 */
    private boolean seedReleased = false;

    // ---- 视口：可以上下左右看这张比屏幕大的图 ----
    /** 视口左上角在内容图里的位置。 */
    private int viewX = 0;
    private int viewY = 0;
    /** 第一次进来要把视口对准树（内容图的上半部分）。 */
    private boolean viewInit = false;
    /** 右键拖动的起点（-1 = 没在拖）。 */
    private double dragX = -1.0D;
    private double dragY = -1.0D;
    /** 这一次按住左键有没有真的拖动过（没拖就是个普通点击）。 */
    private boolean dragMoved = false;
    /** 树的缩放（滚轮调）。 */
    private float zoom = 1.0F;
    private int refreshCooldown = 10;
    private int hoveredSlot = -1;
    private int hoveredLeaf = -1;

    public TreeScreen() {
        super(Component.translatable("screen.goety_revenant.tree"));
    }

    private static float heartbeat() {
        long t = System.currentTimeMillis();
        return (float) ((Math.sin(t / 620.0D) * 0.5D + 0.5D) * 0.7D + Math.sin(t / 210.0D) * 0.3D);
    }

    private float growth() {
        if (!this.playGrowth) {
            return 1.0F;
        }
        float t = Math.min(1.0F, (System.currentTimeMillis() - this.openedAt) / (float) GROW_MS);
        // **分段平滑（关键帧兜底）**
        // 纯粹的三次缓出在低帧率下会"滑过去"：五秒的动画如果只有 15 帧，
        // 中间只剩几个采样点，看着就是跳。
        // 这里把整个生长拆成 4 段、每段两端都慢（smoothstep）——
        // "破土 / 出芽 / 展叶 / 成苗"这几个节点上天然有停顿，
        // 帧率再低也一定会经过它们。
        final int segs = 4;
        float seg = t * segs;
        int i = Math.min(segs - 1, (int) seg);
        float local = seg - i;
        local = local * local * (3.0F - 2.0F * local);
        return (i + local) / segs;
    }

    /** 界面是哪一刻打开的（用来测"从打开到第一帧"有多久）。 */
    private static long openedAtMs;

    @Override
    protected void init() {
        openedAtMs = System.currentTimeMillis();
        // **本地判断"这个阶段见过没"** —— 不依赖服务端那个会被提前消费掉的标志
        this.playGrowth = BodyClientState.get() == null
                || BodyClientState.takeNewStageLocally()
                || BodyClientState.isNewStage();
        this.openedAt = this.playGrowth ? System.currentTimeMillis() : 0L;
        this.ripples.clear();
        RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(BodyActionMessage.OPEN, ""));
    }

    @Override
    public void tick() {
        if (--this.refreshCooldown > 0) {
            return;
        }
        // 每 10 tick 要一次状态太频繁了（每秒两次）—— 状态本身不会那么快变，
        // 每次都要重建一个整包、走一遍序列化。改成 2 秒一次。
        this.refreshCooldown = 40;
        RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(BodyActionMessage.OPEN, ""));
    }

    /** **滚轮缩放树**。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float z0 = this.zoom;
        this.zoom = (float) Math.max(0.60D, Math.min(1.70D, this.zoom + delta * 0.12D));
        float z1 = this.zoom;
        if (Math.abs(z1 - z0) > 1.0E-4F) {
            // **以鼠标指着的地方为锚点** —— 缩放前后，鼠标底下的那块内容不动。
            // 之前是以视口中心为锚点，所以鼠标在哪儿滚，画面都会往中心收 ——
            // 在边角上滚时看着就像"跑到左上角去了"。
            double vw = this.width;
            double vh = this.height;
            double srcW0 = vw / z0;
            double srcH0 = vh / z0;
            double srcW1 = vw / z1;
            double srcH1 = vh / z1;
            double anchorX = (this.viewX + vw / 2.0) + (mouseX - vw / 2.0) * (srcW0 / vw);
            double anchorY = (this.viewY + vh / 2.0) + (mouseY - vh / 2.0) * (srcH0 / vh);
            double centerX = anchorX - (mouseX - vw / 2.0) * (srcW1 / vw);
            double centerY = anchorY - (mouseY - vh / 2.0) * (srcH1 / vh);
            this.viewX = (int) Math.round(centerX - vw / 2.0);
            this.viewY = (int) Math.round(centerY - vh / 2.0);
        }
        this.clampView();
        return true;
    }

    /** **快捷键：把鼠标下那片叶子的方向循环切过去**（空 → 攻 → 守 → 速 → 空）。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // **方向键看这张大图**：土壤下面还有一层血肉，得能看下去
        int step = (int) Math.max(16.0F, this.height * 0.14F);
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                this.viewY -= step;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                this.viewY += step;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> {
                this.viewX -= step;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> {
                this.viewX += step;
                return true;
            }
            default -> {
            }
        }
        this.clampView();
        if (this.hoveredLeaf >= 0 && com.habaiwa.goetyrevenant.client.BodyKeybinds.UPGRADE_CYCLE
                .matches(keyCode, scanCode)) {
            int[] chosen = BodyClientState.upgrades();
            int cur = this.hoveredLeaf < chosen.length ? chosen[this.hoveredLeaf] : 0;
            int next = cur >= 3 ? 0 : cur + 1;
            RevenantNetwork.CHANNEL.sendToServer(
                    new com.habaiwa.goetyrevenant.common.network.UpgradeMessage(this.hoveredLeaf, next));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.ripples.add(new long[]{(long) mouseX, (long) mouseY, System.currentTimeMillis()});
        if (this.hoveredSlot >= 0) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new FocusPickerScreen(this.hoveredSlot));
            return true;
        }
        if (this.hoveredLeaf >= 0) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new UpgradePickerScreen(this.hoveredLeaf));
            return true;
        }
        // **按住左键拖 = 平移这张图**（左边那个右键拖动已经撤掉了 —— 反人类）。
        // 和点选不打架：如果按下时正落在某个可点的东西上（槽位/叶子），
        // 那次点击照旧归它；落在空白处才开始拖。
        if (button == 0) {
            this.dragX = mouseX;
            this.dragY = mouseY;
            this.dragMoved = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /* ------------------------------------------------------------------ */

    /** 第一帧报告过没有。 */
    private static boolean firstFrameLogged;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long frameT = System.nanoTime();
        if (!firstFrameLogged) {
            firstFrameLogged = true;
            LOG.info("[REVENANT-T] open->firstframe: "
                    + (System.currentTimeMillis() - openedAtMs) + "ms");
        }
        this.renderBackground(graphics);
        this.backdrop.build(this.width, this.height);
        this.backdrop.draw(graphics, heartbeat());

        BodyStateMessage state = BodyClientState.get();

        // **没跳井也画种子** —— 不该拿一行字把整棵树挡住。
        // 没有状态就按"未觉醒"处理：stage = -1（种子），没有槽位、没有叶子，
        // 所以下面的交互自然什么都点不到。
        int stage = state == null ? -1 : state.stage();
        int[] upgrades = state == null ? new int[24] : state.upgrades();

        // **地层先画**（静态那张，只要尺寸没变就是现成的）
        this.terrain.ensure(this.width, this.height, 0, 0, 0L, this.zoom);

        // 树的纹理（强化选择 / 槽位状态变了才重建）
        this.tree.setChoices(upgrades);
        this.tree.setSlotColors(slotColors(state));
        long stamp = java.util.Arrays.hashCode(upgrades) * 31L + (state == null ? 0L : focusStamp(state));
        this.tree.ensure(this.width, this.height, stage,
                BodyClientState.sequence(), stamp, this.zoom);

        float grow = this.growth();
        // **种子长成树苗**：只有真在播生长动画、而且已经觉醒了才这么演。
        // 未觉醒时 tree 自己就是种子，不用叠。
        // 第一次进来把视口对准树（水平居中、从内容图顶部往下一点）
        if (!this.viewInit && this.tree.contentWidth() > 0) {
            this.viewX = Math.max(0, (this.tree.contentWidth() - this.width) / 2);
            // **对准"地面"** —— 种子和树苗长在内容图 0.42 那条线上（土壤表面）。
            // 之前这里写 0.04（盯在树冠上），而视口高度只有内容高的 0.385 倍，
            // 于是种子正好落在视口下边缘之外 —— 一直"看不到种子和树苗"就是这个。
            // 内容图变高之后，地面线在 0.50（土壤顶）—— 让视口对着"地面上一点"
            // ----------------------------------------------------------------
            //  **打开时要同时看见：夜空 + 树 + 土壤**（玩家说的"最起码"）。
            //
            //  内容图布局固定：
            //      0.00~0.30 星空 │ 0.30~0.50 夜空+树 │ 0.50~0.66 土壤
            //  这三样要占 0.30 ~ 0.66 = 0.36 的内容高度，
            //  而视口高度 = 1 / (CONTENT_H × zoom)：
            //      zoom = 1.00 → 0.294（装不下，土壤根本看不见 ← 之前的问题）
            //      zoom = 0.80 → 0.368（刚好装下 ✓）
            //  所以默认 zoom 取 0.80，视口下移到让"土壤顶"落在画面下方 62% 处 ——
            //  打开就是从上到下：夜空的边、树、土壤。
            //  想凑近看别的（树冠 / 星空 / 血肉土壤）自己滚轮 + 拖。
            // ----------------------------------------------------------------
            // **zoom 保持 1.0** —— 东西的大小不变（之前压到 0.80 是"缩小画面"，
            // 那正是"我让你扩大，为什么看着更小"的原因）。
            // "扩大"由内容图倍数负责，不由 zoom 负责。
            // ----------------------------------------------------------------
            //  **打开时对着"树 + 土壤"** —— 这两样是玩家最该先看到的。
            //
            //  内容图的布局（和 TreeTexture 里的分区一致）：
            //      0.14 ~ 0.34  星空
            //      0.34 ~ 0.46  夜空 + 树     ← 树在这
            //      0.46 ~ 0.60  土壤
            //
            //  所以视口要"以 0.44 为中心"（树和土壤的交界处），
            //  而不是像之前那样从 0.216 往下排 —— 那样视口到 0.449 就没了，
            //  树只露上半截、土壤整个在画面外，看着就是"偏了"。
            //
            //  同时 zoom 取 0.80（不是 1.0）：视口占 29.1% 的内容高，
            //  刚好把"树（0.34~0.46）+ 土壤（0.46~0.60）"一起装进来。
            //  1.0 只能装 23.3%，装不下这两样 —— 那就是"缩放放太大"。
            // ----------------------------------------------------------------
            this.zoom = 0.80F;
            float vis = 1.0F / (com.habaiwa.goetyrevenant.client.screen.TreeTexture.CONTENT_H
                    * this.zoom);
            final float focus = 0.44F;      // 想要它落在画面正中
            this.viewY = Math.max(0, (int) (this.tree.contentHeight()
                    * (focus - vis * 0.5F)));
            this.viewInit = true;
        }
        // 播之前先报一声（临时诊断：看不到这句话就说明没进这个分支）
        if (this.playGrowth && !this.growthAnnounced) {
            this.growthAnnounced = true;
            long el = System.currentTimeMillis() - this.openedAt;
            if (el < 400L && this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("[回魂] 生长动画开始（5 秒）"), false);
            }
        }
        // **地层先画**（两张叠着：地层在下、树在上）。
        // 放在分支外面 —— 生长动画那一路同样需要它，否则树会浮在空白上。
        this.terrain.drawView(graphics, 1.0F, this.viewX, this.viewY);
        if (this.playGrowth && BodyClientState.stage() >= 0) {
            this.seed.ensure(this.width, this.height, -1, -1, -1L, this.zoom);
            this.seedOpen.ensure(this.width, this.height, -1, -1, -1L, this.zoom);
            // ------------------------------------------------------------------
            //  **种子破开、苗从缝里顶出来** —— 四段叠着演：
            //
            //    0.00~0.14  一粒完整的种子（让人看清它是什么）
            //    0.14~0.36  种皮【裂开】：完整那张淡出、裂开那张淡入
            //    0.32~0.78  苗从那道缝里【往上顶出来】（从下往上裁剪）
            //    0.78~1.00  种壳【脱落】：裂开那张淡出、苗挺直
            //
            //  之前是"种子淡出 + 幼苗淡入"—— 那只是两张图交叉溶解，
            //  没有"裂"这个动作，所以看着不像破开。
            // （旧的"种子淡出 + 幼苗淡入"那两行删掉了 —— 它的效果是交叉溶解，
            //  没有"裂"的动作。现在由下面三张叠出破开的过程。）
            float open = Math.max(0.0F, Math.min(1.0F, (grow - 0.14F) / 0.22F));
            float sprout = Math.max(0.0F, Math.min(1.0F, (grow - 0.32F) / 0.46F));
            float shed = Math.max(0.0F, Math.min(1.0F, (grow - 0.78F) / 0.22F));
            // 完整种子：裂开时淡出、脱壳时再淡一次
            this.seed.drawView(graphics, 1.0F,
                    (1.0F - open) * (1.0F - shed * 0.5F), this.viewX, this.viewY);
            // 裂开的种子：裂开时淡入、脱壳时淡出
            this.seedOpen.drawView(graphics, 1.0F, open * (1.0F - shed), this.viewX, this.viewY);
            this.tree.drawView(graphics, sprout, sprout,
                    this.viewX, this.viewY);
            // ------------------------------------------------------------------
            //  **动画演完就把两张种子图放掉。**
            //
            //  种子图只在两个地方需要：未觉醒时一直看着的那粒种子，
            //  和觉醒动画里那几秒的破开过程。动画一旦演完，它们就是两份
            //  白白占着的全尺寸 RGBA 图（一张就是 32 MiB 量级）。
            //
            //  放掉是安全的：release() 会把尺寸也清掉，下次真要用到会自然重建。
            //  注意条件是"动画已经走完"（grow >= 1）而且界面确实是在播动画，
            //  不是"未觉醒"那种要一直显示种子的情况。
            // ------------------------------------------------------------------
            if (grow >= 1.0F && !this.seedReleased) {
                this.seedReleased = true;
                this.seed.release();
                this.seedOpen.release();
                LOG.info("[REVENANT-T] 生长动画结束，已释放两张种子图");
            }
        } else {
            // 平时就按视口画 —— 屏幕只是这张大图的一个窗口
            this.tree.drawView(graphics, 1.0F, this.viewX, this.viewY);
        }
        if (frameCount < 20) {
            frameCount++;
            LOG.info("[REVENANT-T] 帧 " + frameCount + " "
                    + ((System.nanoTime() - frameT) / 1_000_000L) + "ms"
                    + (frameCount == 20 ? "  （后面不再打）" : ""));
        }

        this.drawRipples(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, SOUL_BRIGHT);
        // 序列的具体设定还没定下来 —— 界面先不显示任何"阶"，
        // 免得把一个还没设计完的东西当成既成事实摆给玩家看
        graphics.drawCenteredString(this.font,
                Component.literal(sequenceTitle()), this.width / 2, 27, sequenceColor());
        // 血肉线那行：和序列名并列（这是状态，不是提示文字）
        int fleshLineId = BodyClientState.fleshLine();
        if (fleshLineId >= 0) {
            com.habaiwa.goetyrevenant.common.flesh.FleshLine fl =
                    com.habaiwa.goetyrevenant.common.flesh.FleshLine.byIndex(fleshLineId);
            if (fl != null) {
                int fs = BodyClientState.fleshStage();
                com.habaiwa.goetyrevenant.client.fx.TextFx.glowCentered(graphics, this.font,
                        Component.literal(fl.cn() + " · " + fl.stepName(fs)), this.width / 2, 40,
                        fl.color(), fl.color(), 0.6F);
            }
        }
        // **当空之宫** —— 和 Goety: Astrology 联动的那一行。
        // 没装那个模组就不显示，界面照常。
        String sign = AstrologyCompat.signName(this.minecraft == null ? null : this.minecraft.level);
        if (sign != null && !sign.isEmpty()) {
            com.habaiwa.goetyrevenant.client.fx.TextFx.glowCentered(graphics, this.font,
                    Component.literal("当空：" + sign), this.width / 2, 52,
                    0xFFCFE8FF, 0xFFCFE8FF, 0.35F);
        }
        // **诊断行（临时）** —— 一眼看清：背景好了没、视口在哪、星空在不在视野里。
        // 看到这行字之后就该知道自己缺的是哪一环，不用再来回猜。
        {
            float vis = 1.0F / (com.habaiwa.goetyrevenant.client.screen.TreeTexture.CONTENT_H
                    * Math.max(0.30F, this.zoom));
            float top = this.viewY / (float) Math.max(1, this.tree.contentHeight());
            float bottom = top + vis;
            graphics.drawString(this.font, Component.literal(
                    this.terrain.status("地层") + "  │  " + this.tree.status("树")
                            + "  │  " + this.backdrop.status()
                            + "  │  视口 " + String.format("%.2f", top)
                            + "~" + String.format("%.2f", bottom)
                            + "  │  zoom " + String.format("%.2f", this.zoom)
                            + "  │  " + this.tree.inkReport()),
                    10, this.height - 12, 0xFF88A0A8, false);
        }
        graphics.drawString(this.font,
                Component.literal("嵌入位 " + this.tree.slotCount()), 10, 10, SOUL_BRIGHT, false);
        graphics.drawString(this.font,
                Component.literal("强化叶 " + this.tree.leafCount()), 10, 22, GOLD, false);

        // **当空之宫的星线** —— 按**当天星象**排布在星空区里（不是随机撒点）。
        // 星点位置来自 goety_astrology 的 nodes(...)，所以它和那边望远镜里看到的是同一座。
        this.drawSign(graphics);

        // **血肉线**：土壤之下那些根节点按"走到第几阶"亮起来。
        // 不用文字 —— 亮几个点就是第几阶。
        int fleshLine = BodyClientState.fleshLine();
        if (fleshLine >= 0) {
            com.habaiwa.goetyrevenant.common.flesh.FleshLine line =
                    com.habaiwa.goetyrevenant.common.flesh.FleshLine.byIndex(fleshLine);
            int fleshStage = BodyClientState.fleshStage();
            int myWay = line.ordinal();          // 玩家走的是哪一条（0/1/2）
            // **三条路都画出来** —— 另外两条是"没走的路"，一直暗着。
            // 只有玩家那条会随阶数亮起来，走到第几阶就亮几个节点。
            for (int way = 0; way < this.tree.wayCount(); way++) {
                boolean mine = way == myWay;
                for (int st = 0; st < this.tree.wayStageCount(); st++) {
                    int[] n = this.tree.wayNodeAt(way, st);
                    if (n == null) {
                        continue;
                    }
                    int sx = n[0] - this.viewX;
                    int sy = n[1] - this.viewY;
                    if (sx < -24 || sy < -24 || sx > this.width + 24 || sy > this.height + 24) {
                        continue;   // 不在视口里
                    }
                    boolean lit = mine && st < fleshStage;
                    int rgb = (lit ? line.color() : (mine ? 0xFF4A2A30 : 0xFF2A181C)) & 0xFFFFFF;
                    if (lit) {
                        com.habaiwa.goetyrevenant.client.fx.GlowRenderer.halo(graphics,
                                sx, sy, 11.0F, 9.0F, rgb, 3, 100);
                    }
                    int r = lit ? 2 : 1;
                    graphics.fill(sx - r, sy - r, sx + r + 1, sy + r + 1,
                            (lit ? 0xFF : 0x88) << 24 | rgb);
                }
            }
        }

        // 交互：年轮槽 + 叶子
        this.hoveredSlot = -1;
        this.hoveredLeaf = -1;
        if (grow >= 0.55F) {
            this.hoveredSlot = this.pickSlot(mouseX, mouseY);
            if (this.hoveredSlot >= 0) {
                this.highlight(graphics, this.tree.slotAt(this.hoveredSlot), 13);
            } else {
                this.hoveredLeaf = this.tree.leafNear(mouseX + this.viewX, mouseY + this.viewY);
                if (this.hoveredLeaf >= 0) {
                    this.highlight(graphics, this.tree.leafAt(this.hoveredLeaf), 9);
                }
            }
        }

        // 槽里的聚晶（物品图标只能叠在纹理上面）
        for (int i = 0; i < this.tree.slotCount(); i++) {
            ItemStack focus = this.focusAt(state, i);
            int[] p = this.tree.slotAt(i);
            if (!focus.isEmpty() && p != null) {
                graphics.renderItem(focus, p[0] - 8, p[1] - 8);
            }
        }

        if (this.hoveredSlot >= 0) {
            this.slotTooltip(graphics, this.hoveredSlot, state, mouseX, mouseY);
        } else if (this.hoveredLeaf >= 0) {
            this.leafTooltip(graphics, this.hoveredLeaf, state, mouseX, mouseY);
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("screen.goety_revenant.tree.legend"),
                this.width / 2, this.height - 20, BONE_DIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * **把当空那一座画到星空上**。
     *
     * <p>星点从 {@code Constellations.nodes(...)} 来（世界坐标里的星位），
     * 这里把它归一化投到界面的星空区，连成星线 —— 于是魂树界面里的星空
     * 和 Goety: Astrology 望远镜里看到的是<b>同一座</b>。</p>
     *
     * <p>画法和背景里那些烤好的星一致：同色系、细点、不加光斑，
     * 只有一点点呼吸 —— 这样它看着就是那片星空的一部分。</p>
     */
    private void drawSign(GuiGraphics graphics) {
        net.minecraft.world.level.Level level = this.minecraft == null ? null : this.minecraft.level;
        net.minecraft.world.phys.Vec3[] nodes = AstrologyCompat.signNodes(level);
        if (nodes == null || nodes.length < 2 || this.tree.contentHeight() <= 0) {
            return;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (net.minecraft.world.phys.Vec3 v : nodes) {
            minX = Math.min(minX, v.x);
            maxX = Math.max(maxX, v.x);
            minZ = Math.min(minZ, v.z);
            maxZ = Math.max(maxZ, v.z);
        }
        double spanX = Math.max(1.0E-4D, maxX - minX);
        double spanZ = Math.max(1.0E-4D, maxZ - minZ);
        // **星座在【内容图】上占的那一块** —— 用内容坐标定，不碰屏幕坐标。
        // 再逐个点用 screenX/screenY 投影，于是它和背景的变换完全一致：
        // 拖视图它跟着走、缩放它跟着缩，因为它本来就是"那片天上的东西"。
        // **星座铺在星空区里**（0.00 ~ 0.24 那一段）—— 那是"往上看"才到的地方。
        // 它跟着星象走：不同的一座星点位置不同，而且随时间缓慢移动
        // （位置来自 Constellations.nodes(世界时间, 座)）。
        float areaCx = this.tree.contentWidth() * 0.5F;
        float areaCy = this.tree.contentHeight() * 0.12F;
        float areaW = this.tree.contentWidth() * 0.80F;
        float areaH = this.tree.contentHeight() * 0.26F;
        float cx0 = areaCx - areaW / 2.0F;
        float cy0 = areaCy - areaH / 2.0F;

        long now = System.currentTimeMillis();
        int pxPrev = Integer.MIN_VALUE;
        int pyPrev = 0;
        for (int i = 0; i < nodes.length; i++) {
            // 先算它在内容图上的位置，再投影到屏幕
            float contentX = cx0 + (float) ((nodes[i].x - minX) / spanX) * areaW;
            float contentY = cy0 + (float) ((nodes[i].z - minZ) / spanZ) * areaH;
            int px = (int) this.screenX(contentX);
            int py = (int) this.screenY(contentY);
            if (pxPrev != Integer.MIN_VALUE) {
                // 星线：连到【上一个点】。
                // 之前这里连的是第一个点（每次都从 i=0 拉过来），
                // 于是整座星座被画成一朵星芒 —— 难怪"连接线"看不出是连接线。
                // 而且颜色太淡（alpha 38），基本看不见；现在加到 96。
                for (int s = 1; s < 8; s++) {
                    int lx = pxPrev + (px - pxPrev) * s / 8;
                    int ly = pyPrev + (py - pyPrev) * s / 8;
                    graphics.fill(lx, ly, lx + 1, ly + 1, 0x60CFE8FF);
                }
            }
            // 星点：呼吸（和背景星同量级）
            float blink = (float) (Math.sin(now / (1500.0D + i * 210.0D) + i) * 0.5D + 0.5D);
            int a = 90 + (int) (blink * 70);
            int c = SoulBackdrop.signColor(i);
            graphics.fill(px, py, px + 2, py + 2, (a << 24) | (c & 0xFFFFFF));
            graphics.fill(px - 1, py - 1, px + 3, py + 3, ((a / 5) << 24) | (c & 0xFFFFFF));
            pxPrev = px;
            pyPrev = py;
        }
    }

    /**
     * **视口能走到哪** —— 放大时能看的范围更小（因为视口覆盖的那块内容变小了）。
     *
     * <p>不做这个限制的话，放大之后能一直往空白处拖。</p>
     */
    /**
     * **内容图坐标 → 屏幕坐标**。
     *
     * <p>和 {@code TreeTexture.drawView} 用的是<b>同一套变换</b>：
     * 视口中心在屏幕上永远是屏幕中心，缩放倍率就是 zoom。
     * 画在内容图上的东西（比如星座）用它换算，就一定和背景同步 ——
     * 拖视图时它跟着走，而不是"浮在屏幕上的一块贴图"。</p>
     */
    /**
     * **背景这一帧实际取的源区域** —— 和 {@code TreeTexture.drawView} 里的算法一模一样。
     *
     * <p>之前我在 screenX 里自己重算了一遍"视口中心 ± 半个源区"，
     * 但漏了 drawView 里那个 <b>clamp</b> —— 视口靠近内容图边缘时，
     * 它的源区左上角会被夹住，不再等于 (中心 - 源宽/2)。
     * 于是靠近边缘时星座和背景就错开了，看着就像"星象能单独拖"。</p>
     *
     * <p>现在不重算了：**直接把背景用的那一组数拿过来**。
     * 两边同一组数，从数学上不可能不同步。</p>
     *
     * @return {vx, vy, srcW, srcH}
     */
    private int[] bgSource() {
        float z = Math.max(0.30F, Math.min(3.0F, this.zoom));
        int srcW = Math.max(32, (int) (this.width / z));
        int srcH = Math.max(32, (int) (this.height / z));
        int centerX = this.viewX + this.width / 2;
        int centerY = this.viewY + this.height / 2;
        int vx = Math.max(0, Math.min(Math.max(0, this.tree.contentWidth() - srcW),
                centerX - srcW / 2));
        int vy = Math.max(0, Math.min(Math.max(0, this.tree.contentHeight() - srcH),
                centerY - srcH / 2));
        return new int[]{vx, vy, srcW, srcH};
    }

    /** 内容图坐标 → 屏幕坐标（用背景那一组数，一定同步）。 */
    private float screenX(float contentX) {
        int[] s = this.bgSource();
        return (contentX - s[0]) * (this.width / (float) s[2]);
    }

    private float screenY(float contentY) {
        int[] s = this.bgSource();
        return (contentY - s[1]) * (this.height / (float) s[3]);
    }

    private void clampView() {
        float z = Math.max(0.30F, Math.min(3.0F, this.zoom));
        int srcW = Math.max(32, (int) (this.width / z));
        int srcH = Math.max(32, (int) (this.height / z));
        this.viewX = Math.max(0, Math.min(Math.max(0, this.tree.contentWidth() - srcW), this.viewX));
        this.viewY = Math.max(0, Math.min(Math.max(0, this.tree.contentHeight() - srcH), this.viewY));
    }

    private long focusStamp(BodyStateMessage state) {
        long s = 0;
        for (BodyStateMessage.Entry e : state == null ? new BodyStateMessage.Entry[0] : state.entries()) {
            s = s * 2 + (e.hasFocus() ? 1 : 0);
        }
        return s;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.dragX >= 0.0D) {
            // 动过 3 像素才算"拖"，否则还是当成点一下
            if (Math.abs(mouseX - this.dragX) > 3.0D || Math.abs(mouseY - this.dragY) > 3.0D) {
                this.dragMoved = true;
            }
            // 往右拖 = 图往右走 = 视口往左移
            this.viewX -= (int) (mouseX - this.dragX);
            this.viewY -= (int) (mouseY - this.dragY);
            this.clampView();
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragX = -1.0D;
        this.dragY = -1.0D;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int pickSlot(double mouseX, double mouseY) {
        int r = (int) Math.max(10.0F, 13.0F * this.zoom);
        // **屏幕坐标 → 内容图坐标**：槽位是记在内容图上的，比的时候要加视口偏移
        double wx = mouseX + this.viewX;
        double wy = mouseY + this.viewY;
        for (int i = 0; i < this.tree.slotCount(); i++) {
            int[] p = this.tree.slotAt(i);
            if (p != null && Math.abs(wx - p[0]) <= r && Math.abs(wy - p[1]) <= r) {
                return i;
            }
        }
        return -1;
    }

    private void highlight(GuiGraphics graphics, int[] p, int r) {
        if (p == null) {
            return;
        }
        // 内容图坐标 → 屏幕坐标：减掉视口偏移
        graphics.renderOutline(p[0] - this.viewX - r, p[1] - this.viewY - r,
                r * 2, r * 2, 0xAAFFFFFF);
    }

    private void drawRipples(GuiGraphics graphics) {
        long now = System.currentTimeMillis();
        this.ripples.removeIf(r -> now - r[2] > RIPPLE_MS);
        for (long[] r : this.ripples) {
            float t = (now - r[2]) / (float) RIPPLE_MS;
            int rad = (int) (5 + t * 44);
            int alpha = (int) ((1.0F - t) * 0xAA);
            int color = (Math.max(0, alpha) << 24) | (SOUL_BRIGHT & 0x00FFFFFF);
            int steps = 36;
            for (int i = 0; i < steps; i++) {
                double a = Math.PI * 2 * i / steps;
                int x = (int) (r[0] + Math.cos(a) * rad);
                int y = (int) (r[1] + Math.sin(a) * rad);
                graphics.fill(x - 1, y, x + 1, y + 1, color);
            }
        }
    }

    private ItemStack focusAt(BodyStateMessage state, int index) {
        com.habaiwa.goetyrevenant.common.body.OrganType organ = SoulTree.organAt(index);
        if (state == null || organ == null) {
            return ItemStack.EMPTY;
        }
        for (BodyStateMessage.Entry entry : state.entries()) {
            if (entry.organ().equals(organ.getId())) {
                return entry.hasFocus() ? entry.focus() : ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    private void slotTooltip(GuiGraphics graphics, int index, BodyStateMessage state,
                             int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("主干 · 年轮 " + (index + 1)).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("嵌入位（打连携）").withStyle(ChatFormatting.AQUA));
        ItemStack focus = this.focusAt(state, index);
        lines.add(focus.isEmpty()
                ? Component.literal("空 —— 点它嵌一枚聚晶")
                : Component.literal("叶：" + focus.getHoverName().getString()));
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    private void leafTooltip(GuiGraphics graphics, int index, BodyStateMessage state,
                             int mouseX, int mouseY) {
        int[] chosen = state == null ? new int[24] : state.upgrades();
        int choice = index < chosen.length ? chosen[index] : 0;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("枝 · 叶子 " + (index + 1)).withStyle(ChatFormatting.GOLD));
        if (choice == 0) {
            lines.add(Component.literal("枯叶 —— 点它选一个方向").withStyle(ChatFormatting.AQUA));
        } else {
            com.habaiwa.goetyrevenant.common.body.UpgradeType.Type[] group =
                    com.habaiwa.goetyrevenant.common.body.UpgradeSlots.candidates(index);
            if (choice <= group.length) {
                com.habaiwa.goetyrevenant.common.body.UpgradeType.Type type = group[choice - 1];
                lines.add(Component.literal(type.group().cn() + "族 · " + type.cn())
                        .withStyle(ChatFormatting.AQUA));
                lines.add(Component.literal(type.gain()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal(type.cost()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /**
     * **六个嵌入位各嵌了什么颜色的聚晶**（0 = 空）。
     *
     * <p>颜色取自聚晶的派系（元素）—— 树干上的年轮会亮成那个颜色，
     * 于是"哪里嵌了什么"不用文字也看得出来。</p>
     */
    private static int[] slotColors(BodyStateMessage state) {
        int[] out = new int[6];
        if (state == null) {
            return out;
        }
        BodyStateMessage.Entry[] entries = state.entries();
        for (int i = 0; i < out.length && i < entries.length; i++) {
            out[i] = focusColor(entries[i].focus());
        }
        return out;
    }

    /** 一个聚晶的代表色（按它的派系取）。 */
    private static int focusColor(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof com.Polarice3.Goety.api.items.magic.IFocus focus)) {
            return 0;
        }
        com.Polarice3.Goety.api.magic.ISpell spell = focus.getSpell();
        if (spell == null || spell.getSpellType() == null) {
            return 0;
        }
        // 派系数固定，按序号取一组对比明显的颜色就够区分了
        int[] palette = {0xFFE06030, 0xFF70C8FF, 0xFFE8C040, 0xFF60C060,
                0xFF52E8DC, 0xFFA060E0, 0xFFFF70A0};
        return palette[Math.abs(spell.getSpellType().ordinal()) % palette.length];
    }

    /** 顶上那行字：觉醒了就写序列名，没觉醒写"序列未定"。 */
    private static String sequenceTitle() {
        com.habaiwa.goetyrevenant.common.sequence.Sequence seq =
                com.habaiwa.goetyrevenant.common.sequence.Sequence.byIndex(BodyClientState.sequence());
        return seq == null ? "序列未定" : seq.cn();
    }

    /** 顶上那行字的颜色：跟着序列走。 */
    private static int sequenceColor() {
        com.habaiwa.goetyrevenant.common.sequence.Sequence seq =
                com.habaiwa.goetyrevenant.common.sequence.Sequence.byIndex(BodyClientState.sequence());
        return seq == null ? 0xFF6A6A60 : seq.color();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
