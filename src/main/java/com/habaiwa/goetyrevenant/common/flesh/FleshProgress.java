package com.habaiwa.goetyrevenant.common.flesh;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * **一个序列者的血肉走到哪儿了**。
 *
 * <h3>两条线是并行的</h3>
 * <pre>
 *   序列    ——  你的灵魂怎么用（能力、聚晶、魂树、元素反应）
 *   血肉线  ——  你的身体变成什么（形态、抗性、代价）
 * </pre>
 * <p>魂树是上面那棵树，血肉线是土壤之下那些扎进肉里的根。</p>
 *
 * <h3>底色已经定了（2026-10-14）</h3>
 * <p>用户 2026-10-11 给了规则：「每个序列者觉醒后，血肉线都会走向其中一条。
 * <b>不是选的，是你的底色决定的</b>：向外要的人 → 异化；向内收的人 → 灵化；向下沉的人 → 硅化」。</p>
 *
 * <p>实现落在 {@link BloodlineBias}：七问答每答一题顺便投一票
 * （哪一票对应哪个方向，是那张表按选项语义定的），答完七题多数决。
 * <b>觉醒</b>（{@code SevenQuestions.match}）时定下来，之后 {@link #advance} 逐阶往上走。</p>
 *
 * <p>三方向平票时仍然返回 {@code null}，玩家停在"未定" —— 不猜。</p>
 */
public final class FleshProgress {
    private static final String KEY_LINE = "GRFleshLine";
    private static final String KEY_STAGE = "GRFleshStage";

    private FleshProgress() {
    }

    /** 走的是哪条路（null = 还没定）。 */
    public static FleshLine line(Player player) {
        if (player == null) {
            return null;
        }
        return FleshLine.byIndex(player.getPersistentData().getInt(KEY_LINE));
    }

    /** 走到第几阶（0 = 路已定但还没起步）。 */
    public static int stage(Player player) {
        return player == null ? 0
                : Math.max(0, Math.min(FleshLine.STAGES, player.getPersistentData().getInt(KEY_STAGE)));
    }

    public static boolean defined(Player player) {
        return line(player) != null;
    }

    /** 直接设定（调试 / 将来的觉醒流程用）。 */
    public static void set(Player player, FleshLine line, int stage) {
        if (player == null) {
            return;
        }
        CompoundTag tag = player.getPersistentData();
        if (line == null) {
            tag.remove(KEY_LINE);
            tag.putInt(KEY_STAGE, 0);
            return;
        }
        tag.putInt(KEY_LINE, line.ordinal());
        tag.putInt(KEY_STAGE, Math.max(0, Math.min(FleshLine.STAGES, stage)));
    }

    public static void advance(Player player, int by) {
        FleshLine line = line(player);
        if (line != null) {
            set(player, line, stage(player) + by);
        }
    }

    /**
     * **底色 → 走哪条路**。
     *
     * <p><b>还没定，所以现在返回 null。</b></p>
     *
     * <p>三种可能的做法（等设计确认）：</p>
     * <ul>
     *   <li><b>序列决定</b>：七条序列映射到三条路（会有挤兑）</li>
     *   <li><b>行为决定</b>：看玩家怎么玩（杀怪 / 用神识 / 挖矿穿甲）</li>
     *   <li><b>七问答顺带算</b>：觉醒时同一场问答再算出一个"朝哪边"的分</li>
     * </ul>
     * <p>我倾向后两种之一 —— "底色"这个词更像是在问答里问出来的，
     * 而不是从序列名硬推。</p>
     */
    public static FleshLine resolveLine(Player player) {
        return BloodlineBias.resolve(player);
    }
}
