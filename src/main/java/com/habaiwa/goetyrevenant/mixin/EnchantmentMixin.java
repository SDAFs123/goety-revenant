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
 * <p>1.20.1 里"某条附魔能不能附到某个物品上"由
 * {@code Enchantment.canEnchant(ItemStack)}（原版）
 * 和 {@code Enchantment.canApplyAtEnchantingTable(ItemStack)}（**Forge 加的**）决定，
 * 两者的默认实现都读 {@code EnchantmentCategory}。附魔在对方模组里、我们改不了它的实现，
 * Forge 1.20.1 也没有"附魔适用性"事件。</p>
 *
 * <h3>为什么不用数据包标签</h3>
 * <p>Malum 的附魔类别是 {@code EnchantmentCategory.create(name, i -> i.is(#malum:scythe))}，
 * 所以理论上"往 `data/malum/tags/items/scythe.json` 里追加我们的镰刀"就能打通 ——
 * 那三个文件现在还留在项目里当双保险。</p>
 *
 * <p>但**用户实测没生效**（「灵灾的附魔附魔不到诡厄巫法镰刀上」），
 * 而同一时间 Goety 的噬魂**能**附到 Malum 镰刀上（那条走 `EnchantmentCategory.WEAPON`，
 * 根本不看标签）—— 反证了跨命名空间追加标签这条路在当前环境不可靠。</p>
 *
 * <h3>⚠️ 方法名为什么要写成 {@code m_6081_}</h3>
 * <p><b>本机构建离线，装不了 `org.spongepowered.mixin` 那个 Gradle 插件，因此没有 refmap。</b>
 * 而生产环境跑的是**混淆名**，不是 MCP 名 —— 写 `"canEnchant"` 会在运行时找不到方法，
 * Mixin 静默不注入（配置里 `required: false`），表现就是"什么都没发生"。</p>
 *
 * <p>两个名字是这么确定的（直接从整合包里那份 SRG jar 读的）：</p>
 * <pre>
 *   client-1.20.1-20230612.114412-srg.jar → net/minecraft/world/item/enchantment/Enchantment
 *     public boolean m_6081_(net.minecraft.world.item.ItemStack);   ← 原版的 canEnchant
 *   （canApplyAtEnchantingTable 不在原版 jar 里 —— 它是 Forge 加的方法，
 *     不参与混淆，运行时名字就是它本身）
 * </pre>
 *
 * <p>所以两处都得加 {@code remap = false}：告诉 Mixin 这个名字**已经是对得上的**，别再映射。</p>
 *
 * <p>⚠️ 代价：**开发环境（`runClient`）下这条 Mixin 不会生效** —— 那边用的是 MCP 名。
 * 本项目只做 `build`，不影响出货。</p>
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    /** 原版的 {@code canEnchant(ItemStack)}（SRG: {@code m_6081_}）。 */
    @Inject(method = "m_6081_", at = @At("HEAD"), cancellable = true, remap = false)
    private void gr$canEnchant(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ScytheInterop.allows((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(true);
        }
    }

    /** Forge 加的 {@code canApplyAtEnchantingTable(ItemStack)}（不混淆，用原名）。 */
    @Inject(method = "canApplyAtEnchantingTable", at = @At("HEAD"), cancellable = true, remap = false)
    private void gr$canApplyAtEnchantingTable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ScytheInterop.allows((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(true);
        }
    }
}
