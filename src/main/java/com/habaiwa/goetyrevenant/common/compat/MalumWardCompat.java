package com.habaiwa.goetyrevenant.common.compat;

import com.Polarice3.Goety.utils.SEHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 把 **Malum（灵灾）的灵魂护盾**改由**诡厄巫法的灵魂能量**驱动。
 *
 * <p>扒了 Malum 的产物后发现**根本不用 Mixin** —— 它的玩家数据是公开可达的：</p>
 * <pre>
 * MalumPlayerDataCapability.getCapability(Player)   // public static
 *   .soulWardHandler                                // public 字段
 *     .soulWard                                     // public double
 * </pre>
 *
 * <p>所以每 tick 做两件事，就能同时满足「用灵魂能量显示」和「只显示灵魂能量」：</p>
 * <ol>
 *   <li>发现护盾值<b>掉了</b>（说明它刚才替你挡了伤害）→ 把掉的量从灵魂能量里扣掉</li>
 *   <li>把护盾值<b>写成当前灵魂能量</b> → Malum 的 HUD 于是只显示诡厄巫法的灵魂能量</li>
 * </ol>
 *
 * <p>全程走**反射**，所以没有编译期依赖、没装 Malum 时一个字节都不会碰它。</p>
 */
public final class MalumWardCompat {
    private static final Map<UUID, Double> LAST_WARD = new HashMap<>();
    private static final Map<UUID, Double> LAST_SYNCED = new HashMap<>();

    private static boolean resolved;
    private static boolean failed;
    private static Method getCapability;
    private static Method syncSelf;
    private static Field soulWardHandlerField;
    private static Field soulWardField;

    private MalumWardCompat() {
    }

    private static void resolve() {
        if (resolved || failed) {
            return;
        }
        try {
            Class<?> capability = Class.forName("com.sammy.malum.common.capability.MalumPlayerDataCapability");
            getCapability = capability.getMethod("getCapability", Player.class);
            syncSelf = capability.getMethod("syncSelf", ServerPlayer.class);
            soulWardHandlerField = capability.getField("soulWardHandler");
            Class<?> handler = Class.forName("com.sammy.malum.core.handlers.SoulWardHandler");
            soulWardField = handler.getField("soulWard");
            resolved = true;
        } catch (Throwable t) {
            failed = true;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || !MalumCompat.loaded()) {
            return;
        }
        resolve();
        if (!resolved) {
            return;
        }

        try {
            Object data = getCapability.invoke(null, player);
            if (data == null) {
                return;
            }
            Object wardHandler = soulWardHandlerField.get(data);
            if (wardHandler == null) {
                return;
            }

            UUID id = player.getUUID();
            double current = soulWardField.getDouble(wardHandler);
            double last = LAST_WARD.getOrDefault(id, current);

            // 1) 护盾掉过 → 它刚才挡了伤害，从灵魂能量里扣掉
            if (current < last - 0.01D) {
                SEHelper.decreaseSESouls(player, (int) Math.round(last - current));
            }

            // 2) 护盾值 = 当前灵魂能量（Malum 的 HUD 于是只显示诡厄巫法的灵魂能量）
            double souls = SEHelper.getSESouls(player);
            soulWardField.setDouble(wardHandler, souls);
            LAST_WARD.put(id, souls);

            // 值有变化才推给客户端，避免每 tick 都发包
            double synced = LAST_SYNCED.getOrDefault(id, Double.NaN);
            if (Double.isNaN(synced) || Math.abs(synced - souls) >= 1.0D) {
                LAST_SYNCED.put(id, souls);
                syncSelf.invoke(null, player);
            }
        } catch (Throwable ignored) {
            // Malum 内部结构变了就安静退出，不影响其它功能
        }
    }
}
