package com.habaiwa.goetyrevenant.common.compat;

import com.Polarice3.Goety.utils.SEHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 灵灾的**灵魂击碎 / 收割** → 我们的灵魂能量结算。
 *
 * <p>从 Malum 产物的 deobf jar 读到的真实结构：</p>
 * <pre>
 * MalumLivingEntityDataCapability.getCapability(LivingEntity)   // public static
 *   .soulData                                                   // public 字段
 *     .exposedSoulDuration                                      // public float ★ 灵魂暴露计时
 * </pre>
 *
 * <p><b>链路</b>：被本模组镰刀击中 → 灵魂暴露（10 秒，重复刷新）→
 * 暴露期间死亡 → **灵魂击碎** → Malum 触发收割 → <b>这里给灵魂能量</b>。</p>
 *
 * <p>精魄产出仍然归 Malum 自己的 `SpiritHarvestHandler`，我们只管灵魂能量 ——
 * 因为用户确认「收割 = 我们的灵魂能量获取」。</p>
 *
 * <p>全程反射：没有编译期依赖、没装 Malum 时一个字节都不碰。</p>
 */
public final class MalumHarvestCompat {
    /** 灵魂被击碎时给的基础灵魂能量。 */
    public static final int HARVEST_BASE = 800;

    private static boolean resolved;
    private static boolean failed;
    private static Method getCapability;
    private static Field soulDataField;
    private static Field exposedField;

    private MalumHarvestCompat() {
    }

    private static void resolve() {
        if (resolved || failed) {
            return;
        }
        try {
            Class<?> capability = Class.forName("com.sammy.malum.common.capability.MalumLivingEntityDataCapability");
            getCapability = capability.getMethod("getCapability", LivingEntity.class);
            soulDataField = capability.getField("soulData");
            Class<?> soulData = Class.forName("com.sammy.malum.core.handlers.SoulDataHandler");
            exposedField = soulData.getField("exposedSoulDuration");
            resolved = true;
        } catch (Throwable t) {
            failed = true;
        }
    }

    /** 这具生物的灵魂当前是否处于「暴露」状态（暴露期间死亡才会被击碎）。 */
    public static boolean isSoulExposed(LivingEntity entity) {
        if (!MalumCompat.loaded()) {
            return false;
        }
        resolve();
        if (!resolved) {
            return false;
        }
        try {
            Object capability = getCapability.invoke(null, entity);
            if (capability == null) {
                return false;
            }
            Object soulData = soulDataField.get(capability);
            return soulData != null && exposedField.getFloat(soulData) > 0.0F;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 收割结算：玩家用镰刀击杀一具**灵魂已暴露**的生物 → 灵魂击碎 → 给灵魂能量。
     * 用 LOWEST 优先级，让 Malum 自己的处理先跑完。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!isSoulExposed(victim)) {
            return;
        }

        // 收割 = 我们的灵魂能量结算（含本模组常态加成与聚魂护符）
        float maxHealth = victim.getMaxHealth();
        int souls = Math.round(HARVEST_BASE * Math.max(1.0F, maxHealth / 20.0F) * MalumCompat.SOUL_GAIN_BONUS);
        if (MalumCompat.charmBonus(player) > 1.0F) {
            souls = Math.round(souls * MalumCompat.charmBonus(player));
        }
        SEHelper.increaseSESouls(player, souls);
    }
}
