package com.habaiwa.goetyrevenant.common.possession;

import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * **哪些东西能被附身、眼前最近的那个是谁**。
 *
 * <p>从 `Possession` 抽出来的（那个类原本把目标判定、附身流程、灵魂形态进出、
 * 强制召回、真死全堆在一起）。这一层只做查询，不改任何状态。</p>
 *
 * <h3>可附身的东西（设计 §4.2）</h3>
 * <pre>
 *   自己的身体        复活 + 物品全数归还
 *   其他玩家的尸体     以对方身体醒来，物品 + 皮肤全继承
 *   存活村民          以村民身体醒来
 *   存活亡灵          继承亡灵特性（白天燃烧、能量恢复压到 25%）
 *   标签里的一切       实体标签 goety_revenant:possessable —— 任何模组写进去就行
 * </pre>
 *
 * <p>标签是给别家模组用的扩展点：他们把自己的生物写进标签就能被附身，
 * <b>不需要适配我们</b>。</p>
 */
public final class PossessionTarget {
    /**
     * 原版「对亡灵特攻」的实体标签 —— 用它兜住模组加的亡灵。
     *
     * <p>只判 {@code MobType.UNDEAD} 会漏掉一批模组生物（它们未必正确设置这个值）。
     * 按<b>注册名</b>构造标签，不依赖 {@code EntityTypeTags} 里的常量名
     * （那个常量在 1.20.1 里叫别的名字）。</p>
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> UNDEAD_TAG =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
                    new net.minecraft.resources.ResourceLocation("minecraft", "sensitive_to_smite"));

    private PossessionTarget() {
    }

    /** 眼前最近的可附身目标（附身半径内，没人就返回 null）。 */
    @Nullable
    public static Entity find(ServerPlayer soul) {
        AABB box = soul.getBoundingBox().inflate(
                com.habaiwa.goetyrevenant.common.config.RevenantConfig.possessRange);
        List<Entity> list = soul.level().getEntities(soul, box, PossessionTarget::isPossessable);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 这个东西能不能被附身。 */
    public static boolean isPossessable(Entity entity) {
        if (entity instanceof CorpseEntity) {
            return true;
        }
        // 标签优先：任何附属把自己的生物写进 goety_revenant:possessable 就能被附身
        if (entity.getType().is(com.habaiwa.goetyrevenant.init.ModTags.POSSESSABLE)) {
            return true;
        }
        if (entity instanceof Player) {
            return com.habaiwa.goetyrevenant.common.config.RevenantConfig.possessPlayers;
        }
        if (entity instanceof com.habaiwa.goetyrevenant.common.entity.RevenantEntity) {
            return true;
        }
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        // 双保险：MobType 判定 + 原版的「对亡灵特攻」标签
        return living.getMobType() == net.minecraft.world.entity.MobType.UNDEAD
                || entity.getType().is(UNDEAD_TAG);
    }
}
