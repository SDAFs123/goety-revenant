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
 * **聚魂护符** —— 一个**饰品**，带在身上就让"击杀获得的灵魂能量 +5%"。
 *
 * <p>用户 2026-09-27 的规格：「聚魂护符修改为增加获取灵魂能量的**护符**增加 **5%**」
 * —— 参照 Malum 的 {@code spirit_plunder}（精魂劫掠）的作用方式，
 * 但给的是**诡厄巫法的灵魂能量**，不是精魄。</p>
 *
 * <p>⚠️ <b>它不产精魄。</b>用户同一天明确纠正过：「我不需要这样产精魄啊，
 * **全部只产生灵魂能量**，不过聚魂护符这个名字不错」——
 * 所以"按怪物强弱产精魄"那套已去掉，Malum 原本的精魄掉落由
 * {@code compat.MalumCompat.onDrops} 拦掉，击杀改给灵魂能量。</p>
 *
 * <p>带在身上即可（背包 / 副手 / Curios 饰品栏都算），**不需要拿在主手**。</p>
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
