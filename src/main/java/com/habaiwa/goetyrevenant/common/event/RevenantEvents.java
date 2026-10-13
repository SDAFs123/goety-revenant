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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            handleDeath(event);
        } catch (Throwable t) {
            report("onLivingDeath", t);
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
            report("onPlayerTick", t);
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
            Traits.undeadDaylight(player);
            Traits.limitUndeadSoulRegen(player);
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
        Traits.applyWeakness(player);

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

    /**
     * 兜底报告：**绝不走 Forge 事件总线的那套日志**（那条路在本整合包里会 LinkageError）。
     *
     * <p>只打一次同样的异常，避免每秒刷屏把日志撑爆。</p>
     */
    private static void report(String where, Throwable t) {
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
        // **不要在这里排除"附身中"** ——
        // 之前写的是 if (!isSoul || isPossessing) return;，那个 || 是错的：
        // 附身只是"魂进了别人的身体"，你还是魂，挨打扣的仍然该是灵魂能量。
        // 排除掉它的后果是附身之后掉血、灵魂能量一点不动 ——
        // 而设计里【灵体状态的那条血就是灵魂能量】（PLAN §7.7.1 需求 4），
        // 附身期间灵魂能量也一直是活的资源（§4.4：附身亡灵能量恢复压到 25%）。
        if (!SoulData.isSoul(player)) {
            return;
        }
        event.setCanceled(true);

        int cost = Math.max(1, Math.round(event.getAmount() * SOUL_DAMAGE_PER_HP));
        int energy = SoulData.getEnergy(player) - cost;
        SoulData.setEnergy(player, energy);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSoulState(player);

        if (energy <= 0) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.soul_energy_gone"), false);
            com.habaiwa.goetyrevenant.common.possession.PossessionRecall.killSoul(player);
        }
    }

    /** 1 点伤害折算多少灵魂能量。 */
    public static final int SOUL_DAMAGE_PER_HP = 100;

    /** 注册调试指令。 */
    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        com.habaiwa.goetyrevenant.common.command.RevenantCommand.register(event.getDispatcher());
    }

    // ===== 灵魂状态禁止指令 =====

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player
                && SoulData.isSoul(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.goety_revenant.no_command"), true);
        }
    }

    // ===== 附身在别人的身体里时用不了聚晶（契合度不够） =====

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

    /** 亡灵身体的特性。 */
    private static final class Traits {
        private Traits() {
        }

        /**
         * **灵魂能量越低越虚弱。**
         *
         * <p>按当前/上限的比例分三档往下叠，都是短时效果、每秒续一次 ——
         * 能量回上来就自动退掉，不会留下永久 debuff。</p>
         */
        static void applyWeakness(ServerPlayer player) {
            if (player.tickCount % 20 != 0) {
                return;
            }
            float ratio = com.habaiwa.goetyrevenant.common.soul.SoulEnergy.ratio(player);
            if (ratio >= SoulConstants.WEAK_THRESHOLD_1) {
                return;
            }
            apply(player, net.minecraft.world.effect.MobEffects.WEAKNESS);
            if (ratio < SoulConstants.WEAK_THRESHOLD_2) {
                apply(player, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            }
            if (ratio < SoulConstants.WEAK_THRESHOLD_3) {
                apply(player, net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN);
                apply(player, net.minecraft.world.effect.MobEffects.BLINDNESS);
            }
        }

        private static void apply(ServerPlayer player, net.minecraft.world.effect.MobEffect effect) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    effect, 40, 0, false, false, false));
        }

        /** 白天没头盔会烧起来。 */
        static void undeadDaylight(ServerPlayer player) {
            Level level = player.level();
            if (level.dimensionType().ultraWarm()) {
                return;
            }
            if (level.isDay() && level.canSeeSky(player.blockPosition())
                    && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                player.setSecondsOnFire(8);
            }
        }

        /**
         * 亡灵身体的灵魂能量恢复速度压到玩家的 25%。
         * 做法：每 tick 对比本体的灵魂能量，一旦发现它涨了，就把涨幅砍掉 75%。
         */
        static void limitUndeadSoulRegen(ServerPlayer player) {
            int current = SEHelper.getSESouls(player);
            int last = SoulData.getLastSe(player);
            if (current > last) {
                current = last + Math.round((current - last) * SoulConstants.UNDEAD_REGEN_FACTOR);
                SEHelper.setSESouls(player, current);
            }
            SoulData.setLastSe(player, current);
        }
    }
}
