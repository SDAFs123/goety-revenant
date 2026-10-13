package com.habaiwa.goetyrevenant.client.quiz;

import com.habaiwa.goetyrevenant.client.state.QuizClientState;

/**
 * 客户端的「那片白」状态。
 *
 * <h3>转场：落到底的时候屏幕慢慢亮起来</h3>
 * <pre>
 *   跳进井里 → 往下落 → 屏幕**迅速暗下去**（半秒不到，刚好盖住那一下）
 *            → （暗底下换到那个维度）
 *            → 人在白房间的上方，屏幕**逐渐亮起来**
 *            → 落到地上，四周是白的、没有影子
 * </pre>
 * <p>所以不是白屏糊脸，而是"越落越亮"。</p>
 */
public final class QuizRoomClient {
    /**
     * **断线清理** —— 这些是静态字段，退出世界后不清干净，
     * 下一个存档/服务器在有效同步包到达之前会继续用旧的 问答房间状态。
     */
    public static void reset() {
        inside = false;
        darkness = 0.0F;
        darkTarget = 0.0F;
    }

    private static boolean inside = false;
    /** 暗到几分（1 = 全黑，0 = 透亮）。 */
    private static float darkness = 0.0F;
    private static float darkTarget = 0.0F;

    private QuizRoomClient() {
    }

    /** phase 0 = 要开始暗（遮住传送）；phase 1 = 到位（逐渐亮起来）。 */
    public static void accept(int phase, boolean inside) {
        if (phase <= 0) {
            darkTarget = 1.0F;
            return;
        }
        boolean was = QuizRoomClient.inside;
        QuizRoomClient.inside = inside;
        darkTarget = 0.0F;
        if (inside && !was) {
            // 记下"刚进来的时刻" —— 问答要等到落地 + 至少 3 秒之后才开
            QuizClientState.onEnterRoom();
        }
        if (!inside && was) {
            // 出来了：把那句话收起来（下次再进去重新说一遍）
            QuizClientState.onLeaveRoom();
        }
    }

    public static boolean inside() {
        return inside;
    }

    /** 每帧推进：变暗快、变亮稍慢。 */
    public static void tick(float step) {
        if (darkness < darkTarget) {
            darkness = Math.min(darkTarget, darkness + step * 2.6F);
        } else if (darkness > darkTarget) {
            darkness = Math.max(darkTarget, darkness - step * 1.1F);
        }
    }

    public static float darkness() {
        return darkness;
    }

    /** 在里面并且已经亮起来了。 */
    public static boolean settled() {
        return inside && darkness < 0.05F;
    }
}
