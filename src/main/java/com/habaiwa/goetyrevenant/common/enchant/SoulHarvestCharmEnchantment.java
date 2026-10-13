package com.habaiwa.goetyrevenant.common.enchant;

import com.habaiwa.goetyrevenant.init.ModTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * **聚魂护符** —— 击杀时增加灵魂能量获取。
 *
 * <p>用户 2026-09-27 的完整规格：</p>
 * <blockquote>
 *   聚魂护符修改为增加获取灵魂能量的护符，**增加 5%**，
 *   适用物品：**精魂收割物品**（本模组与 Iron 的法术与魔法书的武器、工具）`#malum:soul_hunter_weapon`，
 *   **最大等级：II**，概述：增加击杀敌人时的精魂掉落。
 * </blockquote>
 *
 * <h3>适用判定走标签，不走 EnchantmentCategory</h3>
 * <p>{@code EnchantmentCategory} 是枚举、{@code create(...)} 又是 protected，
 * 而我们本来也不需要新枚举 —— 直接重写 {@link #canEnchant(ItemStack)}：</p>
 * <ul>
 *   <li>物品在 {@code #malum:soul_hunter_weapon} 里 → 可以附</li>
 *   <li>否则退回父类行为（保持和原版一致）</li>
 * </ul>
 * <p>这样也顺带让"能附魔到物品栏外的东西"这件事归零 —— 只有那个标签里的物品认它。</p>
 *
 * <h3>为什么按附魔做、不做成饰品</h3>
 * <p>规格里的「适用物品 / 最大等级 / 概述」三个词都是**附魔**的词。
 * 之前把它做成了一个叫 SOUL_HARVEST_CHARM 的物品，那个物品因此完全没有效果。</p>
 */
public class SoulHarvestCharmEnchantment extends Enchantment {
    /** 每级增加的灵魂能量获取比例（用户指定 5%）。 */
    public static final float BONUS_PER_LEVEL = 0.05F;

    public SoulHarvestCharmEnchantment() {
        // category 只是占位 —— canEnchant 被我们重写了，不看它
        super(Rarity.UNCOMMON, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        if (!stack.isEmpty() && stack.is(ModTags.SOUL_HUNTER)) {
            return true;
        }
        return super.canEnchant(stack);
    }

    /** 附魔台也要认它（否则只能靠铁砧）。 */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return this.canEnchant(stack);
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    /** 按用户要求最高 II 级；等级越高越难出。 */
    @Override
    public int getMinCost(int level) {
        return 12 + (level - 1) * 15;
    }

    @Override
    public int getMaxCost(int level) {
        return super.getMinCost(level) + 40;
    }

    /**
     * 这把武器上的聚魂护符等级对应的加成倍率。
     *
     * <p>0 级 = 1.0（无加成）；I 级 = 1.05；II 级 = 1.10。</p>
     */
    public static float bonusOf(ItemStack stack, Enchantment enchantment) {
        if (stack.isEmpty() || enchantment == null) {
            return 1.0F;
        }
        int level = stack.getEnchantmentLevel(enchantment);
        return 1.0F + BONUS_PER_LEVEL * level;
    }
}
