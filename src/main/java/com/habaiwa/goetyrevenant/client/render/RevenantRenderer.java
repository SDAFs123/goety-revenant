package com.habaiwa.goetyrevenant.client.render;

import com.habaiwa.goetyrevenant.client.model.RevenantModel;
import com.habaiwa.goetyrevenant.common.entity.RevenantEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 回魂者渲染器（GeckoLib）。 */
public class RevenantRenderer extends GeoEntityRenderer<RevenantEntity> {
    public RevenantRenderer(EntityRendererProvider.Context context) {
        super(context, new RevenantModel());
        this.shadowRadius = 0.5F;
    }
}
