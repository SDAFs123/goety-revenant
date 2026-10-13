package com.habaiwa.goetyrevenant.common.embed;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * 聚晶的「在哪」与「怎么拿」。
 *
 * <p>界面里的每个聚晶都带一个**槽位编码**，服务端靠它回头找那枚聚晶：</p>
 * <ul>
 *   <li>`0 ~ 35` —— 主背包</li>
 *   <li>`36` —— 副手</li>
 *   <li>`100 + 背包槽 × 100 + 包内序号` —— 聚晶包 / 多晶大袋（走物品处理器能力）</li>
 * </ul>
 *
 * <p><b>设计要点</b>：嵌入流程**只在成功那一刻**才真正拿走聚晶
 * （`take`），中途失败不碰背包 —— 所以不存在「失败还吞聚晶」。</p>
 */
public final class FocusSlots {
    /** 副手槽的编码。 */
    public static final int OFFHAND = 36;
    /** 聚晶包编码的基数。 */
    public static final int BAG_BASE = 100;
    /** 无效槽位。 */
    public static final int NONE = -1;

    private FocusSlots() {
    }

    public static int encodeBag(int bagSlot, int inner) {
        return BAG_BASE + bagSlot * 100 + inner;
    }

    /** 看一眼槽位里现在是什么（不修改任何东西）。 */
    public static ItemStack peek(ServerPlayer player, int slot) {
        if (slot < 0) {
            return ItemStack.EMPTY;
        }
        if (slot == OFFHAND) {
            return player.getInventory().offhand.isEmpty() ? ItemStack.EMPTY : player.getInventory().offhand.get(0);
        }
        if (slot >= BAG_BASE) {
            int bagSlot = (slot - BAG_BASE) / 100;
            int inner = (slot - BAG_BASE) % 100;
            if (bagSlot < 0 || bagSlot >= player.getInventory().items.size()) {
                return ItemStack.EMPTY;
            }
            IItemHandler handler = bagCapability(player, bagSlot);
            if (handler == null || inner >= handler.getSlots()) {
                return ItemStack.EMPTY;
            }
            return handler.getStackInSlot(inner);
        }
        return slot < player.getInventory().items.size() ? player.getInventory().items.get(slot) : ItemStack.EMPTY;
    }

    /**
     * <b>真正拿走</b> 1 枚（只有嵌入成功时才该调）。
     *
     * @return 被拿走的那枚（拿走前的拷贝）；拿不到就返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack take(ServerPlayer player, int slot) {
        ItemStack current = peek(player, slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = current.copyWithCount(1);
        if (slot == OFFHAND) {
            player.getInventory().offhand.get(0).shrink(1);
        } else if (slot >= BAG_BASE) {
            int bagSlot = (slot - BAG_BASE) / 100;
            int inner = (slot - BAG_BASE) % 100;
            IItemHandler handler = bagCapability(player, bagSlot);
            if (handler != null) {
                handler.extractItem(inner, 1, false);
            }
        } else if (slot < player.getInventory().items.size()) {
            player.getInventory().items.get(slot).shrink(1);
        }
        return taken;
    }

    @Nullable
    private static IItemHandler bagCapability(ServerPlayer player, int bagSlot) {
        ItemStack bag = player.getInventory().items.get(bagSlot);
        if (bag.isEmpty()) {
            return null;
        }
        return bag.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
    }
}
