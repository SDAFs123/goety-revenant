package com.habaiwa.goetyrevenant.common.compat;

import com.habaiwa.goetyrevenant.common.embed.BodyModData;
import com.habaiwa.goetyrevenant.common.network.RevenantNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 「改造身体」的解锁条件：**跳进 Malum（灵灾）的哭泣之井，泡到被它吐出来**。
 *
 * <h3>判定逐字对齐 Malum 自己</h3>
 * <p>灵灾的幽影精魄就是这么拿的 —— {@code TouchOfDarknessHandler#entityTick}：</p>
 * <pre>
 * Block block = level.getBlockState(livingEntity.blockPosition()).getBlock();
 * boolean isInTheGoop = block instanceof PrimordialSoupBlock || block instanceof VoidConduitBlock;
 * if (!isInTheGoop) {
 *     block = level.getBlockState(livingEntity.blockPosition().above()).getBlock();
 *     isInTheGoop = ...;
 * }
 * if (isInTheGoop) {
 *     handler.progressToRejection++;
 *     if (handler.rejection == 0 &amp;&amp; handler.progressToRejection &gt; 60) {
 *         handler.reject(livingEntity);      // ← 被弹出去
 *     }
 * } else {
 *     handler.progressToRejection = 0;       // ← 离开就清零，不是累计
 * }
 * </pre>
 *
 * <p>所以：</p>
 * <ul>
 *   <li>认的方块只有**原初之汤**（`primordial_soup`）和**方舟导管**（`void_conduit`）——
 *       井的核心方块 `weeping_well_core` **不算**，它不是那摊东西</li>
 *   <li>看的是**脚下那格**和**身体那格**（`blockPosition()` / `above()`），不是碰撞箱求交</li>
 *   <li>要**连续泡 {@value #REJECTION_TICKS} tick 以上**（第 61 tick 才触发），中途离开**清零重来**</li>
 *   <li>解锁就发生在**被弹出的那一刻** —— 和幽影精魄同一次判定</li>
 * </ul>
 *
 * <p>方块按**注册名**查找，Malum 缺席时列表为空、这段逻辑直接不跑。</p>
 */
public final class WeepingWellUnlock {
    /**
     * 认作「井里那摊东西」的方块 —— 和 Malum 的 `isInTheGoop` 一致。
     *
     * <p>注意**不含** `weeping_well_core`：那是井的结构核心，站上去不算泡在井里。</p>
     */
    private static final String[] GOOP_BLOCKS = {"primordial_soup", "void_conduit"};

    /**
     * 要连续泡多久才会被「弹出」。
     *
     * <p>Malum 写的是 `progressToRejection++` 之后判断 `&gt; 60`，
     * 所以实际是**第 61 tick**才触发 —— 这里照抄同一个阈值。</p>
     */
    public static final int REJECTION_TICKS = 60;

    /**
     * **泡进去几 tick 就送人走。**
     *
     * <p>Malum 要 61 tick 才把玩家"排斥"出去（那一下就是弹出，可能还捎带着给东西）。
     * 我们不等它 —— 刚踩进那摊东西就把人送进那片白，所以从玩家的角度看是
     * <b>"一跳进去，人就没了"</b>，而不是"被弹回来之后才被送走"。</p>
     */
    public static final int ENTER_TICKS = 4;

    /** 「被井排斥」的进度（每个玩家一份，存在 persistent data 上）。 */
    private static final String KEY_PROGRESS = "GRWellImmersion";

    private static final List<Block> RESOLVED = new ArrayList<>();
    private static boolean resolved;

    private WeepingWellUnlock() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!MalumCompat.loaded()) {
            return;
        }
        for (String name : GOOP_BLOCKS) {
            Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(MalumCompat.MALUM, name));
            if (block != Blocks.AIR) {
                RESOLVED.add(block);
            }
        }
    }

    /**
     * 玩家是不是**泡在井里那摊东西里** —— 逐字对齐 Malum 的 `isInTheGoop`：
     * 只看**脚下那格**和**身体那格**。
     *
     * <p>灵窍的元素超载克制条件也复用这个方法。</p>
     */
    /**
     * 某个位置是不是"那摊东西" —— **客户端也能调**（井口的白光要按它定位）。
     */
    public static boolean isGoop(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        resolve();
        if (RESOLVED.isEmpty()) {
            return false;
        }
        return RESOLVED.contains(level.getBlockState(pos).getBlock());
    }

    public static boolean isInGoop(ServerPlayer player) {
        resolve();
        if (RESOLVED.isEmpty()) {
            return false;
        }
        BlockPos feet = player.blockPosition();
        return RESOLVED.contains(player.level().getBlockState(feet).getBlock())
                || RESOLVED.contains(player.level().getBlockState(feet.above()).getBlock());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // 已经解锁了就彻底不跑，代价为零
        if (BodyModData.isUnlocked(player)) {
            return;
        }
        resolve();
        if (RESOLVED.isEmpty()) {
            return;
        }

        // 每一 tick 都判 —— 阈值是 61 tick，按 10 tick 判会晚最多 9 tick 才解锁
        if (!isInGoop(player)) {
            // 和 Malum 一样：离开那摊东西进度就**清零**，不是慢慢掉
            if (player.getPersistentData().getInt(KEY_PROGRESS) != 0) {
                player.getPersistentData().putInt(KEY_PROGRESS, 0);
            }
            return;
        }

        int progress = player.getPersistentData().getInt(KEY_PROGRESS) + 1;
        if (progress <= ENTER_TICKS) {
            player.getPersistentData().putInt(KEY_PROGRESS, progress);
            return;
        }
        // **联动门槛**：戴着七咒之戒 + 莫测之眼，井底才通着那片白。
        // 缺一样，井就只是口井（跳进去什么也不会发生）。
        // 如果整合包里压根没有 Enigmatic Legacy，这道门槛自动放行。
        if (com.habaiwa.goetyrevenant.common.compat.LegacyCompat.loaded()
                && !com.habaiwa.goetyrevenant.common.compat.LegacyCompat.hasGear(player)) {
            player.getPersistentData().putInt(KEY_PROGRESS, 0);
            return;
        }
        // **抢在 Malum 弹出之前**（它要 61 tick，我们 4 tick 就动手）
        player.getPersistentData().putInt(KEY_PROGRESS, 0);
        unlock(player);
    }

    private static void unlock(ServerPlayer player) {
        // 已经觉醒了：井对你没用了（觉醒即锁定，不可更换）
        if (com.habaiwa.goetyrevenant.common.sequence.Sequence.of(player) != null) {
            return;
        }
        // 跳进井里 → **被送进那片白**（不是被井弹回来）。
        com.habaiwa.goetyrevenant.common.sequence.QuizRoom.enter(player);
        // 答到一半再跳进来，是**接着答**，不从头来（进度存在玩家身上）。
        if (com.habaiwa.goetyrevenant.common.sequence.SevenQuestions.progress(player) <= 0) {
            com.habaiwa.goetyrevenant.common.sequence.SevenQuestions.start(player);
        }
        // **这里不发问答状态**：跨维度会关掉客户端界面，早发等于白发。
        // 真正开问是在落地那一刻（见 QuizRoom.doEnter）。
    }
}
