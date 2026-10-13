package com.habaiwa.goetyrevenant.init;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.habaiwa.goetyrevenant.common.entity.RevenantEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 回魂系生物注册。 */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GoetyRevenant.MODID);

    /** 回魂者：回魂主题的基础亡灵生物。 */
    public static final RegistryObject<EntityType<RevenantEntity>> REVENANT = ENTITY_TYPES.register("revenant",
            () -> EntityType.Builder.of(RevenantEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(new ResourceLocation(GoetyRevenant.MODID, "revenant").toString()));

    /** 玩家尸体：保留皮肤与全部装备，是回魂机制的「身体」。 */
    public static final RegistryObject<EntityType<CorpseEntity>> CORPSE = ENTITY_TYPES.register("corpse",
            () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(GoetyRevenant.MODID, "corpse").toString()));

    private ModEntities() {
    }

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }

    /** 实体属性必须在 mod bus 的 EntityAttributeCreationEvent 里绑定。 */
    @Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Attributes {
        private Attributes() {
        }

        @SubscribeEvent
        public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
            event.put(REVENANT.get(), RevenantEntity.createAttributes().build());
            event.put(CORPSE.get(), CorpseEntity.createAttributes().build());
        }
    }
}
