package com.habaiwa.goetyrevenant.common.soul;

import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

/**
 * 契合度：附身非自身身体时的「合身程度」。
 *
 * <p>契合度直接决定能在这具身体里待多久：可附身时长 = 20 分钟 × 契合度。
 * 100% 只属于玩家自己原本的身体（回去即复活，不走限时）。</p>
 */
public final class Attunement {
    /** 亡灵生物的契合度表：每一具身体都不一样。 */
    private static final Map<EntityType<?>, Float> TABLE = Map.ofEntries(
            Map.entry(EntityType.ZOMBIE, 0.55F),
            Map.entry(EntityType.HUSK, 0.55F),
            Map.entry(EntityType.DROWNED, 0.50F),
            Map.entry(EntityType.ZOMBIE_VILLAGER, 0.60F),
            Map.entry(EntityType.SKELETON, 0.45F),
            Map.entry(EntityType.STRAY, 0.45F),
            Map.entry(EntityType.WITHER_SKELETON, 0.35F),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, 0.40F),
            Map.entry(EntityType.ZOGLIN, 0.30F),
            Map.entry(EntityType.SKELETON_HORSE, 0.50F),
            Map.entry(EntityType.ZOMBIE_HORSE, 0.50F),
            Map.entry(EntityType.PHANTOM, 0.25F),
            Map.entry(EntityType.WITHER, 0.10F));

    /**
     * 按注册名登记的「外来身体」契合度（附属模组的生物走这里）。
     * 用注册名而不是类，就不用把别人的 jar 加进编译依赖 —— 对方缺席时也毫无影响。
     */
    private static final Map<net.minecraft.resources.ResourceLocation, Float> COMPAT = new java.util.HashMap<>();

    /** 附属模组登记一具身体的契合度。 */
    public static void registerCompat(String entityId, float value) {
        COMPAT.put(new net.minecraft.resources.ResourceLocation(entityId), value);
    }

    // 契合度数值改成读配置（config/goety_revenant.json 的「契合度」段 / Cloth 界面）

    private Attunement() {
    }

    /** 附身自己的身体 = 100%。 */
    public static float self() {
        return com.habaiwa.goetyrevenant.common.config.RevenantConfig.attuneSelf;
    }

    /** 其他玩家的身体（尸体）。 */
    public static float otherPlayer() {
        return com.habaiwa.goetyrevenant.common.config.RevenantConfig.attuneOtherPlayer;
    }

    public static float of(EntityType<?> type) {
        Float compat = COMPAT.get(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type));
        if (compat != null) {
            return compat;
        }
        Float known = TABLE.get(type);
        if (known != null) {
            return known;
        }
        if (type == EntityType.VILLAGER) {
            return com.habaiwa.goetyrevenant.common.config.RevenantConfig.attuneVillager;
        }
        return type.getCategory().isFriendly() ? com.habaiwa.goetyrevenant.common.config.RevenantConfig.attuneFriendly : com.habaiwa.goetyrevenant.common.config.RevenantConfig.attuneDefault;
    }

    /** 这具身体是不是亡灵（决定白天燃烧、灵魂能量恢复慢）。 */
    public static boolean isUndeadBody(LivingEntity entity) {
        return entity.getMobType() == MobType.UNDEAD;
    }

    public static boolean isUndeadType(EntityType<?> type) {
        return type.getCategory() == MobCategory.MONSTER
                && (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED
                || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.SKELETON || type == EntityType.STRAY
                || type == EntityType.WITHER_SKELETON || type == EntityType.ZOMBIFIED_PIGLIN
                || type == EntityType.ZOGLIN || type == EntityType.SKELETON_HORSE
                || type == EntityType.ZOMBIE_HORSE || type == EntityType.PHANTOM);
    }

    /** 玩家当前是不是附在亡灵身体里。 */
    public static boolean isPossessingUndead(Player player) {
        return SoulData.isPossessing(player) && SoulData.isPossessingUndead(player);
    }
}
