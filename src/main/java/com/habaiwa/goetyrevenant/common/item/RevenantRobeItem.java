package com.habaiwa.goetyrevenant.common.item;

import com.Polarice3.Goety.api.items.armor.ISoulDiscount;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * 回魂长袍（四件套通用）。
 *
 * <p>实现诡厄巫法的 {@code ISoulDiscount}：穿戴后降低施法消耗的魂能，
 * 让整套长袍在诡厄巫法的法术体系里有实际收益（本体认这个接口）。</p>
 */
public class RevenantRobeItem extends ArmorItem implements ISoulDiscount {
    /** 单件提供的魂能折扣（百分比，4 件合计 20%）。 */
    private static final int SOUL_DISCOUNT = 5;

    public RevenantRobeItem(ArmorMaterial material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public int getSoulDiscount(EquipmentSlot slot) {
        return SOUL_DISCOUNT;
    }
}
