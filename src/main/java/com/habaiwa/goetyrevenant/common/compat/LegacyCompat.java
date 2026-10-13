package com.habaiwa.goetyrevenant.common.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * **和 Enigmatic Legacy 的联动** —— 井底那扇门的钥匙。
 *
 * <h3>规矩</h3>
 * <p>同时带着这两样，哭泣之井下面就通着那片白：</p>
 * <ul>
 *   <li><b>七咒之戒</b>（{@code enigmaticlegacy:cursed_ring}）—— 接受七个诅咒的人</li>
 *   <li><b>莫测之眼</b>（{@code enigmaticlegacy:enigmatic_eye_active}）—— 能看见不该看见的</li>
 * </ul>
 *
 * <p>缺一样，井就只是口井：跳进去什么也不会发生。</p>
 *
 * <h3>找不到模组怎么办</h3>
 * <p>如果整合包里<b>没有</b> Enigmatic Legacy（两件物品都解析不出来），那这道门槛直接放行 ——
 * 不能因为我们联动的对象不在，就把整个入口给堵死。</p>
 */
public final class LegacyCompat {
    private static final String LEGACY = "enigmaticlegacy";
    private static final ResourceLocation RING_ID = new ResourceLocation(LEGACY, "cursed_ring");
    /**
     * **必须是被激活的莫测之眼**（{@code enigmatic_eye_active}）。
     *
     * <p>休眠之眼（{@code enigmatic_eye_dormant}）<b>不算</b> —— 那是同一个东西
     * 还没醒过来的样子，它看不见不该看见的东西。</p>
     */
    private static final ResourceLocation EYE_ID = new ResourceLocation(LEGACY, "enigmatic_eye_active");

    private static Item ring;
    private static Item eye;
    private static boolean resolved;

    private LegacyCompat() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        ring = lookup(RING_ID);
        eye = lookup(EYE_ID);
    }

    private static Item lookup(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    /**
     * **Enigmatic Legacy 在不在**。
     *
     * <p>按 <b>modid</b> 查，不看物品解析结果 —— 这是个语义问题：
     * 之前这里写的是"两件物品都找得到才算"，于是万一物品 id 变了、解析失败，
     * 就会判定成"模组不在"从而<b>把门槛整个放行</b>（没戴莫测之眼也能进）。
     * 模组在就该守规矩，认不出物品是另一回事 —— 那时候应该拦住，不是放行。</p>
     */
    public static boolean loaded() {
        try {
            return net.minecraftforge.fml.ModList.get().isLoaded(LEGACY);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * **戴齐了没有**。
     *
     * <p>背包、副手、以及 Curios 饰品位都会看 —— 戒指这种东西正常人都戴在饰品栏里，
     * 只看背包会漏。Curios 那段走反射：没装 Curios 就安静跳过。</p>
     */
    /** 戴着七咒之戒没有。 */
    public static boolean hasRing(Player player) {
        return find(player, true);
    }

    /** 戴着【激活的】莫测之眼没有。 */
    public static boolean hasEye(Player player) {
        return find(player, false);
    }

    public static boolean hasGear(Player player) {
        resolve();
        if (ring == null || eye == null) {
            return false;
        }
        boolean hasRing = false;
        boolean hasEye = false;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ring)) {
                hasRing = true;
            }
            if (stack.is(eye)) {
                hasEye = true;
            }
        }
        if (!(hasRing && hasEye)) {
            try {
                hasRing |= curiosHas(player, ring);
                hasEye |= curiosHas(player, eye);
            } catch (Throwable ignored) {
                // 没装 Curios，或者它的 API 换了名字 —— 那就只认背包
            }
        }
        return hasRing && hasEye;
    }

    /** 找某一件（true = 戒指，false = 眼）。 */
    private static boolean find(Player player, boolean wantRing) {
        resolve();
        Item want = wantRing ? ring : eye;
        if (player == null || want == null) {
            return false;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(want)) {
                return true;
            }
        }
        try {
            return curiosHas(player, want);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 在 Curios 的饰品位里找（反射，失败就当没有）。 */
    private static boolean curiosHas(Player player, Item item) throws Exception {
        Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        Object lazy = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class)
                .invoke(null, player);
        Object optional = lazy.getClass().getMethod("resolve").invoke(lazy);
        if (!(optional instanceof java.util.Optional<?> opt) || opt.isEmpty()) {
            return false;
        }
        Object handler = opt.get();
        Object curios = handler.getClass().getMethod("getCurios").invoke(handler);
        if (!(curios instanceof java.util.Map<?, ?> map)) {
            return false;
        }
        for (Object entry : map.values()) {
            Object stackHandler = entry.getClass().getMethod("getStacks").invoke(entry);
            if (!(stackHandler instanceof net.minecraftforge.items.IItemHandler slots)) {
                continue;
            }
            for (int i = 0; i < slots.getSlots(); i++) {
                if (slots.getStackInSlot(i).is(item)) {
                    return true;
                }
            }
        }
        return false;
    }
}
