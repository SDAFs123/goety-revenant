package com.habaiwa.goetyrevenant.common.soul;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * **附身继承特性** —— 占据一具身体后，把这具身体的属性接过来。
 *
 * <p>原先只继承了亡灵特性（白天燃烧 / 灵魂能量恢复慢），但「附身」在直觉上应该是
 * **连这具身体的体质一起接手**：铁傀儡的肉、骷髅的脆、僵尸的慢，都该体现在玩家身上。</p>
 *
 * <p>实现：给玩家挂**临时属性修饰符**（固定 UUID，退出时精确移除），
 * 使玩家的属性值对齐被附身生物的属性值。</p>
 */
public final class BodyTraits {
    private static final UUID HEALTH_ID = id("health");
    private static final UUID ATTACK_ID = id("attack");
    private static final UUID ARMOR_ID = id("armor");
    private static final UUID SPEED_ID = id("speed");

    private BodyTraits() {
    }

    private static UUID id(String key) {
        return UUID.nameUUIDFromBytes(("goety_revenant:body_" + key).getBytes(StandardCharsets.UTF_8));
    }

    /** 把这具身体的属性接到玩家身上。 */
    public static void apply(ServerPlayer player, LivingEntity body) {
        align(player, Attributes.MAX_HEALTH, body.getAttributeValue(Attributes.MAX_HEALTH), HEALTH_ID);
        align(player, Attributes.ATTACK_DAMAGE, body.getAttributeValue(Attributes.ATTACK_DAMAGE), ATTACK_ID);
        align(player, Attributes.ARMOR, body.getAttributeValue(Attributes.ARMOR), ARMOR_ID);
        align(player, Attributes.MOVEMENT_SPEED, body.getAttributeValue(Attributes.MOVEMENT_SPEED), SPEED_ID);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    /** 退出身体时把继承来的属性全部摘掉。 */
    public static void clear(ServerPlayer player) {
        for (UUID id : new UUID[]{HEALTH_ID, ATTACK_ID, ARMOR_ID, SPEED_ID}) {
            for (Attribute attribute : new Attribute[]{Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE,
                    Attributes.ARMOR, Attributes.MOVEMENT_SPEED}) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(id);
                }
            }
        }
    }

    /**
     * 让玩家的属性值**对齐**目标值。
     *
     * <p>做法是先摘掉旧的修饰符、读出玩家自身的基础值，再用 ADDITION 补差额 ——
     * 这样无论玩家原本穿了多少装备都能算准，也不会叠乘出错。</p>
     */
    private static void align(ServerPlayer player, Attribute attribute, double targetValue, UUID modifierId) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierId);
        double current = instance.getValue();
        double delta = targetValue - current;
        if (Math.abs(delta) < 0.0001D) {
            return;
        }
        instance.addTransientModifier(new AttributeModifier(
                modifierId, "goety_revenant.body_trait", delta, AttributeModifier.Operation.ADDITION));
    }
}
