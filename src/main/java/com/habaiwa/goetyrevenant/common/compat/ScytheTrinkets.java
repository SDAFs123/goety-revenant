package com.habaiwa.goetyrevenant.common.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * **影响镰刀的饰品** —— 只做"外观/行为"的判定，不改对方模组的数据。
 *
 * <p>用户 2026-09-27 的原话（引自 Malum 的镰刀说明）：</p>
 * <blockquote>
 *   若佩戴了**精准镰刃项链**或**隐藏锋刃项链**，将增强**回旋**和**飞腾**魔咒，
 *   **禁止镰刀横扫攻击**。
 * </blockquote>
 *
 * <p>两件饰品的注册名（反编译 Malum 得到）：</p>
 * <ul>
 *   <li>精准镰刃 —— {@code malum:necklace_of_the_narrow_edge}</li>
 *   <li>隐藏锋刃 —— {@code malum:necklace_of_the_hidden_blade}</li>
 * </ul>
 *
 * <p>"增强回旋与飞腾"由 Malum 自己的附魔实现 —— 我们**只要让那两个附魔能上我们的镰刀**
 * （数据包 `#malum:scythe` 已做），附魔就会自己读玩家身上的项链。
 * 所以这里只负责**禁止横扫**这一条。</p>
 */
public final class ScytheTrinkets {
    /** 精准镰刃项链。 */
    public static final ResourceLocation NARROW_EDGE =
            new ResourceLocation("malum", "necklace_of_the_narrow_edge");
    /** 隐藏锋刃项链。 */
    public static final ResourceLocation HIDDEN_BLADE =
            new ResourceLocation("malum", "necklace_of_the_hidden_blade");

    private ScytheTrinkets() {
    }

    /**
     * 玩家身上有没有"精准镰刃"或"隐藏锋刃"（背包 / 副手 / Curios 都算）。
     *
     * <p>这两件都是 Curios 的项链槽物品，但玩家也可能随手放进背包 ——
     * 按用户"佩戴"的说法只需要 Curios，这里放宽到背包是为了容错
     * （放背包里不算佩戴的说法也说得通，但那会让玩家困惑"为什么没生效"）。</p>
     */
    public static boolean hasBladeNecklace(Player player) {
        return has(player, NARROW_EDGE) || has(player, HIDDEN_BLADE);
    }

    private static boolean has(Player player, ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            return false;   // Malum 缺席
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> handler.findFirstCurio(item).isPresent())
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
