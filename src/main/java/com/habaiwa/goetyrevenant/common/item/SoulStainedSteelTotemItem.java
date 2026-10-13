package com.habaiwa.goetyrevenant.common.item;

import com.Polarice3.Goety.api.items.magic.ITotem;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * **染魂钢图腾** —— 一枚能装 {@value #CAPACITY} 灵魂能量的图腾。
 *
 * <h3>照着诡厄巫法自己的图腾写</h3>
 * <p>实现结构和本体的 {@code TotemOfSouls} **一模一样**：</p>
 * <pre>
 * class TotemOfSouls extends Item implements ITotem, ICurioItem {
 *     public int maxSouls;
 *     public int getMaxSouls() { return this.maxSouls; }
 *     public void inventoryTick(...) { this.setTagTick(stack); ... }   // 初始化 / 夹紧 NBT
 *     public void onCraftedBy(...)   { ITotem.setSoulsAmount / setMaxSoulAmount }
 *     public boolean isBarVisible / int getBarWidth / getBarColor       // 装了多少的条
 * }
 * </pre>
 *
 * <p><b>为什么必须实现 `ITotem`</b>：本体判定「这是不是图腾」就是
 * {@code TotemFinder.isTotem} → `itemStack.getItem() instanceof ITotem`。
 * 不实现它，本体那一整套（找图腾、灵魂容量、不死效果）都认不出我们这个东西。</p>
 *
 * <p>灵魂能量**存在图腾自己的 NBT 里**（`Souls` / `Max Souls`），
 * 这是本体的做法 —— 所以它同时也是一个正经的「灵魂容器」。</p>
 */
public class SoulStainedSteelTotemItem extends Item implements ITotem, ICurioItem {
    /** 一枚图腾的容量。 */
    public static final int CAPACITY = 30000;

    public SoulStainedSteelTotemItem(Item.Properties properties) {
        super(properties);
    }

    /** 容量 —— 本体的 {@code ITotem} 就是靠这个方法知道你能装多少。 */
    @Override
    public int getMaxSouls() {
        return CAPACITY;
    }

    /** 装了多少 / 能装多少，靠 NBT 初始化（和本体一样在 inventoryTick 里铺一次）。 */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        this.setTagTick(stack);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    /** 合成出来就是空图腾（0 / 容量）。 */
    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        ITotem.setSoulsAmount(stack, 0);
        ITotem.setMaxSoulAmount(stack, this.getMaxSouls());
        super.onCraftedBy(stack, level, player);
    }

    // ===== 装了多少的条（本体图腾的标志性外观）=====

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int max = maxOf(stack);
        return max <= 0 ? 0 : Math.round(currentOf(stack) * 13.0F / max);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int max = maxOf(stack);
        float f = max <= 0 ? 0.0F : Math.max(0.0F, 1.0F - currentOf(stack) / (float) max);
        // 越空越红、越满越绿（和本体一样的 hsv 画法）
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    private static int currentOf(ItemStack stack) {
        return ITotem.currentSouls(stack);
    }

    private static int maxOf(ItemStack stack) {
        int max = ITotem.maximumSouls(stack);
        if (max <= 0 && stack.getItem() instanceof ITotem totem) {
            max = totem.getMaxSouls();
        }
        return max;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goety_revenant.soul_stained_steel_totem.desc",
                        currentOf(stack), maxOf(stack))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.goety_revenant.soul_stained_steel_totem.capacity", CAPACITY)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.goety_revenant.soul_stained_steel_totem.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
