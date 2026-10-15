package com.habaiwa.goetyrevenant.mixin;

import com.sammy.malum.common.item.ISpiritAffiliatedItem;
import com.sammy.malum.core.systems.spirit.MalumSpiritType;
import com.sammy.malum.registry.common.SpiritTypeRegistry;
import com.Polarice3.Goety.common.items.equipment.DarkMetalScythe;
import org.spongepowered.asm.mixin.Mixin;

/**
 * **让 {@code DarkMetalScythe} 的粒子用「幽影」的黑色。**
 *
 * <h3>Malum 是怎么取颜色的</h3>
 * <p>源码（{@code MalumScytheItem.hurtEvent} / {@code AscensionEnchantment.triggerAscension}）：
 * <pre>
 *   Item item = stack.getItem();
 *   if (item instanceof ISpiritAffiliatedItem affiliated) {
 *       particleEffect.setSpiritType(affiliated);        // ← 唯一来源
 *   }
 * </pre>
 * <p>也就是说**只有实现了 {@link ISpiritAffiliatedItem} 的物品才有颜色**，
 * 其余一律走默认色。Goety 的镰刀当然没实现它。</p>
 *
 * <h3>为什么用 Mixin 注入接口</h3>
 * <p>物品类在 Goety 里，我们改不了它的声明；而循环里的判定是 JVM 层的
 * {@code instanceof} —— 光有数据包或事件都插不进去。
 * Mixin 的**接口注入**（mixin 类 implements 一个接口）会把该接口真的加到目标类上，
 * 于是 {@code instanceof} 就会成立。</p>
 *
 * <h3>为什么是「幽影」</h3>
 * <p>用户 2026-10-14：「**黑暗镰刀的飞腾产生的攻击特效应该同样改为黑色的**」。
 * {@code SpiritTypeRegistry} 的 9 种里，{@code UMBRAL_SPIRIT}（幽影）是最暗的一支，
 * 而且用户 2026-09-27 自己提过「**幽影精魂**」（跳进哭泣之井学的那种）—— 用的是同一个词。</p>
 *
 * <p>⚠️ 这个 Mixin 依赖 Malum 的类（{@code compileOnly}）。Malum 缺席时融合会失败，
 * 所以 {@code mixins.json} 里是 {@code required: false} —— 失败只是没颜色，不影响启动。</p>
 */
@Mixin(DarkMetalScythe.class)
public abstract class DarkMetalScytheMixin implements ISpiritAffiliatedItem {
    @Override
    public MalumSpiritType getDefiningSpiritType() {
        return SpiritTypeRegistry.UMBRAL_SPIRIT;
    }
}
