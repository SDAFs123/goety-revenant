package com.habaiwa.goetyrevenant.init;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.item.RevenantRobeItem;
import com.habaiwa.goetyrevenant.common.item.CorpseStoreFocusItem;
import com.habaiwa.goetyrevenant.common.item.SoulHarvestCharmItem;
import com.habaiwa.goetyrevenant.common.item.SoulRecallFocusItem;
import com.habaiwa.goetyrevenant.common.embed.OrganType;
import com.habaiwa.goetyrevenant.common.item.RevenantStaffItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 回魂系物品：法杖 / 法器 / 长袍。 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GoetyRevenant.MODID);

    /** **魂白方块的物品形态**（能拿走、能铺）。 */
    public static final RegistryObject<Item> WHITE_LIGHT = ITEMS.register("white_light",
            () -> new net.minecraft.world.item.BlockItem(ModBlocks.WHITE_LIGHT.get(),
                    new Item.Properties()));

    /** 回魂法杖 —— 回魂玩法的核心法器，后续接 Goety 的法术/聚灵体系。 */
    public static final RegistryObject<Item> REVENANT_STAFF = ITEMS.register("revenant_staff",
            () -> new RevenantStaffItem(new Item.Properties().stacksTo(1).durability(512).rarity(Rarity.RARE)));

    /** 回魂符 —— 施法与仪式用的法器/材料。 */
    public static final RegistryObject<Item> SOUL_TALISMAN = ITEMS.register("soul_talisman",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    /** 回魂长袍四件套。 */
    public static final RegistryObject<Item> REVENANT_HOOD = ITEMS.register("revenant_hood",
            () -> new RevenantRobeItem(ModArmorMaterials.REVENANT, ArmorItem.Type.HELMET, new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> REVENANT_ROBE = ITEMS.register("revenant_robe",
            () -> new RevenantRobeItem(ModArmorMaterials.REVENANT, ArmorItem.Type.CHESTPLATE, new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> REVENANT_LEGGINGS = ITEMS.register("revenant_leggings",
            () -> new RevenantRobeItem(ModArmorMaterials.REVENANT, ArmorItem.Type.LEGGINGS, new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> REVENANT_BOOTS = ITEMS.register("revenant_boots",
            () -> new RevenantRobeItem(ModArmorMaterials.REVENANT, ArmorItem.Type.BOOTS, new Item.Properties().rarity(Rarity.RARE)));

    /** 回魂聚晶：插在回魂法杖上，消耗 10000 灵魂能量把队友的尸体召到身边。 */
    public static final RegistryObject<Item> SOUL_RECALL_FOCUS = ITEMS.register("soul_recall_focus",
            () -> new SoulRecallFocusItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** 纳尸聚晶：把尸体收进聚晶带走，再放出来。 */
    public static final RegistryObject<Item> CORPSE_STORE_FOCUS = ITEMS.register("corpse_store_focus",
            () -> new CorpseStoreFocusItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 聚魂护符：击杀生物产出大量灵魂能量。可挂 Curios 护符槽。 */
    public static final RegistryObject<Item> SOUL_HARVEST_CHARM = ITEMS.register("soul_harvest_charm",
            () -> new SoulHarvestCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /**
     * **染魂钢图腾** —— 灵魂游离状态扩容，一枚 +30000 上限。
     *
     * <p>灵魂能量上限是**相加**的：灵魂自己的 10000 + 方舟那一档 + 图腾 × 30000。</p>
     */
    public static final RegistryObject<Item> SOUL_STAINED_STEEL_TOTEM = ITEMS.register("soul_stained_steel_totem",
            () -> new com.habaiwa.goetyrevenant.common.item.SoulStainedSteelTotemItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 血肉之心 —— 血肉之笼的燃料。 */
    public static final RegistryObject<Item> FLESH_HEART = ITEMS.register("flesh_heart",
            () -> new com.habaiwa.goetyrevenant.common.item.FleshHeartItem(
                    new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    /** 血肉图腾 —— 血肉能量的容器（和染魂钢图腾是一对）。 */
    public static final RegistryObject<Item> FLESH_TOTEM = ITEMS.register("flesh_totem",
            () -> new com.habaiwa.goetyrevenant.common.item.FleshTotemItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 吞噬基座（物品形态）—— 血肉仪式摆材料的地方。 */
    public static final RegistryObject<Item> DEVOUR_PEDESTAL = ITEMS.register("devour_pedestal",
            () -> new net.minecraft.world.item.BlockItem(
                    com.habaiwa.goetyrevenant.init.ModBlocks.DEVOUR_PEDESTAL.get(),
                    new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** 血管（物品形态）—— 把基座接到祭坛上。 */
    public static final RegistryObject<Item> BLOOD_VESSEL = ITEMS.register("blood_vessel",
            () -> new net.minecraft.world.item.BlockItem(
                    com.habaiwa.goetyrevenant.init.ModBlocks.BLOOD_VESSEL.get(),
                    new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** 血肉之笼（物品形态）。 */
    public static final RegistryObject<Item> FLESH_CAGE = ITEMS.register("flesh_cage",
            () -> new net.minecraft.world.item.BlockItem(
                    com.habaiwa.goetyrevenant.init.ModBlocks.FLESH_CAGE.get(),
                    new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** 血肉祭坛（物品形态）。 */
    public static final RegistryObject<Item> FLESH_ALTAR = ITEMS.register("flesh_altar",
            () -> new net.minecraft.world.item.BlockItem(
                    com.habaiwa.goetyrevenant.init.ModBlocks.FLESH_ALTAR.get(),
                    new Item.Properties().rarity(Rarity.RARE)));

    /** 精魄转化方块（物品形态）。 */
    public static final RegistryObject<Item> SPIRIT_CONVERTER = ITEMS.register("spirit_converter",
            () -> new net.minecraft.world.item.BlockItem(
                    com.habaiwa.goetyrevenant.init.ModBlocks.SPIRIT_CONVERTER.get(),
                    new Item.Properties().rarity(Rarity.RARE)));

    // 开窍魔药那套（六瓶五行魔药 + 开窍流程）已经取消，不再注册。

    /** 回魂者刷怪蛋。 */
    public static final RegistryObject<Item> REVENANT_SPAWN_EGG = ITEMS.register("revenant_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.REVENANT, 0x2E1A47, 0x7B2FBE, new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
