package com.habaiwa.goetyrevenant.common.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * **回魂的配置文件** —— 键名、说明全部中文，玩家直接开文件就能看懂。
 *
 * <h3>两套入口，同一份值</h3>
 * <ul>
 *   <li>游戏里：装了 Cloth Config 就在**模组列表 → 配置**里改（图形界面也是中文）</li>
 *   <li>游戏外：直接编辑 {@code config/goety_revenant.json}（改完重进世界生效）</li>
 * </ul>
 *
 * <h3>读取容错</h3>
 * <p>任何一项读不到就用默认值，不会因为手改坏了某个键而崩游戏。</p>
 */
public final class RevenantConfig {
    /** 配置文件名（放在 config 目录）。 */
    public static final String FILE_NAME = "goety_revenant.json";

    /* ==================== 灵魂 ==================== */
    /** 死后的灵魂时限（分钟）。到时间被强制召回。 */
    public static int soulMinutes = 20;
    /** 灵魂能量基础上限。 */
    public static int soulBase = 10000;
    /** 绑定灵魂方舟后追加的上限。 */
    public static int soulArcaBonus = 100000;
    /** 强制召回时恢复多少灵魂能量。 */
    public static int recallRestore = 1000;

    /* ==================== 契合度 ==================== */
    /** 附身自己的身体。 */
    public static float attuneSelf = 1.00F;
    /** 附身其他玩家的尸体。 */
    public static float attuneOtherPlayer = 0.95F;
    /** 村民。 */
    public static float attuneVillager = 0.85F;
    /** 友好生物。 */
    public static float attuneFriendly = 0.70F;
    /** 默认（敌对 / 亡灵）。 */
    public static float attuneDefault = 0.40F;
    /** 附身时长下限（秒）。 */
    public static int possessMinSeconds = 30;
    /** 附身范围（格）。 */
    public static double possessRange = 3.0D;
    /** 是否允许附身其他玩家。 */
    public static boolean possessPlayers = false;
    /** 是否启用「专属技能配置层」（data 目录下 possess_abilities 里的 json）。 */
    public static boolean possessAbilityLayer = true;

    /* ==================== 七窍 ==================== */
    /** 嵌入聚晶消耗的灵魂能量。 */
    public static int embedCost = 2000;
    /** 嵌入耗时（秒）。 */
    public static int embedSeconds = 60;
    /** 嵌入期间每 tick 抽多少灵魂能量。 */
    public static int embedDrainPerTick = 120;
    /** 嵌入期间血量下限。 */
    public static float embedHealthFloor = 1.0F;

    /* ==================== 神识 ==================== */
    /** 神识每秒消耗的灵魂能量。 */
    public static int shenShiDrainPerSecond = 10;
    /** 神识基础半径。 */
    public static double shenShiBaseRadius = 6.0D;
    /** 灵窍带来的半径加成。 */
    public static double shenShiPerOrifice = 4.0D;
    /** 扫描间隔（tick）。 */
    public static int shenShiScanInterval = 10;

    /* ==================== 血肉 ==================== */
    /** 一颗血肉之心产出多少血肉能量。 */
    public static int fleshPerHeart = 2500;
    /** 血肉图腾的容量（笼子把心脏榨出的能量灌进图腾，能装多少由这里定）。 */
    public static int fleshTotemCapacity = 30000;
    /** 献祭成神需要开几窍。 */
    public static int sacrificeOrifices = 6;

    /* ==================== 强化槽 ==================== */
    /** 攻：每槽加成多少伤害（%）。 */
    public static int upgradeForcePerSlot = 6;
    /** 攻的代价：每槽多受多少伤害（%）。 */
    public static int upgradeForceCostPerSlot = 2;
    /** 守：每槽加多少护甲。 */
    public static int upgradeWardPerSlot = 5;
    /** 速：每槽缩短多少冷却（%）。 */
    public static int upgradeSwiftPerSlot = 6;
    /** 速的代价：每槽少打多少伤害（%）。 */
    public static int upgradeSwiftCostPerSlot = 3;
    /** 切换强化方向的冷却（tick，20 = 1 秒）。 */
    public static int upgradeSwitchCooldown = 1200;

    /* ==================== 灵魂温度 ==================== */
    /** 释放一次聚晶降多少温。 */
    public static int tempPerCast = 6;
    /** 打人或被打一次降多少温。 */
    public static int tempPerHit = 3;
    /** 战斗中每秒降多少温。 */
    public static int tempCombatPerSecond = 2;
    /** 休息时每秒升多少温。 */
    public static int tempRestPerSecond = 2;
    /** 被治疗一次升多少温。 */
    public static int tempPerHeal = 8;
    /** 多久没战斗算脱战（tick）。 */
    public static int tempCombatTicks = 100;
    /** 灵魂能量条是否随灵魂温度变色（温度不单独显示）。 */
    public static boolean hudTemperature = true;

    /* ==================== 连携 ==================== */
    /** 连携系统总开关。 */
    public static boolean comboEnabled = true;
    /** 连携窗口（tick）：施法之后多久内打中才算主聚晶命中。 */
    public static int comboWindow = 40;
    /** 连携链上限层数（2 双元素 / 3 三元素 / 4 混沌）。 */
    public static int comboMaxChain = 4;
    /** 每一层连携消耗的灵魂能量。 */
    public static int comboCostPerLayer = 300;

    /* ==================== 调试 ==================== */
    /** 是否启用 /grdebug 调试指令（仅 OP 可用）。 */
    public static boolean debugCommands = true;

    private RevenantConfig() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /* ------------------------------------------------------------------ */

    public static void load() {
        Path path = file();
        try {
            if (!Files.exists(path)) {
                save();
                return;
            }
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();

            JsonObject o = sec(root, "灵魂");
            soulMinutes = intOf(o, "灵魂时限（分钟）", soulMinutes);
            soulBase = intOf(o, "灵魂能量基础上限", soulBase);
            soulArcaBonus = intOf(o, "绑定方舟后的追加上限", soulArcaBonus);
            recallRestore = intOf(o, "召回时恢复的灵魂能量", recallRestore);

            o = sec(root, "契合度");
            attuneSelf = floatOf(o, "自己的身体", attuneSelf);
            attuneOtherPlayer = floatOf(o, "其他玩家的尸体", attuneOtherPlayer);
            attuneVillager = floatOf(o, "村民", attuneVillager);
            attuneFriendly = floatOf(o, "友好生物", attuneFriendly);
            attuneDefault = floatOf(o, "默认（敌对与亡灵）", attuneDefault);
            possessMinSeconds = intOf(o, "附身时长下限（秒）", possessMinSeconds);

            o = sec(root, "附身");
            possessRange = doubleOf(o, "附身范围（格）", possessRange);
            possessPlayers = boolOf(o, "允许附身其他玩家", possessPlayers);
            possessAbilityLayer = boolOf(o, "启用专属技能配置层", possessAbilityLayer);

            o = sec(root, "七窍");
            embedCost = intOf(o, "嵌入消耗的灵魂能量", embedCost);
            embedSeconds = intOf(o, "嵌入耗时（秒）", embedSeconds);
            embedDrainPerTick = intOf(o, "嵌入每秒抽灵魂能量", embedDrainPerTick);
            embedHealthFloor = floatOf(o, "嵌入时血量下限", embedHealthFloor);

            o = sec(root, "神识");
            shenShiDrainPerSecond = intOf(o, "每秒消耗灵魂能量", shenShiDrainPerSecond);
            shenShiBaseRadius = doubleOf(o, "基础半径", shenShiBaseRadius);
            shenShiPerOrifice = doubleOf(o, "灵窍带来的半径加成", shenShiPerOrifice);
            shenShiScanInterval = intOf(o, "扫描间隔（tick）", shenShiScanInterval);

            o = sec(root, "血肉");
            fleshPerHeart = intOf(o, "一颗心脏产出", fleshPerHeart);
            fleshTotemCapacity = intOf(o, "血肉图腾容量", fleshTotemCapacity);
            sacrificeOrifices = intOf(o, "献祭需要的开窍数", sacrificeOrifices);

            o = sec(root, "强化槽");
            upgradeForcePerSlot = intOf(o, "攻·每槽伤害提升（%）", upgradeForcePerSlot);
            upgradeForceCostPerSlot = intOf(o, "攻·每槽受伤提升（%）", upgradeForceCostPerSlot);
            upgradeWardPerSlot = intOf(o, "守·每槽护甲", upgradeWardPerSlot);
            upgradeSwiftPerSlot = intOf(o, "速·每槽冷却缩短（%）", upgradeSwiftPerSlot);
            upgradeSwiftCostPerSlot = intOf(o, "速·每槽伤害降低（%）", upgradeSwiftCostPerSlot);
            upgradeSwitchCooldown = intOf(o, "切换冷却（tick）", upgradeSwitchCooldown);

            o = sec(root, "灵魂温度");
            tempPerCast = intOf(o, "释放聚晶降温", tempPerCast);
            tempPerHit = intOf(o, "每次战斗降温", tempPerHit);
            tempCombatPerSecond = intOf(o, "战斗中每秒降温", tempCombatPerSecond);
            tempRestPerSecond = intOf(o, "休息每秒升温", tempRestPerSecond);
            tempPerHeal = intOf(o, "被治疗升温", tempPerHeal);
            tempCombatTicks = intOf(o, "脱战判定（tick）", tempCombatTicks);
            hudTemperature = boolOf(o, "能量条随温度变色", hudTemperature);

            o = sec(root, "连携");
            comboEnabled = boolOf(o, "启用连携", comboEnabled);
            comboWindow = intOf(o, "连携窗口（tick）", comboWindow);
            comboMaxChain = intOf(o, "连携链上限", comboMaxChain);
            comboCostPerLayer = intOf(o, "每层消耗灵魂能量", comboCostPerLayer);

            o = sec(root, "调试");
            debugCommands = boolOf(o, "启用调试指令", debugCommands);

            System.out.println("[Goety: Revenant] 配置已加载：" + path);
        } catch (Throwable t) {
            System.err.println("[Goety: Revenant] 配置读取失败，全部用默认值：" + t);
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("_说明", "诡厄巫法：回魂 · 配置文件（全中文）。改完保存，重进世界生效；删掉本文件会重新生成默认值。");

        JsonObject soul = new JsonObject();
        soul.addProperty("灵魂时限（分钟）", soulMinutes);
        soul.addProperty("灵魂能量基础上限", soulBase);
        soul.addProperty("绑定方舟后的追加上限", soulArcaBonus);
        soul.addProperty("召回时恢复的灵魂能量", recallRestore);
        root.add("灵魂", soul);

        JsonObject attune = new JsonObject();
        attune.addProperty("自己的身体", attuneSelf);
        attune.addProperty("其他玩家的尸体", attuneOtherPlayer);
        attune.addProperty("村民", attuneVillager);
        attune.addProperty("友好生物", attuneFriendly);
        attune.addProperty("默认（敌对与亡灵）", attuneDefault);
        attune.addProperty("附身时长下限（秒）", possessMinSeconds);
        root.add("契合度", attune);

        JsonObject possess = new JsonObject();
        possess.addProperty("附身范围（格）", possessRange);
        possess.addProperty("允许附身其他玩家", possessPlayers);
        possess.addProperty("启用专属技能配置层", possessAbilityLayer);
        root.add("附身", possess);

        JsonObject body = new JsonObject();
        body.addProperty("嵌入消耗的灵魂能量", embedCost);
        body.addProperty("嵌入耗时（秒）", embedSeconds);
        body.addProperty("嵌入每秒抽灵魂能量", embedDrainPerTick);
        body.addProperty("嵌入时血量下限", embedHealthFloor);
        root.add("七窍", body);

        JsonObject sense = new JsonObject();
        sense.addProperty("每秒消耗灵魂能量", shenShiDrainPerSecond);
        sense.addProperty("基础半径", shenShiBaseRadius);
        sense.addProperty("灵窍带来的半径加成", shenShiPerOrifice);
        sense.addProperty("扫描间隔（tick）", shenShiScanInterval);
        root.add("神识", sense);

        JsonObject flesh = new JsonObject();
        flesh.addProperty("一颗心脏产出", fleshPerHeart);
        flesh.addProperty("血肉图腾容量", fleshTotemCapacity);
        flesh.addProperty("献祭需要的开窍数", sacrificeOrifices);
        root.add("血肉", flesh);

        JsonObject upgrade = new JsonObject();
        upgrade.addProperty("攻·每槽伤害提升（%）", upgradeForcePerSlot);
        upgrade.addProperty("攻·每槽受伤提升（%）", upgradeForceCostPerSlot);
        upgrade.addProperty("守·每槽护甲", upgradeWardPerSlot);
        upgrade.addProperty("速·每槽冷却缩短（%）", upgradeSwiftPerSlot);
        upgrade.addProperty("速·每槽伤害降低（%）", upgradeSwiftCostPerSlot);
        upgrade.addProperty("切换冷却（tick）", upgradeSwitchCooldown);
        root.add("强化槽", upgrade);

        JsonObject temp = new JsonObject();
        temp.addProperty("释放聚晶降温", tempPerCast);
        temp.addProperty("每次战斗降温", tempPerHit);
        temp.addProperty("战斗中每秒降温", tempCombatPerSecond);
        temp.addProperty("休息每秒升温", tempRestPerSecond);
        temp.addProperty("被治疗升温", tempPerHeal);
        temp.addProperty("脱战判定（tick）", tempCombatTicks);
        temp.addProperty("能量条随温度变色", hudTemperature);
        root.add("灵魂温度", temp);

        JsonObject combo = new JsonObject();
        combo.addProperty("启用连携", comboEnabled);
        combo.addProperty("连携窗口（tick）", comboWindow);
        combo.addProperty("连携链上限", comboMaxChain);
        combo.addProperty("每层消耗灵魂能量", comboCostPerLayer);
        root.add("连携", combo);

        JsonObject debug = new JsonObject();
        debug.addProperty("启用调试指令", debugCommands);
        root.add("调试", debug);

        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root),
                    StandardCharsets.UTF_8);
        } catch (Throwable t) {
            System.err.println("[Goety: Revenant] 配置写入失败：" + t);
        }
    }

    /* ------------------------------------------------------------------ */

    private static JsonObject sec(JsonObject root, String name) {
        JsonElement e = root.get(name);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static int intOf(JsonObject o, String k, int def) {
        try {
            return o.has(k) ? o.get(k).getAsInt() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static float floatOf(JsonObject o, String k, float def) {
        try {
            return o.has(k) ? o.get(k).getAsFloat() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static double doubleOf(JsonObject o, String k, double def) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private static boolean boolOf(JsonObject o, String k, boolean def) {
        try {
            return o.has(k) ? o.get(k).getAsBoolean() : def;
        } catch (Throwable t) {
            return def;
        }
    }
}
