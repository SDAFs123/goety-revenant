package com.habaiwa.goetyrevenant.common.compat;

import com.habaiwa.goetyrevenant.compat.RevenantCompat;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * **启示录的三阶光环**。
 *
 * <p>启示录（`goety_revelation`）是闭源的、没有 API 包，但它的物品都是普通 Java 物品
 * （`AscensionHalo extends Item implements ICurioItem`），**注册名是稳定的**，
 * 所以「联动」就走注册名 + Curios 查询这一条路 —— 和我们对 Malum / Goety 的做法一致。</p>
 *
 * <table border="1">
 *   <tr><th>阶</th><th>注册名</th><th>中文</th><th>来历</th></tr>
 *   <tr><td>1</td><td>`goety_revelation:broken_halo`</td><td>破碎之环</td><td>击杀使徒掉落（"破灭神性"）</td></tr>
 *   <tr><td>2</td><td>`goety_revelation:ascension_halo`</td><td>晋升之环</td><td>成神的核心（"生命擢升"）</td></tr>
 *   <tr><td>3</td><td>`goety_revelation:halo_of_the_end`</td><td>终末之环</td><td>飞升神域（"终末神明"）</td></tr>
 * </table>
 *
 * <p>启示录缺席时全部返回 {@link Tier#NONE}，什么都不做。</p>
 */
public final class RevelationHalo {
    /** 三阶（顺序就是高低）。 */
    public enum Tier {
        NONE(0, null),
        BROKEN(1, "broken_halo"),
        ASCENSION(2, "ascension_halo"),
        END(3, "halo_of_the_end");

        private final int rank;
        private final String path;

        Tier(int rank, String path) {
            this.rank = rank;
            this.path = path;
        }

        public int rank() {
            return this.rank;
        }

        @Nullable
        public String path() {
            return this.path;
        }
    }

    private RevelationHalo() {
    }

    public static boolean loaded() {
        return RevenantCompat.revelation();
    }

    /** 这个物品是不是三阶光环之一（给 tooltip 用）。 */
    @Nullable
    public static Tier tierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !loaded()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals(RevenantCompat.REVELATION)) {
            return null;
        }
        for (Tier tier : Tier.values()) {
            if (tier.path != null && tier.path.equals(id.getPath())) {
                return tier;
            }
        }
        return null;
    }

    @Nullable
    private static Item item(Tier tier) {
        if (tier.path == null) {
            return null;
        }
        Item found = BuiltInRegistries.ITEM.get(
                new ResourceLocation(RevenantCompat.REVELATION, tier.path));
        return found == Items.AIR ? null : found;
    }

    /**
     * 玩家**戴着**的最高一阶光环。
     *
     * <p>光环是 Curios 饰品，所以查饰品槽 —— 和本体的图腾一样，
     * 只有真戴上才算（背包里不算）。</p>
     */
    public static Tier wornTier(ServerPlayer player) {
        if (!loaded()) {
            return Tier.NONE;
        }
        Tier best = Tier.NONE;
        try {
            var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .resolve().orElse(null);
            if (inventory == null) {
                return Tier.NONE;
            }
            for (var slot : inventory.getCurios().values()) {
                for (int i = 0; i < slot.getStacks().getSlots(); i++) {
                    Tier tier = tierOf(slot.getStacks().getStackInSlot(i));
                    if (tier != null && tier.rank > best.rank) {
                        best = tier;
                    }
                }
            }
        } catch (Throwable ignored) {
            return Tier.NONE;
        }
        return best;
    }

    /** 给 tooltip / 提示用的显示名（走启示录自己的语言键，它会跟着它的中文翻译走）。 */
    public static String translationKey(Tier tier) {
        return "item." + RevenantCompat.REVELATION + "." + (tier.path == null ? "ascension_halo" : tier.path);
    }

    /** 调试用。 */
    public static String describe(Tier tier) {
        return tier.name().toLowerCase(Locale.ROOT);
    }
}
