package com.habaiwa.goetyrevenant.client.screen.tree;

import com.habaiwa.goetyrevenant.client.BodyClientState;
import com.habaiwa.goetyrevenant.common.network.BodyActionMessage;
import com.habaiwa.goetyrevenant.common.network.BodyStateMessage;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * **选一枚聚晶嵌进去** —— 点树上的凹槽之后弹的就是它。
 *
 * <p>列出背包（含聚晶包 / 多晶大袋）里所有能用的聚晶，点一下就嵌进那一位。
 * 嵌进去之后回到树界面，槽里就显示这枚聚晶了。</p>
 */
public class FocusPickerScreen extends Screen {
    private static final int CELL = 24;
    private static final int COLS = 6;
    private static final int SOUL_BRIGHT = 0xFF52E8DC;
    private static final int SOUL_DIM = 0xFF1E6E68;
    private static final int BONE_DIM = 0xFF9FB2B8;
    private static final int GOLD = 0xFFD8B24A;
    private static final int PANEL_BG = 0xE00A0E14;

    /** 这是主干上的第几位（0~5）。 */
    private final int slotIndex;

    private final List<BodyStateMessage.FocusEntry> focuses = new ArrayList<>();
    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private int hovered = -1;
    private final List<int[]> cells = new ArrayList<>();

    public FocusPickerScreen(int slotIndex) {
        super(Component.literal("嵌入聚晶"));
        this.slotIndex = slotIndex;
    }

    @Override
    protected void init() {
        BodyStateMessage state = BodyClientState.get();
        this.focuses.clear();
        if (state != null) {
            for (BodyStateMessage.FocusEntry entry : state.focuses()) {
                if (!entry.stack().isEmpty()) {
                    this.focuses.add(entry);
                }
            }
        }

        int rows = Math.max(1, (this.focuses.size() + COLS - 1) / COLS);
        this.panelW = COLS * CELL + 24;
        this.panelH = rows * CELL + 62;
        this.panelLeft = this.width / 2 - this.panelW / 2;
        this.panelTop = this.height / 2 - this.panelH / 2;

        this.cells.clear();
        for (int i = 0; i < this.focuses.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            this.cells.add(new int[]{
                    this.panelLeft + 12 + col * CELL,
                    this.panelTop + 44 + row * CELL});
        }
    }

    @Override
    public void tick() {
        // 每 10 tick 刷新一次背包（可能刚捡到新聚晶）
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.tickCount % 10 == 0) {
            RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(BodyActionMessage.OPEN, ""));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 树界面还透在后面一点点
        graphics.fill(0, 0, this.width, this.height, 0xB004060A);
        graphics.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelW,
                this.panelTop + this.panelH, PANEL_BG);
        graphics.renderOutline(this.panelLeft, this.panelTop, this.panelW, this.panelH, SOUL_DIM);

        graphics.drawCenteredString(this.font,
                Component.literal("主干 · 第 " + (this.slotIndex + 1) + " 位"), 
                this.width / 2, this.panelTop + 12, GOLD);
        graphics.drawCenteredString(this.font,
                Component.literal("选一枚聚晶嵌进这一位"), 
                this.width / 2, this.panelTop + 26, BONE_DIM);

        this.hovered = -1;
        if (this.focuses.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.literal("背包（和聚晶包）里没有聚晶"),
                    this.width / 2, this.panelTop + 52, BONE_DIM);
        } else {
            for (int i = 0; i < this.focuses.size(); i++) {
                int[] c = this.cells.get(i);
                boolean hover = mouseX >= c[0] && mouseX < c[0] + CELL && mouseY >= c[1] && mouseY < c[1] + CELL;
                if (hover) {
                    this.hovered = i;
                }
                graphics.fill(c[0], c[1], c[0] + CELL, c[1] + CELL, hover ? 0xFF143034 : 0xFF0A1418);
                graphics.renderOutline(c[0], c[1], CELL, CELL, hover ? SOUL_BRIGHT : SOUL_DIM);
                ItemStack stack = this.focuses.get(i).stack();
                graphics.renderItem(stack, c[0] + 4, c[1] + 4);
            }
        }

        if (this.hovered >= 0 && this.hovered < this.focuses.size()) {
            ItemStack stack = this.focuses.get(this.hovered).stack();
            List<Component> lines = new ArrayList<>();
            lines.add(stack.getHoverName());
            lines.add(Component.literal("点击嵌入这一位").withStyle(ChatFormatting.AQUA));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }

        graphics.drawCenteredString(this.font, Component.literal("Esc 返回魂树"),
                this.width / 2, this.panelTop + this.panelH - 16, BONE_DIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.focuses.isEmpty()) {
            for (int i = 0; i < this.cells.size(); i++) {
                int[] c = this.cells.get(i);
                if (mouseX >= c[0] && mouseX < c[0] + CELL && mouseY >= c[1] && mouseY < c[1] + CELL) {
                    // 发嵌入请求，然后回树界面看槽里的聚晶
                    RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(
                            BodyActionMessage.EMBED,
                            com.habaiwa.goetyrevenant.common.body.OrganType
                                    .values()[Math.min(this.slotIndex,
                                            com.habaiwa.goetyrevenant.common.body.OrganType.values().length - 1)]
                                    .getId(),
                            this.focuses.get(i).slot()));
                    Minecraft.getInstance().setScreen(new TreeScreen());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
