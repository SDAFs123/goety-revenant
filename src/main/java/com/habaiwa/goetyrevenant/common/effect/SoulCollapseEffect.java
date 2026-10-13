package com.habaiwa.goetyrevenant.common.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * **灵魂溃散** —— 灵魂能量被抽干之后的代价（取代原来的「当场真死」）。
 *
 * <p>纯标记效果：真正的结算在
 * {@link com.habaiwa.goetyrevenant.common.soul.SoulCollapse} 里走 `PlayerTickEvent`。</p>
 *
 * <p><b>为什么不做成死亡</b>：这个整合包里「死」这件事有一堆模组在拦
 * （神秘遗物、各种图腾、防死饰品……），我们硬杀人只会被取消，
 * 然后变成每 tick 重试一次的死循环 —— 那次把服务器搞崩的就是这个。
 * 溃散不碰死亡，所以谁也拦不住。</p>
 */
public class SoulCollapseEffect extends MobEffect {
    public SoulCollapseEffect() {
        // HARMFUL + 灵魂紫
        super(MobEffectCategory.HARMFUL, 0x8B4BD4);
    }
}
