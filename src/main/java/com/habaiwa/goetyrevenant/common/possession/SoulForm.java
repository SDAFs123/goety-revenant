package com.habaiwa.goetyrevenant.common.possession;

import com.Polarice3.Goety.utils.SEHelper;
import com.habaiwa.goetyrevenant.common.entity.CorpseEntity;
import com.habaiwa.goetyrevenant.common.soul.SoulConstants;
import com.habaiwa.goetyrevenant.common.soul.SoulData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * **灵魂形态的进出** —— 什么时候变成魂、落在哪、怎么不变成魂。
 *
 * <p>从 `Possession` 抽出来的。</p>
 *
 * <h3>两种游离（设计 §9.7）</h3>
 * <table>
 *   <tr><th></th><th>死亡游离 `MODE_DEATH`</th><th>出窍游离 `MODE_DRIFT`</th></tr>
 *   <tr><td>怎么进</td><td>死了</td><td>主动按键</td></tr>
 *   <tr><td>尸体</td><td><b>登记 + 追踪</b>（HUD 指方向）</td><td><b>完全不登记</b>（身体放哪自己记）</td></tr>
 *   <tr><td>回自己身体</td><td><b>完整复活</b></td><td><b>残破躯体</b>：血量四成 + 虚弱 1 分钟</td></tr>
 * </table>
 *
 * <h3>能量</h3>
 * <p><b>死了变灵魂 → 能量充满</b>（充"上限"不是写死 10000）；<b>主动出窍不充能</b> ——
 * 否则"出窍 → 回身体 → 出窍"就是免费回满。</p>
 *
 * <h3>灵魂落点</h3>
 * <p>绑了灵魂方舟 → 方舟正上方；没绑 → <b>世界出生点</b>。
 * <b>床不作数了</b>（所以 `clearBedSpawn`）。尸体不会跟着走，留在你倒下的地方。</p>
 */
public final class SoulForm {
    private SoulForm() {
    }

    /**
     * **主动灵魂出窍** —— 跳过井之后随时可以做的事，不需要任何物品。
     *
     * <p>和「死了才变灵魂」走的是同一套状态，只是：
     * 身体不会死，而是原地留下一具**装着全部家当的尸体**（{@code CorpseEntity.of} 会把背包和装备
     * 一起搬进尸体并清空玩家背包，所以不会复制东西）。</p>
     *
     * @return 是否真的出窍了
     */
    public static boolean voluntarySoulOut(ServerPlayer player) {
        if (SoulData.isSoul(player)) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.soul_out_already"), true);
            return false;
        }
        if (player.isCreative() || player.isSpectator()) {
            player.displayClientMessage(Component.translatable("message.goety_revenant.soul_out_creative"), true);
            return false;
        }

        ServerLevel level = player.serverLevel();
        CorpseEntity corpse = CorpseEntity.of(player);   // 这一步会把背包清空
        level.addFreshEntity(corpse);

        SoulData.setSoul(player, true);
        // **出窍游离**：和死亡游离最大的区别就是**不登记尸体** ——
        // 所以 HUD 不会给你指方向，你得自己记住身体放哪儿了。
        // （走近了照样能左键附身回去，只是回去之后是残破躯体。）
        SoulData.setSoulMode(player, SoulConstants.MODE_DRIFT);
        SoulData.setTicks(player, SoulConstants.soulDurationTicks());
        SoulData.setPreviousGameMode(player, player.gameMode.getGameModeForPlayer());
        // 注意：**主动**出窍不充能 —— 不然「出窍 → 回身体 → 出窍」就是免费回满灵魂能量。
        // 只有「死了变灵魂」才会充满（那是一次性的，没有循环）。
        SoulData.setLastSe(player, SEHelper.getSESouls(player));

        enterSoulForm(player);
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.sendSoulState(player);
        player.displayClientMessage(Component.translatable("message.goety_revenant.soul_out",
                com.habaiwa.goetyrevenant.common.util.KeyNames.key("key.attack")), false);
        player.displayClientMessage(Component.translatable("message.goety_revenant.soul_out_body_left"), false);
        return true;
    }

    /**
     * **死亡游离的灵魂落点**。
     *
     * <p>两档，都**和床无关**：</p>
     * <ul>
     *   <li><b>绑了灵魂方舟</b> → 方舟正上方一格（方舟本来就是死亡回归点）</li>
     *   <li><b>没绑</b> → <b>世界出生点</b></li>
     * </ul>
     *
     * <p>尸体**不会**跟着走 —— 它留在你倒下的地方，于是「回出生点/方舟」和
     * 「回去找身体」变成了两件都要跑的事。</p>
     */
    public static void spawnSoul(ServerPlayer player) {
        // 1) 有方舟 → 方舟旁边
        BlockPos arca = SEHelper.getArcaBlock(player);
        ResourceKey<Level> dimension = SEHelper.getArcaDimension(player);
        if (arca != null && dimension != null) {
            ServerLevel bound = player.server.getLevel(dimension);
            if (bound != null) {
                // 站在方舟正上方一格：方舟本身是方块，直接落在里面会卡住
                player.teleportTo(bound, arca.getX() + 0.5D, arca.getY() + 1.0D, arca.getZ() + 0.5D,
                        player.getYRot(), player.getXRot());
                player.displayClientMessage(
                        Component.translatable("message.goety_revenant.soul_spawn_arca"), false);
                return;
            }
        }
        // 2) 没方舟 → **世界出生点**（不是床）
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.displayClientMessage(
                Component.translatable("message.goety_revenant.soul_spawn_world"), false);
    }

    /**
     * **清掉床的重生点。**
     *
     * <p>回魂体系里床已经不作数了：死亡游离的灵魂去的是灵魂方舟 / 世界出生点，
     * 真死之后的回归点也该是这两处。不清掉的话，玩家会在「灵魂去了世界出生点」
     * 和「真死时被拉回床边」之间来回矛盾。</p>
     */
    public static void clearBedSpawn(ServerPlayer player) {
        player.setRespawnPosition(null, null, 0.0F, false, false);
    }

    /**
     * 进入**自研灵魂形态**。
     *
     * <p>为什么不用原版旁观者：旁观者**不可被选中、免疫伤害**，
     * 而需求是「鬼类能看见并攻击灵魂」且「伤害扣灵魂能量」—— 旁观者做不到这两点。</p>
     *
     * <p>所以自己实现：穿墙（noPhysics）+ 隐身 + 可飞行。游戏模式保持原样，因此**原版旁观者完全不受影响**。</p>
     */
    public static void enterSoulForm(ServerPlayer player) {
        // 真要出窍了，灵化那个"半魂"状态就先收起来
        com.habaiwa.goetyrevenant.common.soul.LingHua.forceOff(player);
        // 灵魂 = **别人完全看不见**（invisible 就够，不渲染）；
        // 视角 = 旁观者一样：穿墙（noPhysics）+ 悬浮失重（noGravity）+ 自由飞行
        // 灵魂应当是**完全看不见**（invisible 就够，不渲染）。
        // （曾经有过一条 SoulAppearanceMessage "半透明可见"，方向是反的，已删除）
        player.noPhysics = true;
        player.setInvisible(true);
        player.setNoGravity(true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    /** 退出灵魂形态，恢复正常状态。 */
    public static void exitSoulForm(ServerPlayer player) {
        // 不是魂了：附身外观撤掉（回自己身体 / 被弹出 / 召回都走这里）
        com.habaiwa.goetyrevenant.common.network.RevenantNetwork.broadcastPossessAppearance(player, null);
        player.noPhysics = false;
        player.setInvisible(false);
        player.setNoGravity(false);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
    }
}
