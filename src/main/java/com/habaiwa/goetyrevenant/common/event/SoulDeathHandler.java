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
 * **回魂的入口**：死亡 → 尸体留下 → 人变成灵魂。
 *
 * <p>从 `RevenantEvents` 拆出来的（那个类原本把死亡、tick、战斗、交互全堆在一起）。</p>
 *
 * <h3>触发优先级（设计 §4.6，重要）</h3>
 * <p>不死图腾 / 灵魂图腾 / 灵魂方舟的死亡回归都<b>优先于回魂</b>：</p>
 * <ul>
 *   <li>图腾是在 `die()` 之前消耗的，`LivingDeathEvent` 根本不触发</li>
 *   <li>方舟回归会<b>取消</b>事件 —— 所以我们用 {@code EventPriority.LOWEST}
 *       并且先看 {@code isCanceled()} <b>让路</b></li>
 * </ul>
 * <p><b>只有这些手段都没救下来，才会触发回魂。</b></p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID)
public final class SoulDeathHandler {
    private SoulDeathHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            handleDeath(event);
        } catch (Throwable t) {
            com.habaiwa.goetyrevenant.common.event.RevenantEvents.report("onLivingDeath", t);
        }
    }

    /** 死亡处理的真正逻辑（外面包了保护层，理由见 {@link #onPlayerTick}）。 */
    private static void handleDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 灵魂图腾 / 方舟死亡回归已经把死亡取消了 → 不回魂
        if (event.isCanceled()) {
            return;
        }
        // **灵魂能量耗尽的真死**：放行，绝不取消、也不再把人变成灵魂。
        // 这个标记由 Possession.killSoul 在 hurt() 之前打上，执行完就摘掉。
        if (player.getPersistentData().getBoolean(SoulConstants.KEY_FINAL)) {
            return;
        }
        if (SoulData.isSoul(player)) {
            return;
        }
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
            return;
        }
        event.setCanceled(true);
        enterSoulState(player, mode);
    }

    private static void enterSoulState(ServerPlayer player, GameType previousMode) {
        ServerLevel level = player.serverLevel();
        CorpseEntity corpse = CorpseEntity.of(player);
        level.addFreshEntity(corpse);

        SoulData.setSoul(player, true);
        // 死亡游离：**尸体要追踪**（箭头指过去、附身回自己 = 完整复活）
        SoulData.setSoulMode(player, SoulConstants.MODE_DEATH);
        SoulData.setTicks(player, SoulConstants.soulDurationTicks());
        SoulData.setCorpse(player, corpse.getUUID(), level.dimension());
        SoulData.setPreviousGameMode(player, previousMode);
        // 灵魂状态自带一万灵魂能量（就是本体的灵魂能量）
        // 又死了一次：不再顶着别人的皮
        player.getPersistentData().remove(SoulConstants.KEY_OWNED_SKIN);
        player.getPersistentData().remove("GROwnedSkinValue");
        player.getPersistentData().remove("GROwnedSkinSignature");
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSkin(player, "", "");

        SoulData.penalizeSelfAttunement(player, SoulConstants.PENALTY_ON_DEATH);
        // 死了变灵魂 → **灵魂能量充满**（这是刻意的：灵魂从零开始飘，不继承生前攒的量）。
        // 充的是「上限」而不是写死 10000 —— 没绑灵魂方舟的玩家上限就是 10000（和预期完全一致），
        // 绑了方舟的则是方舟那一档，图腾那一档也照样算进去。
        SoulData.setEnergy(player, com.habaiwa.goetyrevenant.common.soul.SoulEnergy.max(player));
        SoulData.setLastSe(player, SEHelper.getSESouls(player));

        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        // 【自研灵魂形态】不用旁观者 —— 旁观者不可被选中/免疫伤害，
        // 而鬼类要能看见并攻击灵魂、伤害要扣灵魂能量。
        com.habaiwa.goetyrevenant.common.possession.SoulForm.enterSoulForm(player);
        // **灵魂落点**：有灵魂方舟就落在方舟旁，没有就落在**世界出生点**（床不作数）。
        // 尸体留在死的地方，所以「回出生点」和「回去找身体」是两件要跑的事。
        com.habaiwa.goetyrevenant.common.possession.SoulForm.clearBedSpawn(player);
        com.habaiwa.goetyrevenant.common.possession.SoulForm.spawnSoul(player);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSoulState(player);
        player.displayClientMessage(Component.translatable("message.goety_revenant.soul_start",
                    com.habaiwa.goetyrevenant.common.util.KeyNames.key("key.sneak")), false);
    }
}
