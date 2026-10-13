package com.habaiwa.goetyrevenant.common.soul;

import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;

/**
 * **身体特性** —— 附身到不同身体后继承来的"这具身体自己的毛病"。
 *
 * <p>从 `RevenantEvents` 里的私有嵌套类拆出来的（新的事件处理器类看不到嵌套类，
 * 它本来就该独立）。</p>
 *
 * <h3>管三件事</h3>
 * <ul>
 *   <li><b>能量越低越虚弱</b>：按当前/上限的比例分三档叠负面（虚弱 → 减速 → 挖掘疲劳+失明）。
 *       都是短时效果、每秒续一次 —— <b>能量回上来就自动退掉，不留永久 debuff</b>。</li>
 *   <li><b>亡灵白天燃烧</b>：白天、头顶见天、没戴头盔 → 烧 8 秒（设计 §4.4）</li>
 *   <li><b>亡灵的魂能量恢复压到 25%</b>：做法是每 tick 对比本体能量，
 *       发现它涨了就把涨幅砍掉 75%（设计 §4.4）</li>
 * </ul>
 */
public final class SoulTraits {
    private SoulTraits() {
    }

     /**
     * **灵魂能量越低越虚弱。**
     *
     * <p>按当前/上限的比例分三档往下叠，都是短时效果、每秒续一次 ——
     * 能量回上来就自动退掉，不会留下永久 debuff。</p>
     */
    public static void applyWeakness(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        float ratio = com.habaiwa.goetyrevenant.common.soul.SoulEnergy.ratio(player);
        if (ratio >= SoulConstants.WEAK_THRESHOLD_1) {
            return;
        }
        apply(player, net.minecraft.world.effect.MobEffects.WEAKNESS);
        if (ratio < SoulConstants.WEAK_THRESHOLD_2) {
            apply(player, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        }
        if (ratio < SoulConstants.WEAK_THRESHOLD_3) {
            apply(player, net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN);
            apply(player, net.minecraft.world.effect.MobEffects.BLINDNESS);
        }
    }
     private static void apply(ServerPlayer player, net.minecraft.world.effect.MobEffect effect) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                effect, 40, 0, false, false, false));
    }
     /** 白天没头盔会烧起来。 */
    public static void undeadDaylight(ServerPlayer player) {
        Level level = player.level();
        if (level.dimensionType().ultraWarm()) {
            return;
        }
        if (level.isDay() && level.canSeeSky(player.blockPosition())
                && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            player.setSecondsOnFire(8);
        }
    }
     /**
     * 亡灵身体的灵魂能量恢复速度压到玩家的 25%。
     * 做法：每 tick 对比本体的灵魂能量，一旦发现它涨了，就把涨幅砍掉 75%。
     */
    public static void limitUndeadSoulRegen(ServerPlayer player) {
        int current = SEHelper.getSESouls(player);
        int last = SoulData.getLastSe(player);
        if (current > last) {
            current = last + Math.round((current - last) * SoulConstants.UNDEAD_REGEN_FACTOR);
            SEHelper.setSESouls(player, current);
        }
        SoulData.setLastSe(player, current);
    }
}
