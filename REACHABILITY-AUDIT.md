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
- [ ] 方块层逐个核对
- [ ] 系统级触发链：回魂 / 魂树 / 序列 / 血肉线 / 元素反应 / 血肉仪式 / 神识 / 灵化 / 灵魂温度 / 星象
- [ ] `SOUL_WARD` 定性（该做没做完 / 已被取代）
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
