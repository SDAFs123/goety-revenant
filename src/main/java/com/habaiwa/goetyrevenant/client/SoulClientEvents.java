package com.habaiwa.goetyrevenant.client;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.network.PossessRequestMessage;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 灵魂状态在客户端的三件事：**左键附身 / 穿墙 / 藏掉本体的灵魂能量 HUD**。
 *
 * <p>放在一个类里是因为它们都只跟「我现在是不是灵魂」有关，判断条件完全一样。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, value = Dist.CLIENT)
public final class SoulClientEvents {
    /**
     * 被当成灵魂状态来渲染的透明度 —— 已废弃：
     * 灵魂应该是**完全不可见**的（invisible 不渲染），不是半透明。
     * 留着只防旧存档/旧逻辑引用炸掉，新代码不要用它。
     */
    public static final float SOUL_ALPHA = 0.45F;

    /** 灵魂自己的第一人称**手臂不画** —— 和旁观者模式一样，看不见自己。 */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        if (ClientSoulState.isSoul()) {
            event.setCanceled(true);
        }
    }

    /** 隐身中的玩家（灵魂 / 灵化）**名字标签也不显示** —— 别人彻底看不到这个人。 */
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        net.minecraft.world.entity.Entity entity = event.getEntity();
        if (entity instanceof net.minecraft.world.entity.player.Player && entity.isInvisible()) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    private SoulClientEvents() {
    }

    /**
     * **左键 = 附身**。
     *
     * <p><b>为什么用这个事件而不是 `keyAttack.consumeClick()`</b>：
     * 原版的 `Minecraft#tick` 里 `handleKeybinds()` 会先把 `keyAttack` 的点击**全部 consume 掉**
     * 去执行挥拳/挖方块，而 `ClientTickEvent` 是**在那之后**才发的 ——
     * 所以在 tick 事件里 `consumeClick()` 永远是 false，**左键附身根本不会触发**。
     * 这个事件是在原版真正处理攻击**之前**发的，而且可以取消，正好。</p>
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!ClientSoulState.isSoul()) {
            return;
        }
        // 别真的挥拳/挖方块，只发附身请求
        event.setCanceled(true);
        RevenantNetwork.CHANNEL.sendToServer(new PossessRequestMessage());
    }

    /**
     * **灵魂穿墙**。
     *
     * <p>服务端在进灵魂状态时设了 `player.noPhysics = true`，但**这个字段不同步给客户端** ——
     * 客户端自己的 `LocalPlayer` 仍然按原版碰撞走，于是「服务端让你穿、客户端把你卡住」，
     * 表现就是**穿不了墙**。这里在客户端跟着设一遍（退出时复原）。</p>
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()) {
            // 原版旁观者自己的 noPhysics 由游戏模式管，别去动
            return;
        }
        boolean soul = ClientSoulState.isSoul();
        if (minecraft.player.noPhysics != soul) {
            minecraft.player.noPhysics = soul;
        }
    }

    /**
     * **藏掉本体（Goety）的灵魂能量 HUD**。
     *
     * <p>本体把 {@code SoulEnergyGui} 注册成 {@code goety:soul_energy_hud}，画的是**你身体里**
     * 的灵魂能量。可你现在是一缕魂，不在身体里 —— 那个数跟你没关系，右下角挂着一根
     * 灵魂方舟的能量条只会让人以为自己的灵魂血量变了。</p>
     *
     * <p>用 {@code RenderGuiOverlayEvent.Pre} 取消绘制：本体是用
     * {@code RegisterGuiOverlaysEvent} 注册的，注册之后没法注销，只能在这一层拦。</p>
     */
    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();
        if (!id.getNamespace().equals("goety") || !id.getPath().contains("soul")) {
            return;
        }
        // 取消原版条的情形：
        //   1) 灵魂状态（条上的数本来就不属于这缕魂），或
        //   2) 跳井解锁之后 —— 这条 HUD 改由 SoulHudOverlay 画，好让它**随灵魂温度变色**
        if (ClientSoulState.isSoul() || com.habaiwa.goetyrevenant.client.BodyClientState.unlocked()) {
            event.setCanceled(true);
        }
    }
}
