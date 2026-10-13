package com.habaiwa.goetyrevenant.common.command;

import com.habaiwa.goetyrevenant.common.body.BodyModData;
import com.habaiwa.goetyrevenant.common.body.Embedding;
import com.habaiwa.goetyrevenant.common.body.OrganType;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * `/goety_revenant` 调试指令。
 *
 * <ul>
 *   <li>`unlock [true|false]` —— 开关「改造身体」解锁（默认 true），用来跳过哭泣之井</li>
 *   <li>`open` —— 直接打开改造身体界面</li>
 *   <li>`state` —— 打印五脏+灵窍的开启与嵌入情况</li>
 *   <li>`clear` —— 清空所有嵌入的聚晶</li>
 * </ul>
 */
public final class RevenantCommand {
    private RevenantCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("goety_revenant")
                .then(Commands.literal("unlock")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> unlock(context, true))
                        .then(Commands.literal("true").executes(context -> unlock(context, true)))
                        .then(Commands.literal("false").executes(context -> unlock(context, false))))
                .then(Commands.literal("slot")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("organ", StringArgumentType.word())
                                .then(Commands.literal("unlock").executes(context -> setSlot(context, false)))
                                .then(Commands.literal("lock").executes(context -> setSlot(context, true)))))
                .then(Commands.literal("open")
                        .executes(RevenantCommand::open))
                .then(Commands.literal("state")
                        .executes(RevenantCommand::state))
                .then(Commands.literal("clear")
                        .requires(source -> source.hasPermission(2))
                        .executes(RevenantCommand::clear)));
    }

    private static int unlock(CommandContext<CommandSourceStack> context, boolean value) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            BodyModData.setUnlocked(player, value);
            RevenantNetwork.sendBodyState(player);
            source.sendSuccess(() -> Component.translatable("command.goety_revenant.unlock",
                    Component.translatable(value ? "command.goety_revenant.on" : "command.goety_revenant.off")), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.goety_revenant.need_player"));
            return 0;
        }
    }

    /** 解锁 / 封锁某个槽位（心 肝 脾 肺 肾 灵窍，或 all）。 */
    private static int setSlot(CommandContext<CommandSourceStack> context, boolean locked) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            String name = StringArgumentType.getString(context, "organ");
            int changed = 0;
            for (OrganType organ : OrganType.values()) {
                if (!name.equals("all") && !organ.getId().equals(name)) {
                    continue;
                }
                BodyModData.setSlotLocked(player, organ, locked);
                changed++;
            }
            if (changed == 0) {
                source.sendFailure(Component.translatable("command.goety_revenant.slot.unknown", name));
                return 0;
            }
            final int total = changed;
            RevenantNetwork.sendBodyState(player);
            source.sendSuccess(() -> Component.translatable("command.goety_revenant.slot",
                    Component.translatable(locked
                            ? "command.goety_revenant.locked" : "command.goety_revenant.unlocked"),
                    total), true);
            return changed;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.goety_revenant.need_player"));
            return 0;
        }
    }

    private static int open(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (!BodyModData.isUnlocked(player)) {
                // 调试指令：没解锁也直接放行，省得还要先敲 unlock
                BodyModData.setUnlocked(player, true);
            }
            Embedding.applyAll(player);
            RevenantNetwork.sendBodyState(player);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.goety_revenant.need_player"));
            return 0;
        }
    }

    private static int state(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            source.sendSuccess(() -> Component.translatable("command.goety_revenant.state.header",
                    Component.translatable(BodyModData.isUnlocked(player)
                            ? "command.goety_revenant.on" : "command.goety_revenant.off")), false);
            for (OrganType organ : OrganType.values()) {
                ItemStack focus = BodyModData.getFocus(player, organ);
                String focusName = focus == null || focus.isEmpty() ? "-" : focus.getHoverName().getString();
                source.sendSuccess(() -> Component.literal("  ")
                        .append(organ.displayName())
                        .append(Component.literal(" | "))
                        .append(Component.translatable(BodyModData.isOpened(player, organ)
                                ? "screen.goety_revenant.body.opened" : "screen.goety_revenant.body.unopened"))
                        .append(Component.literal(" | " + focusName)), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.goety_revenant.need_player"));
            return 0;
        }
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            BodyModData.clear(player);
            Embedding.applyAll(player);
            RevenantNetwork.sendBodyState(player);
            source.sendSuccess(() -> Component.translatable("command.goety_revenant.cleared"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.goety_revenant.need_player"));
            return 0;
        }
    }
}
