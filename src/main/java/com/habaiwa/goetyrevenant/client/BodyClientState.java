package com.habaiwa.goetyrevenant.client;

import com.habaiwa.goetyrevenant.common.network.BodyStateMessage;
import javax.annotation.Nullable;

/** 客户端暂存的服务端改造状态（身体界面打开时读它渲染）。 */
public final class BodyClientState {
    @Nullable
    /**
     * **断线清理** —— 这些是静态字段，退出世界后不清干净，
     * 下一个存档/服务器在有效同步包到达之前会继续用旧的 玩家状态。
     */
    public static void reset() {
        state = null;
        // seenStage 尤其重要：不清的话，新存档里"第一次看到这个阶"会被当成已经看过，
        // 生长动画莫名不播。
        seenStage = Integer.MIN_VALUE;
    }

    private static BodyStateMessage state;

    private BodyClientState() {
    }

    public static void accept(BodyStateMessage message) {
        state = message;
    }

    /** 由命令触发打开界面。 */
    public static void openScreen() {
        net.minecraft.client.Minecraft.getInstance()
                .setScreen(new com.habaiwa.goetyrevenant.client.screen.BodyScreen());
    }

    @Nullable
    public static BodyStateMessage get() {
        return state;
    }

    public static boolean unlocked() {
        return state != null && state.unlocked();
    }

    /** 当前序列（-1 = 没跳井，0 = 零阶幼苗）。 */
    public static int stage() {
        return state == null ? -1 : state.stage();
    }

    /** **觉醒的是哪一条序列**（-1 = 还没觉醒）。 */
    public static int sequence() {
        return state == null ? -1 : state.sequence();
    }

    /** **血肉线**走的是哪条（-1 = 未定）。 */
    public static int fleshLine() {
        return state == null ? -1 : state.fleshLine();
    }

    /** 血肉线走到第几阶。 */
    public static int fleshStage() {
        return state == null ? 0 : state.fleshStage();
    }

    /** 24 个强化槽的状态：0 = 空，1/2/3 = 攻/守/速。 */
    public static int[] upgrades() {
        return state == null ? new int[24] : state.upgrades();
    }

    /** 距下次能切换强化方向还有多少 tick。 */
    public static int upgradeCooldown() {
        return state == null ? 0 : state.upgradeCooldown();
    }

    /** 这次打开要不要播生长动画（序列升了才播）。 */
    public static boolean isNewStage() {
        return state != null && state.newStage();
    }

    /** 客户端自己记着"上一回看到的是第几阶"。 */
    private static int seenStage = Integer.MIN_VALUE;

    /**
     * **这个阶段是不是头一回被看到** —— 决定要不要播生长动画。
     *
     * <p>为什么不直接用服务端那个 {@code newStage} 标志：它是<b>取一次就清掉</b>的，
     * 而界面每 10 tick 就向服务端要一次状态 —— 打开界面的那一刻，
     * 那个标志早就在某次例行刷新里被消费掉了。于是客户端一直拿到 false，
     * 生长动画永远不播。</p>
     *
     * <p>改成客户端自己记：上一次看到的是第几阶，跟这次比。
     * 第一次打开会播，之后阶段没变就不播，阶段变了再播 —— 不依赖网络时序。</p>
     */
    public static boolean takeNewStageLocally() {
        int s = stage();
        if (s == seenStage) {
            return false;
        }
        seenStage = s;
        return true;
    }

    /** 灵魂温度（0~100）—— 界面 / HUD 显示用。 */
    public static int temperature() {
        return state == null ? 50 : state.temperature();
    }

    /** 玩家当前的灵魂能量（界面显示够不够）。 */
    public static int soulEnergy() {
        return state == null ? 0 : state.soulEnergy();
    }

    /** 这个窍这次嵌入要花多少灵魂能量。 */
    public static int costOf(String organId) {
        if (state == null) {
            return 0;
        }
        for (BodyStateMessage.Entry entry : state.entries()) {
            if (entry.organ().equals(organId)) {
                return entry.cost();
            }
        }
        return 0;
    }

    /** 已经开窍的窍数（tooltip 用）。 */
    public static int openedCount() {
        return countOpened(true);
    }

    /** 窍的总数。 */
    public static int total() {
        return state == null ? 0 : state.entries().length;
    }

    /** 灵魂能量上限（服务端算好的那份）。 */
    public static int energyMax() {
        return state == null ? 0 : state.energyMax();
    }

    private static int countOpened(boolean opened) {
        if (state == null) {
            return 0;
        }
        int count = 0;
        for (BodyStateMessage.Entry entry : state.entries()) {
            if (entry.opened() == opened) {
                count++;
            }
        }
        return count;
    }

    public static boolean isLocked(String organId) {
        if (state == null) {
            return false;
        }
        for (BodyStateMessage.Entry entry : state.entries()) {
            if (entry.organ().equals(organId)) {
                return entry.locked();
            }
        }
        return false;
    }

    public static boolean isOpened(String organId) {
        if (state == null) {
            return false;
        }
        for (BodyStateMessage.Entry entry : state.entries()) {
            if (entry.organ().equals(organId)) {
                return entry.opened();
            }
        }
        return false;
    }
}
