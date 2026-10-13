package com.habaiwa.goetyrevenant.client;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * **挂在 FORGE 事件总线上的客户端事件**。
 *
 * <h3>为什么单独一个类</h3>
 * <p>Forge 有两条事件总线，混用会直接让模组加载失败：</p>
 * <ul>
 *   <li><b>模组总线</b>（{@code Bus.MOD}）：注册物品、渲染器、能力…… 事件类型都
 *       {@code implements IModBusEvent}。{@code EntityRenderersEvent} 就属于这条。</li>
 *   <li><b>FORGE 总线</b>（默认）：游戏运行中发生的事 —— 渲染、tick、玩家进出。
 *       {@code RenderPlayerEvent} 属于这条。</li>
 * </ul>
 *
 * <p>把 FORGE 总线的事件写进 {@code Bus.MOD} 的类里，会在加载时抛
 * {@code IllegalArgumentException}（"方法签名不匹配"），整个模组起不来。
 * 这条踩过一次，所以分开放。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {
    }

    /**
     * **玩家渲染的接管点** —— 只在需要的时候介入。
     *
     * <p>绝大多数玩家（没被附身、也不是别人的容器）走到第一行就返回了，
     * 完全走原版那条路，一点不受影响。只有两种情况我们才接管：</p>
     * <ul>
     *   <li>这个玩家被附身了生物 → 要画成那个生物</li>
     *   <li>这个玩家被附身了别人 → 要用对方的皮肤</li>
     * </ul>
     *
     * <p>接管是"取消原版 + 我们自己调渲染器"。代价：其他模组给玩家加的渲染层
     * （护甲以外的那些附加层）在这一帧也不会被画 —— 这是替换渲染的固有代价，
     * 所以只对"确实变了样子的玩家"这么做。</p>
     *
     * <p>另外：玩家的渲染器<b>不能</b>用 {@code registerEntityRenderer(EntityType.PLAYER, ...)}
     * 注册 —— 玩家渲染走的是皮肤渲染器映射（default / slim 两套），普通实体类型注册
     * 分发不到那里；Forge 也没给"替换它"的接口。所以只能在这里接管。</p>
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player rawPlayer = event.getEntity();
        if (!(rawPlayer instanceof AbstractClientPlayer player)) {
            return;
        }
        if (!com.habaiwa.goetyrevenant.client.render.RevenantPlayerRenderer.needsOverride(player)) {
            return;   // 不需要接管 → 原版自己画
        }
        event.setCanceled(true);
        com.habaiwa.goetyrevenant.client.render.RevenantPlayerRenderer renderer =
                com.habaiwa.goetyrevenant.client.render.RevenantPlayerRenderer.forPlayer(player);
        renderer.render(player, player.getYRot(), event.getPartialTick(),
                event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }
}
