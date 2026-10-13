package com.habaiwa.goetyrevenant.common.reaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * **连携的触发点**：主聚晶命中 → 跑连携链 → 有反应就把伤害乘上去。
 *
 * <p><b>防递归</b>：反应自己也会造成魔法伤害，那又会被这个事件接住 ——
 * 所以处理期间挂一个标记，反应造成的伤害直接放行（不再叠一层）。</p>
 */
public final class ReactionEvents {
    /** 正在结算反应 —— 这期间进来的伤害是反应自己打的。 */
    private static boolean resolving = false;

    private ReactionEvents() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (resolving) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        ServerPlayer player = attacker(event);
        if (player == null || player == target) {
            return;
        }
        resolving = true;
        try {
            // 打人 = 战斗：降温（挨打的那边在下面单独处理）
            com.habaiwa.goetyrevenant.common.soul.SoulTemperature.onCombat(player);
            if (target instanceof ServerPlayer victim) {
                com.habaiwa.goetyrevenant.common.soul.SoulTemperature.onCombat(victim);
            }
            // 强化槽：速的代价（打得轻）、攻的代价（更疼）、守的减伤
            float upgradeMult = com.habaiwa.goetyrevenant.common.body.UpgradeSlots
                    .damageDealtFactor(player);
            if (target instanceof ServerPlayer victim) {
                upgradeMult *= com.habaiwa.goetyrevenant.common.body.UpgradeSlots
                        .damageTakenFactor(victim)
                        * com.habaiwa.goetyrevenant.common.body.UpgradeSlots
                        .damageReduceFactor(victim);
            }
            if (upgradeMult != 1.0F) {
                event.setAmount(event.getAmount() * upgradeMult);
            }
            float mult = ComboChain.onMainHit(player, target);
            if (mult > 1.0F) {
                // 温度也参与威力：低温打得重、高温打得轻
                mult *= com.habaiwa.goetyrevenant.common.soul.SoulTemperature.powerScale(player);
                event.setAmount(event.getAmount() * mult);
            }
        } catch (Throwable t) {
            System.err.println("[Goety: Revenant] 连携结算出错：" + t);
        } finally {
            resolving = false;
        }
    }

    /** 强化「噬灵」：击杀敌人回一点灵魂能量（按上限的 3%）。 */
    @SubscribeEvent
    public static void onKill(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!com.habaiwa.goetyrevenant.common.body.UpgradeSlots.hasSoulEater(player)) {
            return;
        }
        var energy = com.Polarice3.Goety.utils.SEHelper.getCapability(player);
        if (energy != null) {
            energy.increaseSE(Math.max(1,
                    Math.round(com.habaiwa.goetyrevenant.common.soul.SoulEnergy.max(player) * 0.03F)));
        }
    }

    /** **被治疗 = 升温**（灵魂温度）。 */
    @SubscribeEvent
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.habaiwa.goetyrevenant.common.soul.SoulTemperature.onHealed(player);
        }
    }

    /** 伤害是谁打的（法术弹射物也算玩家打的）。 */
    private static ServerPlayer attacker(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer p) {
            return p;
        }
        if (event.getSource().getDirectEntity() instanceof ServerPlayer p) {
            return p;
        }
        return null;
    }
}
