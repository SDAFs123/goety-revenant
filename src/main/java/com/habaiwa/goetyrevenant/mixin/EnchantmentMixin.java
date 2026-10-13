package com.habaiwa.goetyrevenant.mixin;

import com.habaiwa.goetyrevenant.common.compat.ScytheInterop;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * **让跨模组附魔认得镰刀** —— 给原版的适用性判定开一个后门。
 *
 * <h3>为什么必须动原版类</h3>
 * <p>1.20.1 里"某条附魔能不能附到某个物品上"完全由
 * {@link Enchantment#canEnchant(ItemStack)} / {@link Enchantment#canApplyAtEnchantingTable(ItemStack)}
 * 决定，而这两者的默认实现读 {@code EnchantmentCategory}。附魔本身在对方模组里，
 * 我们改不了它的实现；Forge 1.20.1 也没有"附魔适用性"事件。</p>
 *
 * <h3>为什么不用数据包标签</h3>
 * <p>Malum 的附魔类别是 {@code EnchantmentCategory.create(name, i -> i.is(#malum:scythe))}
 * —— 判的是**标签**，所以理论上"往 `data/malum/tags/items/scythe.json` 里追加我们的镰刀"
 * 就能打通，我们一开始也是这么做的（那个文件现在还留着）。</p>
 *
 * <p>但**用户实测没生效**：「灵灾的附魔附魔不到诡厄巫法镰刀上」——
 * 而同一时间 Goety 的噬魂**能**附到 Malum 镰刀上（那条走 `EnchantmentCategory.WEAPON`，
 * 根本不看标签）。也就是说：**跨命名空间追加标签这条路在实际环境里不可靠。**</p>
 *
 * <p>所以改成这里：**判我们自己的标签** {@code #goety_revenant:scythes} ——
 * 它在我们的命名空间里，由我们自己的文件定义，不存在合并问题。</p>
 *
 * <h3>放行范围（写死，避免过度）</h3>
 * <ul>
 *   <li>物品必须在 {@code #goety_revenant:scythes} 里</li>
 *   <li>附魔的命名空间必须是 {@code malum} 或 {@code goety}</li>
 *   <li><b>排除 {@code malum:spirit_plunder}</b> —— 用户 2026-09-27 明确要求
 *       「灵灾的附魔这个附魔**不允许附魔出来**」</li>
 * </ul>
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    /** 附魔台：能不能出现在结果里。 */
    @Inject(method = "canApplyAtEnchantingTable", at = @At("HEAD"), cancellable = true)
    private void gr$canApplyAtEnchantingTable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ScytheInterop.allows((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(true);
        }
    }

    /** 铁砧 / 附魔书等其它路径：物品能不能吃这条附魔。 */
    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void gr$canEnchant(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ScytheInterop.allows((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(true);
        }
    }
}
