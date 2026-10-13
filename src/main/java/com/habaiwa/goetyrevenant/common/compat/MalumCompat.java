package com.habaiwa.goetyrevenant.common.compat;

import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 与 <b>Malum（灵灾）</b> 的联动。
 *
 * <p><b>规则</b>：</p>
 * <ol>
 *   <li>怪物**不再产出精魄** —— Malum 的精魄掉落被我们拦掉。</li>
 *   <li>佩戴**聚魂护符**击杀生物 → 按怪物强弱产出**大量诡厄巫法灵魂能量**（只有灵魂能量，不产精魄）。</li>
 * </ol>
 *
 * <p>护符可放 Curios 护符槽（我们带了 `curios:charm` 标签），也可带在背包/副手。</p>
 */
public final class MalumCompat {
    public static final String MALUM = "malum";

    /** 装了本模组后，灵魂能量获取的常态加成。 */
    public static final float SOUL_GAIN_BONUS = 1.20F;
    /** 聚魂护符的额外加成。 */
    /** **聚魂护符**带在身上时，击杀获得的灵魂能量 +5%（用户 2026-09-27 的规格）。 */
    public static final float CHARM_BONUS = 1.05F;

    private MalumCompat() {
    }

    public static boolean loaded() {
        return ModList.get() != null && ModList.get().isLoaded(MALUM);
    }

    /** 精魄种类注册名（供转化方块选用）。 */
    public static final String[] SPIRITS = {
            "earthen_spirit", "wicked_spirit", "arcane_spirit", "umbral_spirit",
            "aqueous_spirit", "aerial_spirit", "infernal_spirit",
            "sacred_spirit", "eldritch_spirit"};

    /** 按名字取精魄物品；没装 Malum 返回 null。 */
    public static Item spiritItem(String name) {
        if (!loaded()) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(MALUM, name));
        return item == Items.AIR ? null : item;
    }

    private static boolean isMalumSpirit(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(MALUM) && key.getPath().endsWith("_spirit");
    }

    // ===== 1) 怪物不再产出精魄 =====

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!loaded() || event.getDrops().isEmpty()) {
            return;
        }
        event.getDrops().removeIf(drop -> isMalumSpirit(drop.getItem()));
    }

    // ===== 2) 聚魂护符：击杀只产出大量灵魂能量 =====

    /**
     * 击杀产灵魂能量 —— <b>不需要佩戴饰品</b>，任何玩家杀任何生物都给。
     * 佩戴聚魂护符只是**翻倍**。
     */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) {
            return;
        }
        float maxHealth = victim.getMaxHealth();
        int souls = soulsFor(maxHealth);
        // 装了本模组的常态加成 +20%
        souls = Math.round(souls * SOUL_GAIN_BONUS);
        // 聚魂护符再 +5%
        // 主手武器上的「聚魂护符」附魔（I 级 +5% / II 级 +10%）
        souls = Math.round(souls * charmBonus(player));
        SEHelper.increaseSESouls(player, souls);
    }

    /** 按怪物强弱给的灵魂能量（比本体基础值高）。 */
    private static int soulsFor(float maxHealth) {
        if (maxHealth >= 60.0F) {
            return 3000;
        }
        if (maxHealth >= 30.0F) {
            return 1200;
        }
        if (maxHealth >= 12.0F) {
            return 500;
        }
        return 180;
    }

    // ===== 护符状态缓存：Curios 查询不便宜，怪物农场里每次击杀都查会卡 =====

    /**
     * 护符缓存。
     *
     * <p><b>为什么用「世界游戏刻」而不是 `player.tickCount`</b>：后者是每个玩家各自从 0 数的，
     * 没法拿来判断**别的玩家**的缓存过期没有，也就没法做清理 —— 原来那两个只增不减的静态 Map
     * 就是这么漏的（见过的每个 UUID 都永远留在里面）。换成世界时钟以后，
     * 一次遍历就能把所有过期的条目扫掉。</p>
     */
    /**
     * **聚魂护符的加成倍率**（读**主手武器上的附魔等级**）。
     *
     * <p>用户 2026-09-27：「聚魂护符修改为增加获取灵魂能量的护符，增加 5%，
     * 适用物品：#malum:soul_hunter_weapon，最大等级：II」。</p>
     *
     * <p>⚠️ 它是个**附魔**，不是一个叫"聚魂护符"的饰品 ——
     * 之前 `hasCharmCached` 检查的是背包里有没有 `SOUL_HARVEST_CHARM` 这个物品，
     * 而那个物品根本没有效果（规格里的"适用物品/最大等级/概述"都是附魔的词）。</p>
     *
     * @return 1.0 = 无加成；1.05 = I 级；1.10 = II 级
     */
    public static float charmBonus(ServerPlayer player) {
        return hasCharm(player) ? CHARM_BONUS : 1.0F;
    }

    /**
     * 身上带没带**聚魂护符**（背包 / 副手 / Curios 都算）。
     *
     * <p>用户 2026-09-27：「带在身上击杀生物……」——
     * 它是个**饰品**（`goety_revenant:soul_harvest_charm`），带在身上就生效，
     * 不需要拿在手里。</p>
     */
    public static boolean hasCharm(ServerPlayer player) {
        net.minecraft.world.item.Item charm =
                com.habaiwa.goetyrevenant.init.ModItems.SOUL_HARVEST_CHARM.get();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(charm)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty() && stack.is(charm)) {
                return true;
            }
        }
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> handler.findFirstCurio(charm).isPresent())
                    .orElse(false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 旧版这里是「背包里有没有 SOUL_HARVEST_CHARM 那个物品」的检查 + 40 tick 缓存。
     *
     * <p>已删除 —— 用户 2026-09-27 的规格里，「聚魂护符」是一个**附魔**
     * （有"适用物品 / 最大等级 / 概述"），不是一个饰品。
     * 加成现在直接由 {@link #charmBonus(net.minecraft.server.level.ServerPlayer)}
     * 读主手武器的附魔等级得到，不需要缓存也不需要遍历背包。</p>
     */

    public static boolean spiritExists(String name) {
        return loaded() && BuiltInRegistries.ITEM.get(new ResourceLocation(MALUM, name)) != Items.AIR;
    }
}
