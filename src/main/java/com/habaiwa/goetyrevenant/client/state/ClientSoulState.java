package com.habaiwa.goetyrevenant.client.state;

/** 客户端记着服务端下发的「回魂灵魂状态」及其数值。 */
public final class ClientSoulState {
    /**
     * **断线清理** —— 这些是静态字段，退出世界后不清干净，
     * 下一个存档/服务器在有效同步包到达之前会继续用旧的 灵魂状态。
     */
    public static void reset() {
        soul = false;
        attunement = 1.0F;
        energy = 0;
        soulTicks = 0;
        possessTicks = 0;
        bodyDistance = -1;
        bodyBearing = Float.NaN;
        energyMax = 10000;
        drifting = false;
    }

    private static boolean soul;
    private static float attunement = 1.0F;
    private static int energy;
    private static int soulTicks;
    private static int possessTicks;
    /** 到身体的距离（米）；-1 = 找不到身体。 */
    private static int bodyDistance = -1;
    /** 指向身体的方位角（度，MC yaw 约定）；NaN = 没有方向。 */
    private static float bodyBearing = Float.NaN;
    /** 灵魂能量上限（服务端按本体配置算好的：基础量 + 图腾 / 方舟）。 */
    private static int energyMax = 10000;
    /** 是不是出窍游离（主动出窍）—— 这种不追踪尸体、也不高亮。 */
    private static boolean drifting;

    private ClientSoulState() {
    }

    public static void accept(boolean isSoul, float attunementValue, int energyValue,
                              int soulTicksValue, int possessTicksValue,
                              int bodyDistanceValue, float bodyBearingValue, int energyMaxValue,
                              boolean driftingValue) {
        soul = isSoul;
        attunement = attunementValue;
        energy = energyValue;
        soulTicks = soulTicksValue;
        possessTicks = possessTicksValue;
        bodyDistance = bodyDistanceValue;
        bodyBearing = bodyBearingValue;
        energyMax = energyMaxValue;
        drifting = driftingValue;
    }

    /** 是不是**出窍游离**（主动按 G 出窍的那一种）。 */
    public static boolean isDrifting() {
        return drifting;
    }

    /** 灵魂能量上限。 */
    public static int energyMax() {
        return Math.max(1, energyMax);
    }

    /** 只有这个为 true 才是**我们的**灵魂状态 —— 原版旁观者不算。 */
    public static boolean isSoul() {
        return soul;
    }

    public static float attunement() {
        return attunement;
    }

    public static int energy() {
        return energy;
    }

    public static int soulTicks() {
        return soulTicks;
    }

    public static int possessTicks() {
        return possessTicks;
    }

    /** 到身体的距离；-1 = 找不到身体。 */
    public static int bodyDistance() {
        return bodyDistance;
    }

    /** 指向身体的方位角；NaN = 没有方向（没身体，或身体在另一个维度）。 */
    public static float bodyBearing() {
        return bodyBearing;
    }

    /** 身体在不在**另一个维度**（有距离但没方向就是这个情况）。 */
    public static boolean bodyOtherDimension() {
        return bodyDistance >= 0 && Float.isNaN(bodyBearing);
    }

    /** 有没有可以指的身体。 */
    public static boolean hasBody() {
        return bodyDistance >= 0;
    }

    /** 身体是不是已经近在眼前（6 米内）。 */
    public static boolean bodyIsHere() {
        return bodyDistance >= 0 && bodyDistance <= 6;
    }
}
