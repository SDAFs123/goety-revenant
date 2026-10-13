package com.habaiwa.goetyrevenant.common.command;

import com.habaiwa.goetyrevenant.common.embed.BodyModData;
import com.habaiwa.goetyrevenant.common.embed.OrganType;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import com.habaiwa.goetyrevenant.common.soul.SoulCollapse;
import com.habaiwa.goetyrevenant.common.soul.SoulData;
import com.habaiwa.goetyrevenant.common.soul.SoulEnergy;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * **调试指令**（OP 权限）。整合包调试七窍 / 神识 / 能量用的。
 *
 * <pre>
 * /grdebug state           全部状态：资格、灵魂、神识、能量、每窍（开/喝/献祭/聚晶）
 * /grdebug unlock          解锁资格 + 全部六窍（含"已开窍"标志）
 * /grdebug relock          全部锁回、聚晶清空
 * /grdebug energy <n>      把灵魂能量设成 n
 * /grdebug shenshi on|off  神识强制开/关
 * /grdebug collapse        立刻来一段灵魂溃散（调试扣血扣能量流程）
 * /grdebug clear           清空全部嵌入
 * /grdebug whisper         放一遍那句话的低语（调音用）
 * /grdebug sound id         放任意音效给自己听（whisper = 本模组的低语）
 * </pre>
 */
public final class DebugCommands {
    private DebugCommands() {
    }

    /**
     * 直接把魂树设到某一阶（指令 {@code grdebug tree <阶>}）。
     *
     * <p>测试用。正常路径下这个阶数是<b>序列晋升</b>推进的
     * （答完七问答觉醒是一阶，之后一阶一阶往上），这条指令不改变那套机制，
     * 只是让你能立刻看到某个阶段的树长什么样、有多少分枝。</p>
     */
    private static int treeStage(CommandContext<CommandSourceStack> ctx, int stage) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        // 写的是 SoulTree 认的那个测试键（不是 CandleValue —— 那一个是烛火值，无关）
        player.getPersistentData().putInt("GRTreeStageTest", Math.max(0, Math.min(8, stage)));
        RevenantNetwork.sendBodyState(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "魂树 → 第 " + stage + " 阶（"
                        + com.habaiwa.goetyrevenant.common.tree.SoulTree.stageName(stage)
                        + "）· 关闭界面重开就能看到")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /** 看看血肉线走到哪了。 */
    private static int fleshStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        com.habaiwa.goetyrevenant.common.flesh.FleshLine line =
                com.habaiwa.goetyrevenant.common.flesh.FleshProgress.line(player);
        if (line == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "血肉线：未定").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        int stage = com.habaiwa.goetyrevenant.common.flesh.FleshProgress.stage(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "血肉线：" + line.cn() + " · " + line.direction() + " · 第 " + stage + " 阶（"
                        + line.stepName(stage) + "）· 血肉变成" + line.becomes()
                        + " · 终点 " + line.source()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    /** 设定血肉线：/grdebug flesh &lt;路&gt; [阶]。 */
    private static int flesh(CommandContext<CommandSourceStack> ctx, int stage) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "line");
        if (raw.equalsIgnoreCase("clear")) {
            com.habaiwa.goetyrevenant.common.flesh.FleshProgress.set(player, null, 0);
            ctx.getSource().sendSuccess(() -> Component.literal("血肉线已清空"), false);
            return 1;
        }
        com.habaiwa.goetyrevenant.common.flesh.FleshLine line =
                com.habaiwa.goetyrevenant.common.flesh.FleshLine.byName(raw);
        if (line == null) {
            ctx.getSource().sendFailure(Component.literal("没有这条路：" + raw
                    + "（mutation / spirit / silicon）"));
            return 0;
        }
        int use = stage < 0 ? com.habaiwa.goetyrevenant.common.flesh.FleshProgress.stage(player) : stage;
        com.habaiwa.goetyrevenant.common.flesh.FleshProgress.set(player, line, use);
        RevenantNetwork.sendBodyState(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "血肉线 → " + line.cn() + " 第 " + use + " 阶（" + line.stepName(use) + "）")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    /** 常听的几个，给 tab 补全用。 */
    private static final String[] SOUND_HINTS = {
            "goety_revenant:whisper_sequence", "whisper",
            "minecraft:entity.elder_guardian.curse",
            "minecraft:ambient.soul_sand_valley.mood",
            "minecraft:entity.vex.ambient",
            "minecraft:entity.warden.nearby_close",
            "minecraft:block.bell.use",
            "minecraft:entity.enderman.ambient",
            "minecraft:ui.button.click",
    };

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            soundSuggestions(CommandContext<CommandSourceStack> ctx,
                             com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (String hint : SOUND_HINTS) {
            if (hint.toLowerCase(java.util.Locale.ROOT).startsWith(
                    builder.getRemainingLowerCase())) {
                builder.suggest(hint);
            }
        }
        return builder.buildFuture();
    }

    /** 放一遍那句话的低语（{@code grdebug whisper}）—— 调音效时最常用的一条。 */
    /**
     * **诊断：这个物品认不认那些附魔**（`/grdebug enchant`，手持物品执行）。
     *
     * <p>镰刀附魔互通涉及三层，任何一层没通都会表现成"没反应"，而游戏里看不出是哪层：</p>
     * <ol>
     *   <li><b>标签层</b>：Goety 镰刀在不在 `#malum:scythe` 里（靠我们的数据包追加）</li>
     *   <li><b>物品层</b>：这个物品本身能不能被附魔（`isEnchantable` / 附魔性）</li>
     *   <li><b>附魔层</b>：那条附魔的 `canEnchant` 认不认它</li>
     * </ol>
     */
    private static int enchantDiag(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("只能在游戏里用（手持物品执行）"));
            return 0;
        }
        var src = ctx.getSource();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            src.sendFailure(Component.literal("主手是空的 —— 拿一把镰刀再执行"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("=== 附魔互通诊断 ===").withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("物品: " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())), false);

        // ① 标签层
        String[] tags = {"malum:scythe", "malum:soul_hunter_weapon", "malum:magic_capable_weapon",
                "malum:staff", "goety_revenant:scythes", "forge:tools/swords"};
        for (String t : tags) {
            String[] parts = t.split(":", 2);
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> key = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    new net.minecraft.resources.ResourceLocation(parts[0], parts[1]));
            boolean in = stack.is(key);
            src.sendSuccess(() -> Component.literal((in ? "  [在] " : "  [  ] ") + "#" + t)
                    .withStyle(in ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
        }

        // ② 物品层
        int enchVal = stack.getItem().getEnchantmentValue();
        boolean enchantable = stack.isEnchantable();
        src.sendSuccess(() -> Component.literal("附魔性: " + enchVal + "  可附魔: " + enchantable)
                .withStyle(enchVal > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);

        // ③ 附魔层：Malum / Goety 的附魔逐条判
        int ok = 0;
        for (net.minecraft.world.item.enchantment.Enchantment ench
                : net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.getKey(ench);
            if (id == null || (!id.getNamespace().equals("malum") && !id.getNamespace().equals("goety"))) {
                continue;
            }
            boolean can = ench.canEnchant(stack);
            boolean table = ench.canApplyAtEnchantingTable(stack);
            if (can) {
                ok++;
            }
            String text = (can ? "  [可] " : "  [  ] ") + id
                    + "   铁砧:" + (can ? "是" : "否") + "  附魔台:" + (table ? "是" : "否");
            src.sendSuccess(() -> Component.literal(text)
                    .withStyle(can ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY), false);
        }
        int fok = ok;
        src.sendSuccess(() -> Component.literal("认得这个物品的跨模组附魔: " + fok + " 条")
                .withStyle(fok > 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }

    private static int whisper(CommandContext<CommandSourceStack> ctx) {
        return sound(ctx, "whisper");
    }

    /**
     * 就地放一个音效给玩家听（{@code grdebug sound &lt;id&gt;}）。
     *
     * <p>写 {@code whisper} 就等于 {@code goety_revenant:whisper_sequence}；
     * 只写名字不带冒号的话按本模组的命名空间找。找不到会告诉你。</p>
     */
    private static int sound(CommandContext<CommandSourceStack> ctx, String raw) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("只能在游戏里用（要有个玩家来听）"));
            return 0;
        }
        // **快捷别名要先展开**。
        // 之前只做了"没冒号就补本模组前缀"，于是 whisper 变成了
        // goety_revenant:whisper —— 一个不存在的 id，客户端只会回一句
        // "Unable to play unknown soundEvent"。别名得真的映射过去。
        String expanded = switch (raw) {
            case "whisper", "whispers", "低语" -> "goety_revenant:whisper_sequence";
            default -> raw;
        };
        net.minecraft.resources.ResourceLocation id = expanded.contains(":")
                ? net.minecraft.resources.ResourceLocation.tryParse(expanded)
                : new net.minecraft.resources.ResourceLocation("goety_revenant", expanded);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("id 不合法：" + raw));
            return 0;
        }
        // **不在服务端查注册表。**
        // assets/ 里的 sounds.json 是**客户端资源** —— 它加载出来的音效只进客户端的
        // 声音注册表；服务端根本不读 assets/，所以在这里查永远是
        // "没有这个音效：goety_revenant:whisper_sequence"。
        // 交给客户端去放：那边才有这份清单。
        RevenantNetwork.playSound(player, id.toString(), 1.0F, 1.0F);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "已让客户端播放 " + id + "  ·  音量 1.0 / 原调")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int sound(CommandContext<CommandSourceStack> ctx) {
        return sound(ctx, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id"));
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("grdebug")
                .requires(src -> src.hasPermission(2)
                        && com.habaiwa.goetyrevenant.common.config.RevenantConfig.debugCommands)
                .then(Commands.literal("state").executes(DebugCommands::state))
                // **快捷放音效**：/grdebug sound whisper 之类，调音用
                .then(Commands.literal("sound")
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                                .suggests(DebugCommands::soundSuggestions)
                                .executes(DebugCommands::sound)))
                .then(Commands.literal("whisper").executes(DebugCommands::whisper))
                // **诊断镰刀附魔互通** —— 手持物品打这条，看它到底在不在那些标签里、
                // 每个 Malum/Goety 附魔认不认它。互通出问题时先看这里。
                .then(Commands.literal("enchant").executes(DebugCommands::enchantDiag))
                // **血肉线**：调试三条路的进度（mutation / spirit / silicon / clear）
                .then(Commands.literal("flesh")
                        .executes(DebugCommands::fleshStatus)
                        .then(Commands.argument("line",
                                        com.mojang.brigadier.arguments.StringArgumentType.string())
                                .suggests((c, b) -> {
                                    for (String s : new String[]{"mutation", "spirit", "silicon",
                                            "异化", "灵化", "硅化", "clear"}) {
                                        if (s.startsWith(b.getRemaining())) {
                                            b.suggest(s);
                                        }
                                    }
                                    return b.buildFuture();
                                })
                                .executes(c -> flesh(c, -1))
                                .then(Commands.argument("stage",
                                                IntegerArgumentType.integer(0, 7))
                                        .executes(c -> flesh(c, IntegerArgumentType.getInteger(c, "stage"))))))
                .then(Commands.literal("unlock").executes(DebugCommands::unlock))
                // **魂树的阶数**：正常路径是序列晋升（答七问答 → 觉醒 → 一阶一阶往上），
                // 这条指令只是给测试用的 —— 直接跳到某一阶看形态，不改动晋升机制。
                .then(Commands.literal("tree")
                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, 8))
                                .executes(c -> treeStage(c, IntegerArgumentType.getInteger(c, "stage")))))
                .then(Commands.literal("relock").executes(DebugCommands::relock))
                .then(Commands.literal("energy")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 999999))
                                .executes(DebugCommands::energy)))
                .then(Commands.literal("shenshi")
                        .then(Commands.literal("on").executes(c -> shenshi(c, true)))
                        .then(Commands.literal("off").executes(c -> shenshi(c, false))))
                .then(Commands.literal("temp")
                        .executes(DebugCommands::tempShow)
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                .executes(DebugCommands::tempSet)))
                .then(Commands.literal("tree").executes(DebugCommands::treeReplay))
                .then(Commands.literal("collapse").executes(DebugCommands::collapse))
                .then(Commands.literal("clear").executes(DebugCommands::clear)));
    }

    /* ------------------------------------------------------------------ */

    private static int state(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        p.displayClientMessage(Component.literal("=== 七窍 / 状态 ===").withStyle(ChatFormatting.RED), false);
        p.displayClientMessage(Component.literal("资格: " + BodyModData.isUnlocked(p)
                + "  灵魂: " + SoulData.isSoul(p)
                + "  神识: " + com.habaiwa.goetyrevenant.common.shenshi.ShenShi.isActive(p)), false);
        p.displayClientMessage(Component.literal("灵魂能量: " + SoulEnergy.current(p) + " / " + SoulEnergy.max(p)), false);
        p.displayClientMessage(Component.literal("契合度: " + Math.round(SoulData.getSelfAttunement(p) * 100) + "%"
                + "  灵魂温度: " + com.habaiwa.goetyrevenant.common.soul.SoulTemperature.get(p)
                + "（" + com.habaiwa.goetyrevenant.common.soul.SoulTemperature.band(p).cn() + "）"), false);
        for (OrganType organ : OrganType.values()) {
            ItemStack f = BodyModData.getFocus(p, organ);
            p.displayClientMessage(Component.literal(String.format("  %-18s 已开:%s 喝魔药:%s 献祭:%s 聚晶:%s",
                    organ.getId(),
                    BodyModData.isOpened(p, organ),
                    BodyModData.isSlotUnlocked(p, organ),
                    BodyModData.isSacrificed(p, organ),
                    f.isEmpty() ? "-" : f.getHoverName().getString())).withStyle(ChatFormatting.GRAY), false);
        }
        return 0;
    }

    private static int unlock(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        BodyModData.setUnlocked(p, true);
        for (OrganType organ : OrganType.values()) {
            BodyModData.setSlotUnlocked(p, organ, true);
            BodyModData.setOpened(p, organ, true);
        }
        RevenantNetwork.sendBodyState(p);
        p.displayClientMessage(Component.literal("已解锁资格 + 全部六窍").withStyle(ChatFormatting.GREEN), false);
        return 0;
    }

    private static int relock(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        BodyModData.setUnlocked(p, false);
        for (OrganType organ : OrganType.values()) {
            BodyModData.setSlotUnlocked(p, organ, false);
            BodyModData.setOpened(p, organ, false);
            BodyModData.setSacrificed(p, organ, false);
            BodyModData.setFocus(p, organ, ItemStack.EMPTY);
        }
        RevenantNetwork.sendBodyState(p);
        p.displayClientMessage(Component.literal("全部锁回，聚晶已清空").withStyle(ChatFormatting.YELLOW), false);
        return 0;
    }

    private static int energy(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        SoulData.setEnergy(p, amount);
        p.displayClientMessage(Component.literal("灵魂能量 -> " + amount).withStyle(ChatFormatting.AQUA), false);
        return 0;
    }

    private static int shenshi(CommandContext<CommandSourceStack> ctx, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        com.habaiwa.goetyrevenant.common.shenshi.ShenShi.force(p, on);
        p.displayClientMessage(Component.literal("神识 -> " + (on ? "开" : "关")).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 0;
    }

    private static int tempShow(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        com.habaiwa.goetyrevenant.common.soul.SoulTemperature.Band band =
                com.habaiwa.goetyrevenant.common.soul.SoulTemperature.band(p);
        p.displayClientMessage(Component.literal("灵魂温度: " + com.habaiwa.goetyrevenant.common.soul.SoulTemperature.get(p)
                + " / 100  （" + band.cn() + "）"
                + "  冷却×" + com.habaiwa.goetyrevenant.common.soul.SoulTemperature.cooldownScale(p)
                + " 威力×" + com.habaiwa.goetyrevenant.common.soul.SoulTemperature.powerScale(p)
                + " 恢复×" + com.habaiwa.goetyrevenant.common.soul.SoulTemperature.regenScale(p))
                .withStyle(ChatFormatting.GOLD), false);
        return 0;
    }

    private static int tempSet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int value = IntegerArgumentType.getInteger(ctx, "value");
        com.habaiwa.goetyrevenant.common.soul.SoulTemperature.set(p, value);
        p.displayClientMessage(Component.literal("灵魂温度 -> " + value).withStyle(ChatFormatting.GOLD), false);
        return 0;
    }

    /** 重置「上次看到的序列」—— 下次打开魂树会重播一次生长动画。 */
    private static int treeReplay(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        p.getPersistentData().remove("GRTreeSeenStage");
        p.displayClientMessage(Component.literal("魂树生长动画已重置 —— 下次按 B 会重播一遍")
                .withStyle(ChatFormatting.AQUA), false);
        return 0;
    }

    private static int collapse(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        SoulCollapse.start(p);
        return 0;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        BodyModData.clear(p);
        RevenantNetwork.sendBodyState(p);
        p.displayClientMessage(Component.literal("聚晶已清空").withStyle(ChatFormatting.YELLOW), false);
        return 0;
    }
}
