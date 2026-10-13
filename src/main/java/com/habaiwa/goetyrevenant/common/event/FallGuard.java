package com.habaiwa.goetyrevenant.common.event;

import com.habaiwa.goetyrevenant.GoetyRevenant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * **从天而降，但摔不着。**
 *
 * <p>之前是"进去时给 6 秒无敌 + 每 tick 倒计时解除" —— 那套依赖 tick 真的在跑，
 * 任何一环没跑到就会在主世界算上真实的下落距离。现在改成<b>直接拦掉落伤事件</b>：
 * 只要这个人正在"进那片白"的过程里（还没落地），摔落伤害就直接取消。</p>
 *
 * <p>这样不依赖 tick、不依赖原版的 invulnerable 标记，最直白也最可靠。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID)
public final class FallGuard {
    /** 还在落向那片白的路上（服务端标记）。 */
    public static final String KEY_FALLING = "GRFallingIn";

    private FallGuard() {
    }

    /** 开始下落（进维度那一刻调用）。 */
    public static void beginFall(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_FALLING, true);
    }

    /** 落地/离开时清掉。 */
    public static void endFall(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_FALLING, false);
    }

    public static boolean falling(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY_FALLING);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (falling(player)) {
            event.setCanceled(true);
            endFall(player);
        }
    }
}
