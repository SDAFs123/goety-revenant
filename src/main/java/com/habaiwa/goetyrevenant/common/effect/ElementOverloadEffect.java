package com.habaiwa.goetyrevenant.common.effect;

import com.habaiwa.goetyrevenant.common.body.OrganType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * **元素超载** —— 喝下开窍魔药之后的副作用，一个**纯标记**效果。
 *
 * <p>它自己不做任何 tick 逻辑：真正的结算在
 * {@link com.habaiwa.goetyrevenant.common.body.ElementOverload} 里走 `PlayerTickEvent`。</p>
 *
 * <p><b>为什么不写在 `applyEffectTick` 里</b>：那个回调是在
 * `LivingEntity#tickEffects` **遍历 activeEffects 的途中**调用的，
 * 而我们压制成功时要**当场把这个效果摘掉** —— 直接 `removeEffect` 会
 * `ConcurrentModificationException`。放到玩家 tick 里就没有这个隐患。</p>
 */
public class ElementOverloadEffect extends MobEffect {
    private final OrganType organ;

    public ElementOverloadEffect(OrganType organ) {
        // HARMFUL + 用魔药自己的颜色，图标一眼认得出是哪个元素
        super(MobEffectCategory.HARMFUL, organ.brewColor());
        this.organ = organ;
    }

    public OrganType organ() {
        return this.organ;
    }
}
