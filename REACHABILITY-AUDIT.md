# 代码可达性审计（有效果 / 无效果）

> **目的**：用户 2026-10-13 要求「查清楚所有代码有效果无效果」。
> **方法**：从入口往下追调用链 —— 注册了但类里没有处理器 = 无效；写了方法但没人调用 = 死代码；
> 数据同步了但 UI 不读 = 到不了玩家。
> **判定**：✅ 有调用链 ｜ ★ 死代码 / 断链 ｜ ⚠️ 需人工确认

---

## 一、事件层 —— ✅ 全部接上

| 项 | 数量 | 结论 |
|---|---|---|
| 有 `@SubscribeEvent` 的类 | **31** | —— |
| 其中带 `@Mod.EventBusSubscriber` 自动注册 | 16 | ✅ |
| 需手动注册（`MinecraftForge.EVENT_BUS.register`） | 15 | ✅ **14 个已注册** |
| `ModEntities.Attributes`（嵌套类带 MOD bus 注解） | 1 | ✅ 自动注册，**不是漏** |

**结论**：**没有"注册了空类"或"有处理器没注册"的情况** —— 这一层是干净的。

**（历史问题已修）**：`RevenantEvents` 曾经**双重注册**（注解 + 手动），导致
`onPlayerTick` 跑两遍、`onSoulHurt` 扣双倍能量。已于 2026-10-13 修掉。

## 二、网络层 —— ✅ 22 个消息全注册，★ 1 个发送方法无人用

| 项 | 数量 |
|---|---|
| 注册的消息 | **22** |
| `RevenantNetwork` 的发送方法 | 9 |

| 发送方法 | 调用者 |
|---|---|
| `sendSoulState` | SoulCombatHandler / SoulDeathHandler / SoulTickHandler / SoulForm / LingHua 等 ✅ |
| `sendBodyState` | ElementOverload / DebugCommands / RevenantCommand / RevenantEvents / AnswerMessage 等 ✅ |
| `sendSkin` | RevenantEvents / SoulDeathHandler / Possession ✅ |
| `broadcastPossessAppearance` | SoulForm / Possession ✅ |
| `sendQuizRoom` | QuizRoom ✅ |
| `sendQuizState` | AnswerMessage / QuizRoom ✅ |
| `sendConverterSync` | SpiritConverterBlock / ConverterQueryMessage / ConverterSetMessage ✅ |
| **`broadcastSoulAppearance`** | ★ **没有任何调用者** |

**★ `broadcastSoulAppearance` + `SoulAppearanceMessage`：死代码**。
但这是**故意不用的** —— `SoulForm` 的注释写着「**不要 broadcastSoulAppearance** ——
那会让人半透明可见，方向反了」（灵魂应该是**完全看不见**，不是半透明）。
**建议**：删掉这条消息 + 方法，或保留但在注释里写明"已弃用"。

## 三、键位 —— ★ 1 个死键位

`BodyKeybinds` 定义 8 个 `KeyMapping`，其中 **7 个有 `consumeClick`**：

| 键位 | 状态 |
|---|---|
| `OPEN_BODY` / `OPEN_RADIAL` / `SOUL_OUT` / `SHEN_SHI` / `LING_HUA` / `QUIZ_BEGIN` / `QUIZ_LEAVE` | ✅ 有响应 |
| **`UPGRADE_CYCLE`** | ★ **定义了但没有任何 `consumeClick`** —— 按下去没反应 |

**这正是用户 2026-10-10 要的那个**：「同样的被动效果一个个调很麻烦，
**增加快捷键可以切换当前序列的选项**」—— 键位建好了，**切换逻辑没写**。

## 四、配置项 —— ✅ 43 项全部有读取点

`RevenantConfig` 的 43 个字段，**没有一个定义了却没人读**。这一层干净。

（用户 2026-10-06 要求：「我们的添加和 cloth 的兼容，**我们的配置文件全中文**」——
`ClothConfigEntry` + `ClothConfigScreen` 已实现，配置界面标题目前仍叫「七窍」（术语待更新）。）

## 五、状态效果 —— ★ 1 个死效果

| 效果 | 使用者 |
|---|---|
| `SOUL_COLLAPSE`（灵魂溃散） | ✅ SoulTickHandler / SoulCollapse |
| **`SOUL_WARD`** | ★ **没有任何实现体用它** |

（另有 `ElementOverload` 动态注册的一组「元素超载」效果，走 `ModEffects.overload(organ)`。）

**★ `SOUL_WARD`：死效果**。名字看起来是"灵魂护盾"，但代码里没人施加它 ——
需要确认是**该做没做完**，还是**已经被别的机制取代**。

## 六、物品 / 方块 —— ⚠️ 待逐个核对

| 注册表 | 条目数 |
|---|---|
| `ModItems` | 40 处注册 |
| `ModBlocks` | 17 处注册 |

**尚未逐个核对"每个物品/方块有没有实际用途"** —— 这是下一批。

---

## 待办

- [x] **物品层已核对**（见 §七）—— 2 个完全无效
- [x] **方块层已核对**（见 §八）
- [x] **系统级触发链已核对**（见 §九）
- [ ] `SOUL_WARD` 定性（**待你定**）
- [ ] `UPGRADE_CYCLE` 补上切换逻辑（用户明确要过的功能）
- [ ] `broadcastSoulAppearance` 删或标注弃用

## 七、物品层 —— ★ 2 个完全无效，其余接上

19 个物品全部进了创造栏，但"进创造栏"不等于"有效果"。逐个追实现类和外部引用：

| 物品 | 实现类 | 行数 | 关键方法 | 外部引用 | 判定 |
|---|---|---|---|---|---|
| **`SOUL_TALISMAN`（聚魂护符）** | **原版 `Item`** | — | **无** | **0 处** | ★ **完全无效** |
| **`SOUL_HARVEST_CHARM`（精魂收割护符）** | `SoulHarvestCharmItem` | 30 | 只有 `appendHoverText` | **0 处** | ★ **完全无效** |
| `FLESH_HEART`（血肉之心） | `FleshHeartItem` | 33 | 只有 `appendHoverText` | **5 处** | ✅ 有效（血肉之笼的槽位判定） |
| `FLESH_TOTEM`（血肉图腾） | `FleshTotemItem` | 87 | `capacity` / `inventoryTick` | **11 处** | ✅ 有效（血肉能量容器） |
| `SOUL_STAINED_STEEL_TOTEM` | `SoulStainedSteelTotemItem` | 117 | `getMaxSouls` / `inventoryTick` | ✅ | ✅ 有效 |
| `REVENANT_STAFF` | `RevenantStaffItem` | 24 | `getSpellType` | ✅ | ✅（走本体施法接口） |
| `SOUL_RECALL_FOCUS` | `SoulRecallFocusItem` | 26 | `getSpell` | ✅ | ✅ |
| `CORPSE_STORE_FOCUS` | `CorpseStoreFocusItem` | 26 | `getSpell` | ✅ | ✅ |
| `REVENANT_*`（装备 4 件） | `RevenantRobeItem` | 27 | `getSoulDiscount` | ✅ | ✅ |
| `SPIRIT_CONVERTER` / `FLESH_CAGE` / `FLESH_ALTAR` / `DEVOUR_PEDESTAL` / `BLOOD_VESSEL` | 各方块类 | — | — | ✅ | ✅ |
| `WHITE_LIGHT` | `BlockItem` | — | — | ✅ | ✅（纯装饰方块） |
| `REVENANT_SPAWN_EGG` | `ForgeSpawnEggItem` | — | — | ✅ | ✅ |

### ⚠️ 两个死物都是用户明确要求过的功能

| 用户原话（2026-09-27） | 现状 |
|---|---|
| 「**聚魂护符修改为增加获取灵魂能量的护符，增加 5%**」 | `SOUL_TALISMAN` 是原版 `Item`，**一行逻辑都没有** → **没做** |
| 「精魂收割物品（本模组与 Iron 的法术与魔法书的武器、工具）`#malum:soul_hunter_weapon`…**增加击杀敌人时的精魂掉落**」 | `SoulHarvestCharmItem` **只有 tooltip**，没接进任何击杀结算 → **没做** |

**（注意）**：这两个不是"废弃设计"，是**用户要的功能没有实现** —— 与七窍那批性质不同。

## 八、方块层 —— ★ 1 个空类

6 个方块**全部被引用**，9 个 block 类：

| 方块 | 实现 | 判定 |
|---|---|---|
| **`BLOOD_VESSEL`（血管）** | `BloodVesselBlock` **20 行纯空类**（只有构造器） | ★ **"用血管连接每一个基座"没实现** |
| `FLESH_ALTAR` | `FleshAltarBlock` 50 行，`mouth_open` / `eyes` 两个 blockstate + 继承本体 `DarkAltarBlock` | ✅ |
| `FLESH_CAGE` | `FleshCageBlock` 116 + `FleshCageBlockEntity` 181 行 | ✅ |
| `SPIRIT_CONVERTER` | `SpiritConverterBlock` 79 + `SpiritConverterBlockEntity` 239 行 | ✅ |
| `DEVOUR_PEDESTAL` | `DevourPedestalBlock` 37 行，继承本体 `PedestalBlock` | ✅（但**没有自己的基座逻辑**） |
| `WHITE_LIGHT` | `WhiteLightBlock` 20 行空类 | ✅ 正常（纯装饰） |

## 九、系统级触发链 —— 8 个通、2 个断

| 系统 | 触发链 | 判定 |
|---|---|---|
| **回魂** | 死亡 → `SoulDeathHandler` → `SoulForm` → 附身（`PossessionTarget`/`Possession`）→ 召回/真死（`PossessionRecall`）。**210 处 / 32 文件** | ✅ 通 |
| **魂树** | 按键 → `TreeScreen` → 嵌入（`UpgradeMessage`）→ `UpgradeSlots`。**40 处 / 18 文件** | ✅ 通 |
| **序列** | 跳井 → `WeepingWellUnlock` → 纯白维度 → `SevenQuestions`/`QuizRoom` → 觉醒。**157 处 / 22 文件** | ✅ 通 |
| **神识** | 键位 → `ShenShiMessage` → `ShenShi` → `ShenShiSyncMessage` → `ShenShiOverlay`。**19 文件** | ✅ 通 |
| **灵化** | 键位 → `LingHuaMessage` → `LingHua` → `SoulTickHandler`。**18 文件** | ✅ 通 |
| **灵魂温度** | 战斗/治疗/休息 → `SoulTemperature` → `BodyStateMessage` 同步 → HUD/反应威力。**12 文件** | ✅ 通 |
| **星象** | `AstrologyCompat` 反射调 `Constellations.inSeason/nodes` → `TreeScreen` 画当空之宫。**4 文件** | ✅ 通 |
| **元素反应** | `EmbeddedCasting:85` 调 `ComboChain.recordCast` → 主命中 → `ReactionEvents.onHurt` → `ComboChain.onMainHit` → 反应 + 温度 + 强化槽倍率。**7 文件** | ✅ **通**（链路完整，含防递归标记与超时重开） |
| **血肉仪式** | 注册进本体 `RitualType` → `FleshRitual.canStart` → `onFinishRitual` → `FleshRites.devour/sacrifice`。**273 行** | ⚠️ **有代码但缺 2 项**（见下） |
| **血肉线** | `FleshProgress.line/stage` 只被**读**；`set` 只被 `DebugCommands` 调；**`advance` / `resolveLine` / `defined` 无人调用** | ★ **断** —— 游戏内没有晋升途径，只能靠 `/grdebug` |

## 十、★ 完整"无效果 / 缺功能"清单（10 条）

| # | 项目 | 性质 | 用户是否要求过 |
|---|---|---|---|
| 1 | **`SOUL_TALISMAN`（聚魂护符）** | 注册成原版 `Item`，**零逻辑**，外部 0 引用 | ✅ 要求过「**增加 5% 灵魂能量获取**」 |
| 2 | **`SoulHarvestCharmItem`（精魂收割护符）** | 30 行，**只有 tooltip**，外部 0 引用 | ✅ 要求过「**增加击杀敌人时的精魂掉落**」 |
| 3 | **`UPGRADE_CYCLE` 键位** | 定义了但**没有 `consumeClick`**，按下无反应 | ✅ 要求过「**快捷键切换当前序列的选项**」 |
| 4 | **`BloodVesselBlock`** | **20 行纯空类** | ✅ 要求过「**用血管连接每一个基座**」 |
| 5 | **`FleshRitual.getRequirement`** | 直接 `return true`，**没有多方块判定** | ✅ 要求过「祭坛 + 基座……多方块」 |
| 6 | **`FleshProgress.advance` 等** | **无人调用** → 血肉线只能靠指令推进 | ✅ 要求过「血肉晋升线」 |
| 7 | `SOUL_WARD` 状态效果 | 定义了，**无人施加** | ❓ 待你定（该做 / 已取代） |
| 8 | `broadcastSoulAppearance` + `SoulAppearanceMessage` | 死代码，**故意不用**（灵魂该完全看不见） | — 建议删或标注弃用 |
| 9 | `ComboChain.clear` | 死方法（但**超时机制正常**，无害） | — 可删 |
| 10 | `FleshRitual` / `FleshRites` 用 `OrganType` | **用的是已废的六窍枚举** | — 需改成"枝" |

**⚠️ 第 1~6 条不是"废弃设计"，是用户要求过、但没实现或没接上。**
