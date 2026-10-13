package com.habaiwa.goetyrevenant.client.event;

import com.habaiwa.goetyrevenant.common.tooltip.HighlightHook;

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
        com.habaiwa.goetyrevenant.common.tooltip.HighlightHook.set(new com.habaiwa.goetyrevenant.client.render.ClientHighlight());
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
        // **注意：玩家的渲染器不能在这里注册。**
        // 之前这里做过 registerEntityRenderer(EntityType.PLAYER, ...) 外加一次泛型强转 ——
        // 那条路走不通：玩家的渲染走的是【皮肤渲染器映射】（default / slim 两套），
        // 普通实体类型注册分发不到那里。强转泛型也解决不了分发机制的问题。
        // 玩家渲染的接入改在下面这个事件里做。
    }

    /**
     * **借一次渲染上下文** —— 之后自己造玩家渲染器要用它。
     *
     * <p>Forge 没有提供"替换玩家渲染器"的接口（AddLayers 只给 getSkin() 这个 getter，
     * 内部的 skinMap 是 private final）。所以我们不去替换它，
     * 而是从这里借它的 Context，自己造渲染器、在需要接管的时候自己调用。</p>
     */
    @SubscribeEvent
    public static void onAddLayers(
            net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) {
        com.habaiwa.goetyrevenant.client.render.RevenantPlayerRenderer.captureContext(
                event.getContext());
    }

    /**
     * **玩家渲染的接管点** —— 只在需要的时候介入。
     *
     * <p>绝大多数玩家（没被附身、也不是别人的容器）走到第一行就返回了，
     * 完全走原版那条路，一点不受影响。只有两种情况下我们才接管：</p>
     * <ul>
     *   <li>这个玩家被附身了生物 → 要画成那个生物</li>
     *   <li>这个玩家被附身了别人 → 要用对方的皮肤</li>
     * </ul>
     *
     * <p>接管是"取消原版 + 我们自己调渲染器"。<b>代价</b>：其他模组给玩家加的渲染层
     * （护甲以外的那些附加层）在这一帧也不会被画 —— 这是替换渲染的固有代价，
     * 所以只对"确实变了样子的玩家"这么做。</p>
     */
}
