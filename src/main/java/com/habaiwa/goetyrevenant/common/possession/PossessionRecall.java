package com.habaiwa.goetyrevenant.common.possession;

import com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy;
import com.habaiwa.goetyrevenant.common.possession.Possession;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.habaiwa.goetyrevenant.common.soul.SoulConstants;
import com.habaiwa.goetyrevenant.common.soul.SoulData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * **强制召回与真死** —— 这条链的终点。
 *
 * <p>从 `Possession` 抽出来的。</p>
 *
 * <h3>强制召回（20 分钟没找到身体）</h3>
 * <p>召回点是<b>灵魂方舟</b>（绑了的话）或<b>世界出生点</b>，
 * <b>尸体一并召回</b> —— 否则你还得千里迢迢回去找它。
 * 绑了方舟时顺带补回一笔灵魂能量（数值读配置）。</p>
 *
 * <h3>真死</h3>
 * <p>灵魂能量耗尽就是真的死了（设计里唯一的"彻底死亡"）。
 * 关键细节：先把 `KEY_FINAL` 标记打上<b>再</b>调 `hurt()` ——
 * 死亡事件处理器看到这个标记就会<b>放行</b>，不再把人变回灵魂。
 * 否则会陷入"能量归零 → 变灵魂 → 能量还是 0 → 又归零"的死循环
 * （§9.8 那次线上崩服就是这么来的）。</p>
 */
public final class PossessionRecall {
    private PossessionRecall() {
    }

    /**
     * 20 分钟到 / 灵魂能量耗尽前的兜底召回：
     * 绑定了灵魂方舟就回方舟（并恢复灵魂能量），没绑定就出现在出生点（无法补充）。
     * 尸体一并召回，免得玩家复活后还得走回去。
     */
    public static void forceRecall(ServerPlayer player) {
        MinecraftServer server = player.server;
        BlockPos arca = SEHelper.getArcaBlock(player);
        ResourceKey<Level> dimension = SEHelper.getArcaDimension(player);
        ServerLevel bound = dimension == null ? null : server.getLevel(dimension);

        ServerLevel destination;
        double x;
        double y;
        double z;
        boolean hasArca = arca != null && bound != null;

        if (hasArca) {
            destination = bound;
            x = arca.getX() + 0.5D;
            y = arca.getY() + 1.0D;
            z = arca.getZ() + 0.5D;
        } else {
            destination = server.overworld();
            BlockPos spawn = destination.getSharedSpawnPos();
            x = spawn.getX() + 0.5D;
            y = spawn.getY();
            z = spawn.getZ() + 0.5D;
        }

        player.teleportTo(destination, x, y, z, player.getYRot(), player.getXRot());
        recallCorpse(player, destination, x, y, z);

        if (hasArca) {
            ISoulEnergy energy = SEHelper.getCapability(player);
            if (energy != null) {
                energy.increaseSE(Math.max(0, com.habaiwa.goetyrevenant.common.config.RevenantConfig.recallRestore));
            }
        }

        // 被强行拽回来一次，灵魂和自己的本体就更疏远一分
        SoulData.penalizeSelfAttunement(player, SoulConstants.PENALTY_ON_RECALL);

        Possession.endSoul(player);
        player.displayClientMessage(Component.translatable(hasArca
                ? "message.goety_revenant.forced_recall"
                : "message.goety_revenant.forced_recall_spawn",
                Math.round(SoulData.getSelfAttunement(player) * 100.0F)), false);
    }

    /** 把玩家的尸体一起搬到目的地。 */
    private static void recallCorpse(ServerPlayer player, ServerLevel destination, double x, double y, double z) {
        CorpseEntity corpse = Possession.findCorpse(player);
        if (corpse == null) {
            return;
        }
        if (corpse.level() == destination) {
            corpse.teleportTo(x, y, z);
        } else {
            Entity moved = corpse.changeDimension(destination);
            if (moved != null) {
                moved.teleportTo(x, y, z);
            }
        }
    }

    /**
     * 灵魂能量耗尽：这次是真的死了，一无所有地重生。
     *
     * <p><b>这里曾经把服务器搞崩过，所以顺序很讲究。</b> 原来的写法是
     * 「打标记 → 扣血杀人」，可如果**这次死亡被别的模组挡下来了**
     * （整合包里有一堆死亡保护：神秘遗物、各种图腾……），
     * 玩家的灵魂状态还在、能量还是 0 —— 于是**下一 tick 又进来杀一次**，
     * 每 tick 循环，聊天栏一秒刷十几条。循环到某一次抛异常时，
     * Forge 的 EventBus.handleException 会去打日志，而这个包的 log4j 类加载器是坏的
     * （LinkageError: MessageSupplier），**打日志这一步直接把游戏崩掉**。</p>
     *
     * <p>现在：<b>先脱离灵魂状态</b>（这样下一 tick 的 isSoul 检查就把它挡在门外了），
     * 再用一个只活一次、SoulData.clear 不会碰的标记放行这次死亡。</p>
     */
    public static void killSoul(ServerPlayer player) {
        // 1) 先脱离灵魂状态 —— 这是**防重入的关键**：
        //    主循环里那一句「不是灵魂就 return」下一 tick 就不会再走到这儿。
        //    注意 setSoul(false) 内部会 clear() 掉 KEY_FINAL，所以标记必须打在它**之后**。
        SoulData.setSoul(player, false);
        SoulForm.exitSoulForm(player);
        player.setGameMode(GameType.SURVIVAL);
        // 真死的回归点也是**世界出生点** —— 床在回魂体系里不作数。
        SoulForm.clearBedSpawn(player);
        player.setHealth(1.0F);

        // 2) 放行这一次死亡：onLivingDeath 看到这个标记就**不取消、也不再把人变成灵魂**
        player.getPersistentData().putBoolean(SoulConstants.KEY_FINAL, true);
        try {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        } finally {
            // hurt 是同步的，死亡事件在它里面就处理完了
            player.getPersistentData().remove(SoulConstants.KEY_FINAL);
        }
    }
}
