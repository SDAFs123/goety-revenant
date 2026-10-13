package com.habaiwa.goetyrevenant.init;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 回魂的实体标签。
 *
 * <p>关键联动接口：任何模组（含闭源的诡厄巫法附属）只要把自己的生物写进
 * `goety_revenant:possessable`，那具身体就能被灵魂附身 —— 不需要我们认识它们的类。</p>
 */
public final class ModTags {
    /** 可以被灵魂附身的身体。 */
    public static final TagKey<EntityType<?>> POSSESSABLE =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(GoetyRevenant.MODID, "possessable"));

    /**
     * 统一镰刀标签。
     *
     * <p>1.20.1 的**任何镰刀**只要写进这个标签，就共享两套镰刀特性
     * （Goety 侧的灵魂能量/连锁破坏，Malum 侧的收割/附魔）。
     * 以后整合包加新镰刀，改一行 JSON 就适配，不用改代码。</p>
     */
    public static final TagKey<net.minecraft.world.item.Item> SCYTHES =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    new ResourceLocation(GoetyRevenant.MODID, "scythes"));

    /** **完全察觉**：本身就是灵体，无视距离能看见并攻击灵魂。 */
    public static final TagKey<EntityType<?>> SOUL_SEERS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(GoetyRevenant.MODID, "soul_seers"));

    /** **近距离察觉**：靠近一定范围才看得见灵魂（亡灵类 / 使用诡厄巫法力量的）。 */
    public static final TagKey<EntityType<?>> SOUL_SENSORS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(GoetyRevenant.MODID, "soul_sensors"));

    private ModTags() {
    }
}
