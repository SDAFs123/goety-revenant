package com.habaiwa.goetyrevenant.common.compat;

import com.habaiwa.goetyrevenant.common.soul.SoulData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 与 <b>Enigmatic Legacy（神秘遗物）</b> 的兼容。
 *
 * <p>modId `enigmaticlegacy`。它**没有自定义生物**，所以灵魂感知标签不需要加它；
 * 需要处理的是它的**死亡保护类物品** —— 这类物品必须先于回魂生效。</p>
 *
 * <p><b>我们的机制天然让路</b>：回魂挂在 {@code LivingDeathEvent} 的
 * {@link EventPriority#LOWEST}，而且**先检查 {@code isCanceled()}** ——
 * 只要神秘遗物（或任何模组）把死亡取消了、或者在 {@code LivingHurtEvent} 阶段就把伤害挡掉了，
 * 回魂根本不会触发。这里再补一层显式确认。</p>
 *
 * <p>它的灵魂系物品（灵魂水晶 / 灵魂尘 / 灵液瓶 / 被诅咒者的寻路指针）目前不参与回魂机制，
 * 需要联动的话再单独设计。</p>
 */
public final class EnigmaticLegacyCompat {
    public static final String MOD_ID = "enigmaticlegacy";

    private static final Logger LOGGER = LogUtils.getLogger();

    private EnigmaticLegacyCompat() {
    }

    public static boolean loaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    public static void init() {
        if (loaded()) {
            LOGGER.info("[Goety: Revenant] 联动「神秘遗物」(Enigmatic Legacy) 已启用："
                    + "它的死亡保护优先于回魂（LOWEST 优先级 + isCanceled 检查）");
        }
    }

    /**
     * 兜底确认：如果进入本方法时死亡已被取消，说明是别的模组救下了玩家，回魂必须让路。
     * （真正的回魂判定在 {@code RevenantEvents.onLivingDeath}，同样是 LOWEST 优先级。）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeathGuard(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !loaded()) {
            return;
        }
        if (event.isCanceled() && SoulData.isSoul(player)) {
            LOGGER.debug("[Goety: Revenant] 死亡已被取消，回魂让路：{}", player.getGameProfile().getName());
        }
    }
}
