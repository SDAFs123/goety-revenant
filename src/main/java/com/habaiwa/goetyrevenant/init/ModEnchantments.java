package com.habaiwa.goetyrevenant.init;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 本模组自己的附魔。 */
public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, GoetyRevenant.MODID);

    /**
     * **聚魂护符** —— 击杀时增加灵魂能量获取。
     *
     * <p>用户 2026-09-27：「聚魂护符修改为增加获取灵魂能量的护符，增加 5%，
     * 适用物品：精魂收割物品 `#malum:soul_hunter_weapon`，最大等级：II」。</p>
     */
    public static final RegistryObject<Enchantment> SOUL_HARVEST_CHARM =
            ENCHANTMENTS.register("soul_harvest_charm",
                    com.habaiwa.goetyrevenant.common.enchant.SoulHarvestCharmEnchantment::new);

    private ModEnchantments() {
    }

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }
}
