package com.habaiwa.goetyrevenant.client;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.client.render.CorpseRenderer;
import com.habaiwa.goetyrevenant.client.render.RevenantRenderer;
import com.habaiwa.goetyrevenant.client.render.RevenantPlayerRenderer;
import com.habaiwa.goetyrevenant.init.ModEntities;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端注册（渲染器等）。 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // 灵魂视角高亮自己尸体的钩子
        com.habaiwa.goetyrevenant.common.HighlightHook.set(new com.habaiwa.goetyrevenant.client.ClientHighlight());
    }

    /**
     * 开窍魔药的瓶身染色。
     *
     * <p>模型是「底图 = 魔药瓶 + 覆盖层 = 原版 `potion_overlay`」，这里给覆盖层上色，
     * 所以六瓶魔药一眼能分清（心红 / 肝绿 / 脾黄 / 肺白 / 肾蓝 / 灵窍紫）。</p>
     */
    // 开窍魔药那套已经取消，瓶身染色也跟着删了。

    /** 灵魂 HUD（血条 = 灵魂能量 / 契合度 / 弹出倒计时）。 */
    @SubscribeEvent
    public static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        // **那句话**（分三段说，戴着莫测之眼才听得见）——
        // 之前它写好了却没人调用，所以字不出现、声音也不响
        event.registerAboveAll("goety_revenant_whisper",
                new com.habaiwa.goetyrevenant.client.fx.WhisperOverlay());
        event.registerAboveAll("goety_revenant_soul",
                new com.habaiwa.goetyrevenant.client.overlay.SoulHudOverlay());
        // 觉醒那一下的中央提示（几秒后自己散掉）
        event.registerAboveAll("goety_revenant_awaken",
                new com.habaiwa.goetyrevenant.client.overlay.AwakenOverlay());
        // 神识：七窍的血点 + 低能量时的血色暗角 / 心跳（不用看界面也能知道情况）
        event.registerAboveAll("goety_revenant_shenshi",
                new com.habaiwa.goetyrevenant.client.overlay.ShenShiOverlay());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.REVENANT.get(), RevenantRenderer::new);
        event.registerEntityRenderer(ModEntities.CORPSE.get(), CorpseRenderer::new);
        // 覆写原版玩家渲染器，好让「附身他人」能显示对方的皮肤。
        // EntityType.PLAYER 是 EntityType<Player>，而玩家渲染器的泛型是 AbstractClientPlayer，这里转换一次。
        @SuppressWarnings("unchecked")
        EntityType<AbstractClientPlayer> playerType = (EntityType<AbstractClientPlayer>) (EntityType<?>) EntityType.PLAYER;
        event.registerEntityRenderer(playerType, context -> new RevenantPlayerRenderer(context, false));
    }
}
