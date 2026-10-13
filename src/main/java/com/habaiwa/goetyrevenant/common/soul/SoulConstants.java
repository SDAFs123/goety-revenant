package com.habaiwa.goetyrevenant.common.soul;

/** 回魂机制的数值常量。 */
public final class SoulConstants {
    /** 灵魂状态总时长上限：20 分钟。 */
    /** 灵魂时限（tick）—— 读配置「灵魂 / 灵魂时限（分钟）」。 */
    public static int soulDurationTicks() {
        return Math.max(1, com.habaiwa.goetyrevenant.common.config.RevenantConfig.soulMinutes) * 60 * 20;
    }

    /** 进入灵魂状态时自带的灵魂能量（就是诡厄巫法本体的灵魂能量，不是新资源）。 */
    public static final int SOUL_START_ENERGY = 10000;

    /**
     * **自主恢复**的间隔与幅度。
     *
     * <p>灵魂状态**不再有常驻抽干** —— 现在能量是「花掉才少、平时自己回」：
     * 花它的地方是施放聚晶和附身，回的是这里。</p>
     *
     * <p>每秒 +20 → 基础档 10000 大约 8 分钟回满，方舟档 100000 就慢得多，
     * 所以「乱放法术会把自己抽干」这件事还是成立的。</p>
     */
    public static final int REGEN_INTERVAL = 20;
    public static final int REGEN_PER_INTERVAL = 20;

    /** 灵魂能量低于这些比例就开始虚弱（越低越惨）。 */
    public static final float WEAK_THRESHOLD_1 = 0.60F;
    public static final float WEAK_THRESHOLD_2 = 0.35F;
    public static final float WEAK_THRESHOLD_3 = 0.15F;

    /** 回魂聚晶（召回队友尸体）消耗。 */
    public static final int RECALL_FOCUS_COST = 10000;
    /** 纳尸聚晶（收纳尸体）消耗。 */
    public static final int STORE_FOCUS_COST = 100;

    /** 附身判定半径。 */
    public static final double POSSESS_RANGE = 4.0D;
    /** 纳尸判定半径。 */
    public static final double STORE_RANGE = 8.0D;

    /** 附身自己的身体时的契合度（100%）。 */
    public static final float ATTUNEMENT_SELF = 1.0F;
    /** 附身在亡灵身体里时，灵魂能量的恢复速度（玩家的 25%）。 */
    public static final float UNDEAD_REGEN_FACTOR = 0.25F;

    // ===== 灵魂游离状态的**两种**（分开存，不然容易弄混）=====
    /** 死亡游离：死了变灵魂，**尸体被追踪**（箭头指过去，附身回自己身体 = 完整复活）。 */
    public static final String MODE_DEATH = "death";
    /** 出窍游离：**主动**按 G 出窍，**不追踪尸体**（没有箭头），走回去附身自己是**残破躯体**。 */
    public static final String MODE_DRIFT = "drift";

    /** 残破躯体：回到自己身体时的血量比例。 */
    public static final float BROKEN_BODY_HEALTH = 0.4F;
    /** 残破躯体的虚弱持续（tick）。 */
    public static final int BROKEN_BODY_WEAK_TICKS = 20 * 60;

    // ===== 灵化：肉身与灵魂完全融合 =====
    /** 灵魂游离（成功回归）过几次 —— 「肉身和灵魂打过交道再重新融合」的凭据。 */
    public static final String KEY_SOUL_WALKS = "GRSoulWalks";
    /** 灵化是否已解锁。 */
    public static final String KEY_LINGHUA_UNLOCKED = "GRLingHuaUnlocked";
    /** 灵化是否正开着。 */
    public static final String KEY_LINGHUA_ACTIVE = "GRLingHuaActive";

    public static final String KEY_SOUL_MODE = "GRSoulMode";
    public static final String KEY_ACTIVE = "GRSoulActive";
    public static final String KEY_TICKS = "GRSoulTicks";
    public static final String KEY_ENERGY = "GRSoulEnergy";
    public static final String KEY_FINAL = "GRSoulFinal";
    public static final String KEY_CORPSE_UUID = "GRCorpse";
    public static final String KEY_CORPSE_DIM = "GRCorpseDim";
    public static final String KEY_PREV_MODE = "GRPrevMode";
    public static final String KEY_POSSESS_TYPE = "GRPossessType";
    public static final String KEY_POSSESS_TICKS = "GRPossessTicks";
    public static final String KEY_POSSESS_UNDEAD = "GRPossessUndead";
    public static final String KEY_ATTUNEMENT = "GRAttunement";
    // ===== 自身身体的契合度（只有「附身自己的身体」才吃这个机制）=====
    /** 初始：自己和自己 100% 契合。 */
    public static final float SELF_ATTUNEMENT_START = 1.0F;
    /** 下限：再惨也不会低于这个值。 */
    public static final float SELF_ATTUNEMENT_MIN = 0.10F;
    /** 每次死亡（灵魂出窍）扣多少。 */
    public static final float PENALTY_ON_DEATH = 0.10F;
    /** 每次被强制召回灵魂方舟扣多少。 */
    public static final float PENALTY_ON_RECALL = 0.10F;
    /** 恢复间隔：1 分钟回 1%。 */
    public static final int ATTUNEMENT_RECOVER_INTERVAL = 1200;
    public static final float ATTUNEMENT_RECOVER_AMOUNT = 0.01F;

    public static final String KEY_SELF_ATTUNEMENT = "GRSelfAttunement";
    public static final String KEY_LAST_SE = "GRLastSe";
    public static final String KEY_OWNED_SKIN = "GROwnedSkin";
    public static final String KEY_STORED_CORPSE = "GRStoredCorpse";

    private SoulConstants() {
    }
}
