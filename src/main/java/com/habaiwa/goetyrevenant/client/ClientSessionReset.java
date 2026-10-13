package com.habaiwa.goetyrevenant.client;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * **客户端会话状态的统一清理**。
 *
 * <h3>为什么需要这个</h3>
 * <p>客户端有一批用静态字段保存的"从服务器同步过来"的状态：
 * 灵魂状态、玩家状态（含"这一阶见没见过"）、皮肤覆盖、附身外观、灵魂名单、问答房间。
 * 它们是静态的，所以<b>退出世界不会自己清</b>。</p>
 *
 * <p>后果是：退出服务器 A、进入服务器 B（或换一个存档），在 B 的第一个同步包
 * 到达之前，画面用的还是 A 的状态 —— 旧的 HUD、旧的附身外观、旧的问答房间。
 * 如果新会话恰好没发对应的清除消息，这些缓存会留得更久。</p>
 *
 * <p>其中 {@code BodyClientState.seenStage} 还有个具体表现：
 * 它记录"这个阶我已经看过生长动画了"。带着上一个存档的记录进新存档，
 * 如果两边阶段相同，新存档的第一次生长动画会被误判成"已经看过"而不播。</p>
 *
 * <h3>做法</h3>
 * <p>在 <b>LoggingOut</b>（断开连接）时把上面这些全部复位到"未初始化"。
 * 之后要么收到新会话的同步包填上，要么就一直保持未初始化状态 ——
 * 两种都比"用着上一个存档的数据"好。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID, value = Dist.CLIENT)
public final class ClientSessionReset {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private ClientSessionReset() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetAll();
    }

    /** 把所有客户端会话状态复位。 */
    public static void resetAll() {
        safe(ClientSoulState::reset, "ClientSoulState");
        safe(BodyClientState::reset, "BodyClientState");
        safe(ClientSkinOverride::reset, "ClientSkinOverride");
        safe(PossessedAppearance::reset, "PossessedAppearance");
        safe(ClientSoulRegistry::reset, "ClientSoulRegistry");
        safe(QuizRoomClient::reset, "QuizRoomClient");
        // 代理实体绑着旧世界，带着走会出问题
        safe(com.habaiwa.goetyrevenant.client.render.PossessedProxyCache::reset, "PossessedProxyCache");
    }

    /** 一个失败不该影响其他几个。 */
    private static void safe(Runnable r, String name) {
        try {
            r.run();
        } catch (Throwable t) {
            LOG.warn("[回魂] 清理 {} 失败", name, t);
        }
    }
}
