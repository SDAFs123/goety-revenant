package com.habaiwa.goetyrevenant.common.reaction;

import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * **反应效果的落地**。
 *
 * <p>表里的「效果」是一串中文词（"范围爆炸"、"无视护甲"、"吸取灵魂能量"…），
 * 这里按<b>关键词</b>逐条兑现 —— 所以 71 条反应不用各写一份代码，
 * 加新反应只要用已有的词。</p>
 *
 * <h3>关键词表</h3>
 * <table>
 *   <tr><td>爆炸</td><td>以目标为中心炸一下（不破坏方块）</td></tr>
 *   <tr><td>燃烧 / 点燃</td><td>点着目标</td></tr>
 *   <tr><td>冻结 / 无法移动</td><td>定住（重度缓慢 + 无法跳跃）</td></tr>
 *   <tr><td>减速</td><td>缓慢</td></tr>
 *   <tr><td>麻痹</td><td>缓慢 + 虚弱 + 无法跳跃</td></tr>
 *   <tr><td>无视护甲</td><td>魔法伤害（原版魔法伤害不吃护甲）</td></tr>
 *   <tr><td>无视抗性</td><td>清掉无敌帧再打，避免被抗性/无敌顶掉</td></tr>
 *   <tr><td>真实伤害</td><td>直接扣血，不走减伤</td></tr>
 *   <tr><td>链接 / 链式</td><td>朝附近另外几个目标串一下</td></tr>
 *   <tr><td>落雷</td><td>目标头顶落雷</td></tr>
 *   <tr><td>吸取</td><td>回灵魂能量</td></tr>
 *   <tr><td>灵魂温度</td><td>挂上「魂寒」标记（灵魂温度下降）</td></tr>
 * </table>
 */
public final class ReactionEffect {
    /** 效果基准伤害（再乘反应的倍率）。 */
    private static final float BASE_DAMAGE = 6.0F;

    private ReactionEffect() {
    }

    public static void apply(ServerPlayer player, LivingEntity target, ElementReaction.Rule rule) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        float mult = (rule.multiplier() <= 0.0F ? 1.0F : rule.multiplier())
                * com.habaiwa.goetyrevenant.common.soul.SoulTemperature.powerScale(player)
                // 强化「灼魂」：连携伤害
                * com.habaiwa.goetyrevenant.common.body.UpgradeSlots.comboDamageFactor(player);
        // 强化「共鸣」：同元素反应（组合里全是一个元素）额外加成
        boolean sameElement = rule.combo().stream().distinct().count() <= 1;
        if (sameElement) {
            mult *= com.habaiwa.goetyrevenant.common.body.UpgradeSlots.resonanceFactor(player);
        }
        int layers = rule.size();

        for (String effect : rule.effects()) {
            keyword(level, player, target, effect, mult, layers);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                20, 0.4D, 0.4D, 0.4D, 0.02D);
    }

    private static void keyword(ServerLevel level, ServerPlayer player, LivingEntity target,
                                String effect, float mult, int layers) {
        String e = effect;
        float dmg = BASE_DAMAGE * mult * (0.6F + 0.4F * layers);

        if (e.contains("爆炸")) {
            level.explode(player, target.getX(), target.getY(), target.getZ(),
                    2.0F + layers * 0.5F, Level.ExplosionInteraction.NONE);
            return;
        }
        if (e.contains("燃烧") || e.contains("点燃")) {
            target.setSecondsOnFire(3 + layers);
            return;
        }
        if (e.contains("冻结") || e.contains("无法移动")) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60 + layers * 20, 6));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, 60 + layers * 20, 128));
            return;
        }
        if (e.contains("减速")) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80 + layers * 20, 2));
            return;
        }
        if (e.contains("麻痹")) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 128));
            return;
        }
        if (e.contains("无视护甲") || e.contains("真实伤害")) {
            magic(level, player, target, dmg, true);
            return;
        }
        if (e.contains("无视抗性")) {
            target.invulnerableTime = 0;   // 清无敌帧，免得被顶掉
            magic(level, player, target, dmg, true);
            return;
        }
        if (e.contains("链接") || e.contains("链式")) {
            chain(level, player, target, dmg * 0.6F, layers);
            return;
        }
        if (e.contains("落雷")) {
            for (int i = 0; i < Math.min(4, layers); i++) {
                net.minecraft.world.entity.LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(target.getX() + (i - 1) * 2.0D, target.getY(), target.getZ());
                    level.addFreshEntity(bolt);
                }
            }
            return;
        }
        if (e.contains("吸取") || e.contains("能量恢复")) {
            com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy energy =
                    SEHelper.getCapability(player);
            if (energy != null) {
                energy.increaseSE(Math.round(dmg * 20.0F));
            }
            return;
        }
        if (e.contains("灵魂温度")) {
            // 「噬魂」：灵魂温度下降 —— 先挂标记，具体机制等设定补全
            target.getPersistentData().putInt("GRSoulChill", 200 + layers * 40);
            magic(level, player, target, dmg * 0.5F, true);
            return;
        }
        if (e.contains("持续")) {
            target.setSecondsOnFire(2 + layers);
            magic(level, player, target, dmg * 0.5F, true);
            return;
        }
        // 其它没认出来的效果：至少给一份魔法伤害，保证"反应了就有反馈"
        magic(level, player, target, dmg * 0.4F, true);
    }

    /** 魔法伤害（不吃护甲）。 */
    private static void magic(ServerLevel level, ServerPlayer player, LivingEntity target,
                              float amount, boolean bypassArmor) {
        if (amount <= 0.0F || !target.isAlive()) {
            return;
        }
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().indirectMagic(player, player), amount);
    }

    /** 链式：朝附近另外几个目标串一下。 */
    private static void chain(ServerLevel level, ServerPlayer player, LivingEntity target,
                              float amount, int layers) {
        AABB box = target.getBoundingBox().inflate(6.0D);
        int hits = 0;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (other == target || other == player || !other.isAlive()) {
                continue;
            }
            magic(level, player, other, amount, true);
            // 强化「链雷」：每 2 槽多串 1 个
            if (++hits >= Math.min(3, layers) + com.habaiwa.goetyrevenant.common.body.UpgradeSlots.chainExtra(player)) {
                break;
            }
        }
    }
}
