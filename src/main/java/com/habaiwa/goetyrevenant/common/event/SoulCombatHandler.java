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
 * **灵魂状态的受伤结算** —— 扣的是灵魂能量，不是血。
 *
 * <p>从 `RevenantEvents` 拆出来的。</p>
 *
 * <h3>关键：不要排除"附身中"</h3>
 * <p>设计 §7.7.1 需求 4：<b>灵体状态的那条血就是灵魂能量</b>。
 * 附身只是"魂进了别人的身体"，你还是魂 —— 挨打扣的仍然该是灵魂能量。
 * （所以附身亡灵时能量恢复被压到 25%，它是活着的资源。）</p>
 *
 * <p>能量归零 = 真死（交给 `PossessionRecall.killSoul`）。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID)
public final class SoulCombatHandler {
    private SoulCombatHandler() {
    }

    /**
     * 灵魂被攻击 → **扣灵魂能量**（不掉血）。
     *
     * <p>能量归零就是真死（{@code Possession.killSoul}）。
     * 附身状态下不拦，那时是正常的肉体受伤。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSoulHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // ------------------------------------------------------------------
        //  **灵魂状态【或】附身状态 —— 两种都算"灵体"，挨打都扣灵魂能量。**
        //
        //  这里踩过一次坑：`possess()` 的流程里会调 `endSoul()`，而它把
        //  `setSoul(false)` —— 也就是说【附身时 isSoul 是 false】。
        //  所以只判 `isSoul` 的话，附身中挨打会直接放行、扣血，能量一点不动。
        //
        //  设计里：灵体状态的那条血就是灵魂能量（§7.7.1 需求 4），
        //  附身期间灵魂能量也一直是活的资源（§4.4：附身亡灵能量恢复压到 25%）。
        //  附身只是"魂进了别人的身体"，你还是魂。
        // ------------------------------------------------------------------
        if (!SoulData.isSoul(player) && !SoulData.isPossessing(player)) {
            return;
        }
        event.setCanceled(true);

        int cost = Math.max(1, Math.round(event.getAmount() * com.habaiwa.goetyrevenant.common.event.RevenantEvents.SOUL_DAMAGE_PER_HP));
        int energy = SoulData.getEnergy(player) - cost;
        SoulData.setEnergy(player, energy);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSoulState(player);

        if (energy <= 0) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.soul_energy_gone"), false);
            com.habaiwa.goetyrevenant.common.possession.PossessionRecall.killSoul(player);
        }
    }
}
