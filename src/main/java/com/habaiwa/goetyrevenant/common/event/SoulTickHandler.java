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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * **灵魂状态与附身状态的每 tick 维持**。
 *
 * <p>从 `RevenantEvents` 拆出来的。这一块是"状态机的心跳"，负责：</p>
 * <ul>
 *   <li>灵魂形态的维持（穿墙 / 隐身 / 飞行 / 失重）</li>
 *   <li><b>附身倒计时</b> —— 到点调 `PossessionRecall` 那条链把人弹出去</li>
 *   <li>附身亡灵时维持"白天燃烧 + 能量恢复压到 25%"</li>
 *   <li>附身能力的持续供给（水生呼吸 / 火焰免疫 / 爬墙 / 跳跃）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID)
public final class SoulTickHandler {
    private SoulTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 灵魂温度：底层资源，每 tick 都要漂（休息升温 / 战斗降温 / 灵魂低温）
        if (event.phase == TickEvent.Phase.END
                && event.player instanceof net.minecraft.server.level.ServerPlayer tempPlayer) {
            com.habaiwa.goetyrevenant.common.soul.SoulTemperature.tick(tempPlayer);
            // 强化槽的切换冷却
            com.habaiwa.goetyrevenant.common.body.UpgradeSlots.tick(tempPlayer);
            // 火种三态：体内保持满、环绕/离体时烛火值下降、归零自动回体
            com.habaiwa.goetyrevenant.common.sequence.EmberState.tick(tempPlayer);
            // 答题空间：别让人从白地里飘出去
            com.habaiwa.goetyrevenant.common.sequence.QuizRoom.tick(tempPlayer);
        }
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // ★ 整段 tick 逻辑包在 try/catch 里。
        //
        // 为什么必须这么做：这个整合包的 Forge 事件总线**只要有一个监听器抛异常**，
        // 它自己「打日志」那一步就会 LinkageError（log4j 的 MessageSupplier 类加载器冲突，
        // RevelationFix 引入的），**打日志直接把游戏崩掉**。
        // 也就是说：我们任何一个小 bug 都会变成「整个存档没了」。
        // 与其那样，不如自己吞掉、记一行，让游戏继续跑。
        try {
            tickPlayer(player);
        } catch (Throwable t) {
            com.habaiwa.goetyrevenant.common.event.RevenantEvents.report("onPlayerTick", t);
        }
    }

    /** 玩家每 tick 的真正逻辑（外面包了保护层，见 {@link #onPlayerTick}）。 */
    private static void tickPlayer(ServerPlayer player) {
        // 契合度：**在自己身体里养得最快**（活着时每次恢复两份），当灵魂时恢复慢一些
        if (player.tickCount % SoulConstants.ATTUNEMENT_RECOVER_INTERVAL == 0) {
            SoulData.recoverSelfAttunement(player);
            if (!SoulData.isSoul(player)) {
                SoulData.recoverSelfAttunement(player);
            }
        }

        // 灵化：状态被别的模组重置了就补回来
        if (com.habaiwa.goetyrevenant.common.soul.LingHua.isActive(player)) {
            com.habaiwa.goetyrevenant.common.soul.LingHua.tick(player);
        }

        // 嵌入式聚晶的冷却
        com.habaiwa.goetyrevenant.common.body.EmbeddedCasting.tickCooldowns(player);

        // 附身能力：摔落免疫的生物（飞行类 / 蜘蛛 / 猫）落地不掉血
        if (com.habaiwa.goetyrevenant.common.soul.PossessAbilities.has(player,
                com.habaiwa.goetyrevenant.common.soul.PossessAbilities.BIT_FALL_IMMUNE)) {
            player.fallDistance = 0.0F;
        }

        // 首次嵌入的仪式（在极速抽灵魂力 / 扣血，别被别的逻辑打断）
        if (com.habaiwa.goetyrevenant.common.body.Embedding.isRunning(player)) {
            com.habaiwa.goetyrevenant.common.body.Embedding.tick(player);
            return;
        }

        if (!SoulData.isSoul(player)) {
            return;
        }

        // 灵魂形态**每 tick 维持**：穿墙、隐身、飞行、失重。
        // 别的模组（附身类、状态类）会随手重置这些 —— 一重置，魂就落地、就现身。
        if (!player.noPhysics) {
            player.noPhysics = true;
        }
        if (!player.isInvisible()) {
            player.setInvisible(true);
        }
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        if (!player.isNoGravity()) {
            player.setNoGravity(true);
        }

        if (SoulData.isPossessingUndead(player)) {
            com.habaiwa.goetyrevenant.common.soul.SoulTraits.undeadDaylight(player);
            com.habaiwa.goetyrevenant.common.soul.SoulTraits.limitUndeadSoulRegen(player);
        }

        // 附身能力每 tick 维持：水生呼吸、火焰免疫、跳跃、飞行、**爬墙**（蜘蛛那种）
        com.habaiwa.goetyrevenant.common.soul.PossessAbilities.tick(player);

        if (SoulData.isPossessing(player)) {
            int left = SoulData.getPossessTicks(player) - 1;
            SoulData.setPossessTicks(player, left);
            if (left <= 0) {
                Possession.eject(player);
            }
            return;
        }

        // **正在溃散就交给溃散逻辑全权处理** —— 这一段里它要独占血量和灵魂能量，
        // 不然我们的自动恢复会跟它对着干（一边抽一边回，永远抽不干）。
        if (player.hasEffect(com.habaiwa.goetyrevenant.init.ModEffects.SOUL_COLLAPSE.get())) {
            return;
        }

        // 灵魂能量 = 这条命的血条，但它**会自己往回升**（花掉才少）。
        // 花它的地方是放聚晶和附身；一直乱放就会把自己抽干。
        if (player.tickCount % SoulConstants.REGEN_INTERVAL == 0) {
            int energy = SoulData.getEnergy(player);
            int max = com.habaiwa.goetyrevenant.common.soul.SoulEnergy.max(player);
            if (energy < max) {
                SoulData.setEnergy(player,
                        Math.min(max, energy + SoulConstants.REGEN_PER_INTERVAL));
            }
        }

        // 越低越虚弱：能量见底的时候人不人鬼不鬼
        com.habaiwa.goetyrevenant.common.soul.SoulTraits.applyWeakness(player);

        // **抽干了 → 灵魂溃散**（不是当场真死）。
        // 原来的写法是直接 killSoul，可这个整合包里防死模组一大堆，
        // 死亡被取消之后灵魂状态还在、能量还是 0 → 下一 tick 又杀一次 → 死循环 → 崩服。
        // 溃散不碰死亡事件，所以谁也拦不住。
        if (SoulData.getEnergy(player) <= 0
                && com.habaiwa.goetyrevenant.common.soul.SoulCollapse.canTrigger(player)) {
            com.habaiwa.goetyrevenant.common.soul.SoulCollapse.start(player);
            return;
        }

        int left = SoulData.getTicks(player) - 1;
        SoulData.setTicks(player, left);
        if (left <= 0) {
            com.habaiwa.goetyrevenant.common.possession.PossessionRecall.forceRecall(player);
            return;
        }

        if (player.tickCount % 20 == 0) {
            // 「身体在北边 35 格」那种文字提示已经删掉了 ——
            // 现在只把「距离 + 方位角」同步给客户端，由灵魂 HUD 画**箭头**（见 SoulHudOverlay）。
            com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSoulState(player);
        }
        // 操作提示只在刚变灵魂的头 10 秒说一次，之后不再刷屏
        if (SoulData.getTicks(player) > SoulConstants.soulDurationTicks() - 200
                && player.tickCount % 100 == 0) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.soul_click_hint",
                    com.habaiwa.goetyrevenant.common.util.KeyNames.key("key.attack")), true);
        }

        // 附身改由「左键」触发（客户端发包，见 PossessRequestMessage）。
        // 原来蹲下就秒进，已经去掉。
    }
}
