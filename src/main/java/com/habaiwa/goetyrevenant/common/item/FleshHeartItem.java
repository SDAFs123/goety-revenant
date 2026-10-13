package com.habaiwa.goetyrevenant.common.item;

import com.habaiwa.goetyrevenant.common.flesh.FleshEnergy;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * **血肉之心** —— 血肉之笼的燃料。
 *
 * <p>血肉之笼里放一颗它，笼子就把它"嚼碎"，榨出 {@link FleshEnergy#PER_HEART} 点血肉能量。
 * 血肉仪式每 20 tick 从笼子里扣一次能量，笼子空了仪式就断。</p>
 */
public class FleshHeartItem extends Item {

    public FleshHeartItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goety_revenant.flesh_heart.desc",
                com.habaiwa.goetyrevenant.common.config.RevenantConfig.fleshPerHeart).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.goety_revenant.flesh_heart.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
