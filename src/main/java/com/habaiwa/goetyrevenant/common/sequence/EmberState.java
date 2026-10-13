package com.habaiwa.goetyrevenant.common.sequence;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * **火种的三态** —— 烛守的一切都绕着它转。
 *
 * <table>
 *   <tr><th>状态</th><th>聚晶</th><th>烛火值</th><th>效果</th></tr>
 *   <tr><td><b>体内</b></td><td>禁用</td><td>保持最高</td>
 *       <td>强化 + 恒温，火种效果最强；**只有这个状态能放温场**</td></tr>
 *   <tr><td><b>环绕</b></td><td>可用</td><td>缓慢下降</td>
 *       <td>强化减半；范围内的友方获得一半恒温</td></tr>
 *   <tr><td><b>离体</b></td><td>可用</td><td>快速下降</td>
 *       <td>强化集中在目标身上；**自己失去所有火种加成**；范围内所有人的灵魂温度被锁住</td></tr>
 * </table>
 *
 * <p>烛火值归零 → 火种自动回体。</p>
 *
 * <h3>火种伤害 = 灵魂温度伤害</h3>
 * <ul>
 *   <li>无视物理护甲（走魔法伤害）</li>
 *   <li>无视火焰抗性（本来就不是火焰伤害）</li>
 *   <li><b>只受灵魂抗性影响</b>：普通 0~5%、精英 5~10%、小BOSS 10~20%、大BOSS 20~35%</li>
 * </ul>
 */
public final class EmberState {
    /** 火种在哪。 */
    public enum Where {
        INSIDE("体内"), ORBIT("环绕"), AWAY("离体");

        private final String cn;

        Where(String cn) {
            this.cn = cn;
        }

        public String cn() {
            return this.cn;
        }
    }

    private static final String KEY_WHERE = "GREmberWhere";
    /** 环绕时每秒掉多少烛火值。 */
    public static final int ORBIT_DRAIN = 2;
    /** 离体时每秒掉多少。 */
    public static final int AWAY_DRAIN = 6;

    private EmberState() {
    }

    public static Where where(ServerPlayer player) {
        int v = player.getPersistentData().getInt(KEY_WHERE);
        Where[] all = Where.values();
        return v >= 0 && v < all.length ? all[v] : Where.INSIDE;
    }

    /** 切换火种状态（切到体内时把烛火值顶满）。 */
    public static void set(ServerPlayer player, Where where) {
        player.getPersistentData().putInt(KEY_WHERE, where.ordinal());
        if (where == Where.INSIDE) {
            CandleValue.holdAtMax(player);
        }
    }

    /* ------------------------------------------------------------------ */

    /** **聚晶能不能用**：火种在体内时禁用（那会儿火种最强，代价就是不能放法术）。 */
    public static boolean focusLocked(ServerPlayer player) {
        return where(player) == Where.INSIDE;
    }

    /** **温场能不能放**：只有火种在体内时才行。 */
    public static boolean canCastField(ServerPlayer player) {
        return where(player) == Where.INSIDE;
    }

    /**
     * **火种强化的系数**。
     *
     * <p>体内 100%、环绕 50%、离体 0%（加成全跑到目标身上去了）。</p>
     */
    public static float powerFactor(ServerPlayer player) {
        return switch (where(player)) {
            case INSIDE -> 1.0F;
            case ORBIT -> 0.5F;
            case AWAY -> 0.0F;
        };
    }

    /** 火种范围内别人的恒温强度：环绕时给一半。 */
    public static float warmthShare(ServerPlayer player) {
        return where(player) == Where.ORBIT ? 0.5F : 0.0F;
    }

    /**
     * **每 tick 维护**：体内保持满、环绕慢降、离体快降；归零就把火种收回来。
     */
    public static void tick(ServerPlayer player) {
        Where where = where(player);
        if (where == Where.INSIDE) {
            CandleValue.holdAtMax(player);
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        CandleValue.drain(player, where == Where.ORBIT ? ORBIT_DRAIN : AWAY_DRAIN);
        if (CandleValue.exhausted(player)) {
            set(player, Where.INSIDE);
            player.displayClientMessage(
                    Component.translatable("message.goety_revenant.ember_returned"), true);
        }
    }

    /* ------------------------------------------------------------------ */

    /**
     * **火种造成的伤害 = 灵魂温度伤害**。
     *
     * <p>走魔法伤害（所以不吃物理护甲），只按目标的**灵魂抗性**打折；
     * 对 BOSS 再乘一次该序列阶段对应的减伤。</p>
     */
    public static void emberDamage(ServerPlayer player, LivingEntity target, float amount) {
        if (amount <= 0.0F || !target.isAlive()) {
            return;
        }
        CandleValue.SoulResist resist = CandleValue.resistOf(target);
        float dmg = amount * resist.factor();
        if (resist == CandleValue.SoulResist.BOSS) {
            dmg *= CandleValue.bossFactor(player);
        }
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().indirectMagic(player, player), dmg);
    }
}
