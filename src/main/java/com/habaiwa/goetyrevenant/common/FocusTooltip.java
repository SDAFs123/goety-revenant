package com.habaiwa.goetyrevenant.common;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.habaiwa.goetyrevenant.common.body.FocusPassive;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * **所有聚晶的 tooltip** —— 每一枚都告诉你「装进身体能拿什么被动」。
 *
 * <p>走 Forge 的 {@link ItemTooltipEvent}，**不需要给任何一个聚晶加 Mixin、
 * 也不用管它是哪个模组加的** —— 只要是 `IFocus`，鼠标一放上去就会多出：</p>
 * <pre>
 * 嵌入「心（火）」· 稀有 ×1.70
 *   · 战斗力 +3.5      ← 被火窍扭曲后的主被动
 *   · 行动 +2.5%       ← 聚晶本职，打了对折
 * 需要先喝「开窍魔药 · 火」开窍
 * </pre>
 *
 * <p>被动由 {@link FocusPassive} 算：**本职方向**四级查找 + **等级倍率**（越难做越强）
 * + **元素扭曲**（进哪个窍就按那个窍的性子扭一下）。</p>
 */
// 注意：这个类【已经在 GoetyRevenant.java 里手动注册】了
// （MinecraftForge.EVENT_BUS.register(FocusTooltip.class)）——
// 不要再加 @Mod.EventBusSubscriber，那会注册两次、tooltip 显示两遍。
public final class FocusTooltip {
    private FocusTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof IFocus)) {
            return;
        }
        OrganType organ = OrganType.forSpellType(FocusPassive.spellTypeOf(stack));
        int tier = FocusPassive.tierOf(stack);

        List<Component> lines = event.getToolTip();
        lines.add(Component.empty());
        lines.add(Component.translatable("tooltip.goety_revenant.focus.header",
                        organ.displayName(),
                        Component.translatable("tier.goety_revenant." + tier),
                        String.format("%.2f", FocusPassive.tierMultiplier(stack)))
                .withStyle(ChatFormatting.AQUA));

        FocusPassive.Category twisted = FocusPassive.twistedCategory(stack, organ);
        FocusPassive.Category own = FocusPassive.resolve(stack).category();
        for (Map.Entry<FocusPassive.Category, Double> entry : FocusPassive.grants(stack, organ)) {
            boolean isTwist = entry.getKey() == twisted && twisted != own;
            lines.add(Component.translatable("tooltip.goety_revenant.focus.line",
                            entry.getKey().displayName(), entry.getKey().format(entry.getValue()))
                    .append(Component.translatable(isTwist
                                    ? "tooltip.goety_revenant.focus.twisted"
                                    : "tooltip.goety_revenant.focus.own")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(isTwist ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }

        lines.add(Component.translatable("tooltip.goety_revenant.focus.need_brew", organ.brewName())
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
