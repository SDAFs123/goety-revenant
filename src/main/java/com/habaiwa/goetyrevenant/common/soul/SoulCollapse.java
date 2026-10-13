package com.habaiwa.goetyrevenant.common.soul;

import com.habaiwa.goetyrevenant.init.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * **灵魂溃散** —— 灵魂能量被抽干之后的代价。
 *
 * <h3>它到底是什么</h3>
 * <p>能量归零时**不再当场杀人**（杀了也会被这个整合包里的防死模组拦下来，
 * 然后变成每 tick 重试的死循环 —— 那次崩服就是这么来的）。
 * 改成挂一个溃散状态，走完一段固定时长：</p>
 *
 * <ul>
 *   <li><b>一直掉血，但不算受伤</b> —— 用的是 {@code setHealth()}，
 *       <b>完全不触发伤害事件</b>。所以没有受击无敌帧、没有伤害来源、防死模组也拿不到插手的机会</li>
 *   <li><b>灵魂能量极速下降</b> —— 整段一共抽掉 {@value #SE_COST}</li>
 *   <li><b>结束时刚好剩 1 点血</b> —— 血量是按进度线性插值到 1 的，不是"扣到差不多"</li>
 * </ul>
 *
 * <h3>防重复触发</h3>
 * <p>溃散结束之后能量还是 0，如果不管就会立刻再来一轮。所以结束时会留一个门槛：
 * 能量回到 {@value #RETRIGGER_SE} 以上之前不再溃散（按现在的恢复速度大约 25 秒）。</p>
 */
public final class SoulCollapse {
    /** 整段溃散持续多久。 */
    public static final int DURATION_TICKS = 100;
    /** 整段一共抽掉多少灵魂能量。 */
    public static final int SE_COST = 5000;
    /** 结束时的血量。 */
    public static final float END_HEALTH = 1.0F;
    /** 要恢复到这么多能量才允许再次溃散。 */
    public static final int RETRIGGER_SE = 500;

    private static final String KEY_ELAPSED = "GRCollapseTicks";
    private static final String KEY_START_HP = "GRCollapseHp";
    private static final String KEY_START_SE = "GRCollapseSe";

    private SoulCollapse() {
    }

    /** 现在能不能触发溃散（没在溃散中 + 能量还没回到门槛）。 */
    public static boolean canTrigger(ServerPlayer player) {
        if (player.hasEffect(ModEffects.SOUL_COLLAPSE.get())) {
            return false;
        }
        return SoulData.getEnergy(player) < RETRIGGER_SE;
    }

    /** 开始溃散。 */
    public static void start(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        tag.putInt(KEY_ELAPSED, 0);
        tag.putFloat(KEY_START_HP, player.getHealth());
        tag.putInt(KEY_START_SE, SoulData.getEnergy(player));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                ModEffects.SOUL_COLLAPSE.get(), DURATION_TICKS, 0, false, true, true));
        player.displayClientMessage(Component.translatable("message.goety_revenant.soul_collapse"), false);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        try {
            tick(player);
        } catch (Throwable t) {
            System.err.println("[Goety: Revenant] SoulCollapse 抛异常（已吞掉）: " + t);
        }
    }

    private static void tick(ServerPlayer player) {
        if (!player.hasEffect(ModEffects.SOUL_COLLAPSE.get())) {
            return;
        }
        CompoundTag tag = player.getPersistentData();
        int elapsed = tag.getInt(KEY_ELAPSED) + 1;
        if (!tag.contains(KEY_START_HP)) {
            // 状态是从别的地方挂上来的（比如指令），当作刚开始
            tag.putFloat(KEY_START_HP, player.getHealth());
            tag.putInt(KEY_START_SE, SoulData.getEnergy(player));
        }
        float startHealth = tag.getFloat(KEY_START_HP);
        int startSe = tag.getInt(KEY_START_SE);

        float progress = Math.min(1.0F, elapsed / (float) DURATION_TICKS);

        // 1) 血量线性掉到刚好 1 点 —— 用 setHealth，**不走伤害事件**
        float target = Math.max(END_HEALTH, startHealth - (startHealth - END_HEALTH) * progress);
        player.setHealth(target);

        // 2) 灵魂能量极速下降，整段正好 SE_COST
        int spent = Math.round(SE_COST * progress);
        SoulData.setEnergy(player, Math.max(0, startSe - spent));

        tag.putInt(KEY_ELAPSED, elapsed);
        if (elapsed >= DURATION_TICKS) {
            finish(player);
        }
    }

    private static void finish(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        int startSe = tag.getInt(KEY_START_SE);
        player.setHealth(END_HEALTH);
        SoulData.setEnergy(player, Math.max(0, startSe - SE_COST));
        tag.remove(KEY_ELAPSED);
        tag.remove(KEY_START_HP);
        tag.remove(KEY_START_SE);
        player.removeEffect(ModEffects.SOUL_COLLAPSE.get());
        player.displayClientMessage(Component.translatable("message.goety_revenant.soul_collapse_end", SE_COST),
                false);
    }
}
