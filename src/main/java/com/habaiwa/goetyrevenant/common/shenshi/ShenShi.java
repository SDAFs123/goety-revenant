package com.habaiwa.goetyrevenant.common.shenshi;

import com.habaiwa.goetyrevenant.common.embed.BodyModData;
import com.habaiwa.goetyrevenant.common.embed.OrganType;

import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.habaiwa.goetyrevenant.common.network.ShenShiSyncMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * **神识** —— 修仙者那种神识，不是界面。
 *
 * <p>它不是「一份面板让你去看」，而是**一种感知**：不用你去看任何东西，
 * 它自己就知道状况、并且**主动告诉你**。</p>
 *
 * <h3>神识强度 = 开了几个窍</h3>
 * <p>修为就是这个：七窍开得越多，神识越强、扫得越远。没开窍的人感知不到任何东西。</p>
 *
 * <table border="1">
 *   <tr><th>强度（已开窍）</th><th>半径</th></tr>
 *   <tr><td>0</td><td>无知无觉</td></tr>
 *   <tr><td>1</td><td>10 格</td></tr>
 *   <tr><td>4</td><td>22 格</td></tr>
 *   <tr><td>7（七窍全开）</td><td>34 格</td></tr>
 * </table>
 *
 * <h3>它感知什么</h3>
 * <ul>
 *   <li><b>活物</b> —— 附近有几个、在哪个方向</li>
 *   <li><b>尸体</b> —— 包括**你自己的那具**（这点和回魂咬合：灵魂状态下神识能帮你找身体）</li>
 * </ul>
 *
 * <h3>为什么「不用看」</h3>
 * <p>它<b>只在情况变化时说话</b> —— 有新的活物进范围、或者尸体出现了，才提示一句；
 * 一切照旧的时候一个字都不说。所以玩家不需要盯任何东西，耳朵就够了。</p>
 *
 * <h3>主动扫视</h3>
 * <p>按 <b>N</b> 可以主动「神识扫视」一次，把当半径内的东西完整报一遍。</p>
 */
public final class ShenShi {
    /** 基础半径。 */
    private static double baseRadius() {
        return com.habaiwa.goetyrevenant.common.config.RevenantConfig.shenShiBaseRadius;
    }

    private static double perOrifice() {
        return com.habaiwa.goetyrevenant.common.config.RevenantConfig.shenShiPerOrifice;
    }

    private static int scanInterval() {
        return Math.max(1, com.habaiwa.goetyrevenant.common.config.RevenantConfig.shenShiScanInterval);
    }

    private static int drainPerSecond() {
        return Math.max(0, com.habaiwa.goetyrevenant.common.config.RevenantConfig.shenShiDrainPerSecond);
    }

    /** 每开一个窍加多少半径。 */
    private static final double PER_ORIFICE = 4.0D;
    /** 扫描间隔（tick）—— 神识是一直开着的，所以画面才能"常驻"。 */
    private static final int SCAN_INTERVAL = 10;
    /** 开启的那一下给多久的夜视（=「感知照亮周围」）。 */
    private static final int SWEEP_LIGHT_TICKS = 60;
    /** 神识开着 / 关着的存档键。 */
    private static final String KEY_ACTIVE = "GRShenShiActive";
    /** 神识每秒烧掉多少灵魂能量。 */
    public static final int DRAIN_PER_SECOND = 10;

    /** 每个玩家上一次扫到的东西，用来判断「变了没有」。 */
    private static final Map<UUID, List<UUID>> LAST_SEEN = new HashMap<>();

    private ShenShi() {
    }

    /**
     * 神识强度 —— **灵体只有一窍：灵窍**。
     * 五脏那几窍是给肉身的，不算神识的账。所以强度只有 0 或 1。
     */
    public static int strength(ServerPlayer player) {
        return BodyModData.isSlotUnlocked(player, OrganType.SPIRIT_APERTURE) ? 1 : 0;
    }

    /** 神识能扫多远。没开窍就是 0（无知无觉）。 */
    public static double radius(ServerPlayer player) {
        int strength = strength(player);
        if (strength <= 0) {
            return 0.0D;
        }
        // 强化「深识」：神识半径
        return (baseRadius() + perOrifice() * strength) * com.habaiwa.goetyrevenant.common.embed.UpgradeSlots.senseFactor(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // 神识是**要开的**。关着就什么都不做 —— 不扫描、不发包、不耗能。
        // （开着的时候才是"一直算"，因为本来就是常驻感知。）
        if (!isActive(player)) {
            return;
        }
        // 开着就一直烧灵魂能量；烧不动了自动关掉
        if (player.tickCount % 20 == 0) {
            if ((int) com.Polarice3.Goety.utils.SEHelper.getSESouls(player) < drainPerSecond()) {
                setActive(player, false);
                sendOff(player);
                player.displayClientMessage(Component.translatable(
                        "message.goety_revenant.shenshi_out_of_energy"), true);
                return;
            }
            com.Polarice3.Goety.utils.SEHelper.decreaseSouls(player, drainPerSecond());
        }
        if (player.tickCount % scanInterval() != 0) {
            return;
        }
        try {
            // 灵魂状态下神识也照常工作 —— 而且是唯一能帮你找身体的东西
            List<LivingEntity> found = scan(player);
            if (found == null) {
                return;
            }
            // 画面是全客户端的：这里只把「感知到什么」发过去（每 10 tick 一次，够跟得上）
            sendSnapshot(player, found, false);
            List<UUID> now = new ArrayList<>();
            for (LivingEntity entity : found) {
                now.add(entity.getUUID());
            }
            List<UUID> before = LAST_SEEN.get(player.getUUID());
            LAST_SEEN.put(player.getUUID(), now);
            if (before == null) {
                return;   // 第一次扫不提示，免得一上线就刷屏
            }
            // 只报「新出现的」
            List<LivingEntity> fresh = new ArrayList<>();
            for (LivingEntity entity : found) {
                if (!before.contains(entity.getUUID())) {
                    fresh.add(entity);
                }
            }
            if (!fresh.isEmpty()) {
                announce(player, fresh);
            }
        } catch (Throwable t) {
            System.err.println("[Goety: Revenant] ShenShi 抛异常（已吞掉）: " + t);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  开关                                                               */
    /* ------------------------------------------------------------------ */

    /** 神识现在是不是开着的。 */
    public static boolean isActive(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY_ACTIVE);
    }

    public static void setActive(ServerPlayer player, boolean active) {
        player.getPersistentData().putBoolean(KEY_ACTIVE, active);
    }

    /**
     * 按 N **开 / 关神识**。
     *
     * <p>神识不是白给的：开着的时候每秒烧（数值见配置）灵魂能量，
     * 烧干了自动关掉。所以它是"要用的时候才开"的东西，不是永远挂着的背景板。</p>
     *
     * <p>开启的那一下会来一发扩散波纹 + 夜视（「感知照亮周围」），
     * 之后就是常驻的低强度感知，每 10 tick 刷新一次。</p>
     */
    public static void toggle(ServerPlayer player) {
        // 神识是**灵体的本事**。跳进哭泣之井只能算"半灵"，
        // 肉身还是肉身的灵 —— 眼睛没闭上，神识展不开。
        if (!com.habaiwa.goetyrevenant.common.soul.SoulData.isSoul(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.goety_revenant.shenshi_soul_only"), true);
            return;
        }
        if (strength(player) <= 0) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.shenshi_blind"), true);
            return;
        }
        boolean now = !isActive(player);
        setActive(player, now);

        if (!now) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.shenshi_off"), true);
            sendOff(player);
            return;
        }

        player.displayClientMessage(Component.translatable("message.goety_revenant.shenshi_on",
                drainPerSecond()), true);
        // 「感知照亮周围」：神识一展，四周的形体就显出来了
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SWEEP_LIGHT_TICKS, 0,
                false, false, false));

        List<LivingEntity> found = scan(player);
        if (found == null) {
            found = List.of();
        }
        // pulse = true → 客户端放波纹
        sendSnapshot(player, found, true);

        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.shenshi_empty",
                    Math.round(radius(player))), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.goety_revenant.shenshi_sweep",
                found.size(), Math.round(radius(player))), false);
        announce(player, found);
    }

    /** 调试 / 指令用：强制开到指定状态（复用 toggle 的完整路径，不会绕过提示和波纹）。 */
    public static void force(ServerPlayer player, boolean on) {
        if (isActive(player) == on) {
            return;
        }
        toggle(player);
    }

    /** 告诉客户端"神识关了" —— 半径发 0，客户端据此把整层感知收掉。 */
    private static void sendOff(ServerPlayer player) {
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new ShenShiSyncMessage(new ShenShiSyncMessage.Contact[0], false, 0.0D));
    }

    /** 把感知结果打包发给客户端（画面全在那边画）。 */
    private static void sendSnapshot(ServerPlayer player, List<LivingEntity> found, boolean pulse) {
        List<ShenShiSyncMessage.Contact> contacts = new ArrayList<>();
        for (LivingEntity entity : found) {
            byte kind = ShenShiSyncMessage.KIND_LIVING;
            if (entity instanceof CorpseEntity corpse) {
                kind = corpse.isOwner(player)
                        ? ShenShiSyncMessage.KIND_OWN_CORPSE
                        : ShenShiSyncMessage.KIND_CORPSE;
            } else if (entity instanceof ServerPlayer) {
                kind = ShenShiSyncMessage.KIND_PLAYER;
            }
            contacts.add(new ShenShiSyncMessage.Contact(
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), kind));
        }
        // 掉在地上的聚晶 —— 神识也认得出
        double radius = radius(player);
        AABB box = player.getBoundingBox().inflate(radius);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, box)) {
            if (item.getItem().getItem() instanceof com.Polarice3.Goety.api.items.magic.IFocus) {
                contacts.add(new ShenShiSyncMessage.Contact(
                        item.getX(), item.getY() + 0.2D, item.getZ(), ShenShiSyncMessage.KIND_FOCUS));
            }
        }
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new ShenShiSyncMessage(contacts.toArray(new ShenShiSyncMessage.Contact[0]), pulse, radius));
    }

    /** 扫周围：活物 + 尸体。没开窍返回 null（= 什么都感知不到）。 */
    private static List<LivingEntity> scan(ServerPlayer player) {
        double radius = radius(player);
        if (radius <= 0.0D) {
            return null;
        }
        AABB box = player.getBoundingBox().inflate(radius);
        List<LivingEntity> result = new ArrayList<>(player.level().getEntitiesOfClass(LivingEntity.class, box));
        result.removeIf(entity -> entity == player || !entity.isAlive());
        return result;
    }

    /** 把一批目标按方向报出来。 */
    private static void announce(ServerPlayer player, List<LivingEntity> targets) {
        // 自己的尸体单独点名 —— 这是回魂最想知道的事
        for (LivingEntity entity : targets) {
            if (entity instanceof CorpseEntity corpse && corpse.isOwner(player)) {
                player.displayClientMessage(Component.translatable(
                        "message.goety_revenant.shenshi_own_corpse",
                        bearing(player, entity), (int) player.distanceTo(entity)), true);
                return;
            }
        }
        LivingEntity nearest = targets.get(0);
        double best = player.distanceToSqr(nearest);
        for (LivingEntity entity : targets) {
            double d = player.distanceToSqr(entity);
            if (d < best) {
                best = d;
                nearest = entity;
            }
        }
        player.displayClientMessage(Component.translatable("message.goety_revenant.shenshi_contact",
                targets.size(), bearing(player, nearest), (int) player.distanceTo(nearest)), true);
    }

    /** 八方向罗盘（和寻尸指引同一套说法）。 */
    private static Component bearing(ServerPlayer player, net.minecraft.world.entity.Entity target) {
        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        if (dx * dx + dz * dz < 16.0D) {
            return Component.translatable("dir.goety_revenant.here");
        }
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.round(angle / 45.0D) & 7;
        return Component.translatable("dir.goety_revenant." + COMPASS[index]);
    }

    private static final String[] COMPASS = {"north", "northeast", "east", "southeast",
            "south", "southwest", "west", "northwest"};
}
