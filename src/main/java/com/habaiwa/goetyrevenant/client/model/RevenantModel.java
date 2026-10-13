package com.habaiwa.goetyrevenant.client.model;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.entity.RevenantEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 回魂者的 GeckoLib 模型定义。 */
public class RevenantModel extends GeoModel<RevenantEntity> {
    @Override
    public ResourceLocation getModelResource(RevenantEntity animatable) {
        return new ResourceLocation(GoetyRevenant.MODID, "geo/revenant.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RevenantEntity animatable) {
        return new ResourceLocation(GoetyRevenant.MODID, "textures/entity/revenant.png");
    }

    @Override
    public ResourceLocation getAnimationResource(RevenantEntity animatable) {
        return new ResourceLocation(GoetyRevenant.MODID, "animations/revenant.animation.json");
    }
}
