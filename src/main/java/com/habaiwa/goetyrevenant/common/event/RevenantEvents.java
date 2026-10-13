package com.habaiwa.goetyrevenant.common.event;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.habaiwa.goetyrevenant.common.soul.Possession;
import com.habaiwa.goetyrevenant.common.soul.SoulConstants;
import com.habaiwa.goetyrevenant.common.soul.SoulData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 回魂机制：死亡 → 灵魂状态 → 找身体附身 / 灵魂能量耗尽才算真死。
 *
 * <p><b>触发优先级</b>：不死图腾、诡厄巫法的灵魂图腾、灵魂方舟的死亡回归都会在
 * {@link LivingDeathEvent} 之前把人救下来（图腾是在 die 之前消耗的），
 * 所以本监听器用 {@link EventPriority#LOWEST} 并且先看 {@code isCanceled()}——
 * <b>只有这些手段都没救下来，才会触发回魂</b>。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID)
public final class RevenantEvents {
    private RevenantEvents() {
    }

    // ===== 死亡 =====




    /**
     * 服务端起来时把**附身适配层**读进内存 ——
     * 数据都在 {@code data/<命名空间>/possess_abilities/*.json}，加生物不用改代码。
     */
    @SubscribeEvent
    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        com.habaiwa.goetyrevenant.common.soul.PossessProfile.load(event.getServer());
        // 元素反应表（71 条，数据驱动）
        com.habaiwa.goetyrevenant.common.reaction.ElementReaction.load(event.getServer());
    }

    // ===== 每 tick =====



    /**
     * 兜底报告：**绝不走 Forge 事件总线的那套日志**（那条路在本整合包里会 LinkageError）。
     *
     * <p>只打一次同样的异常，避免每秒刷屏把日志撑爆。</p>
     */
    public static void report(String where, Throwable t) {
        String key = where + ":" + t.getClass().getName();
        if (REPORTED.add(key)) {
            System.err.println("[Goety: Revenant] " + where + " 抛异常（已吞掉，游戏继续）: " + t);
            t.printStackTrace();
        }
    }

    /** 已经报告过的异常种类（避免刷屏）。 */
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 重连 / 登录后把改造状态与皮肤补发给客户端 —— 物品 tooltip 要靠这份数据。 */
    @SubscribeEvent
    public static void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getPersistentData().hasUUID(SoulConstants.KEY_OWNED_SKIN)) {
            String value = player.getPersistentData().getString("GROwnedSkinValue");
            String signature = player.getPersistentData().getString("GROwnedSkinSignature");
            if (!value.isEmpty()) {
                com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSkin(player, value, signature);
            }
        }
        com.habaiwa.goetyrevenant.common.body.Embedding.applyAll(player);
        // 补发一次改造状态：启示录光环的 tooltip 要读「开了几个窍」之类的数据
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendBodyState(player);
    }


    /** 1 点伤害折算多少灵魂能量。 */
    public static final int SOUL_DAMAGE_PER_HP = 100;


    // ===== 灵魂状态禁止指令 =====


    // ===== 附身在别人的身体里时用不了聚晶（契合度不够） =====




}
