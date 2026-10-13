package com.habaiwa.goetyrevenant.common.soul;

import com.habaiwa.goetyrevenant.common.possession.SoulForm;

import com.Polarice3.Goety.common.capabilities.soulenergy.ISoulEnergy;
import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 附身、弹出、强制召回灵魂方舟、灵魂能量耗尽后的真死。 */
public final class Possession {
    // 可附身判定与目标查找在 common/possession/PossessionTarget

    /** 被强制召回灵魂方舟时恢复的灵魂能量。 */
    /** 召回时补回的灵魂能量 —— 实际数值读配置「灵魂 / 召回时恢复的灵魂能量」。 */
    public static final int RECALL_SOUL_RESTORE_DEFAULT = 1000;

    private Possession() {
    }


    /** 附身。附身他人尸体 = 连人带物品一起继承。 */
    public static void possess(ServerPlayer player, Entity target) {
        if (target instanceof CorpseEntity corpse) {
            boolean own = corpse.isOwner(player);
            // **站到身体所在的位置** —— 之前 corpse 这条分支没有 teleport
            // （附身活着的东西那条有），于是回自己身体时玩家停在【灵魂站的那一格】：
            // 灵魂飘在半空或差半格点右键，复活后就悬空/错位，不像"回到了自己的身体"。
            // 身体就在眼前（canReturnToOwnBody 限定附身半径内），复活后就该站在它的位置上。
            net.minecraft.world.phys.Vec3 corpsePos = corpse.position();
            corpse.giveItemsTo(player);
            // 继承对方的身份（皮肤）：记下被附身者的 UUID，客户端用它渲染
            if (!own && corpse.getOwnerUUID() != null) {
                player.getPersistentData().putUUID(SoulConstants.KEY_OWNED_SKIN, corpse.getOwnerUUID());
                player.getPersistentData().putString("GROwnedSkinValue", corpse.getSkinValue());
                player.getPersistentData().putString("GROwnedSkinSignature", corpse.getSkinSignature());
                // ★ 连皮肤属性一起发，否则客户端只能查到默认皮肤
                com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSkin(
                        player, corpse.getSkinValue(), corpse.getSkinSignature());
            }
            corpse.discard();

            if (own) {
                // 自己的身体 = 永远彻底复活，**不设限时、不会被弹出**。
                // （原设计让契合度不满时"限时附身"是错的：玩家会莫名被挤出自己的身体。）
                boolean drifting = SoulData.isDrifting(player);
                player.teleportTo(corpsePos.x, corpsePos.y, corpsePos.z);
                endSoul(player);
                // 灵魂回到了自己的身体 —— 这就是"肉身与灵魂重新融合"的那一刻
                LingHua.recordSoulWalk(player);
                if (drifting) {
                    // **出窍游离**回去的是**残破躯体**：魂离体太久，肉身已经不顶用了。
                    // 死亡游离不受影响 —— 那条路本来就是「真死一次再回来」，不该再罚。
                    player.setHealth(Math.max(1.0F, player.getMaxHealth() * SoulConstants.BROKEN_BODY_HEALTH));
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.WEAKNESS,
                            SoulConstants.BROKEN_BODY_WEAK_TICKS, 1, false, true, true));
                    player.displayClientMessage(
                            Component.translatable("message.goety_revenant.possess_self_broken"), false);
                    return;
                }
                player.displayClientMessage(Component.translatable("message.goety_revenant.possess_self"), false);
                return;
            }

            player.teleportTo(corpsePos.x, corpsePos.y, corpsePos.z);
            endSoul(player);
            // **附身的是别人的尸体** —— 按 §4.7，这种状态下用不了聚晶/法杖。
            // 之前 corpse 这条分支压根没标记，于是"附身别人"反而比"附身村民"更自由，
            // 和设计正好相反。标记放在 endSoul 之后（它会清掉，所以顺序不能反）。
            // §4.2：其他玩家的尸体 = 契合度 95%；§4.3：附身时长 = 20 分钟 × 契合度
            SoulData.setPossession(player, "other",
                    Math.round(com.habaiwa.goetyrevenant.common.soul.SoulConstants
                            .soulDurationTicks() * 0.95F),
                    false, 0.95F);
            player.displayClientMessage(Component.translatable("message.goety_revenant.possess_other_corpse"), false);
            return;
        }

        if (!(target instanceof LivingEntity living)) {
            return;
        }

        // 黑名单：这种生物不能附身（挡掉会出问题的，愚者也这么干）
        if (com.habaiwa.goetyrevenant.common.soul.PossessProfile.blacklisted(living)) {
            player.displayClientMessage(Component.translatable(
                    "message.goety_revenant.possess_blacklisted",
                    living.getType().getDescription()), true);
            return;
        }

        float attunement = Attunement.of(living.getType());
        boolean undead = Attunement.isUndeadBody(living);
        Vec3 pos = living.position();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = living.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack.copy());
            }
        }

        // **仇恨保留**：当着能察觉你的生物的面附身 → 附身完成后它们继续敌视你
        com.habaiwa.goetyrevenant.common.soul.SoulPerception.keepAggro(player, 32.0D);

        living.discard();
        player.teleportTo(pos.x, pos.y, pos.z);
        player.setGameMode(SoulData.getPreviousGameMode(player));
        SoulForm.exitSoulForm(player);

        // **继承这具身体的体质**（最大生命/攻击/护甲/移速）
        BodyTraits.apply(player, living);
        // **继承这具身体的能力**（飞行 / 水下呼吸 / 火焰免疫 / 爬墙 / 摔落免疫 / 跳跃）
        PossessAbilities.apply(player, living);
        // **这具身体的独有本事**（数据驱动的适配层：法术 / 效果 / 属性 / 指令）
        com.habaiwa.goetyrevenant.common.soul.PossessProfile.apply(player, living);

        int ticks = Math.max(20 * Math.max(1, com.habaiwa.goetyrevenant.common.config.RevenantConfig.possessMinSeconds),
                (int) (SoulConstants.soulDurationTicks() * attunement));
        SoulData.setPossession(player, living.getType().getDescriptionId(), ticks, undead, attunement);
        // 外貌：别人看你是这个生物（模型 / 装备层 / 尺寸全部跟着生物走）
        // 带上这具身体自己的存档数据 —— 羊的颜色、猫的品种、史莱姆大小、村民职业……
        // 没有它，变体就只能显示默认形态
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.broadcastPossessAppearance(player,
                living.getType().builtInRegistryHolder().key().location().toString(),
                living.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSoulState(player);
        player.displayClientMessage(Component.translatable(
                undead ? "message.goety_revenant.possess_undead" : "message.goety_revenant.possess_body",
                Math.round(attunement * 100.0F)), false);
    }




    /** 灵魂能不能自己回去（有身体且在旁边）。 */
    public static boolean canReturnToOwnBody(ServerPlayer player) {
        CorpseEntity corpse = findCorpse(player);
        return corpse != null && player.distanceToSqr(corpse)
                <= com.habaiwa.goetyrevenant.common.config.RevenantConfig.possessRange * com.habaiwa.goetyrevenant.common.config.RevenantConfig.possessRange;
    }



    /** 契合度耗尽：被从这具身体里弹出去，回到灵魂状态。 */
    public static void eject(ServerPlayer player) {
        float attunement = SoulData.getAttunement(player);
        boolean self = "self".equals(SoulData.getPossessType(player));
        BodyTraits.clear(player);
        PossessAbilities.clear(player);
        SoulData.clearPossession(player);
        SoulForm.enterSoulForm(player);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSoulState(player);

        if (self) {
            // 身体把灵魂挤出来了：东西重新留在尸体里，免得灵魂背着背包到处飘
            ServerLevel level = player.serverLevel();
            CorpseEntity corpse = CorpseEntity.of(player);
            level.addFreshEntity(corpse);
            SoulData.setCorpse(player, corpse.getUUID(), level.dimension());
        }

        player.displayClientMessage(Component.translatable("message.goety_revenant.ejected",
                Math.round(attunement * 100.0F)), false);
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

        endSoul(player);
        player.displayClientMessage(Component.translatable(hasArca
                ? "message.goety_revenant.forced_recall"
                : "message.goety_revenant.forced_recall_spawn",
                Math.round(SoulData.getSelfAttunement(player) * 100.0F)), false);
    }

    /** 把玩家的尸体一起搬到目的地。 */
    private static void recallCorpse(ServerPlayer player, ServerLevel destination, double x, double y, double z) {
        CorpseEntity corpse = findCorpse(player);
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

    @Nullable
    public static CorpseEntity findCorpse(ServerPlayer player) {
        // 出窍游离**不追踪尸体** —— 下面那个 scanForOwned 回退会把「任何属于自己的尸体」
        // 都找出来，那等于变相追踪了，所以这里要先拦掉。
        if (SoulData.isSoul(player) && SoulData.isDrifting(player)) {
            return null;
        }
        UUID corpseId = SoulData.getCorpseId(player);
        ResourceKey<Level> dimension = SoulData.getCorpseDimension(player);
        if (corpseId == null || dimension == null) {
            return null;
        }
        ServerLevel level = player.server.getLevel(dimension);
        if (level != null && level.getEntity(corpseId) instanceof CorpseEntity corpse) {
            return corpse;
        }
        // 回退：Carry On 搬运尸体时会「移除 → 重建」，**实体 UUID 会变**，
        // 记录里的旧 UUID 就查不到了（表现为「搬运后高亮消失」）。
        // 所以按「尸体主人」在所有维度重新找一遍，并顺手更新记录。
        return scanForOwned(player);
    }

    @Nullable
    private static CorpseEntity scanForOwned(ServerPlayer player) {
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof CorpseEntity corpse && corpse.isOwner(player)) {
                    SoulData.setCorpse(player, corpse.getUUID(), level.dimension());
                    return corpse;
                }
            }
        }
        return null;
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

    /** 结束灵魂状态，回到原本的游戏模式。 */
    public static void endSoul(ServerPlayer player) {
        GameType previous = SoulData.getPreviousGameMode(player);
        BodyTraits.clear(player);
        // ------------------------------------------------------------------
        //  **把"附身痕迹"清干净 —— 不清的话，回到自己的身体也不能正常游玩。**
        //
        //  isPossessing() 有 8 处在用，最关键的一处是 EmbeddedCasting：
        //      if (SoulData.isPossessing(player)) { 不给施法 }
        //  那是 §4.7 的正确限制（附在【别人】身体里用不了聚晶/法杖）。
        //
        //  但清除只在 eject() 里做过，endSoul() 里没有。于是这条链走完：
        //      附身在别人身体里 → 契合度到点被弹出 → 飘回自己的尸体 → 附身回自己
        //  玩家已经在自己身体里了，isPossessing 却还是 true
        //  —— 聚晶/法杖永远用不了，怎么都不像"正常游玩"。
        //
        //  顺带把 soulMode / 附身类型也清掉：残留会让下次 isDrifting 判错
        //  （把死亡游离误当成出窍游离，于是"完整复活"被按"残破躯体"处理）。
        // ------------------------------------------------------------------
        SoulData.clearPossession(player);
        SoulData.setSoulMode(player, "");
        SoulData.setTicks(player, 0);
        // 附身来的能力（飞行/水下呼吸/火焰免疫/爬墙/摔落免疫…）也要还回去。
        // 常规路径里 eject() 已经清过一次，但 endSoul() 是共用出口 ——
        // 清两遍是无害的，漏一遍就是"回自己身体后还带着别人的体质"。
        PossessAbilities.clear(player);
        SoulData.setSoul(player, false);
        SoulForm.exitSoulForm(player);
        player.setGameMode(previous == null || previous == GameType.SPECTATOR ? GameType.SURVIVAL : previous);
    }
}
