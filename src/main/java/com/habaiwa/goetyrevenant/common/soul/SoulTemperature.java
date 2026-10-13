package com.habaiwa.goetyrevenant.common.soul;

import com.habaiwa.goetyrevenant.common.embed.BodyModData;
import com.habaiwa.goetyrevenant.common.embed.OrganType;
import com.habaiwa.goetyrevenant.common.config.RevenantConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * **灵魂温度** —— 底层资源，管的是"放得多快、打得多重、回得多快、留得多久"。
 *
 * <h3>三个区间</h3>
 * <table>
 *   <tr><td>低温</td><td>释放<b>快</b>、威力<b>高</b>、恢复<b>慢</b>、持续<b>短</b></td></tr>
 *   <tr><td>温</td><td>全都正常</td></tr>
 *   <tr><td>高温</td><td>释放<b>慢</b>、威力<b>低</b>、恢复<b>快</b>、持续<b>长</b></td></tr>
 * </table>
 *
 * <h3>怎么变</h3>
 * <pre>
 *   降温：释放聚晶、战斗、被攻击
 *   升温：休息、被治疗、靠近友方
 * </pre>
 *
 * <h3>与灵体阶段</h3>
 * <p>一二三阶受肉体限制、波动大；四五阶波动减小；六阶灵体化完成后可自由调节；
 * 七阶温度恒定；成源之后温度不再是限制。</p>
 *
 * <h3>与回魂</h3>
 * <p>灵魂状态天然低温（飞得快），回体后要慢慢暖回来；六阶以后回魂失效，温度也不再受影响。</p>
 */
public final class SoulTemperature {
    /** 存档键。 */
    private static final String KEY_VALUE = "GRSoulTemp";
    /** 上次被攻击 / 参与战斗的时间戳。 */
    private static final String KEY_COMBAT = "GRTempCombat";

    /** 温度上下限。 */
    public static final int MIN = 0;
    public static final int MAX = 100;
    /** 灵魂状态时的温度（低温 → 飞得快）。 */
    public static final int SOUL_FORM = 22;

    public enum Band {
        LOW("低温"), WARM("温"), HIGH("高温");

        private final String cn;

        Band(String cn) {
            this.cn = cn;
        }

        public String cn() {
            return this.cn;
        }
    }

    private SoulTemperature() {
    }

    /* ------------------------------------------------------------------ */

    /** 当前温度（0~100）。没存过就用这一阶的默认值。 */
    public static int get(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(KEY_VALUE)) {
            return defaultOf(player);
        }
        return clamp(tag.getInt(KEY_VALUE));
    }

    public static void set(ServerPlayer player, int value) {
        player.getPersistentData().putInt(KEY_VALUE, clamp(value));
    }

    public static void add(ServerPlayer player, int delta) {
        // 成源之后温度不再是限制
        if (isAscended(player)) {
            return;
        }
        set(player, get(player) + scaled(player, delta));
    }

    /** 这一阶的默认温度。 */
    public static int defaultOf(ServerPlayer player) {
        int stage = stage(player);
        return switch (stage) {
            case 0 -> 50;         // 零阶（刚跳井）：不偏不倚
            case 1 -> 75;         // 烛守：高温
            case 5, 6 -> 75;      // 筑墙 / 归墟：高温
            default -> 30;        // 拾荒 / 缄口 / 追影 / 回声：低温
        };
    }

    /** 温度区间。 */
    public static Band band(int value) {
        if (value < 35) {
            return Band.LOW;
        }
        return value > 65 ? Band.HIGH : Band.WARM;
    }

    public static Band band(ServerPlayer player) {
        return band(get(player));
    }

    /* ------------------------------------------------------------------ */
    /*  温度带来的四个倍率                                                  */
    /* ------------------------------------------------------------------ */

    /** 释放速度（冷却乘这个，越小越快）。 */
    public static float cooldownScale(ServerPlayer player) {
        return cooldownScaleFor(get(player));
    }

    /** 数值版（客户端界面也能用）。 */
    public static float cooldownScaleFor(int value) {
        return switch (band(value)) {
            case LOW -> 0.70F;
            case WARM -> 1.00F;
            case HIGH -> 1.40F;
        };
    }

    /** 威力（伤害乘这个）。 */
    public static float powerScale(ServerPlayer player) {
        return powerScaleFor(get(player));
    }

    public static float powerScaleFor(int value) {
        return switch (band(value)) {
            case LOW -> 1.20F;
            case WARM -> 1.00F;
            case HIGH -> 0.85F;
        };
    }

    /** 恢复速度（灵魂能量回复乘这个）。 */
    public static float regenScale(ServerPlayer player) {
        return regenScaleFor(get(player));
    }

    public static float regenScaleFor(int value) {
        return switch (band(value)) {
            case LOW -> 0.60F;
            case WARM -> 1.00F;
            case HIGH -> 1.50F;
        };
    }

    /** 持续时间（效果时长乘这个）。 */
    public static float durationScale(ServerPlayer player) {
        return durationScaleFor(get(player));
    }

    public static float durationScaleFor(int value) {
        return switch (band(value)) {
            case LOW -> 0.80F;
            case WARM -> 1.00F;
            case HIGH -> 1.30F;
        };
    }

    /* ------------------------------------------------------------------ */
    /*  事件                                                              */
    /* ------------------------------------------------------------------ */

    /** 放了一发聚晶 —— 降温。 */
    public static void onCast(ServerPlayer player) {
        add(player, -RevenantConfig.tempPerCast);
    }

    /** 打人或被打 —— 都算战斗，降温 + 记一笔战斗时间。 */
    public static void onCombat(ServerPlayer player) {
        player.getPersistentData().putInt(KEY_COMBAT, player.tickCount);
        add(player, -RevenantConfig.tempPerHit);
    }

    /** 被治疗 —— 升温。 */
    public static void onHealed(ServerPlayer player) {
        add(player, RevenantConfig.tempPerHeal);
    }

    /** 是否还在战斗（最近打过或被打过）。 */
    public static boolean inCombat(ServerPlayer player) {
        int last = player.getPersistentData().getInt(KEY_COMBAT);
        return last != 0 && player.tickCount - last < RevenantConfig.tempCombatTicks;
    }

    /**
     * **每 tick 的漂移**：休息升温、灵魂状态强制低温、七阶锁死、成源不管。
     */
    public static void tick(ServerPlayer player) {
        if (isAscended(player)) {
            return;
        }
        int stage = stage(player);
        int now = get(player);

        // 灵魂状态：天然低温，飞得快
        if (SoulData.isSoul(player)) {
            if (now > SOUL_FORM) {
                set(player, now - 2);
            }
            return;
        }

        // 七阶「回声」之后：温度恒定（不再漂移）
        if (stage >= 7) {
            int def = defaultOf(player);
            if (now != def) {
                set(player, now + (now < def ? 1 : -1));
            }
            return;
        }

        // 六阶：灵体化完成 —— 可以自由调节，所以只是缓慢靠向默认值
        int step = stage >= 6 ? 1 : 0;

        if (player.tickCount % 20 != 0) {
            return;
        }
        if (inCombat(player)) {
            add(player, -RevenantConfig.tempCombatPerSecond);
            return;
        }
        // 休息（和平状态）：升温
        add(player, RevenantConfig.tempRestPerSecond);
        // 低阶受肉体限制：波动大，会被"拽"回这一阶的基准
        if (step > 0) {
            int def = defaultOf(player);
            if (now < def) {
                add(player, 1);
            } else if (now > def) {
                add(player, -1);
            }
        }
    }

    /* ------------------------------------------------------------------ */

    /** 当前序列（1~7，未跳井是 0）。 */
    public static int stage(ServerPlayer player) {
        if (!BodyModData.isUnlocked(player)) {
            return 0;
        }
        int open = 0;
        for (OrganType organ : OrganType.values()) {
            if (BodyModData.isSlotUnlocked(player, organ)) {
                open++;
            }
        }
        return Math.min(7, open + 1);
    }

    /** 是否已成源（温度不再是限制）。 */
    public static boolean isAscended(ServerPlayer player) {
        return player.getPersistentData().getBoolean("GRAscended");
    }

    /**
     * **波动缩放**：灵体化程度越高，温度越稳。
     *
     * <p>一二三阶 100%（肉体限制，波动大）；四五阶 65%；六阶 35%；七阶 0%。</p>
     */
    private static int scaled(ServerPlayer player, int delta) {
        int stage = stage(player);
        float factor = whenStage(stage);
        if (factor <= 0.0F) {
            return 0;
        }
        int out = Math.round(delta * factor);
        if (out == 0 && delta != 0) {
            out = delta > 0 ? 1 : -1;
        }
        return out;
    }

    private static float whenStage(int stage) {
        if (stage >= 7) {
            return 0.0F;
        }
        if (stage >= 6) {
            return 0.35F;
        }
        if (stage >= 4) {
            return 0.65F;
        }
        return 1.0F;
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
