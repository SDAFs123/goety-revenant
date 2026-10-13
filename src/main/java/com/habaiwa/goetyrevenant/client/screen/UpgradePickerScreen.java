package com.habaiwa.goetyrevenant.client.screen;

import com.habaiwa.goetyrevenant.client.BodyClientState;
import com.habaiwa.goetyrevenant.common.body.UpgradeType;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import com.habaiwa.goetyrevenant.common.network.UpgradeMessage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * **强化槽：三个方向选一个** —— 点枝上的金色小菱弹的就是它。
 *
 * <pre>
 *   攻 · 锐  伤害 +6%／每槽      代价：受到伤害 +2%／每槽
 *   守 · 壁  受到伤害 -5%／每槽   代价：移速 -1.5%／每槽
 *   速 · 羽  冷却 -6%／每槽      代价：伤害 -3%／每槽
 * </pre>
 *
 * <p>点已经选中的那个 = 清空这个槽。切换有冷却，冷却期间点不动。</p>
 */
public class UpgradePickerScreen extends Screen {
    private static final int PANEL_BG = 0xE00A0E14;
    private static final int BONE = 0xFF9FB2B8;
    private static final int GOLD = 0xFFD8B24A;

    private final int slotIndex;
    private int panelLeft;
    private int panelTop;
    private int panelW;
    private int panelH;
    private int hovered = -1;
    /** **本地倒计时**：每 tick 自己减，显示才是实时的（服务端那份只在收包时更新）。 */
    private int localCooldown = 0;
    /** 多久跟服务端校准一次。 */
    private int syncCooldown = 20;

    public UpgradePickerScreen(int slotIndex) {
        super(Component.literal("强化选项"));
        this.slotIndex = slotIndex;
    }

    @Override
    protected void init() {
        this.localCooldown = BodyClientState.upgradeCooldown();
        this.panelW = 260;
        this.panelH = 150;
        this.panelLeft = this.width / 2 - this.panelW / 2;
        this.panelTop = this.height / 2 - this.panelH / 2;
    }

    /** 本地递减 + 每 20 tick 跟服务端校准一次（倒计时才走得对）。 */
    @Override
    public void tick() {
        if (this.localCooldown > 0) {
            this.localCooldown--;
        }
        if (--this.syncCooldown <= 0) {
            this.syncCooldown = 20;
            RevenantNetwork.CHANNEL.sendToServer(new com.habaiwa.goetyrevenant.common.network.BodyActionMessage(
                    com.habaiwa.goetyrevenant.common.network.BodyActionMessage.OPEN, ""));
            int server = BodyClientState.upgradeCooldown();
            if (server < this.localCooldown) {
                this.localCooldown = server;   // 服务端更快就以它为准
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xB004060A);
        graphics.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelW,
                this.panelTop + this.panelH, PANEL_BG);
        graphics.renderOutline(this.panelLeft, this.panelTop, this.panelW, this.panelH, 0xFF1E6E68);

        graphics.drawCenteredString(this.font,
                Component.literal("枝 · 第 " + (this.slotIndex + 1) + " 个强化槽　（"
                        + com.habaiwa.goetyrevenant.common.body.UpgradeType
                                .groupOfSlot(this.slotIndex).cn() + "族）"),
                this.width / 2, this.panelTop + 12, GOLD);
        graphics.drawCenteredString(this.font,
                Component.literal("选一个方向，另外两个不生效"), 
                this.width / 2, this.panelTop + 26, BONE);

        int[] chosen = BodyClientState.upgrades();
        int current = this.slotIndex < chosen.length ? chosen[this.slotIndex] : 0;
        int cd = this.localCooldown;   // 实时值

        this.hovered = -1;
        int rowH = 30;
        int rowY = this.panelTop + 42;
        com.habaiwa.goetyrevenant.common.body.UpgradeType.Type[] options =
                com.habaiwa.goetyrevenant.common.body.UpgradeSlots.candidates(this.slotIndex);
        for (int i = 0; i < options.length; i++) {
            UpgradeType.Type type = options[i];
            boolean selected = current == i + 1;
            boolean hover = mouseX >= this.panelLeft + 10 && mouseX < this.panelLeft + this.panelW - 10
                    && mouseY >= rowY && mouseY < rowY + rowH - 4;
            if (hover) {
                this.hovered = i;
            }
            int bg = selected ? 0xFF1A3430 : (hover ? 0xFF14262A : 0xFF0C161A);
            graphics.fill(this.panelLeft + 10, rowY, this.panelLeft + this.panelW - 10, rowY + rowH - 4, bg);
            graphics.renderOutline(this.panelLeft + 10, rowY, this.panelW - 20, rowH - 4,
                    selected ? 0xFF52E8DC : (hover ? type.color() : 0xFF2A3A40));

            graphics.drawString(this.font, Component.literal("· " + type.cn() + " ·"), 
                    this.panelLeft + 20, rowY + 6, type.color(), false);
            graphics.drawString(this.font,
                    Component.literal(type.gain() + "　" + type.cost()),
                    this.panelLeft + 70, rowY + 6, BONE, false);
            if (selected) {
                graphics.drawString(this.font, Component.literal("已选"),
                        this.panelLeft + this.panelW - 46, rowY + 6, 0xFF52E8DC, false);
            }
            rowY += rowH;
        }

        if (cd > 0) {
            graphics.drawCenteredString(this.font,
                    Component.literal("冷却中：" + ((cd + 19) / 20) + " 秒"),
                    this.width / 2, this.panelTop + this.panelH - 30, ChatFormatting.RED.getColor());
        } else {
            graphics.drawCenteredString(this.font,
                    Component.literal("再点一次已选中的方向 = 清空这个槽"),
                    this.width / 2, this.panelTop + this.panelH - 30, BONE);
        }
        graphics.drawCenteredString(this.font, Component.literal("Esc 返回魂树"),
                this.width / 2, this.panelTop + this.panelH - 16, BONE);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.hovered >= 0) {
            int[] chosen = BodyClientState.upgrades();
            int current = this.slotIndex < chosen.length ? chosen[this.slotIndex] : 0;
            int type = this.hovered + 1;
            // 点已经选中的那个 → 清空
            int send = (current == type) ? 0 : type;
            RevenantNetwork.CHANNEL.sendToServer(new UpgradeMessage(this.slotIndex, send));
            Minecraft.getInstance().setScreen(new TreeScreen());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
