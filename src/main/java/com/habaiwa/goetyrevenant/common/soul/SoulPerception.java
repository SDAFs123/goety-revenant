package com.habaiwa.goetyrevenant.common.soul;

import com.habaiwa.goetyrevenant.init.ModTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 灵魂感知。
 *
 * <p>灵魂状态**不是对所有人隐身** —— 鬼类 / 魂类 / 灵类 / 亡灵类，以及一切能使用诡厄巫法力量的生物
 * 都能看见并攻击灵魂。分两档，用**实体标签**配置（详见 `data/goety_revenant/tags/entity_types/`）：</p>
 *
 * <ul>
 *   <li><b>{@code goety_revenant:soul_seers}</b> —— 完全察觉：本身就是灵体，始终能看见并锁定</li>
 *   <li><b>{@code goety_revenant:soul_sensors}</b> —— 近距离察觉：靠近 {@link #SENSOR_RANGE} 格才看得见</li>
 * </ul>
 */
public final class SoulPerception {
    /** 近距离察觉的范围（格）。 */
    public static final double SENSOR_RANGE = 16.0D;
    /** 完全察觉的扫描半径（它们「无视距离」，但总得有个扫描边界）。 */
    public static final double SEER_RANGE = 64.0D;

    private SoulPerception() {
    }

    /** 这个生物属不属于「能察觉灵魂」的类别。 */
    public static boolean canSense(EntityType<?> type) {
        return type.is(ModTags.SOUL_SEERS) || type.is(ModTags.SOUL_SENSORS);
    }

    /** 在这个距离上能不能察觉。 */
    public static boolean canSenseAt(EntityType<?> type, double distanceSqr) {
        if (type.is(ModTags.SOUL_SEERS)) {
            return distanceSqr <= SEER_RANGE * SEER_RANGE;
        }
        return type.is(ModTags.SOUL_SENSORS) && distanceSqr <= SENSOR_RANGE * SENSOR_RANGE;
    }

    /** 每 20 tick：让能察觉的生物锁定灵魂。 */
    @SubscribeEvent
    public static void onSoulTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!SoulData.isSoul(player) || SoulData.isPossessing(player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        lockOnto(player, SEER_RANGE);
    }

    /**
     * 让周围能察觉的生物把目标锁定到这名玩家身上。
     *
     * @return 锁定了几个
     */
    public static int lockOnto(ServerPlayer player, double scanRadius) {
        AABB box = player.getBoundingBox().inflate(scanRadius);
        int count = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box)) {
            if (!canSense(mob.getType())) {
                continue;
            }
            if (!canSenseAt(mob.getType(), mob.distanceToSqr(player))) {
                continue;
            }
            mob.setTarget(player);
            count++;
        }
        return count;
    }

    /**
     * **仇恨保留**：在能察觉你的生物面前附身 → 附身完成后它们继续敌视你。
     *
     * <p>玩家实体本身不变，所以只要在附身前后把它们的目标重新指向玩家即可。</p>
     */
    public static void keepAggro(ServerPlayer player, double radius) {
        lockOnto(player, radius);
    }
}
