package com.habaiwa.goetyrevenant.common.body;

import com.habaiwa.goetyrevenant.common.compat.WeepingWellUnlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 元素超载的**克制条件** —— 「到什么地步才算压下去了」。
 *
 * <p>按**五行相克**来：火克金、水克火、土克水、木克土、金克木。
 * 所以超载了就得**去找克它的那个东西**：</p>
 *
 * <table border="1">
 *   <tr><th>超载</th><th>谁来克</th><th>玩家要做什么</th></tr>
 *   <tr><td>火</td><td>水克火</td><td><b>泡进水里</b></td></tr>
 *   <tr><td>水</td><td>土克水</td><td><b>埋进土里</b>（踩土/沙 + 头顶被挡住）</td></tr>
 *   <tr><td>土</td><td>木克土</td><td><b>走进树林</b>（附近有原木或树叶）</td></tr>
 *   <tr><td>木</td><td>金克木</td><td><b>手持斧头</b>（拿金属去砍木）</td></tr>
 *   <tr><td>金</td><td>火克金</td><td><b>让火烧起来</b>（着火或泡岩浆）</td></tr>
 *   <tr><td>灵</td><td>不在五行 —— 回井里</td><td><b>跳进哭泣之井</b></td></tr>
 * </table>
 *
 * <p>条件要**连续满足 {@link #HOLD_TICKS} tick** 才算数，来回蹭一下不算。</p>
 */
public final class ElementCounter {
    /** 需要连续满足多久（tick）才算压住。 */
    public static final int HOLD_TICKS = 60;
    /** 找木头的半径。 */
    private static final int WOOD_RADIUS = 4;

    private ElementCounter() {
    }

    /** 这名玩家现在是否处在能克制 {@code organ} 对应元素的地方。 */
    public static boolean satisfied(ServerPlayer player, OrganType organ) {
        return switch (organ) {
            // 水克火 —— 泡在水里
            case HEART -> player.isInWater() || player.isUnderWater();
            // 土克水 —— 埋进土里：脚下是土/沙，头顶见不到天
            case KIDNEY -> standingOnSoil(player)
                    && !player.level().canSeeSky(player.blockPosition().above());
            // 木克土 —— 附近有原木或树叶
            case SPLEEN -> nearWood(player);
            // 金克木 —— 手上拿着斧头
            case LIVER -> player.getMainHandItem().is(ItemTags.AXES)
                    || player.getOffhandItem().is(ItemTags.AXES);
            // 火克金 —— 烧起来
            case LUNG -> player.isOnFire() || player.isInLava();
            // 灵 —— 回哭泣之井里泡着（和幽影精魄、和解锁七窍用的是同一套判定）
            case SPIRIT_APERTURE -> WeepingWellUnlock.isInGoop(player);
        };
    }

    /** 脚下方块是不是「土类」（泥土 / 草 / 沙 / 砂土……）。 */
    private static boolean standingOnSoil(ServerPlayer player) {
        BlockState below = player.level().getBlockState(player.blockPosition().below());
        return below.is(BlockTags.DIRT) || below.is(BlockTags.SAND);
    }

    /** 附近有没有木类方块。 */
    private static boolean nearWood(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-WOOD_RADIUS, -1, -WOOD_RADIUS),
                center.offset(WOOD_RADIUS, 2, WOOD_RADIUS))) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }

    /** 给玩家看的「要去哪儿」提示。 */
    public static Component hint(OrganType organ) {
        return Component.translatable("counter.goety_revenant." + organ.getElement());
    }
}
