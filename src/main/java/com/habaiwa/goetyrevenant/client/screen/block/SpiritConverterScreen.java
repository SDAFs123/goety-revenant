package com.habaiwa.goetyrevenant.client.screen.block;

import com.habaiwa.goetyrevenant.common.block.SpiritConverterBlockEntity;
import com.habaiwa.goetyrevenant.common.compat.MalumCompat;
import com.habaiwa.goetyrevenant.common.network.ConverterQueryMessage;
import com.habaiwa.goetyrevenant.common.network.ConverterSetMessage;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 精魄转化台的界面。
 *
 * <h3>勾选式多选</h3>
 * <p>九种精魄**各自一个格子，点一下选中、再点一下取消**，最多九种一起产。
 * 以前那种「选一个起始种类 + 用数字按钮调 1~4 种」已经删掉了 ——
 * 想同时产不相邻的两种（比如大地 + 狱火）以前根本做不到。</p>
 *
 * <p><b>默认一个都没选 → 什么都不产。</b> 必须先在界面上点出你要的精魄，
 * 底部的状态行会直接告诉你现在为什么不动。</p>
 *
 * <h3>零原版控件</h3>
 * <p>整个界面都是自己画的，不用 {@code Button}：点格子切换、右键清空、
 * 每 20 tick 问一次服务端，所以<b>笼内灵魂能量和进度条都是活的</b>。</p>
 */
public class SpiritConverterScreen extends Screen {
    // ===== 配色 =====
    private static final int PANEL_BG = 0xF0140E20;
    private static final int PANEL_EDGE = 0xFF3A2A55;
    private static final int SLOT_BG = 0xFF221836;
    private static final int SLOT_HOVER = 0xFF2E2049;
    private static final int SLOT_PICKED = 0xFF3C2A63;
    private static final int TEXT = 0xFFEDE7F6;
    private static final int TEXT_DIM = 0xFF9E93B3;
    private static final int ACCENT = 0xFFB98CF0;
    private static final int OK_GREEN = 0xFF7CE38B;
    private static final int WARN = 0xFFFFC24A;
    private static final int BAD = 0xFFFF6B6B;
    private static final int BAR_BG = 0xFF2A1E3E;

    /** 九种精魄各自的颜色（顺序与 {@link MalumCompat#SPIRITS} 一一对应）。 */
    private static final int[] SPIRIT_COLORS = {
            0xFF8B6B3D, 0xFF9B4BD4, 0xFF4B7BE8, 0xFF5B3D8B, 0xFF3D9BE8,
            0xFF7FD4E8, 0xFFE8503A, 0xFFE8C24B, 0xFF2FA88A,
    };

    private static final int CELL = 24;
    private static final int CELL_GAP = 2;
    private static final int BAR_H = 10;

    // ===== 行距（相对面板左上角）=====
    private static final int ROW_TITLE = 10;
    private static final int ROW_HINT = 26;
    private static final int ROW_SPIRITS = 40;
    private static final int ROW_SUMMARY = 74;
    private static final int ROW_LABEL_CAGE = 92;
    private static final int ROW_CAGE_BAR = 103;
    private static final int ROW_PROGRESS = 119;
    private static final int ROW_PROGRESS_TEXT = 126;
    private static final int STATUS_FROM_BOTTOM = 13;
    private static final int PANEL_H = ROW_PROGRESS_TEXT + 13 + STATUS_FROM_BOTTOM;

    private final BlockPos pos;
    /** 9 位掩码：第 i 位 = 第 i 种精魄要不要产。 */
    private int mask;
    private int cageSouls;
    private int progress;
    private int queryCooldown = 20;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    public SpiritConverterScreen(BlockPos pos, int mask, int cageSouls, int progress) {
        super(Component.translatable("screen.goety_revenant.converter"));
        this.pos = pos;
        this.mask = mask;
        this.cageSouls = cageSouls;
        this.progress = progress;
    }

    /**
     * 由网络包调用：打开界面。
     *
     * <p>已经开着同一个方块就**就地刷新**，不重开 —— 否则每 20 tick 重建一次，鼠标状态会丢。</p>
     */
    public static void open(BlockPos pos, int mask, int cageSouls, int progress) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SpiritConverterScreen existing && existing.pos.equals(pos)) {
            existing.mask = mask;
            existing.cageSouls = cageSouls;
            existing.progress = progress;
            return;
        }
        minecraft.setScreen(new SpiritConverterScreen(pos, mask, cageSouls, progress));
    }

    // ===== 布局 =====

    @Override
    protected void init() {
        int spirits = MalumCompat.SPIRITS.length * (CELL + CELL_GAP) - CELL_GAP;
        this.panelW = Math.max(spirits + 32, 276);
        this.panelH = Math.min(PANEL_H, Math.max(140, this.height - 16));
        this.left = (this.width - this.panelW) / 2;
        this.top = (this.height - this.panelH) / 2;
    }

    private int spiritsX() {
        int spirits = MalumCompat.SPIRITS.length * (CELL + CELL_GAP) - CELL_GAP;
        return this.left + (this.panelW - spirits) / 2;
    }

    private int barX() {
        return this.left + 16;
    }

    private int barW() {
        return this.panelW - 32;
    }

    private int selectedCount() {
        return Integer.bitCount(this.mask);
    }

    private int roundCost() {
        return SpiritConverterBlockEntity.SOUL_COST * Math.max(1, selectedCount());
    }

    // ===== 状态推送 =====

    private void push() {
        RevenantNetwork.CHANNEL.sendToServer(new ConverterSetMessage(this.pos, this.mask));
    }

    @Override
    public void tick() {
        if (--this.queryCooldown > 0) {
            return;
        }
        this.queryCooldown = 20;
        RevenantNetwork.CHANNEL.sendToServer(new ConverterQueryMessage(this.pos));
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(this.left, this.top, this.left + this.panelW, this.top + this.panelH, PANEL_BG);
        frame(graphics, this.left, this.top, this.left + this.panelW, this.top + this.panelH, PANEL_EDGE);
        graphics.fill(this.left + 1, this.top + 1, this.left + this.panelW - 1, this.top + 3, ACCENT);

        graphics.drawCenteredString(this.font, this.title, this.left + this.panelW / 2,
                this.top + ROW_TITLE, TEXT);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.goety_revenant.converter.pick_hint"),
                this.left + this.panelW / 2, this.top + ROW_HINT, TEXT_DIM);

        renderSpirits(graphics, mouseX, mouseY);
        renderSummary(graphics);
        renderCageBar(graphics);
        renderProgress(graphics);
        renderStatus(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 九个精魄格子：点一下切换选中。 */
    private void renderSpirits(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = spiritsX();
        int y = this.top + ROW_SPIRITS;
        for (int i = 0; i < MalumCompat.SPIRITS.length; i++) {
            int x = x0 + i * (CELL + CELL_GAP);
            boolean picked = (this.mask & (1 << i)) != 0;
            boolean hovered = inside(mouseX, mouseY, x, y, CELL, CELL);

            graphics.fill(x, y, x + CELL, y + CELL, hovered ? SLOT_HOVER : (picked ? SLOT_PICKED : SLOT_BG));
            frame(graphics, x, y, x + CELL, y + CELL, picked ? ACCENT : SPIRIT_COLORS[i]);

            ItemStack stack = spiritStack(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 4, y + 4);
            }
            if (picked) {
                // 选中的格子底下加一条亮线，比只换边框更好认
                graphics.fill(x + 2, y + CELL - 2, x + CELL - 2, y + CELL, ACCENT);
            }
            if (hovered) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("spirit.goety_revenant." + MalumCompat.SPIRITS[i]));
                tip.add(Component.translatable(picked
                        ? "screen.goety_revenant.converter.click_off"
                        : "screen.goety_revenant.converter.click_on").withStyle(ChatFormatting.GRAY));
                graphics.renderComponentTooltip(this.font, tip, mouseX, mouseY);
            }
        }
    }

    /** 「已选 N 种 · 每轮 -X 灵魂能量」。 */
    private void renderSummary(GuiGraphics graphics) {
        int count = selectedCount();
        Component line = count == 0
                ? Component.translatable("screen.goety_revenant.converter.none_selected")
                : Component.translatable("screen.goety_revenant.converter.summary",
                        count, SpiritConverterBlockEntity.SOUL_COST * count);
        graphics.drawCenteredString(this.font, line, this.left + this.panelW / 2,
                this.top + ROW_SUMMARY, count == 0 ? WARN : TEXT);
    }

    /** 笼内灵魂能量：一根条 + 精确数字，不够一轮就变红。 */
    private void renderCageBar(GuiGraphics graphics) {
        int x = barX();
        int w = barW();
        int y = this.top + ROW_CAGE_BAR;
        int cost = roundCost();

        graphics.fill(x, y, x + w, y + BAR_H, BAR_BG);

        // 没有笼子的时候**只在这里写一行**（以前这里和底部状态行各写一遍，重复了）
        if (this.cageSouls < 0) {
            frame(graphics, x, y, x + w, y + BAR_H, PANEL_EDGE);
            return;
        }

        int full = Math.max(cost * 5, 1);
        int filled = (int) (w * Math.min(1.0D, this.cageSouls / (double) full));
        int color = this.cageSouls < cost ? BAD : (this.cageSouls < cost * 3 ? WARN : OK_GREEN);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + BAR_H, color);
        }
        frame(graphics, x, y, x + w, y + BAR_H, PANEL_EDGE);

        graphics.drawString(this.font,
                Component.translatable("screen.goety_revenant.converter.cage_bar", this.cageSouls, cost),
                x, this.top + ROW_LABEL_CAGE, color);
    }

    /** 这一轮的进度。 */
    private void renderProgress(GuiGraphics graphics) {
        int x = barX();
        int w = barW();
        int y = this.top + ROW_PROGRESS;
        int interval = SpiritConverterBlockEntity.interval();
        double ratio = interval <= 0 ? 0.0D : Math.min(1.0D, this.progress / (double) interval);

        graphics.fill(x, y, x + w, y + 4, BAR_BG);
        int filled = (int) (w * ratio);
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 4, ACCENT);
        }
        graphics.drawString(this.font, Component.translatable("screen.goety_revenant.converter.progress",
                        (int) (ratio * 100.0D), interval / 20),
                x, this.top + ROW_PROGRESS_TEXT, TEXT_DIM);
    }

    /** 底部状态行：**唯一**一处说明「现在为什么动/不动」。 */
    private void renderStatus(GuiGraphics graphics) {
        Component status;
        int color;
        int count = selectedCount();
        int cost = roundCost();

        if (this.cageSouls < 0) {
            status = Component.translatable("screen.goety_revenant.converter.hint_no_cage");
            color = BAD;
        } else if (count == 0) {
            status = Component.translatable("screen.goety_revenant.converter.hint_none");
            color = WARN;
        } else if (this.cageSouls < cost) {
            status = Component.translatable("screen.goety_revenant.converter.hint_low", cost - this.cageSouls);
            color = WARN;
        } else {
            status = Component.translatable("screen.goety_revenant.converter.hint_ok", count);
            color = OK_GREEN;
        }
        graphics.drawString(this.font, status, this.left + 16,
                this.top + this.panelH - STATUS_FROM_BOTTOM, color);
    }

    private static ItemStack spiritStack(int index) {
        Item item = MalumCompat.spiritItem(MalumCompat.SPIRITS[index]);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int x0 = spiritsX();
        int y = this.top + ROW_SPIRITS;

        for (int i = 0; i < MalumCompat.SPIRITS.length; i++) {
            int x = x0 + i * (CELL + CELL_GAP);
            if (inside(mx, my, x, y, CELL, CELL)) {
                if (button == 1) {
                    // 右键点格子 → 清空全部选择
                    if (this.mask != 0) {
                        this.mask = 0;
                        this.push();
                    }
                } else {
                    this.mask ^= (1 << i);
                    this.push();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 面板里滚轮：没别的可调了，就当作「全选 / 全不选」的快捷键。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside((int) mouseX, (int) mouseY, this.left, this.top, this.panelW, this.panelH)) {
            this.mask = this.mask == 0 ? SpiritConverterBlockEntity.FULL_MASK : 0;
            this.push();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ===== 小工具 =====

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void frame(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
