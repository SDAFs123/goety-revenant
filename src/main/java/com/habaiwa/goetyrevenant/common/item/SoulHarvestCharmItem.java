package com.habaiwa.goetyrevenant.common.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

/**
 * 聚魂护符。
 *
 * <p>佩戴（带在身上即可）后击杀生物：按**怪物强弱**产出 Malum 的**精魄**，
 * 同时回充**大量诡厄巫法灵魂能量**。这是精魄的唯一来源 ——
 * Malum 原本的怪物掉落会被我们拦掉（见 {@code compat.MalumCompat}）。</p>
 */
public class SoulHarvestCharmItem extends Item {
    public SoulHarvestCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goety_revenant.soul_harvest_charm.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.goety_revenant.soul_harvest_charm.slot").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
