package com.habaiwa.goetyrevenant.common.flesh;

import com.habaiwa.goetyrevenant.common.item.FleshTotemItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * **血肉能量** —— 和 Goety 的灵魂能量<b>完全独立</b>的一条新池子。
 *
 * <h3>存在哪</h3>
 * <p>存在**血肉图腾自己的 NBT** 里（{@link #KEY}），和本体把灵魂存在图腾里是一个思路。
 * 玩家身上所有带着的血肉图腾加起来就是他此刻的血肉能量总量 ——
 * 所以"没有图腾 = 一点血肉能量都存不住"，这正是"需要对应能量的图腾"的意思。</p>
 *
 * <h3>认哪些位置</h3>
 * <p>和本体图腾一样：<b>饰品槽 → 主副手 → 快捷栏 0-8</b>。
 * 背包深处不算（本体也是这么定的）。</p>
 */
public final class FleshEnergy {
    /** 血肉能量存在图腾 NBT 里的键。 */
    public static final String KEY = "GRFleshEnergy";
    /** 一颗血肉之心能榨出多少血肉能量。 */
    // 数值全部走配置：一颗心脏产出、笼子容量见 RevenantConfig（config/goety_revenant.json）
    /** 血肉之笼自己那个池子的上限（笼子里的能量等着被抽走）。 */
    public static final int CAGE_CAPACITY = 30000;
    /** 血肉之笼里"心脏"槽 / "血肉图腾"槽的下标。 */
    public static final int SLOT_HEART = 0;
    public static final int SLOT_TOTEM = 1;

    private FleshEnergy() {
    }

    /* ------------------------------------------------------------------ */
    /*  单个图腾                                                           */
    /* ------------------------------------------------------------------ */

    public static int get(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
            return 0;
        }
        return stack.getTag().getInt(KEY);
    }

    public static void set(ItemStack stack, int value) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().putInt(KEY, Math.max(0, value));
    }

    public static int capacity(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof FleshTotemItem totem) {
            return totem.capacity();
        }
        return 0;
    }

    /** 往图腾里灌，返回<b>实际灌进去</b>的量。 */
    public static int add(ItemStack stack, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int cap = capacity(stack);
        int now = get(stack);
        int moved = Math.min(amount, Math.max(0, cap - now));
        if (moved > 0) {
            set(stack, now + moved);
        }
        return moved;
    }

    /** 从图腾里抽，返回<b>实际抽出来</b>的量。 */
    public static int drain(ItemStack stack, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int now = get(stack);
        int moved = Math.min(amount, now);
        if (moved > 0) {
            set(stack, now - moved);
        }
        return moved;
    }

    /* ------------------------------------------------------------------ */
    /*  玩家身上所有图腾                                                   */
    /* ------------------------------------------------------------------ */

    /** 玩家身上认得的血肉图腾（饰品槽 → 主副手 → 快捷栏），按顺序。 */
    public static List<ItemStack> totems(Player player) {
        List<ItemStack> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        // 1) 饰品槽
        CuriosApi.getCuriosInventory(player).resolve()
                .flatMap(handler -> handler.findFirstCurio(s -> s.getItem() instanceof FleshTotemItem))
                .map(top.theillusivec4.curios.api.SlotResult::stack)
                .ifPresent(out::add);
        // 2) 主副手
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof FleshTotemItem && !contains(out, stack)) {
                out.add(stack);
            }
        }
        // 3) 快捷栏
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof FleshTotemItem && !contains(out, stack)) {
                out.add(stack);
            }
        }
        return out;
    }

    private static boolean contains(List<ItemStack> list, ItemStack stack) {
        for (ItemStack s : list) {
            if (s == stack) {
                return true;
            }
        }
        return false;
    }

    /** 玩家此刻身上的血肉能量总量。 */
    public static int total(Player player) {
        int sum = 0;
        for (ItemStack s : totems(player)) {
            sum += get(s);
        }
        return sum;
    }

    /** 玩家此刻的血肉能量上限（所有带着的图腾容量相加）。 */
    public static int totalCapacity(Player player) {
        int sum = 0;
        for (ItemStack s : totems(player)) {
            sum += capacity(s);
        }
        return sum;
    }

    /** 灌能量，返回实际灌进去的量（会平摊到所有图腾上）。 */
    public static int give(Player player, int amount) {
        int left = amount;
        for (ItemStack s : totems(player)) {
            if (left <= 0) {
                break;
            }
            left -= add(s, left);
        }
        return amount - left;
    }

    /** 扣能量。**够才扣**，不够一点不扣，返回是否成功。 */
    public static boolean consume(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (total(player) < amount) {
            return false;
        }
        int left = amount;
        for (ItemStack s : totems(player)) {
            if (left <= 0) {
                break;
            }
            left -= drain(s, left);
        }
        return true;
    }
}
