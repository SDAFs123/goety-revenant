package com.habaiwa.goetyrevenant.client.config;

import com.habaiwa.goetyrevenant.common.config.RevenantConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * **Cloth Config 配置界面** —— 全中文。
 *
 * <p>标签和说明都直接写在代码里（{@code Component.literal("中文")}），
 * 不依赖语言文件，所以无论游戏语言是什么，这个界面都是中文。</p>
 *
 * <p>入口：**模组列表 → 诡厄巫法：回魂 → 配置**。</p>
 */
public final class ClothConfigScreen {
    private ClothConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("诡厄巫法：回魂 · 配置"))
                .setSavingRunnable(RevenantConfig::save);
        ConfigEntryBuilder e = builder.entryBuilder();

        /* ==================== 灵魂 ==================== */
        ConfigCategory soul = builder.getOrCreateCategory(Component.literal("灵魂"));
        soul.addEntry(e.startIntField(Component.literal("灵魂时限（分钟）"), RevenantConfig.soulMinutes)
                .setDefaultValue(20).setMin(1).setMax(180)
                .setTooltip(Component.literal("死后的灵魂能撑多久。到时间会被强制召回（回方舟或出生点）。"))
                .setSaveConsumer(v -> RevenantConfig.soulMinutes = v).build());
        soul.addEntry(e.startIntField(Component.literal("灵魂能量基础上限"), RevenantConfig.soulBase)
                .setDefaultValue(10000).setMin(0).setMax(10000000)
                .setTooltip(Component.literal("灵魂自己的底子。填 0 = 跟随诡厄巫法本体的配置。"))
                .setSaveConsumer(v -> RevenantConfig.soulBase = v).build());
        soul.addEntry(e.startIntField(Component.literal("绑定方舟后的追加上限"), RevenantConfig.soulArcaBonus)
                .setDefaultValue(100000).setMin(0).setMax(10000000)
                .setTooltip(Component.literal("绑了灵魂方舟后，在基础之上**相加**这么多。填 0 = 跟随本体配置。"))
                .setSaveConsumer(v -> RevenantConfig.soulArcaBonus = v).build());
        soul.addEntry(e.startIntField(Component.literal("召回时恢复的灵魂能量"), RevenantConfig.recallRestore)
                .setDefaultValue(1000).setMin(0).setMax(10000000)
                .setTooltip(Component.literal("被强制召回时补回多少灵魂能量。"))
                .setSaveConsumer(v -> RevenantConfig.recallRestore = v).build());

        /* ==================== 契合度 ==================== */
        ConfigCategory attune = builder.getOrCreateCategory(Component.literal("契合度"));
        attune.addEntry(e.startFloatField(Component.literal("自己的身体"), RevenantConfig.attuneSelf)
                .setDefaultValue(1.0F).setMin(0.05F).setMax(1.0F)
                .setTooltip(Component.literal("回到自己的身体。1.0 = 100%%。"))
                .setSaveConsumer(v -> RevenantConfig.attuneSelf = v).build());
        attune.addEntry(e.startFloatField(Component.literal("其他玩家的尸体"), RevenantConfig.attuneOtherPlayer)
                .setDefaultValue(0.95F).setMin(0.05F).setMax(1.0F)
                .setTooltip(Component.literal("附身别人留下的尸体。"))
                .setSaveConsumer(v -> RevenantConfig.attuneOtherPlayer = v).build());
        attune.addEntry(e.startFloatField(Component.literal("村民"), RevenantConfig.attuneVillager)
                .setDefaultValue(0.85F).setMin(0.05F).setMax(1.0F)
                .setTooltip(Component.literal("附身村民。"))
                .setSaveConsumer(v -> RevenantConfig.attuneVillager = v).build());
        attune.addEntry(e.startFloatField(Component.literal("友好生物"), RevenantConfig.attuneFriendly)
                .setDefaultValue(0.70F).setMin(0.05F).setMax(1.0F)
                .setTooltip(Component.literal("附身猪牛羊这类友好生物。"))
                .setSaveConsumer(v -> RevenantConfig.attuneFriendly = v).build());
        attune.addEntry(e.startFloatField(Component.literal("默认（敌对与亡灵）"), RevenantConfig.attuneDefault)
                .setDefaultValue(0.40F).setMin(0.05F).setMax(1.0F)
                .setTooltip(Component.literal("附身僵尸、骷髅这类敌对与亡灵生物。契合度越低，能待的时间越短。"))
                .setSaveConsumer(v -> RevenantConfig.attuneDefault = v).build());
        attune.addEntry(e.startIntField(Component.literal("附身时长下限（秒）"), RevenantConfig.possessMinSeconds)
                .setDefaultValue(30).setMin(1).setMax(3600)
                .setTooltip(Component.literal("不管契合度多低，至少能待这么久。"))
                .setSaveConsumer(v -> RevenantConfig.possessMinSeconds = v).build());

        /* ==================== 附身 ==================== */
        ConfigCategory possess = builder.getOrCreateCategory(Component.literal("附身"));
        possess.addEntry(e.startDoubleField(Component.literal("附身范围（格）"), RevenantConfig.possessRange)
                .setDefaultValue(3.0D).setMin(0.5D).setMax(32.0D)
                .setTooltip(Component.literal("灵魂能附身多远的生物。"))
                .setSaveConsumer(v -> RevenantConfig.possessRange = v).build());
        possess.addEntry(e.startBooleanToggle(Component.literal("允许附身其他玩家"), RevenantConfig.possessPlayers)
                .setDefaultValue(false)
                .setTooltip(Component.literal("开启后可以直接附身活着的其他玩家。默认关闭。"))
                .setSaveConsumer(v -> RevenantConfig.possessPlayers = v).build());
        possess.addEntry(e.startBooleanToggle(Component.literal("启用专属技能配置层"), RevenantConfig.possessAbilityLayer)
                .setDefaultValue(true)
                .setTooltip(Component.literal("读取 data/*/possess_abilities/*.json 里为各个生物配的专属技能。"))
                .setSaveConsumer(v -> RevenantConfig.possessAbilityLayer = v).build());

        /* ==================== 七窍 ==================== */
        ConfigCategory body = builder.getOrCreateCategory(Component.literal("七窍"));
        body.addEntry(e.startIntField(Component.literal("嵌入消耗的灵魂能量"), RevenantConfig.embedCost)
                .setDefaultValue(2000).setMin(0).setMax(10000000)
                .setTooltip(Component.literal("把一枚聚晶嵌进窍里要花的灵魂能量。"))
                .setSaveConsumer(v -> RevenantConfig.embedCost = v).build());
        body.addEntry(e.startIntField(Component.literal("嵌入耗时（秒）"), RevenantConfig.embedSeconds)
                .setDefaultValue(60).setMin(0).setMax(3600)
                .setTooltip(Component.literal("嵌入要抽多久。期间会一直掉血、掉灵魂能量。"))
                .setSaveConsumer(v -> RevenantConfig.embedSeconds = v).build());
        body.addEntry(e.startIntField(Component.literal("嵌入每秒抽灵魂能量"), RevenantConfig.embedDrainPerTick)
                .setDefaultValue(120).setMin(0).setMax(100000)
                .setTooltip(Component.literal("嵌入期间每 tick 抽走的灵魂能量（20 tick = 1 秒）。"))
                .setSaveConsumer(v -> RevenantConfig.embedDrainPerTick = v).build());
        body.addEntry(e.startFloatField(Component.literal("嵌入时血量下限"), RevenantConfig.embedHealthFloor)
                .setDefaultValue(1.0F).setMin(0.5F).setMax(20.0F)
                .setTooltip(Component.literal("嵌入过程中血量最多掉到这里，不会把自己耗死。"))
                .setSaveConsumer(v -> RevenantConfig.embedHealthFloor = v).build());

        /* ==================== 神识 ==================== */
        ConfigCategory sense = builder.getOrCreateCategory(Component.literal("神识"));
        sense.addEntry(e.startIntField(Component.literal("每秒消耗灵魂能量"), RevenantConfig.shenShiDrainPerSecond)
                .setDefaultValue(10).setMin(0).setMax(100000)
                .setTooltip(Component.literal("神识开着的时候每秒烧多少灵魂能量；烧干了会自动收起来。"))
                .setSaveConsumer(v -> RevenantConfig.shenShiDrainPerSecond = v).build());
        sense.addEntry(e.startDoubleField(Component.literal("基础半径"), RevenantConfig.shenShiBaseRadius)
                .setDefaultValue(6.0D).setMin(0.0D).setMax(128.0D)
                .setTooltip(Component.literal("神识能感知多远（1 级强度时）。"))
                .setSaveConsumer(v -> RevenantConfig.shenShiBaseRadius = v).build());
        sense.addEntry(e.startDoubleField(Component.literal("灵窍带来的半径加成"), RevenantConfig.shenShiPerOrifice)
                .setDefaultValue(4.0D).setMin(0.0D).setMax(128.0D)
                .setTooltip(Component.literal("灵窍每提供一份强度，半径再加多少。"))
                .setSaveConsumer(v -> RevenantConfig.shenShiPerOrifice = v).build());
        sense.addEntry(e.startIntField(Component.literal("扫描间隔（tick）"), RevenantConfig.shenShiScanInterval)
                .setDefaultValue(10).setMin(1).setMax(200)
                .setTooltip(Component.literal("每隔多少 tick 刷新一次感知。越小越跟手，也越费性能。"))
                .setSaveConsumer(v -> RevenantConfig.shenShiScanInterval = v).build());

        /* ==================== 血肉 ==================== */
        ConfigCategory flesh = builder.getOrCreateCategory(Component.literal("血肉"));
        flesh.addEntry(e.startIntField(Component.literal("一颗心脏产出"), RevenantConfig.fleshPerHeart)
                .setDefaultValue(2500).setMin(0).setMax(1000000)
                .setTooltip(Component.literal("血肉之笼嚼掉一颗血肉之心，能榨出多少血肉能量。"))
                .setSaveConsumer(v -> RevenantConfig.fleshPerHeart = v).build());
        flesh.addEntry(e.startIntField(Component.literal("血肉图腾容量"), RevenantConfig.fleshTotemCapacity)
                .setDefaultValue(30000).setMin(0).setMax(10000000)
                .setTooltip(Component.literal("血肉图腾最多能装多少血肉能量（笼子榨出来的能量灌进图腾）。"))
                .setSaveConsumer(v -> RevenantConfig.fleshTotemCapacity = v).build());
        flesh.addEntry(e.startIntField(Component.literal("献祭需要的开窍数"), RevenantConfig.sacrificeOrifices)
                .setDefaultValue(6).setMin(0).setMax(9)
                .setTooltip(Component.literal("献祭仪式要求先开几个窍（默认六窍）。"))
                .setSaveConsumer(v -> RevenantConfig.sacrificeOrifices = v).build());

        /* ==================== 灵魂温度 ==================== */
        ConfigCategory temp = builder.getOrCreateCategory(Component.literal("灵魂温度"));
        temp.addEntry(e.startIntField(Component.literal("释放聚晶降温"), RevenantConfig.tempPerCast)
                .setDefaultValue(6).setMin(0).setMax(100)
                .setTooltip(Component.literal("每放一发聚晶掉多少温。低温打得重、高温回得快。"))
                .setSaveConsumer(v -> RevenantConfig.tempPerCast = v).build());
        temp.addEntry(e.startIntField(Component.literal("每次战斗降温"), RevenantConfig.tempPerHit)
                .setDefaultValue(3).setMin(0).setMax(100)
                .setTooltip(Component.literal("打人或被打一次掉多少温。"))
                .setSaveConsumer(v -> RevenantConfig.tempPerHit = v).build());
        temp.addEntry(e.startIntField(Component.literal("战斗中每秒降温"), RevenantConfig.tempCombatPerSecond)
                .setDefaultValue(2).setMin(0).setMax(100)
                .setTooltip(Component.literal("还在打架的时候，每秒持续掉温。"))
                .setSaveConsumer(v -> RevenantConfig.tempCombatPerSecond = v).build());
        temp.addEntry(e.startIntField(Component.literal("休息每秒升温"), RevenantConfig.tempRestPerSecond)
                .setDefaultValue(2).setMin(0).setMax(100)
                .setTooltip(Component.literal("脱战之后每秒回暖多少。"))
                .setSaveConsumer(v -> RevenantConfig.tempRestPerSecond = v).build());
        temp.addEntry(e.startIntField(Component.literal("被治疗升温"), RevenantConfig.tempPerHeal)
                .setDefaultValue(8).setMin(0).setMax(100)
                .setTooltip(Component.literal("每次被治疗涨多少温。"))
                .setSaveConsumer(v -> RevenantConfig.tempPerHeal = v).build());
        temp.addEntry(e.startIntField(Component.literal("脱战判定（tick）"), RevenantConfig.tempCombatTicks)
                .setDefaultValue(100).setMin(20).setMax(1200)
                .setTooltip(Component.literal("多久没打人/被打算脱战，开始回暖。20 tick = 1 秒。"))
                .setSaveConsumer(v -> RevenantConfig.tempCombatTicks = v).build());

        temp.addEntry(e.startBooleanToggle(Component.literal("能量条随温度变色"), RevenantConfig.hudTemperature)
                .setDefaultValue(true)
                .setTooltip(Component.literal("灵魂温度不单独占一块地方 —— 它体现在右下角灵魂能量条的颜色上：低温偏蓝、高温偏橙红。"))
                .setSaveConsumer(v -> RevenantConfig.hudTemperature = v).build());

        /* ==================== 连携 ==================== */
        ConfigCategory combo = builder.getOrCreateCategory(Component.literal("连携"));
        combo.addEntry(e.startBooleanToggle(Component.literal("启用连携"), RevenantConfig.comboEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("主聚晶命中后，自动唤醒身上嵌入的聚晶，叠出元素反应。"))
                .setSaveConsumer(v -> RevenantConfig.comboEnabled = v).build());
        combo.addEntry(e.startIntField(Component.literal("连携窗口（tick）"), RevenantConfig.comboWindow)
                .setDefaultValue(40).setMin(5).setMax(400)
                .setTooltip(Component.literal("施法之后多久内打中敌人算「主聚晶命中」。20 tick = 1 秒。"))
                .setSaveConsumer(v -> RevenantConfig.comboWindow = v).build());
        combo.addEntry(e.startIntField(Component.literal("连携链上限"), RevenantConfig.comboMaxChain)
                .setDefaultValue(4).setMin(2).setMax(7)
                .setTooltip(Component.literal("2 = 只能出双元素反应，3 = 三元素，4 = 混沌。"))
                .setSaveConsumer(v -> RevenantConfig.comboMaxChain = v).build());
        combo.addEntry(e.startIntField(Component.literal("每层消耗灵魂能量"), RevenantConfig.comboCostPerLayer)
                .setDefaultValue(300).setMin(0).setMax(100000)
                .setTooltip(Component.literal("连携每唤醒一枚嵌入聚晶要花的灵魂能量；不够就连携不起来。"))
                .setSaveConsumer(v -> RevenantConfig.comboCostPerLayer = v).build());

        /* ==================== 调试 ==================== */
        ConfigCategory debug = builder.getOrCreateCategory(Component.literal("调试"));
        debug.addEntry(e.startBooleanToggle(Component.literal("启用调试指令"), RevenantConfig.debugCommands)
                .setDefaultValue(true).requireRestart()
                .setTooltip(Component.literal("/grdebug state|unlock|relock|energy|shenshi|collapse|clear（需要 OP）。改完要重启游戏。"))
                .setSaveConsumer(v -> RevenantConfig.debugCommands = v).build());

        return builder.build();
    }
}
