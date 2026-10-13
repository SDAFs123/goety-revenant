package com.habaiwa.goetyrevenant.client.screen;

import com.habaiwa.goetyrevenant.client.BodyClientState;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import com.habaiwa.goetyrevenant.common.network.BodyActionMessage;
import com.habaiwa.goetyrevenant.common.network.CastEmbeddedMessage;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 聚晶轮盘。
 *
 * <p>五脏 + 灵窍围着中心排一圈，鼠标指向哪个就选中哪个，<b>点一下释放</b>它里面嵌的聚晶。
 * 走的是 {@code EmbeddedCasting}，和法杖是两条独立路径 —— 各有各的冷却，可以同时用。</p>
 */
public class RadialScreen extends Screen {
    private static final int RING = 58;
    private static final int NODE = 20;
    private static final int SLOT_FILL = 0xC0201533;
    private static final int SLOT_RING = 0xFF7B2FBE;
    private static final int SLOT_HOVER = 0xFFFFFFFF;
    private static final int SLOT_EMPTY = 0xFF505055;

    public RadialScreen() {
        super(Component.translatable("screen.goety_revenant.radial"));
    }

    @Override
    protected void init() {
        // 打开时拉一份最新的嵌入状态
        RevenantNetwork.CHANNEL.sendToServer(new BodyActionMessage(BodyActionMessage.OPEN, ""));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;

        graphics.fill(cx - 24, cy - 24, cx + 24, cy + 24, SLOT_FILL);
        graphics.drawCenteredString(this.font, Component.translatable("screen.goety_revenant.radial"),
                cx, cy - 4, 0xFFFFFF);

        OrganType[] organs = OrganType.values();
        int hovered = hoveredIndex(mouseX, mouseY);
        for (int i = 0; i < organs.length; i++) {
            int[] pos = nodePos(cx, cy, i, organs.length);
            int x = pos[0];
            int y = pos[1];
            ItemStack focus = focusOf(organs[i]);
            boolean locked = BodyClientState.isLocked(organs[i].getId());

            fillCircle(graphics, x, y, NODE, SLOT_FILL);
            ringCircle(graphics, x, y, NODE, locked ? SLOT_EMPTY : (i == hovered ? SLOT_HOVER : SLOT_RING));
            if (!focus.isEmpty()) {
                graphics.renderItem(focus, x - 8, y - 8);
            }
            graphics.drawCenteredString(this.font, organs[i].displayName(), x, y + NODE + 3, 0xDDDDDD);
        }

        if (hovered >= 0) {
            ItemStack focus = focusOf(organs[hovered]);
            Component tip = focus.isEmpty()
                    ? Component.translatable("screen.goety_revenant.radial.empty")
                    : focus.getHoverName();
            graphics.drawCenteredString(this.font, tip, cx, cy + RING + NODE + 24, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = hoveredIndex((int) mouseX, (int) mouseY);
        if (index >= 0) {
            OrganType organ = OrganType.values()[index];
            if (!focusOf(organ).isEmpty()) {
                RevenantNetwork.CHANNEL.sendToServer(new CastEmbeddedMessage(organ.getId()));
            }
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ===== 布局与命中 =====

    private int[] nodePos(int cx, int cy, int index, int total) {
        double angle = Math.toRadians(-90.0D + index * (360.0D / total));
        return new int[]{(int) (cx + Math.cos(angle) * RING), (int) (cy + Math.sin(angle) * RING)};
    }

    private int hoveredIndex(int mouseX, int mouseY) {
        OrganType[] organs = OrganType.values();
        int cx = this.width / 2;
        int cy = this.height / 2;
        int best = -1;
        double bestDist = NODE * NODE;
        for (int i = 0; i < organs.length; i++) {
            int[] pos = nodePos(cx, cy, i, organs.length);
            int dx = mouseX - pos[0];
            int dy = mouseY - pos[1];
            double dist = dx * dx + dy * dy;
            if (dist <= bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private static ItemStack focusOf(OrganType organ) {
        var state = BodyClientState.get();
        if (state == null) {
            return ItemStack.EMPTY;
        }
        for (var entry : state.entries()) {
            if (entry.organ().equals(organ.getId())) {
                return entry.focus();
            }
        }
        return ItemStack.EMPTY;
    }

    private static void fillCircle(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, color);
        }
    }

    private static void ringCircle(GuiGraphics graphics, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int outer = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            int inner = (int) Math.sqrt(Math.max(0, (r - 2) * (r - 2) - dy * dy));
            if (outer > inner) {
                graphics.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
                graphics.fill(cx + inner, cy + dy, cx + outer, cy + dy + 1, color);
            }
        }
        for (int dx = -r; dx <= r; dx++) {
            int outer = (int) Math.sqrt(Math.max(0, r * r - dx * dx));
            int inner = (int) Math.sqrt(Math.max(0, (r - 2) * (r - 2) - dx * dx));
            if (outer > inner) {
                graphics.fill(cx + dx, cy - outer, cx + dx + 1, cy - inner, color);
                graphics.fill(cx + dx, cy + inner, cx + dx + 1, cy + outer, color);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
