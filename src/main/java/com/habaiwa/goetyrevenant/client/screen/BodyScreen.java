package com.habaiwa.goetyrevenant.client.screen;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.magic.ISpell;
import com.habaiwa.goetyrevenant.client.BodyClientState;
import com.habaiwa.goetyrevenant.common.body.BodyLayout;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import com.habaiwa.goetyrevenant.common.network.BodyActionMessage;
import com.habaiwa.goetyrevenant.common.network.BodyStateMessage;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * **七窍**界面 —— 血腥 / 黑暗风格。
 *
 * <h3>视觉语言</h3>
 * <ul>
 *   <li><b>剥了皮的躯壳</b>：暗红血肉，边缘更深，靠一层层半透明横条堆出体积</li>
 *   <li><b>七窍 = 七个伤口</b>：外圈是**结痂**的深红环，里面是**血肉**，中心一口**黑洞**</li>
 *   <li><b>已开窍</b>：伤口中心有颗随心跳搏动的光</li>
 *   <li><b>已嵌入</b>：聚晶就嵌在伤口里（原版物品图标直接画上去）</li>
 *   <li><b>未开窍</b>：伤口缝着线（黑底 + 交叉缝线）</li>
 *   <li><b>鼠标停上去</b>：伤口开始渗血，往下滴</li>
 * </ul>
 *
 * <p>伤口轮廓用**按角度扰动半径**画，所以每个窍的形状都不一样、且不会随帧抖动
 * （扰动值在类加载时按窍的下标算好，是常量）。</p>
 */
public class BodyScreen extends Screen {
    // ===== 血腥配色 =====
    private static final int VOID = 0xF00A0406;
    private static final int VIGNETTE = 0x60100000;
    private static final int FLESH = 0xFF6B1E22;
    private static final int FLESH_DARK = 0xFF3A0D10;
    private static final int FLESH_LIGHT = 0xFF8A2A2E;
    private static final int SCAB = 0xFF2A0508;
    private static final int BLOOD = 0xFFB01B22;
    private static final int BLOOD_BRIGHT = 0xFFE23A3A;
    private static final int BONE = 0xFFE8DED2;
    private static final int BONE_DIM = 0xFF8A7A70;
    private static final int OPEN_GLOW = 0xFFFF6B5A;
    private static final int STITCH = 0xFF6A5A52;
    private static final int CELL = 20;
    /** 每 1/4 秒的心跳相位（0~1）。 */
    private static final long HEARTBEAT_MS = 900L;

    /** 每个窍的伤口轮廓扰动（按角度，8 个采样点），类加载时按窍下标固定下来。 */
    private static final float[][] WOUND = new float[OrganType.values().length][12];

    /** 血肉背景（自带按尺寸缓存，见 FleshBackdrop）。 */
    private final FleshBackdrop backdrop = new FleshBackdrop();

    /** 本帧的心跳相位 —— 一帧只算一次，见 render()。 */
    private float beat;

    /** 那具人体（按解剖图画，自带缓存，见 BodyFigure）。 */
    private final BodyFigure figure = new BodyFigure();

    /** 伤口形状的分段表缓存（每帧重算太贵 —— 见 woundSpans）。 */
    private final int[][][] spanCache = new int[OrganType.values().length][][];
    private final int[] spanCacheR = new int[OrganType.values().length];

    static {
        for (int i = 0; i < WOUND.length; i++) {
            for (int a = 0; a < WOUND[i].length; a++) {
                // 用下标做种子：同一个窍每次都是同一种"撕裂"形状，且各窍不同
                int seed = i * 31 + a * 17;
                WOUND[i][a] = 0.86F + ((seed * 2654435761L >>> 8) % 1000) / 1000.0F * 0.34F;
            }
        }
    }

    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private float scale;
    private int gridLeft;
    private int gridTop;
    private int refreshCooldown = 10;

    public BodyScreen() {
        super(Component.translatable("screen.goety_revenant.body"));
    }

    @Override
    protected void init() {
        this.scale = Math.min((this.height - 60.0F) / BodyLayout.PANEL_HEIGHT, 1.7F);
        this.panelW = (int) (BodyLayout.PANEL_WIDTH * this.scale);
        this.panelH = (int) (BodyLayout.PANEL_HEIGHT * this.scale);
        this.panelLeft = this.width / 2 - this.panelW - 24;
        this.panelTop = this.height / 2 - this.panelH / 2;
        this.gridLeft = this.panelLeft + this.panelW + 30;
        this.gridTop = this.panelTop + 26;
        RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(BodyActionMessage.OPEN, ""));
    }

    /** 每 10 tick 拉一次状态（灵魂能量和嵌入进度跟着动）。 */
    @Override
    public void tick() {
        if (--this.refreshCooldown > 0) {
            return;
        }
        this.refreshCooldown = 10;
        RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(BodyActionMessage.OPEN, ""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        // 整块"皮"：一层层血肉 + 跟着心跳搏动的血管（只在窗口尺寸变化时重算）
        this.backdrop.draw(graphics, this.width, this.height, heartbeat());

        BodyStateMessage state = BodyClientState.get();
        // 标题：血红
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.panelTop - 26, BLOOD_BRIGHT);

        if (state == null) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.goety_revenant.body.loading"),
                    this.width / 2, this.height / 2, BONE_DIM);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        if (!state.unlocked()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.goety_revenant.body.locked"),
                    this.width / 2, this.height / 2, BLOOD);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        drawFleshSlab(graphics, this.panelLeft - 10, this.panelTop - 10,
                this.panelLeft + this.panelW + 10, this.panelTop + this.panelH + 10);
        // 人体轮廓 (BodyFigure) + 器官 (renderWound 用 OrganShapes 画) —— 两个都要。
        // 人体压暗一层，好让器官从肉里浮出来。
        this.figure.draw(graphics, this.panelLeft, this.panelTop, this.scale);
        // 心跳相位一帧只算一次。
        this.beat = heartbeat();
        for (BodyLayout.Spot spot : BodyLayout.SPOTS) {
            renderWound(graphics, spot, mouseX, mouseY);
        }
        renderFocusPanel(graphics, mouseX, mouseY, state);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // ===== 背景与躯壳 =====

    /** 四周压暗的血色暗角。 */
    private void renderVignette(GuiGraphics graphics) {
        int band = Math.max(24, this.width / 12);
        for (int i = 0; i < band; i += 2) {
            int a = (int) (0x60 * (1.0F - i / (float) band));
            int color = (a << 24) | 0x400000;
            graphics.fill(i, 0, i + 2, this.height, color);
            graphics.fill(this.width - i - 2, 0, this.width - i, this.height, color);
            graphics.fill(0, i, this.width, i + 2, color);
            graphics.fill(0, this.height - i - 2, this.width, this.height - i, color);
        }
    }

    /** 一块「肉」：底下是黑洞，横向一层层堆出暗红渐变。 */
    private void drawFleshSlab(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0, y0, x1, y1, VOID);
        int h = y1 - y0;
        for (int y = y0; y < y1; y++) {
            float t = (y - y0) / (float) h;
            // 中间亮、两头暗
            float v = 1.0F - Math.abs(t - 0.45F) * 1.6F;
            int color = lerpColor(FLESH_DARK, FLESH, Math.max(0.0F, Math.min(1.0F, v)));
            graphics.fill(x0 + 1, y, x1 - 1, y + 1, (0xE0 << 24) | (color & 0xFFFFFF));
        }
        frame(graphics, x0, y0, x1, y1, SCAB);
    }

    /** 剥皮躯壳：头 / 颈 / 胸 / 双臂 / 双腿。 */
    private void renderBody(GuiGraphics graphics) {
        int cx = panelLeft + panelW / 2;
        int top = panelTop;
        int s = Math.max(1, (int) this.scale);

        // 头
        fillFlesh(graphics, cx - 16 * s, top + 6 * s, cx + 16 * s, top + 38 * s, 0.0F);
        // 颈
        fillFlesh(graphics, cx - 6 * s, top + 38 * s, cx + 6 * s, top + 50 * s, 0.3F);
        // 躯干（上宽下窄）
        for (int y = 0; y < 84 * s; y++) {
            float t = y / (84.0F * s);
            int half = (int) ((34 - 8 * t) * s);
            graphics.fill(cx - half, top + 48 * s + y, cx + half, top + 48 * s + y + 1,
                    (0xE0 << 24) | (lerpColor(FLESH_DARK, FLESH, 0.55F - t * 0.25F) & 0xFFFFFF));
        }
        // 双臂
        fillFlesh(graphics, cx - 50 * s, top + 54 * s, cx - 34 * s, top + 120 * s, 0.25F);
        fillFlesh(graphics, cx + 34 * s, top + 54 * s, cx + 50 * s, top + 120 * s, 0.25F);
        // 双腿
        fillFlesh(graphics, cx - 26 * s, top + 130 * s, cx - 5 * s, top + 212 * s, 0.35F);
        fillFlesh(graphics, cx + 5 * s, top + 130 * s, cx + 26 * s, top + 212 * s, 0.35F);
    }

    /** 一块带纵向渐变的血肉。 */
    private void fillFlesh(GuiGraphics graphics, int x0, int y0, int x1, int y1, float darkness) {
        int h = Math.max(1, y1 - y0);
        for (int y = y0; y < y1; y++) {
            float t = (y - y0) / (float) h;
            float v = (1.0F - Math.abs(t - 0.4F) * 1.3F) * (1.0F - darkness * 0.6F);
            int color = lerpColor(FLESH_DARK, FLESH_LIGHT, Math.max(0.0F, Math.min(1.0F, v * 0.8F)));
            graphics.fill(x0, y, x1, y + 1, (0xE0 << 24) | (color & 0xFFFFFF));
        }
    }

    // ===== 七窍（伤口）=====

    private void renderWound(GuiGraphics graphics, BodyLayout.Spot spot, int mouseX, int mouseY) {
        OrganType organ = spot.organ();
        int cx = panelLeft + (int) (spot.x() * panelW);
        int cy = panelTop + (int) (spot.y() * panelH);
        int r = Math.max(6, (int) (spot.radius() * panelW));

        boolean locked = BodyClientState.isLocked(organ.getId());
        boolean opened = BodyClientState.isOpened(organ.getId());
        boolean hovered = within(cx, cy, r + 2, mouseX, mouseY);
        ItemStack focus = focusOf(organ);

        // 1) 结痂外环
        woundShape(graphics, organ, cx, cy, r, SCAB);
        // 2) 血肉
        woundShape(graphics, organ, cx, cy, (int) (r * 0.82F), locked ? 0xFF16090B : FLESH_DARK);
        // 3) 中心「窍口」—— 平滑的圆洞。**不要网格**：
        //    原来未开窍的地方画了横竖交叉的缝合线，交叉出来就是一张网。
        //    现在：未开窍 = 一层肉塞把洞塞死；开窍 = 一个黑孔。
        if (locked) {
            fillDisc(graphics, cx, cy, (int) (r * 0.62F), 0xFF0D0306);                         // 洞底
            fillDisc(graphics, cx, cy, (int) (r * 0.46F), 0xFF2A0508);                         // 肉塞
            ringDisc(graphics, cx, cy, (int) (r * 0.52F), (0x99 << 24) | (FLESH & 0xFFFFFF));  // 塞沿
            fillDisc(graphics, cx, cy, (int) (r * 0.20F), 0xFF16090B);                         // 塞心
        } else {
            fillDisc(graphics, cx, cy, (int) (r * 0.55F), 0xFF1A0507);                         // 黑洞
            ringDisc(graphics, cx, cy, (int) (r * 0.44F), (0x88 << 24) | (FLESH_DARK & 0xFFFFFF)); // 孔壁
        }

        // 4) 已开窍 —— 中心一颗随心跳搏动的光
        if (opened && !locked) {
            int glow = OPEN_GLOW;
            int gr = Math.max(2, (int) (r * (0.16F + 0.10F * this.beat)));
            fillDisc(graphics, cx, cy, gr, glow);
            ringDisc(graphics, cx, cy, gr + 2, (0x80 << 24) | (glow & 0xFFFFFF));
        }

        // 5) 嵌进去的聚晶
        if (!focus.isEmpty()) {
            graphics.renderItem(focus, cx - 8, cy - 8);
        }

        // 6) 悬停 → 渗血
        if (hovered) {
            ringDisc(graphics, cx, cy, r + 2, BLOOD_BRIGHT);
            bloodDrip(graphics, cx, cy + r, r);
            graphics.renderComponentTooltip(this.font, woundTooltip(organ, locked, opened, focus), mouseX, mouseY);
        }
    }

    /**
     * 按角度扰动画出的「撕裂状」圆 —— 比正圆像伤口。
     *
     * <h3>为什么要有缓存</h3>
     * <p>原来这里是<b>一个像素发一次 {@code fill()}</b>：半径 27 的伤口就是 (2·27+1)² ≈ 3000 次绘制调用，
     * 每个伤口画 4 层、一共 7 个窍 —— <b>一帧八万多次绘制调用</b>，界面当然卡。</p>
     *
     * <p>现在两件事：</p>
     * <ol>
     *   <li>每一行把连续的像素<b>合并成一段</b>再画 —— 一次 fill 画一整行</li>
     *   <li>形状<b>按半径缓存</b>。{@link #WOUND} 是静态的、不随时间变，半径也只听窗口大小，
     *       所以除了改窗口那一下，之后每帧直接照抄段表，连 sqrt / atan2 都不用算</li>
     * </ol>
     * <p>八万多次 → 四百次左右。</p>
     */
    private void woundShape(GuiGraphics graphics, OrganType organ, int cx, int cy, int r, int color) {
        for (int[] span : woundSpans(organ, r)) {
            graphics.fill(cx + span[1], cy + span[0], cx + span[2] + 1, cy + span[0] + 1, color);
        }
    }

    /** 伤口形状的分段表，每项是 {dy, x0, x1}。按 (窍, 半径) 缓存。 */
    private int[][] woundSpans(OrganType organ, int r) {
        int i = organ.ordinal();
        if (this.spanCacheR[i] == r && this.spanCache[i] != null) {
            return this.spanCache[i];
        }
        int[][] out = OrganShapes.spans(organ, r);
        this.spanCache[i] = out;
        this.spanCacheR[i] = r;
        return out;
    }

    /** 缝合线：未开窍的伤口是被"缝上"的。 */
    private void stitch(GuiGraphics graphics, int cx, int cy, int r) {
        for (int i = -r; i <= r; i += 5) {
            graphics.fill(cx - r, cy + i, cx + r, cy + i + 1, STITCH);
            graphics.fill(cx + i, cy - r, cx + i + 1, cy + r, STITCH);
        }
    }

    /** 往下滴的血。 */
    private void bloodDrip(GuiGraphics graphics, int cx, int cy, int r) {
        long t = System.currentTimeMillis() % 2000L;
        for (int i = 0; i < 3; i++) {
            int len = 4 + i * 3;
            int y = cy + (int) ((t / 40L + i * 7) % 18);
            graphics.fill(cx - 1 + i * 3, y, cx + 1 + i * 3, y + len, BLOOD);
            graphics.fill(cx - 1 + i * 3, y + len, cx + 2 + i * 3, y + len + 2, BLOOD_BRIGHT);
        }
    }

    private List<Component> woundTooltip(OrganType organ, boolean locked, boolean opened, ItemStack focus) {
        List<Component> lines = new ArrayList<>();
        lines.add(organ.displayName().copy().withStyle(ChatFormatting.DARK_RED));

        if (locked) {
            lines.add(Component.translatable("screen.goety_revenant.body.need_brew", organ.brewName())
                    .withStyle(ChatFormatting.RED));
            return lines;
        }
        int cost = BodyClientState.costOf(organ.getId());
        boolean enough = BodyClientState.soulEnergy() >= cost;
        lines.add(Component.translatable("screen.goety_revenant.body.cost", cost)
                .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (!opened) {
            lines.add(Component.translatable("screen.goety_revenant.body.cost_first")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        if (!focus.isEmpty()) {
            for (Map.Entry<com.habaiwa.goetyrevenant.common.body.FocusPassive.Category, Double> grant
                    : com.habaiwa.goetyrevenant.common.body.FocusPassive.grants(focus, organ)) {
                lines.add(Component.translatable("tooltip.goety_revenant.focus.line",
                                grant.getKey().displayName(), grant.getKey().format(grant.getValue()))
                        .withStyle(ChatFormatting.GOLD));
            }
            lines.add(Component.translatable("screen.goety_revenant.body.embedded", focus.getHoverName())
                    .withStyle(ChatFormatting.AQUA));
            lines.add(Component.translatable("screen.goety_revenant.body.click_extract")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable(opened
                    ? "screen.goety_revenant.body.opened"
                    : "screen.goety_revenant.body.unopened").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    // ===== 右侧聚晶与能量 =====

    private void renderFocusPanel(GuiGraphics graphics, int mouseX, int mouseY, BodyStateMessage state) {
        int energy = state.soulEnergy();
        int max = Math.max(1, state.energyMax());
        graphics.drawString(this.font, Component.translatable("screen.goety_revenant.body.soul_energy", energy),
                gridLeft, gridTop - 20, BLOOD_BRIGHT);

        // 能量条：血条
        int barW = 120;
        int filled = (int) (barW * Math.min(1.0F, energy / (float) max));
        graphics.fill(gridLeft, gridTop - 9, gridLeft + barW, gridTop - 4, 0xFF1A0507);
        if (filled > 0) {
            graphics.fill(gridLeft, gridTop - 9, gridLeft + filled, gridTop - 4, BLOOD);
        }
        frame(graphics, gridLeft, gridTop - 9, gridLeft + barW, gridTop - 4, SCAB);

        BodyStateMessage.FocusEntry[] focuses = state.focuses();
        graphics.drawString(this.font, Component.translatable("screen.goety_revenant.body.focuses"),
                gridLeft, gridTop + 4, BONE);

        for (int i = 0; i < focuses.length; i++) {
            int x = gridLeft + (i % 9) * CELL;
            int y = gridTop + 16 + (i / 9) * CELL;
            graphics.renderItem(focuses[i].stack(), x, y);
            if (within(x + 8, y + 8, 9, mouseX, mouseY)) {
                graphics.fill(x - 1, y - 1, x + 17, y + 17, 0x50FF0000);
                frame(graphics, x - 1, y - 1, x + 17, y + 17, BLOOD_BRIGHT);
                graphics.renderComponentTooltip(this.font, focusTooltip(focuses[i].stack()), mouseX, mouseY);
            }
        }

        if (focuses.length == 0) {
            graphics.drawString(this.font, Component.translatable("screen.goety_revenant.body.no_focus"),
                    gridLeft, gridTop + 20, BONE_DIM);
        }
    }

    private List<Component> focusTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        OrganType organ = organFor(stack);
        lines.add(Component.translatable("screen.goety_revenant.body.goes_to", organ.displayName())
                .withStyle(ChatFormatting.GRAY));
        if (BodyClientState.isLocked(organ.getId())) {
            lines.add(Component.translatable("screen.goety_revenant.body.need_brew", organ.brewName())
                    .withStyle(ChatFormatting.RED));
            return lines;
        }
        int cost = BodyClientState.costOf(organ.getId());
        lines.add(Component.translatable("screen.goety_revenant.body.cost", cost)
                .withStyle(BodyClientState.soulEnergy() >= cost ? ChatFormatting.GREEN : ChatFormatting.RED));
        for (Map.Entry<com.habaiwa.goetyrevenant.common.body.FocusPassive.Category, Double> grant
                : com.habaiwa.goetyrevenant.common.body.FocusPassive.grants(stack, organ)) {
            lines.add(Component.translatable("tooltip.goety_revenant.focus.line",
                            grant.getKey().displayName(), grant.getKey().format(grant.getValue()))
                    .withStyle(ChatFormatting.GOLD));
        }
        return lines;
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        BodyStateMessage state = BodyClientState.get();
        if (state != null && state.unlocked()) {
            BodyStateMessage.FocusEntry[] focuses = state.focuses();
            for (int i = 0; i < focuses.length; i++) {
                int x = gridLeft + (i % 9) * CELL;
                int y = gridTop + 16 + (i / 9) * CELL;
                if (within(x + 8, y + 8, 9, (int) mouseX, (int) mouseY)) {
                    OrganType organ = organFor(focuses[i].stack());
                    RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(
                            BodyActionMessage.EMBED, organ.getId(), focuses[i].slot()));
                    return true;
                }
            }
        }
        for (BodyLayout.Spot spot : BodyLayout.SPOTS) {
            int cx = panelLeft + (int) (spot.x() * panelW);
            int cy = panelTop + (int) (spot.y() * panelH);
            int r = Math.max(6, (int) (spot.radius() * panelW));
            if (within(cx, cy, r + 2, (int) mouseX, (int) mouseY)) {
                if (!focusOf(spot.organ()).isEmpty()) {
                    RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(
                            BodyActionMessage.EXTRACT, spot.organ().getId()));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ===== 小工具 =====

    private static OrganType organFor(ItemStack stack) {
        if (stack.getItem() instanceof IFocus focus) {
            ISpell spell = focus.getSpell();
            if (spell != null) {
                return OrganType.forSpellType(spell.getSpellType());
            }
        }
        return OrganType.SPIRIT_APERTURE;
    }

    private static ItemStack focusOf(OrganType organ) {
        BodyStateMessage state = BodyClientState.get();
        if (state == null) {
            return ItemStack.EMPTY;
        }
        for (BodyStateMessage.Entry entry : state.entries()) {
            if (entry.organ().equals(organ.getId())) {
                return entry.focus();
            }
        }
        return ItemStack.EMPTY;
    }

    /** 心跳相位 0~1（两次搏动一循环）。 */
    private static float heartbeat() {
        long t = System.currentTimeMillis() % HEARTBEAT_MS;
        float p = t / (float) HEARTBEAT_MS;
        return (float) Math.max(0.0D, Math.sin(p * Math.PI * 2.0D) * 0.7D + 0.3D);
    }

    private static boolean within(int cx, int cy, int r, int mx, int my) {
        int dx = mx - cx;
        int dy = my - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static void fillDisc(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    private static void ringDisc(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int inner = (int) Math.sqrt(Math.max(0, (r - 2) * (r - 2) - dy * dy));
            if (outer > inner) {
                graphics.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
                graphics.fill(cx + inner, cy + dy, cx + outer, cy + dy + 1, color);
            }
        }
    }

    private static void frame(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
