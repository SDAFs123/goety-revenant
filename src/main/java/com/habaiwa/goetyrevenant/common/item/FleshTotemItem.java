package com.habaiwa.goetyrevenant.common.item;

import com.habaiwa.goetyrevenant.common.flesh.FleshEnergy;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * **血肉图腾** —— 血肉能量的容器。
 *
 * <p>和 {@code 染魂钢图腾} 是一对：那个装灵魂，这个装血肉。
 * <b>有意不实现本体的 {@code ITotem}</b> —— 一旦实现了，本体的灵魂那一套就会把它当灵魂图腾用，
 * 两条能量池就串味了。</p>
 *
 * <p>认的位置和本体图腾一致：饰品槽 / 主副手 / 快捷栏 0-8。</p>
 */
public class FleshTotemItem extends Item implements ICurioItem {
    /** 一枚图腾默认的容量。 */
    public static final int CAPACITY = 30000;

    private final int capacity;

    public FleshTotemItem(Item.Properties properties, int capacity) {
        super(properties);
        this.capacity = capacity;
    }

    public FleshTotemItem(Item.Properties properties) {
        this(properties, CAPACITY);
    }

    public int capacity() {
        // 配置优先（整合包可控）；配置填 0 就用物品自己带的容量
        int configured = com.habaiwa.goetyrevenant.common.config.RevenantConfig.fleshTotemCapacity;
        return configured > 0 ? configured : this.capacity;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // 和本体图腾一样：第一次摸到它就把 NBT 铺好
        if (!stack.hasTag() || !stack.getTag().contains(FleshEnergy.KEY)) {
            FleshEnergy.set(stack, FleshEnergy.get(stack));
        }
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int cap = capacity();
        return cap <= 0 ? 0 : Math.round(FleshEnergy.get(stack) * 13.0F / cap);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float cap = capacity();
        float f = cap <= 0 ? 0.0F : Math.max(0.0F, 1.0F - FleshEnergy.get(stack) / cap);
        // 越空越红 —— 血肉的配色，和灵魂那条青色的区分开
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goety_revenant.flesh_totem.desc",
                FleshEnergy.get(stack), capacity()).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.goety_revenant.flesh_totem.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
