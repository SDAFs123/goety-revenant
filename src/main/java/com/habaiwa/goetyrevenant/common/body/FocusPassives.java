package com.habaiwa.goetyrevenant.common.body;

import com.Polarice3.Goety.api.magic.SpellType;

/**
 * 聚晶被动的**点名表**（第一级查找）。
 *
 * <p>这里只写「值得单独定」的：我们自己的两枚 + 诡厄巫法本体的核心聚晶。
 * 附属模组那几百枚不在这里写 —— 它们走
 * {@link FocusPassive#resolve} 的第二 / 三 / 四级（派系表 → 名字启发 → 兜底），
 * 所以**每一枚都有被动**，不需要一个个登记。</p>
 */
public final class FocusPassives {
    private FocusPassives() {
    }

    public static void registerDefaults() {
        // ===== 我们自己 =====
        // 回魂聚晶：灵魂向 —— 给生存
        FocusPassive.register("goety_revenant:soul_recall_focus", FocusPassive.Category.SURVIVAL, 6.0D);
        // 纳尸聚晶：搬运向 —— 给行动
        FocusPassive.register("goety_revenant:corpse_store_focus", FocusPassive.Category.MOBILITY, 0.06D);

        // 等级可以点名钉死（1~5）—— 稀有度看不准的时候用。不写就走「稀有度 + 注册名关键词」自动判。
        FocusPassive.registerTier("goety_revenant:soul_recall_focus", 4);
        FocusPassive.registerTier("goety_revenant:corpse_store_focus", 3);

        // ===== 本体核心聚晶：点名给（比派系默认更贴定位）=====
        FocusPassive.register("goety:vexing_focus", FocusPassive.Category.COMBAT, 2.0D);
        FocusPassive.register("goety:telekinesis_focus", FocusPassive.Category.MOBILITY, 0.05D);
        FocusPassive.register("goety:bulwark_focus", FocusPassive.Category.ARMOR, 3.0D);
        FocusPassive.register("goety:healing_focus", FocusPassive.Category.SURVIVAL, 6.0D);
        FocusPassive.register("goety:sword_focus", FocusPassive.Category.COMBAT, 3.0D);
        FocusPassive.register("goety:fire_focus", FocusPassive.Category.COMBAT, 2.5D);
        FocusPassive.register("goety:frost_focus", FocusPassive.Category.ARMOR, 2.5D);
        FocusPassive.register("goety:wind_focus", FocusPassive.Category.MOBILITY, 0.05D);
        FocusPassive.register("goety:storm_focus", FocusPassive.Category.SWIFT, 0.15D);
        FocusPassive.register("goety:abyss_focus", FocusPassive.Category.SURVIVAL, 4.0D);
        FocusPassive.register("goety:geomancy_focus", FocusPassive.Category.TOUGHNESS, 2.5D);
        FocusPassive.register("goety:wild_focus", FocusPassive.Category.SURVIVAL, 4.0D);
        FocusPassive.register("goety:void_focus", FocusPassive.Category.MOBILITY, 0.05D);
        FocusPassive.register("goety:necromancy_focus", FocusPassive.Category.FORTUNE, 1.0D);
        FocusPassive.register("goety:illusion_focus", FocusPassive.Category.MOBILITY, 0.05D);
        FocusPassive.register("goety:hostility_focus", FocusPassive.Category.KNOCKBACK, 0.5D);
        FocusPassive.register("goety:mauling_focus", FocusPassive.Category.COMBAT, 3.0D);
        FocusPassive.register("goety:osmantus_focus", FocusPassive.Category.STEADFAST, 0.1D);

        // ===== 派系表（第二级）：本体九大派系各一条 =====
        FocusPassive.registerType(SpellType.NETHER, FocusPassive.Category.COMBAT, 2.5D);
        FocusPassive.registerType(SpellType.FROST, FocusPassive.Category.ARMOR, 2.5D);
        FocusPassive.registerType(SpellType.GEOMANCY, FocusPassive.Category.TOUGHNESS, 2.5D);
        FocusPassive.registerType(SpellType.WIND, FocusPassive.Category.MOBILITY, 0.05D);
        FocusPassive.registerType(SpellType.STORM, FocusPassive.Category.SWIFT, 0.15D);
        FocusPassive.registerType(SpellType.ABYSS, FocusPassive.Category.SURVIVAL, 4.0D);
        FocusPassive.registerType(SpellType.WILD, FocusPassive.Category.SURVIVAL, 4.0D);
        FocusPassive.registerType(SpellType.VOID, FocusPassive.Category.MOBILITY, 0.05D);
        FocusPassive.registerType(SpellType.NECROMANCY, FocusPassive.Category.FORTUNE, 1.0D);
        FocusPassive.registerType(SpellType.NONE, FocusPassive.Category.COMBAT, 2.0D);
    }
}
