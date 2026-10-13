package com.habaiwa.goetyrevenant.common.body;

import com.habaiwa.goetyrevenant.init.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * **元素超载的结算**（喝魔药之后的那一段）。
 *
 * <p>每 20 tick 跑一次，对六个元素各看一遍：</p>
 * <ol>
 *   <li>玩家身上有没有这个元素的超载效果？没有就跳过（绝大多数时候一个都没带）</li>
 *   <li>有 → 先上**负面**（每秒续一次短时效果）</li>
 *   <li>再看 {@link ElementCounter} —— 到对的地方了就把「连续克制」的计时往上加</li>
 *   <li>连续够 {@link ElementCounter#HOLD_TICKS} tick → **开窍**，并把超载摘掉</li>
 * </ol>
 *
 * <p>条件断了（离开水、放下斧头……）计时清零，并提示一次「被打断了」。</p>
 *
 * <p>放在玩家 tick 里而不是效果的 `applyEffectTick` 里，是因为这里要**主动摘效果**，
 * 而 `applyEffectTick` 是在遍历 activeEffects 的途中调用的（见 {@code ElementOverloadEffect} 的注释）。</p>
 */
public final class ElementOverload {
    /** 「连续克制」的进度键前缀（每个元素一份）。 */
    public static final String KEY_HOLD = "GRCounterHold_";

    private ElementOverload() {
    }

    /** 喝下魔药时调用：挂上对应元素的超载。 */
    public static void start(ServerPlayer player, OrganType organ) {
        player.getPersistentData().putInt(KEY_HOLD + organ.getElement(), 0);
        player.addEffect(new MobEffectInstance(ModEffects.overload(organ).get(),
                DURATION_TICKS, 0, false, true, true));
        player.displayClientMessage(Component.translatable("message.goety_revenant.overload_start",
                organ.elementName(), ElementCounter.hint(organ)), false);
    }

    /** 超载持续多久（5 分钟，够跑一趟）。 */
    public static final int DURATION_TICKS = 6000;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        // 这个整合包的 Forge 事件总线**只要监听器抛异常就会 LinkageError 崩游戏**
        // （它自己打日志那一步坏的），所以这里自己吞掉。
        try {
            for (OrganType organ : OrganType.values()) {
                tick(player, organ);
            }
        } catch (Throwable t) {
            System.err.println("[Goety: Revenant] ElementOverload 抛异常（已吞掉）: " + t);
        }
    }

    private static void tick(ServerPlayer player, OrganType organ) {
        MobEffect effect = ModEffects.overload(organ).get();
        if (!player.hasEffect(effect)) {
            return;
        }
        // 1) 超载的代价
        applyDebuff(player, organ);

        // 2) 克制判定
        String key = KEY_HOLD + organ.getElement();
        if (!ElementCounter.satisfied(player, organ)) {
            if (player.getPersistentData().getInt(key) != 0) {
                player.getPersistentData().putInt(key, 0);
                player.displayClientMessage(Component.translatable("message.goety_revenant.overload_interrupted",
                        ElementCounter.hint(organ)), true);
            }
            return;
        }
        int held = player.getPersistentData().getInt(key) + 20;
        if (held < ElementCounter.HOLD_TICKS) {
            player.getPersistentData().putInt(key, held);
            return;
        }
        open(player, organ);
    }

    private static void open(ServerPlayer player, OrganType organ) {
        player.getPersistentData().remove(KEY_HOLD + organ.getElement());
        player.removeEffect(ModEffects.overload(organ).get());
        BodyModData.setSlotUnlocked(player, organ, true);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendBodyState(player);
        player.displayClientMessage(Component.translatable("message.goety_revenant.slot_opened",
                organ.displayName()), false);
    }

    /** 超载期间的负面：每个元素不一样，都是「这个元素在体内烧起来会有的毛病」。 */
    private static void applyDebuff(ServerPlayer player, OrganType organ) {
        switch (organ) {
            case HEART -> player.setSecondsOnFire(3);
            case KIDNEY -> add(player, MobEffects.MOVEMENT_SLOWDOWN);
            case SPLEEN -> add(player, MobEffects.DIG_SLOWDOWN);
            case LIVER -> add(player, MobEffects.POISON);
            case LUNG -> add(player, MobEffects.WEAKNESS);
            case SPIRIT_APERTURE -> {
                add(player, MobEffects.CONFUSION);
                add(player, MobEffects.BLINDNESS);
            }
        }
    }

    private static void add(ServerPlayer player, MobEffect effect) {
        player.addEffect(new MobEffectInstance(effect, 40, 0, false, false, false));
    }
}
