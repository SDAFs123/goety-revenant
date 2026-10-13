package com.habaiwa.goetyrevenant.common.sequence;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * **烛火值** —— 烛守的核心资源。
 *
 * <h3>怎么变</h3>
 * <ul>
 *   <li><b>击杀怪物获取</b>，不超过当前阶段的上限</li>
 *   <li><b>火种在体内</b>时：保持在最高，**不会下降**</li>
 *   <li><b>火种环绕 / 离体</b>时：开始下降（环绕慢、离体快）</li>
 *   <li><b>归零</b>：火种自动回体</li>
 *   <li>烛火值存在期间，火种的**强化与恒温**持续生效</li>
 * </ul>
 *
 * <h3>上限随阶段长</h3>
 * <table>
 *   <tr><th>阶段</th><th>上限</th><th>对 BOSS 的灵魂减伤</th></tr>
 *   <tr><td>觉醒</td><td>100</td><td>50%</td></tr>
 *   <tr><td>一阶</td><td>200</td><td>45%</td></tr>
 *   <tr><td>二阶</td><td>350</td><td>40%</td></tr>
 *   <tr><td>三阶</td><td>550</td><td>35%</td></tr>
 *   <tr><td>四阶</td><td>800</td><td>30%</td></tr>
 *   <tr><td>五阶</td><td>1100</td><td>25%</td></tr>
 *   <tr><td>六阶</td><td>1500</td><td>20%</td></tr>
 *   <tr><td>七阶</td><td>2000</td><td>15%</td></tr>
 *   <tr><td>成源</td><td>无上限</td><td>无减伤</td></tr>
 * </table>
 */
public final class CandleValue {
    /** 各阶段的上限：索引 = 阶段（0 觉醒 ~ 7 七阶），成源单独处理。 */
    public static final int[] MAX = {100, 200, 350, 550, 800, 1100, 1500, 2000};
    /** 各阶段对 BOSS 的灵魂减伤（0.50 = 只打出 50%）。成源是 0（直接秒）。 */
    public static final float[] BOSS_REDUCTION = {0.50F, 0.45F, 0.40F, 0.35F, 0.30F, 0.25F, 0.20F, 0.15F};

    private static final String KEY_VALUE = "GRCandleValue";
    private static final String KEY_STAGE = "GRCandleStage";

    private CandleValue() {
    }

    /** 当前阶段：0 觉醒，1~7，8 = 成源。 */
    public static int stage(ServerPlayer player) {
        return Math.max(0, Math.min(8, player.getPersistentData().getInt(KEY_STAGE)));
    }

    public static void setStage(ServerPlayer player, int stage) {
        player.getPersistentData().putInt(KEY_STAGE, Math.max(0, Math.min(8, stage)));
    }

    /** 成源之后没有上限。 */
    public static boolean unlimited(ServerPlayer player) {
        return stage(player) >= 8;
    }

    public static int max(ServerPlayer player) {
        if (unlimited(player)) {
            return Integer.MAX_VALUE;
        }
        return MAX[Math.min(MAX.length - 1, stage(player))];
    }

    public static int get(ServerPlayer player) {
        return Math.max(0, player.getPersistentData().getInt(KEY_VALUE));
    }

    public static void set(ServerPlayer player, int value) {
        int capped = unlimited(player) ? Math.max(0, value) : Math.max(0, Math.min(max(player), value));
        player.getPersistentData().putInt(KEY_VALUE, capped);
    }

    public static void add(ServerPlayer player, int delta) {
        set(player, get(player) + delta);
    }

    /** **击杀获取** —— 不超过当前阶段上限。 */
    public static void onKill(ServerPlayer player, int amount) {
        add(player, Math.max(0, amount));
    }

    /** 火种在体内时：保持在最高，不会下降。 */
    public static void holdAtMax(ServerPlayer player) {
        if (!unlimited(player)) {
            set(player, max(player));
        }
    }

    /** 火种不在体内时按状态下降（环绕慢、离体快）。 */
    public static void drain(ServerPlayer player, int perSecond) {
        add(player, -Math.max(0, perSecond));
    }

    /** 归零了没有 —— 归零则火种自动回体。 */
    public static boolean exhausted(ServerPlayer player) {
        return get(player) <= 0;
    }

    /**
     * **对 BOSS 的灵魂减伤**：按阶段查表。烛守的火焰伤害只受灵魂抗性影响，
     * 而 BOSS 的灵魂抗性用这张表来体现。
     */
    public static float bossFactor(ServerPlayer player) {
        if (unlimited(player)) {
            return 0.0F;   // 成源：直接秒
        }
        return BOSS_REDUCTION[Math.min(BOSS_REDUCTION.length - 1, stage(player))];
    }

    /** 敌怪的灵魂抗性档位（普通 / 精英 / 小BOSS / 大BOSS）。 */
    public enum SoulResist {
        COMMON("普通", 0.03F),
        ELITE("精英", 0.08F),
        MINI_BOSS("小BOSS", 0.15F),
        BOSS("大BOSS", 0.28F);

        private final String cn;
        private final float resist;

        SoulResist(String cn, float resist) {
            this.cn = cn;
            this.resist = resist;
        }

        public String cn() {
            return this.cn;
        }

        /** 抗性系数：伤害要乘 (1 - resist)。 */
        public float factor() {
            return 1.0F - this.resist;
        }
    }

    /** 看看某个生物算哪一档（血量与是不是 Boss 决定，粗分）。 */
    public static SoulResist resistOf(net.minecraft.world.entity.LivingEntity entity) {
        float hp = entity.getMaxHealth();
        if (entity.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER && hp >= 200.0F) {
            return SoulResist.BOSS;
        }
        if (hp >= 100.0F) {
            return SoulResist.MINI_BOSS;
        }
        if (hp >= 40.0F) {
            return SoulResist.ELITE;
        }
        return SoulResist.COMMON;
    }
}
