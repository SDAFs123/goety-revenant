package com.habaiwa.goetyrevenant.init;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.block.BloodVesselBlock;
import com.habaiwa.goetyrevenant.common.block.DevourPedestalBlock;
import com.habaiwa.goetyrevenant.common.block.FleshAltarBlock;
import com.habaiwa.goetyrevenant.common.block.FleshCageBlock;
import com.habaiwa.goetyrevenant.common.block.FleshCageBlockEntity;
import com.habaiwa.goetyrevenant.common.block.SpiritConverterBlock;
import com.habaiwa.goetyrevenant.common.block.SpiritConverterBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方块与方块实体注册。 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, GoetyRevenant.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, GoetyRevenant.MODID);

    /** **魂白方块**：纯白 + 自发光。那片白空间的地与墙，以及可以拿走的建材。 */
    public static final RegistryObject<Block> WHITE_LIGHT = BLOCKS.register("white_light",
            () -> new com.habaiwa.goetyrevenant.common.block.WhiteLightBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.SNOW)
                            .strength(1.6F)
                            .lightLevel(state -> 15)));

    /** 精魄转化方块：骑在诅咒之笼上方，把笼子里的灵魂能量换成精魄。 */
    public static final RegistryObject<Block> SPIRIT_CONVERTER = BLOCKS.register("spirit_converter",
            () -> new SpiritConverterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /** 血肉之笼：血肉祭坛的供能笼，放在祭坛正下方。心脏进去，血肉能量出来。 */
    public static final RegistryObject<Block> FLESH_CAGE = BLOCKS.register("flesh_cage",
            () -> new FleshCageBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .strength(4.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /** 吞噬基座：血肉仪式摆材料的地方（本体基座不算数）。 */
    public static final RegistryObject<Block> DEVOUR_PEDESTAL = BLOCKS.register("devour_pedestal",
            () -> new DevourPedestalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    /** 血管：把每个吞噬基座接到血肉祭坛上。 */
    public static final RegistryObject<Block> BLOOD_VESSEL = BLOCKS.register("blood_vessel",
            () -> new BloodVesselBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.CRIMSON_HYPHAE)
                    .strength(2.0F)
                    .noOcclusion()));

    /** 血肉祭坛：血肉仪式的专属祭坛（只认血肉之笼）。 */
    public static final RegistryObject<Block> FLESH_ALTAR = BLOCKS.register("flesh_altar",
            () -> new FleshAltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .strength(5.0F)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<BlockEntityType<SpiritConverterBlockEntity>> SPIRIT_CONVERTER_BE =
            BLOCK_ENTITIES.register("spirit_converter",
                    () -> BlockEntityType.Builder.of(SpiritConverterBlockEntity::new, SPIRIT_CONVERTER.get()).build(null));

    /** 血肉之笼的方块实体（笼子自己的两个槽 + 嚼心脏的 tick）。 */
    public static final RegistryObject<BlockEntityType<FleshCageBlockEntity>> FLESH_CAGE_BE =
            BLOCK_ENTITIES.register("flesh_cage",
                    () -> BlockEntityType.Builder.of(FleshCageBlockEntity::new, FLESH_CAGE.get()).build(null));

    private ModBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
