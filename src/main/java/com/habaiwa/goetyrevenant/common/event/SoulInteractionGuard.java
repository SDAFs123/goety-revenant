package com.habaiwa.goetyrevenant.common.event;

import com.Polarice3.Goety.api.items.magic.IFocus;
import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.GoetyRevenant;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.habaiwa.goetyrevenant.common.possession.Possession;
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
 * **灵魂状态的交互守卫** —— 灵魂不许做的事。
 *
 * <p>从 `RevenantEvents` 拆出来的。</p>
 *
 * <h3>挡掉什么</h3>
 * <ul>
 *   <li><b>指令</b>（设计 §4.7：灵魂状态禁止指令）</li>
 *   <li><b>右键物品 / 右键方块</b>（灵魂不可交互）</li>
 * </ul>
 *
 * <p>另外这里注册了 `/goety_revenant` 指令树。</p>
 */
@Mod.EventBusSubscriber(modid = GoetyRevenant.MODID)
public final class SoulInteractionGuard {
    private SoulInteractionGuard() {
    }

    /** 注册调试指令。 */
    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.habaiwa.goetyrevenant.common.command.RevenantCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player
                && SoulData.isSoul(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.goety_revenant.no_command"), true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blocked(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    private static boolean blocked(Player player, ItemStack stack) {
        if (!SoulData.isPossessing(player)) {
            return false;
        }
        if (stack.getItem() instanceof IWand || stack.getItem() instanceof IFocus) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.focus_blocked"), true);
            return true;
        }
        return false;
    }
}
