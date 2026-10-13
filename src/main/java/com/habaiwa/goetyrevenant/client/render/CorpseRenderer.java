package com.habaiwa.goetyrevenant.client.render;

import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * 尸体渲染器：用玩家模型 + 玩家皮肤 + 护甲层，所以尸体看起来就是「躺在那里的你」，
 * 装备原样穿在身上。
 */
public class CorpseRenderer extends LivingEntityRenderer<CorpseEntity, PlayerModel<CorpseEntity>> {
    private static final UUID FALLBACK = new UUID(0L, 0L);

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    /**
     * 让尸体**躺下**而不是站着。
     *
     * <p>绕 X 轴转 90° 把直立模型放平，再往下挪一点贴地。
     * 如果位置/朝向不对，调这两个数字即可。</p>
     */
    @Override
    protected void setupRotations(CorpseEntity entity, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                  float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0F));
        poseStack.translate(0.0D, -1.05D, 0.0D);
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity entity) {
        UUID owner = entity.getOwnerUUID() == null ? FALLBACK : entity.getOwnerUUID();
        GameProfile profile = new GameProfile(owner, entity.getOwnerName());
        // ★ 关键：把皮肤属性带进 profile，否则 getInsecureSkinLocation 只会返回默认皮肤
        String value = entity.getSkinValue();
        if (value != null && !value.isEmpty()) {
            profile.getProperties().put("textures",
                    new com.mojang.authlib.properties.Property("textures", value, entity.getSkinSignature()));
        }
        try {
            return Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(profile);
        } catch (Throwable ignored) {
            return DefaultPlayerSkin.getDefaultSkin(owner);
        }
    }
}
