# 诡厄巫法：回魂 / Goety: Revenant —— 设计与实现（全量）

> **整合包特供附属** ｜ 作者 habaiwa ｜ Minecraft **1.20.1** / Forge **47.3.0** ｜ 主体 Goety **2.5.58.4**
> 最后更新：对应 **59 个源文件 / 68 个 class / jar 145 KB**，构建通过。

---

## 1. 一句话

玩家不会真的死 —— 身体留在原地（保留皮肤与装备），灵魂被弹出来找身体附身；
找不到就烧光灵魂能量真死。另有一套**嵌入身体**系统：把聚晶嵌进五脏+灵窍，不拿法杖也能施法。

---

## 2. 技术基线

| 项 | 取值 |
|---|---|
| Minecraft / Forge | 1.20.1 / 47.3.0 |
| 构建 | ForgeGradle **6.0.54** + Gradle **8.8**（腾讯云镜像）+ JDK **17** |
| 映射 | official（mojmap） |
| modId / 包名 | `goety_revenant` / `com.habaiwa.goetyrevenant` |
| Goety | `maven.modrinth:goety:2.5.58.4` |
| GeckoLib | `software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4`（Cloudsmith） |
| Curios / Patchouli | `curios-forge:5.14.1+1.20.1` / `Patchouli:1.20.1-84-FORGE` |
| JEI | `jei-1.20.1-forge:15.62.0.216`（compileOnly API） |

---

## 3. 模块清单（文件 → 职责）

### 3.1 入口与注册
| 文件 | 职责 |
|---|---|
| `GoetyRevenant` | 主类：mod bus 注册、game bus 挂 4 个事件类、网络与联动初始化 |
| `init/ModItems` | 物品注册（法杖/符/聚晶×3/长袍×4/护符/刷怪蛋/转化台 BlockItem） |
| `init/ModBlocks` | 方块与方块实体注册（转化台） |
| `init/ModEntities` | 实体注册（回魂者、尸体）+ 属性绑定 |
| `init/ModEffects` / `ModCreativeTabs` / `ModArmorMaterials` / `ModTags` | 效果 / 创造栏 / 护甲材质 / 实体标签 |

### 3.2 回魂机制（`common/soul/`）
| 文件 | 职责 |
|---|---|
| `SoulConstants` | 全部常数（见 §6） |
| `SoulData` | 灵魂状态读写（玩家 persistent data） |
| `Attunement` | 契合度表 + 外来身体注册 |
| `Possession` | 附身 / 弹出 / 强制召回方舟 / 能量耗尽真死 |
| `Guidance` | 八方向 + 距离 + 跨维度指引 |

### 3.3 嵌入系统（`common/body/`）
| 文件 | 职责 |
|---|---|
| `OrganType` | 五脏+灵窍、五行映射、派系反查 |
| `BodyModData` | 解锁 / 每槽开启 / 每槽封锁 / 嵌入内容 |
| `FocusPassive(s)` | 行动/生存/战斗力三类被动 + 内置表 |
| `Embedding` | 代价流程（抽灵魂力→护盾扛→扣血到1）+ 被动重算 |
| `EmbeddedCasting` | 施法管线（与法杖并行、按器官冷却） |
| `BodyLayout` | 中医人体图上的器官坐标（比例值） |

### 3.4 内容
| 文件 | 职责 |
|---|---|
| `entity/CorpseEntity` | 尸体（41 格原版背包布局、皮肤、装备、发光钩子） |
| `entity/RevenantEntity` | 回魂者（GeckoLib + IOwned） |
| `item/RevenantStaffItem` | 回魂法杖（`IWand`） |
| `item/RevenantRobeItem` | 回魂长袍四件套（`ISoulDiscount`） |
| `item/SoulRecallFocusItem` + `spell/SoulRecallSpell` | 回魂聚晶（召回队友尸体，10000 灵魂能量） |
| `item/CorpseStoreFocusItem` + `spell/CorpseStoreSpell` | 纳尸聚晶（收纳/放出尸体） |
| `item/SoulHarvestCharmItem` | 聚魂护符（击杀 +5% 灵魂能量） |
| `block/SpiritConverterBlock(+BlockEntity)` | 精魄转化台 |
| `item/OpeningBrewItem`（6 个） | 开窍魔药 —— 每一个窍一瓶，喝下才开那个窍（继承 Goety `BrewItem`） |
| `body/FocusSlots` | 聚晶的槽位编码与搬运（**只在嵌入成功时才拿走**） |

### 3.5 事件（`common/event/RevenantEvents`）
死亡 / 每 tick / 禁指令 / 右键拦截 / 指令注册 / 登录补发 —— 全在这一个类。

---

## 4. 回魂机制

### 4.1 主循环
| 阶段 | 行为 |
|---|---|
| 死亡 | 取消真死 → 生成尸体（皮肤+装备+背包）→ 玩家进**原版旁观者** |
| 灵魂状态 | 穿墙 / 别人看不见 / 不可交互 / **禁止指令** / 行动栏倒计时 + 寻尸指引 |
| 寻尸 | 八方向 + 距离；**自己那具尸体只在自己视野里发光** |
| 附身 | **潜行贴身**目标即可 |
| 超时 20 分钟 | 强制召回**灵魂方舟**（绑定了）或**出生点**（没绑定），**尸体一并召回** |

### 4.2 可附身目标
| 目标 | 结果 | 契合度 |
|---|---|---|
| 自己的身体 | 复活 + 物品全数归还 | 100%（见 §4.5 衰减） |
| 其他玩家的尸体 | 以对方身体醒来，**物品+皮肤全继承** | 95% |
| 存活村民 | 以村民身体醒来 | 85% |
| 存活亡灵 | 继承亡灵特性 | 逐个体不同 |
| 标签 `goety_revenant:possessable` 里的任何生物 | 同上 | 按类型 |

### 4.3 契合度表（亡灵逐个体不同）
```
僵尸/尸壳 55%   溺尸 50%   僵尸村民 60%   骷髅/流浪者 45%
凋灵骷髅 35%    僵尸猪灵 40%  僵尸疣猪兽 30%  幻翼 25%   凋灵 10%
村民 85%        其他友好 70%  其他亡灵 40%
启示录：凋灵仆从 35% 幻翼仆从 25% 阿波利昂 15%
觉醒：尸妖 50% 僵尸系 55% 骷髅系 45% 巨人 45% 苍白魔像 40% 凋灵仆从 35% 黑曜石/地鸣巨碑 30%
```
**附身时长 = 20 分钟 × 契合度**（下限 30 秒）。

### 4.4 亡灵身体特性
- 白天没头盔**燃烧**
- 灵魂能量恢复速度**压到玩家的 25%**（做法：每 tick 对比本体灵魂能量，涨幅砍掉 75%）

### 4.5 自身契合度（只有自己的身体吃这套）
| 事件 | 影响 |
|---|---|
| 初始 | 100% |
| 每次死亡 | −10% |
| 每次被强制召回 | −10% |
| 下限 / 恢复 | 10% / **每 1 分钟 +1%** |
| < 100% 时回到自己身体 | 只能待 20 分钟 × 契合度；到点被挤出，**重新留下一具尸体** |

### 4.6 触发优先级（重要）
**不死图腾 / 灵魂图腾 / 灵魂方舟的死亡回归都优先于回魂**。
图腾在 `die()` 之前消耗，`LivingDeathEvent` 根本不触发；方舟回归会取消事件 ——
我们用 `EventPriority.LOWEST` + 检查 `isCanceled()` 让路。**只有这些都没救下来才回魂。**

### 4.7 附身的限制
- 附身在**别人身体**里 → **用不了聚晶/法杖**（右键被拦）
- **灵魂状态禁止指令**

---

## 5. 嵌入系统

### 5.1 槽位（五脏 + 特殊灵窍）
| 器官 | 五行 | 接受的派系 |
|---|---|---|
| 心 HEART | 火 | NETHER、STORM |
| 肝 LIVER | 木 | WILD |
| 脾 SPLEEN | 土 | GEOMANCY |
| 肺 LUNG | 金 | ILL、WIND |
| 肾 KIDNEY | 水 | FROST、ABYSS、VOID |
| **灵窍 SPIRIT_APERTURE**（特殊） | 亡灵 | NECROMANCY |

**派系 → 器官是一对一**（`OrganType.forSpellType`），所以**点聚晶就知道该进哪个器官**，不用先选目标。

### 5.2 一个状态：开窍
| 状态 | 含义 |
|---|---|
| **未开窍 locked** | 这个窍还没开（界面灰环 + 直接告诉你要喝哪一瓶魔药，服务端拒绝嵌入）|
| **已开窍** | 喝过魔药并把元素超载压下去了 → 可以嵌聚晶 |

**窍默认全部锁着。** 跳哭泣之井只发「改造身体」的资格，**不代替魔药**。

> 改造前这里是两个状态（封锁 / 开启），代价是「扣血 + 极速抽灵魂力」。现在整套换成了
> **元素超载**（见 §5.3），所以两个状态合并成一个 —— 开窍就是开窍。

### 5.3 开窍 = 元素超载 → 去克制它（**取代了原来的扣血 + 极速消耗**）

原来的「撑住扣血和抽灵魂力」**整个删掉了**。现在开窍是一条**要出门跑一趟**的流程：

```
喝下「开窍魔药 · 火」
   ↓  立刻获得 debuff  元素超载 · 火（5 分钟）
   ↓  超载期间每秒吃负面（火 = 一直着火）
   ↓  去克它的地方：水克火 → 跳进水里
   ↓  连续待够 3 秒（60 tick）          ← 中途走开就清零重来
开窍！效果消失，那个窍打开
```

- 超载**没压住就自然到期** → 效果退掉，**窍不开**（白喝一瓶，再酿一瓶就是了）
- 「连续」是按 tick 算的：离开水 / 放下斧头就**清零**，并提示一次「克制被打断了」

#### 五行相克表（每个元素去哪儿）
| 超载 | 谁来克 | 玩家要做什么 | 判定 |
|---|---|---|---|
| **火** | 水克火 | **跳进水里** | `isInWater()` / `isUnderWater()` |
| **水** | 土克水 | **埋进土里** | 踩土或沙 + 头顶见不到天 |
| **土** | 木克土 | **走进树林** | 半径 4 内有 `#logs` 或 `#leaves` |
| **木** | 金克木 | **手持斧头** | 主手/副手在 `#minecraft:axes` 里 |
| **金** | 火克金 | **让火烧起来** | `isOnFire()` / `isInLava()` |
| **灵** | 不在五行 —— 回井里 | **跳进哭泣之井** | 复用 §4 的井判定 |

超载期间的负面（每秒续一次，40 tick）：火=着火，水=缓慢，土=挖掘疲劳，
木=中毒，金=虚弱，灵=反胃+失明。

#### 嵌入本身：分两条路（**扣血 + 极速抽灵魂力保留**）

开窍只是「资格」。真正把聚晶刻进去还要看这个窍**有没有行过仪式**：

| 情况 | 代价 |
|---|---|
| **首次嵌入某个窍** | **3 秒仪式**：每秒抽 **120** 灵魂力 → 灵魂护盾先扛 → 破盾后扣血，**硬性下限 1 点** → 撑满 60 tick 才算刻好 |
| **仪式走过之后换聚晶** | 只花 **2000** 灵魂能量，瞬间完成 |

仪式撑不住（灵魂力先见底）→ 中断，这一趟白费。

#### ★ 聚晶只会在**成功那一刻**被拿走
整个流程（不管哪条路）都**只记「哪一格」**（{@link FocusSlots} 槽位编码），
**从不提前从背包里拿东西**。只有真正装上时才 `take`，而且动手前还要再确认
那一格里确实还是同一枚聚晶。所以：

- 仪式中断 → **聚晶还在**
- 玩家中途把聚晶挪走 → **不装也不扣灵魂能量**
- 服务端崩了 → 东西从头到尾都在背包里，丢不了

（更早的版本是「一按就扣」，仪式中断聚晶就没了 —— 那个 bug 已经不存在了。）

### 5.6 开窍魔药（每个窍一瓶，**按五行元素命名**）
| 物品 | 元素 | 对应窍 | 瓶身颜色 |
|---|---|---|---|
| `goety_revenant:fire_brew` 开窍魔药 · 火 | fire | HEART 心 | 火红 `0xE8503A` |
| `goety_revenant:wood_brew` 开窍魔药 · 木 | wood | LIVER 肝 | 木绿 `0x4BC46B` |
| `goety_revenant:earth_brew` 开窍魔药 · 土 | earth | SPLEEN 脾 | 土黄 `0xC4913A` |
| `goety_revenant:metal_brew` 开窍魔药 · 金 | metal | LUNG 肺 | 金白 `0xE8E0B0` |
| `goety_revenant:water_brew` 开窍魔药 · 水 | water | KIDNEY 肾 | 水蓝 `0x3D6BE8` |
| `goety_revenant:spirit_brew` 开窍魔药 · 灵 | spirit | SPIRIT_APERTURE 灵窍 | 灵紫 `0x8B4BD4` |

- 注册名统一是 **`元素id + "_brew"`**（`OrganType.brewId()` 推出来，元素 id 存在枚举的
  `element` 字段里），这样界面想拼「要喝哪一瓶」永远不会拼错。
- 元素显示名走 `element.goety_revenant.<element>`（火/木/土/金/水/灵）。
- **制作走诡厄巫法自己的制药流程**（巫婆大锅），**配方不在代码里写** ——
  本次只做物品本身。
- 喝下去 → **点燃对应元素的超载**（`ElementOverload.start`）→ 去克制地待 3 秒 → 开窍。
  不是喝下去就开（见 §5.3）。

**实现上的坑**：`BrewItem(Item.Properties)` 构造器**在它自己包外不可见**，
而 `Item#getMaxStackSize` 又是 `final` —— 所以物品属性一个都改不了，
只能 `super()`。好在 `BrewItem()` 自己已经设成「一瓶一份」，正好够用；
「喝完还玻璃瓶」由 `finishUsingItem` 自己补。

### 5.4 嵌入被动：**等级分化 + 元素扭曲**（9 个方向，每个方向一条原版属性）
| 方向 | 属性 | 默认值 |
|---|---|---|
| MOBILITY 行动 | 移动速度 | +5% |
| SURVIVAL 生存 | 最大生命 | +4 |
| COMBAT 战斗力 | 攻击伤害 | +2 |
| ARMOR 护甲 | 护甲 | +2 |
| TOUGHNESS 韧性 | 护甲韧性 | +2 |
| KNOCKBACK 击退 | 攻击击退 | +0.5 |
| STEADFAST 坚毅 | 击退抗性 | +10% |
| SWIFT 迅捷 | 攻击速度 | +0.1 |
| FORTUNE 幸运 | 幸运 | +1 |

按**槽位生成固定 UUID**，换聚晶先移除旧的，所以一个窍永远只吃一枚聚晶的被动。

#### ★★ 一枚聚晶给**两条**被动
```
主被动 = 元素扭曲(本职方向) × 等级倍率
次被动 = 本职方向           × 等级倍率 × 0.5
```

**元素扭曲表**（`FocusPassive.TWIST`，行 = 窍的元素，列 = 聚晶本职方向）——
聚晶进哪个窍，就被那个窍的性子扭一下：

| 窍 | 性子 | 效果 |
|---|---|---|
| 心 · 火 | 攻伐 | 什么都往凶里扭（行动→战斗力、护甲→击退……）|
| 肝 · 木 | 生长 | 往长久、往运里扭 |
| 脾 · 土 | 厚重 | 往硬里扭（行动→韧性、生存→护甲……）|
| 肺 · 金 | 锐利 | 往快、往准里扭 |
| 肾 · 水 | 绵长 | 往韧里扭 |
| **灵窍 · 灵** | **反转** | **把方向翻到对立面** |

灵窍那行是真正的「扭曲」——**召唤 → 吞噬**就是这么来的：
亡灵派系的本职是 `幸运`，进灵窍被反转成 `战斗力`（掠夺/吞噬的味道）。
同理：守护→掠夺、迅捷→坚毅、行动→坚毅。

#### ★★ 越难做的聚晶越强（等级倍率）
倍率 = `1 + 0.35 × (等级 - 1)` → 普通 ×1.00、罕见 ×1.35、稀有 ×1.70、史诗 ×2.05。

等级取三者**最大**：

| 来源 | 说明 |
|---|---|
| **稀有度**（物品名字颜色） | 普通 1 / 罕见 2 / 稀有 3 / 史诗 4 —— 每个物品都有，模组作者用它表达强度 |
| **注册名关键词** | `greater_` `supreme_` `ancient_` `astral_` `master_` `black_hole_` … 拉到 4 级；`lesser_` `crude_` `minor_` … 压到 1 级（附属很爱用这类前缀） |
| **点名表** | `FocusPassive.registerTier(物品id, 1~5)` 直接钉死，稀有度看不准的时候用 |

所以**做出来越难的聚晶，嵌进去越强**，不会再出现「辛苦做的还不如路边捡的」。

#### ★ 四级查找 —— 保证「每一个聚晶都有被动」
整合包里光聚晶就有 **300 多枚**（本体 + goety_aether / goety_astrology /
goety_cataclysm / goeticlegacy / goety_ladder / goetyawaken ……），一个个手写不现实。
所以 {@code FocusPassive#resolve} 分四级：

| 级 | 依据 | 覆盖 |
|---|---|---|
| 1 | `FocusPassives` 点名表 | 我们自己的 2 枚 + 本体核心的十几枚（数值更贴定位） |
| 2 | **派系表**（本体九大 `SpellType` 各一条） | 本体 + 所有复用本体派系的附属 |
| 3 | **派系名关键词启发** | 附属自己 `SpellType.create(...)` 出来的新派系（fire→战斗、frost→护甲、astral→幸运……） |
| 4 | 兜底 `战斗力 +2` | 连派系都拿不到的（不是聚晶 / spell 为 null） |

**「归属哪个窍」同理**：`OrganType.forSpellType` 现在**永远不返回 null** ——
精确匹配 → 按派系名猜五行 → 兜底进**灵窍**（它本来就是「专门收不适合五脏的东西」的特殊槽）。
客户端界面和服务端校验走的是同一个函数，不会打架。

#### ★ 所有聚晶的 tooltip（不需要给任何人加 Mixin）
走 Forge 的 `ItemTooltipEvent`，只要是 `IFocus`，鼠标放上去就多三行：

```
嵌入被动：行动 +5%
归属：心（火）
需要先喝「开窍魔药 · 火」开窍
```

这是**唯一**一处能对「别人模组的物品」零侵入加说明的办法 —— 不用 Mixin、不用改物品类、
新增附属也自动生效。

### 5.5 界面
| 界面 | 打开方式 | 内容 |
|---|---|---|
| **身体界面** | 按键 **B** 或指令 | 程序绘制的人体 + **中医方位**的六个器官热点（已嵌入的聚晶就画在窍里）+ **右侧背包/聚晶包里所有可用聚晶**（点一下即嵌入）+ **灵魂能量**与**本次消耗**（不够就变红）+ 未开窍时直接写「要喝哪一瓶魔药」 |
| **聚晶轮盘** | 按键 **V** | 六个器官围一圈，指向高亮、点击释放，**与法杖并行不冲突** |
| **转化台界面** | 空手右键转化台 | **零原版控件 + 勾选式多选**：九颗精魄格子点一下选中、再点一下取消，**最多 9 种一起产**；右键格子清空、面板内滚轮 = 全选/全不选；笼内灵魂能量条 + 下一轮进度条 + **底部唯一一处说明为什么不动** |

---

## 6. 数值常数总表

| 常数 | 值 | 位置 |
|---|---|---|
| 灵魂状态时长 | 24000 tick（20 分钟） | `SoulConstants.SOUL_DURATION_TICKS` |
| 灵魂状态自带能量 | 10000（**独立，不写进本体**） | `SOUL_START_ENERGY` |
| 灵魂能量消耗 | 每 12 tick 扣 **5**（常驻）/ 加速飞行扣 **15** | `DRAIN_BASE` / `DRAIN_FAST` |
| 附身判定半径 | 4 格 | `POSSESS_RANGE` |
| 自身契合度 | 初始 1.0 / 下限 0.10 / 罚 0.10 / 每分钟 +0.01 | §4.5 |
| 回魂聚晶消耗 | 10000 灵魂能量 | `RECALL_FOCUS_COST` |
| 纳尸聚晶消耗 / 半径 | 100 / 8 格 | `STORE_FOCUS_COST` / `STORE_RANGE` |
| 亡灵恢复倍率 | 25% | `UNDEAD_REGEN_FACTOR` |
| 嵌入时长 / 抽能 / 血下限 | 60 tick / 120 每 tick / 1 点 | `Embedding` |
| 重复嵌入消耗 | 2000 | `Embedding.REEMBED_SE_COST` |
| 击杀灵魂能量 | 3000 / 1200 / 500 / 180（按血量 ≥60/≥30/≥12/其余） | `MalumCompat.soulsFor` |
| 本模组常态加成 | **×1.20** | `SOUL_GAIN_BONUS` |
| 聚魂护符加成 | **×1.05** | `CHARM_BONUS` |
| 护符状态缓存 | 40 tick | `CACHE_TICKS` |
| 转化台：5 能量→1 精魄 / 2 秒一次 / 最多 4 种 | 5 / 40 tick / 4 | `SpiritConverterBlockEntity` |
| 强制召回恢复灵魂能量 | +1000（仅绑定方舟时） | `Possession.RECALL_SOUL_RESTORE` |

---

## 7. 联动

### 7.1 Goety 本体（全部从编译产物核对）
| 用途 | 接口 | 依据 |
|---|---|---|
| 法杖 | `api.items.magic.IWand` | 唯一抽象方法 `getSpellType()` |
| 长袍 | `api.items.armor.ISoulDiscount` | 全 default |
| 回魂者 | `api.entities.IOwned` | 5 个抽象方法 |
| 聚晶 | `api.items.magic.IFocus` → `ISpell` | `defaultSoulCost`/`defaultCastDuration`/`defaultSpellCooldown`/`getSpellType`/`acceptedEnchantments` |
| 灵魂能量 | `utils.SEHelper` | `getSESouls`/`increaseSESouls`/`decreaseSESouls`/`getCapability` |
| 灵魂方舟 | `SEHelper.getArcaBlock` / `getArcaDimension` | 本体方块 `block.goety.arca`「灵魂方舟」 |
| **转化台供能** | `CursedCageBlockEntity.getSouls()` / `decreaseSouls(int)` / `markUpdated()` | **公开方法，无需 Mixin** |
| 镰刀 | `DarkScytheItem` / `DarkMetalScythe`（`goety:dark_metal_scythe`） | 有 `canApplyAtEnchantingTable` 准入钩子 |
| 镰刀伤害类型 | 未确认 | 见 §9 未验证项 |

**发现**：`SpellType implements IExtensibleEnum` 且有 `create(String,String)` —— 后续可做**回魂专属法术派系**。

### 7.2 通用扩展接口：`goety_revenant:possessable` 实体标签
任何模组/数据包把自己的生物写进这个标签即可被附身 —— **不需要它适配我们**。
判定顺序：**标签 → 自己的尸体 → 亡灵(MobType) → 村民**。

我们的标签文件里预置了启示录与觉醒的亡灵单位，全部 `"required": false`。

### 7.3 启示录 / 觉醒（软依赖 + 注册名）
| 附属 | modId | 已登记契合度 |
|---|---|---|
| 诡厄巫法：启示录 | `goety_revelation` | 凋灵仆从 35%、幻翼仆从 25%、阿波利昂 15% |
| 诡厄巫法：觉醒 | `goetyawaken` | 见 §4.3 |

全部按**注册名**登记（不引 jar），缺席时零影响。

### 7.4 Carry On 2.1.2.7（**已核对源码，开箱即用**）
逐条对照 `PickupHandler.tryPickupEntity`：`invulnerableTime` 恒 0 ✓、黑名单默认空 ✓、
尸体是 `MobCategory.MISC`（躲开 hostile 检查）✓、尺寸 0.6×1.8 在 1.5/2.5 限制内 ✓、
NBT 往返完整 ✓。**玩家双手必须空着**、距离 ≤ 2.5。
⚠️ 不要把 `entity_whitelist` 标签塞进我们的 jar —— 一旦整合包开了白名单模式会限制住 Carry On。

### 7.5 Malum（灵灾）**modId `malum`**
| 功能 | 实现 |
|---|---|
| **怪物不再产出精魄** | `LivingDropsEvent` 无条件清掉 `malum:*_spirit` |
| **聚魂护符** | 击杀产灵魂能量（**不需要佩戴**，佩戴 +5%）；Curios 查询做 40 tick 缓存防卡 |
| **灵魂护盾 ↔ 灵魂能量** | 反射穿透 `MalumPlayerDataCapability.getCapability(player).soulWardHandler.soulWard`：护盾掉多少就扣多少灵魂能量，再把护盾值写成灵魂能量 → HUD 只显示诡厄巫法灵魂能量 |
| **精魄转化台** | 骑在诅咒之笼上，`cage.getSouls()`/`decreaseSouls(5)` 换精魄，产物自动进周围容器 |
| **哭泣之井解锁** | 站在 `malum:weeping_well_core` / `void_conduit` / `primordial_soup` ±2 格内即解锁改造身体 |
| **镰刀附魔兼容** | 合并物品标签 `malum:scythe` 加入 `goety:dark_metal_scythe` |

Malum 精魄 9 种：earthen/wicked/arcane/umbral/aqueous/aerial/infernal/sacred/eldritch。

---

### 7.6 镰刀统一适配（大工程，未开工）

**目标**：1.20.1 的**所有镰刀**共享两套特性，且两套附魔互通。这需要把特性从物品类里"抽出来"用事件层重实现 ——
Goety 的写在 `DarkScytheItem` 里、Malum 的写在它们自己的物品 + `malum:is_scythe` 伤害类型标签里，**都不在标签上**。

#### 7.6.1 Goety 侧镰刀特性（用户提供）
| 特性 | 说明 |
|---|---|
| 命中给灵魂能量 | 数量取决于 `goety.toml` 的 **`darkScytheSouls`**，以及是否带**噬魂**附魔 |
| 横扫攻击 | 像剑一样横扫，但**冷却更长**；冷却 ~50% 时范围较小，≥90% 时**范围大于剑** |
| 横扫之刃 | I/II/III = **+50% / +67% / +75%** 横扫伤害 |
| 横扫也给灵魂能量 | 同命中结算 |
| 副手空 / 戴**坟墓手套** | 攻击冷却更短，**二者可叠加** |
| 当锄头用 | 可挖锄头类方块，破坏作物/灌木/茎块，且**连锁破坏 2×1×2 范围内**同类可采方块 |
| 附魔 | 可获得**剑的全部附魔 + 时运** |
| 不祥镰刀 | 无精准采集也能采**幽匿块**；且**抢夺/时运与精准采集冲突** |

#### 7.6.2 Malum 侧镰刀特性（用户提供，引自"击碎灵魂"）
| 特性 | 说明 |
|---|---|
| 可投掷 | 附魔后**镰刀可掷出**（回旋 = 像回旋镖） |
| 伤害加成 | 攻击伤害受**镰刀熟练度**加成（**不含魔法伤害**）；魔法伤害受**魔法熟练度**加成 |
| 可附魔 | **横扫之刃**、**缚灵/舞刃**、**飞腾/回旋**、**精魂劫掠** |
| 横扫范围 | 以被击生物**碰撞箱**为基础，每方向扩大 **`1 + 横扫之刃等级 × 0.25` 格** |
| 横扫伤害 | 近战伤害为 **`0.5 + 横扫之刃伤害比率` 倍**，**魔法伤害不变** |
| 项链影响 | 戴**精准镰刃项链**或**隐藏锋刃项链** → 增强**回旋/飞腾**，并**禁止镰刀横扫** |
| **灵魂暴露** | 被本模组武器/伤害类型击中 → 灵魂**暴露 10 秒**（重复触发刷新）；这期间死亡则**灵魂击碎**，触发**精魂收割**与**隐秘收割** |

#### 7.6.3 实施分层（按风险从低到高）
| 层 | 内容 | 手段 | 风险 |
|---|---|---|---|
| **L1 灵魂能量共享** | `LivingHurtEvent`：攻击者手持`#malum:scythe`武器（非 Goety 镰刀）→ 按 `darkScytheSouls` × 噬魂给灵魂能量 | 纯事件 | **低** |
| **L2 连锁破坏** | `BlockEvent.BreakEvent`：镰刀破坏锄头类方块时，连带 2×1×2 | 纯事件 | **中** |
| **L3 统一镰刀标签** | 自建 `goety_revenant:scythes` 标签，任何镰刀写进来即获得上述行为 | 数据包 | **低** |
| **L4 横扫重实现** | 复刻两套横扫规则（范围/伤害/冷却联动），且**不能和原版剑的横扫打架** | 攻击事件 + 自算范围 | **高** |
| **L5 攻击冷却修改** | 副手空/坟墓手套 → 缩短冷却，可叠加 | 需注入冷却计算 | **中高** |
| **L6 附魔准入与冲突** | 剑系附魔+时运可上镰刀；不祥镰刀的精准采集/抢夺/时运冲突 | `AnvilUpdateEvent` + 附魔台 | **中高** |
| **L7 灵魂暴露 / 击碎** | 被本模组武器击中 → 暴露 10 秒 → 期间死亡触发收割 | 需与 Malum 的 `SpiritHarvestHandler` 协作 | **高** |
| **L8 熟练度加成** | 镰刀/魔法熟练度影响伤害 | 依赖 Malum 的熟练度系统（要另扒） | **高** |

#### 7.6.4 共享点（用户确认）

> **灵灾的「收割」触发就是我们的 +20% 灵魂能量获取。**

灵灾主要靠**镰刀收割**来产出精魄与附魔效果，所以**收割事件 = 灵魂能量获取**，
两边共用同一套结算（含本模组的 **×1.20 常态加成**，以及聚魂护符的 ×1.05）。

也就是说 **L7（灵魂暴露/击碎/收割）不是要另做一套奖励**，而是：

1. 生物被本模组镰刀击中 → 灵魂**暴露 10 秒**（重复刷新）
2. 暴露期间死亡 → **灵魂击碎** → Malum 的 `SpiritHarvestHandler` 触发收割
3. **收割触发时** → 走我们的灵魂能量结算（×1.20）
4. 精魄产出仍由 Malum 自己的体系负责（我们的拦截只管**怪物普通掉落**那条线）

#### 7.6.5 熟练度 = Malum 的**属性**（已查实）

Malum 一共 8 个属性，其中就有镰刀熟练度：

```
attribute.name.malum.scythe_proficiency = 镰刀熟练度
  描述原文：Damage multiplier for Scythes   ← 是「伤害乘数」
```

全部 8 个：`scythe_proficiency`（镰刀熟练度）、`arcane_resonance`、`malignant_conversion`、
`reserve_staff_charges`、`soul_ward_capacity`、`soul_ward_integrity`、`soul_ward_recovery_rate`、`spirit_spoils`。

**所以 L8 从「要对接系统」降级为「读一个属性」** —— 运行时按注册名查即可，没装 Malum 返回 null 安全跳过。

⚠️ **"魔法熟练度"不在属性列表里** —— 但语言文件多处提到它（暴食效果、屠戮符文 +2/5、恶念白镴吸取一半），
说明它存在但不是 attribute，位置待查（可能在灵魂/奥术数据或 Lodestone 的属性系统里）。

**待确认的语义**：`scythe_proficiency` 的基础值是 **1.0（倍率）** 还是 **0（加成量）**？
- 描述写的是 "Damage **multiplier**" → **强烈倾向 1.0**
- 猜错后果严重（写成加法会一刀秒杀、写成乘法会零伤害），所以不猜
- **一条指令就能确认**：`/attribute @s scythe_proficiency get`（不穿装备时看基础值）

#### 7.6.6 L4 横扫：两边的实现方式（已严查）

**核心事实：两边都不继承 `SwordItem`，所以两边都拿不到原版横扫，各自实现了自己的横扫。**

| | Goety 镰刀 | Malum 镰刀 |
|---|---|---|
| 基类 | `DarkScytheItem extends TieredItem implements Vanishable` | `MalumScytheItem extends ModCombatItem implements IMalumEventResponderItem` |
| 命中入口 | `hurtEnemy(ItemStack, LivingEntity, LivingEntity)`、`attackMobs(...)` | **`hurtEvent(LivingHurtEvent, LivingEntity, LivingEntity, ItemStack)`** |
| 附魔准入 | `canApplyAtEnchantingTable` | `canApplyAtEnchantingTable` |
| 横扫开关 | 无公开接口 | **`public static boolean canSweep(LivingEntity)`** ← 项链禁横扫就在这里生效 |
| 其他 | — | `getScytheSound(boolean)`（对应 `malum.subtitle.scythe_sweeps`） |

**关键发现：Malum 有公开的事件响应接口 `IMalumEventResponderItem`** —— 物品实现它就能收到
`hurtEvent`。这意味着**我们自己的物品可以接进 Malum 的事件流**（但改不了 Goety 的物品去实现它）。

**没有任何一个模组用专门的横扫类** —— 逻辑全在物品类内部。Malum 的横扫用的是自己的伤害类型
`malum:scythe_sweep`（`malum:is_scythe` 标签之一）。

**原版/Forge 的横扫机制（扫 class 常量池实读，非记忆）**：

| 位置 | 名字 | 说明 |
|---|---|---|
| `Player` | **`sweepAttack`** | 原版横扫的**执行方法**（mojmap 名，不是 `doSweepAttack`） |
| `Player` | **`getSweepHitBox`** | 横扫判定用的碰撞箱 |
| `EnchantmentHelper` | `getSweepingDamageRatio` | 横扫之刃的伤害比率 |
| `Enchantments` | `SWEEPING_EDGE` | 横扫之刃附魔 |
| **`IForgeItem`** | **`getSweepHitBox`** | ★ **Forge 的横扫扩展钩子**（不是 `canSweepAttack`，我原先记错了） |
| `Item`/`SwordItem` | 无 | 横扫**不在**物品类里，在 `Player` |

**两边模组怎么用的**（同样扫常量池实读）：

| 类 | 出现的横扫符号 | 含义 |
|---|---|---|
| Goety `DarkScytheItem` | **`sweepAttack`**、`getSweepingDamageRatio` | **直接调用原版 `Player#sweepAttack`**，用自己的条件触发 |
| Malum `MalumScytheItem` | `canSweep`、`SCYTHE_SWEEP`、`sweeping`、`SWEEPING_EDGE`、`getSweepingDamageRatio` | 自建逻辑 + **自己的伤害类型 `malum:scythe_sweep`** |
| Malum `MagicScytheItem` | **（无）** | 魔法镰刀不横扫 |

**这意味着两件好事**：
1. 两把镰刀**都复用原版的横扫执行方法**，所以我们的第三方镰刀横扫**也可以直接调 `player.sweepAttack(target)`**，
   不必从头复刻一套判定 —— 风险大幅下降。
2. 原版横扫**只对 `SwordItem` 生效**（Forge `getSweepHitBox` 默认对非剑返回 null），
   两把镰刀都不是 `SwordItem` → **我们动手不会波及原版剑**。

**结论（L4 的架构）**：
1. **原版横扫**只对 `SwordItem` 生效（Forge 的 `IForgeItem#canSweepAttack`），两把镰刀都不吃它 → **不会打架**
2. 我们给**标签里的第三方镰刀**实现自己的横扫（复刻两套规则）
3. **对 Goety / Malum 原生镰刀必须跳过** —— 它们内部已经各自横扫过了，我们再扫就是双倍
4. 判定"原生"用**命名空间**（`malum:` / `goety:`），不要用标签（我们把 Goety 镰刀合并进了 `malum:scythe`）

#### 7.6.7 L5 攻击冷却 / L6 附魔准入（同样扫常量池实读）

**L6 —— 附魔准入与冲突的钩子全在 Forge 侧：**

| 位置 | 成员 | 用途 |
|---|---|---|
| **`IForgeItem`** | **`canApplyAtEnchantingTable`** | ★ **物品侧准入钩子**（原版 `Item` 里**没有**这个方法，是 Forge 加的） |
| **`Enchantment`** | **`canApplyAtEnchantingTable`** | ★ **附魔侧准入钩子** |
| **`Enchantment`** | **`checkCompatibility`** | ★ **冲突规则**（抢夺/时运 vs 精准采集 就走这里） |
| `Enchantment` | `canEnchant`、`isAllowedOnBooks` | 其他准入面 |
| **`IForgeItem`** | **`getAttributeModifiers`** | ★ 对应「每 1 点熟练度 +0.5 面板攻击力」那类面板改动 |
| **Forge 事件** | **`AnvilUpdateEvent`**、**`EnchantmentLevelSetEvent`** | ★ 铁砧合成 / 附魔台候选 —— **改第三方物品附魔的唯一入口** |

**关键结论**：两个准入钩子**都在别人的类上**（物品是别人的、附魔也是别人的），
所以对第三方镰刀**只能从 `AnvilUpdateEvent` + `EnchantmentLevelSetEvent` 下手**。
好消息是**这两个事件都存在**，不用 Mixin。

**L5 —— 攻击冷却，Goety 的实现线索：**

| 线索 | 含义 |
|---|---|
| `DarkScytheItem` 常量池含 **`attackSpeed`**、**`getAttackStrengthScale`**、**`ScytheAttackSpeed`** | Goety 是**改攻速属性** + **读攻击冷却进度**（横扫范围随冷却变化就是这个） |
| `com/Polarice3/Goety/common/items/curios/GloveItem` | ★ **坟墓手套是个 Curios 物品**（还有 `GloveModel` 客户端模型） |

**L5 的实现路径**：Goety 在物品的 `getAttributeModifiers` 里改攻速；我们改不了别人的物品，
所以改用**事件给玩家挂临时属性修饰符**（副手空 / 佩带手套时加攻速），两者可叠加。

#### 7.6.8 附魔台路线（已查实：**走不通，必须 Mixin**）

**问：原版附魔台有没有"附魔战利品表"这样的东西？**
**答：没有。** 扫 `EnchantmentMenu` 常量池，只有 `getEnchantmentList`、`getEnchantmentCost`、
`isEnchantable`、`addEnchantment`、`enchantmentSeed` —— **完全没有 `LootTable` / `RandomizableContainer`**。
候选项是**代码里算的**，不是数据驱动的。

**原版到底怎么决定"哪些附魔能附出来"（常量池实读，逐环有据）**：

`EnchantmentCategory` 是个枚举，**每个成员持有一个 `Predicate<Item>`**（实现放在 `EnchantmentCategory$N` 内部类里）。
把每个内部类引用的物品类扫出来：

| 内部类 | 引用的物品类 | 对应类别 |
|---|---|---|
| $1–$5 | `ArmorItem` | ARMOR / ARMOR_FEET / LEGS / CHEST / HEAD |
| **$6** | **`SwordItem`** | ★ **WEAPON** ← 剑系附魔卡在这里 |
| $7 | `DiggerItem` | DIGGER |
| $8 | `FishingRodItem` | FISHING_ROD |
| $9 | `TridentItem` | TRIDENT |
| $10/$12/$14 | （无物品类） | BREAKABLE / WEARABLE / VANISHABLE |
| $11 / $13 | `BowItem` / `CrossbowItem` | BOW / CROSSBOW |

**所以"能不能附出来"的闸门是：`物品 instanceof <某个具体物品类>`。**
`WEAPON` 的谓词就是 `item instanceof SwordItem` —— **镰刀不是 `SwordItem`，永远过不了这一关。**

另外 `EnchantmentHelper` 引用了 `isTreasureOnly` / `isDiscoverable` / `isAllowedOnBooks` / `getMinCost` / `getMaxLevel`，
说明候选生成还要过滤**宝藏附魔、是否可发现、等级成本区间**。

**★ 重要发现：`EnchantmentCategory` 实现了 `IExtensibleEnum` 且有 `create(String, Predicate<Item>)`** ——
模组**可以自己造新类别**。但**改不了原版 WEAPON 的谓词**（那是原版枚举成员）。

**过滤链条**：
```
EnchantmentMenu.getEnchantmentList
  → EnchantmentHelper.getAvailableEnchantmentResults(power, stack, treasure)
      → 对每个附魔调 Enchantment#canEnchant(stack)
          → DamageEnchantment.canEnchant 检查 stack 是不是 SwordItem
              → 镰刀不是 SwordItem ⇒ 永远过不了
```

**Forge 的 `EnchantmentLevelSetEvent` 救不了**（实读签名）：

```java
public EnchantmentLevelSetEvent(Level, BlockPos, int, int, ItemStack, int)  // 只带 ItemStack，不带 Enchantment
public int getOriginalLevel();  public int getEnchantLevel();
public void setEnchantLevel(int);   // ★ 只有 setEnchantLevel —— 没有 setEnchantment
```

**它只能改「这一格的等级」，不能注入一个本来不在候选里的附魔。** 所以剑系附魔永远不会出现在镰刀的候选里，
这个事件也就无从修改。

**结论：「附魔台能附出来」只有两条路**
| 路线 | 代价 |
|---|---|
| **A. 加 Mixin** 注入 `EnchantmentMenu.getEnchantmentList`（或 `EnchantmentHelper.getAvailableEnchantmentResults`），把剑系附魔 + 时运塞进候选 | 要动构建体系（工程目前**没有 Mixin 基建**） |
| **B. 维持铁砧路线**（已实现） | 零代价，但玩家得自己找附魔书 |

#### 7.6.9 ★ Malum 的附魔是怎么"附出来"的（关键发现：**走物品标签，不用 Mixin**）

扫 `EnchantmentRegistry.class` 常量池，直接看到它的做法：

```
create  (String, Predicate) -> EnchantmentCategory      ← Forge 的 IExtensibleEnum.create
scythe_only                                             ← 自定义类别的名字
ItemTagRegistry  +  TagKey  +  (ItemStack)Z             ← 谓词：读**物品标签**
SCYTHE                                                  ← 标签常量
```

**即：Malum 用 `EnchantmentCategory.create("scythe_only", stack -> stack.is(ItemTagRegistry.SCYTHE))`
造了一个自定义附魔类别，谓词判的是「物品在不在 `malum:scythe` 标签里」，
而不是 `instanceof SwordItem`。** 这就是它绕过原版剑类闸门的办法。

**由此推出两件大事**：

1. **我们已经把 `goety:dark_metal_scythe` 合并进了 `malum:scythe` 标签** ——
   所以**诡厄巫法的镰刀早已落在 Malum 的 `scythe_only` 附魔类别里**，
   附魔台理论上就能给它出 Malum 的镰刀附魔（舞刃/飞腾/回旋）。

2. **对我们标签里的任何第三方镰刀同理** —— 只要写进 `malum:scythe` 标签，
   就能吃到 Malum 的镰刀附魔，**这条路线完全不需要 Mixin**。

**剑系附魔（锋利/横扫之刃）则是另一条路**：原版那些附魔绑死在 `WEAPON` 类别（`instanceof SwordItem`），
改不了 —— 所以它们靠的是**物品侧的 `canApplyAtEnchantingTable` 重写**（Goety 与 Malum 都重写了它，
这也是「镰刀能得到剑的全部附魔」的实现方式）。第三方物品我们改不了这个钩子 → 附魔台路线仍需 Mixin。

#### 7.6.10 ★ 双向互通收尾（读 Malum 源码后补标签，**零代码**）

之前 §9.2 把「Malum 镰刀 ↔ Goety 双向互通」记成「要改代码」，**这是错的** ——
读了 Malum 1.20.1 分支的源码后确认链路全在**物品标签**上：

```java
// SoulDataHandler.exposeSoul(LivingHurtEvent)
if (source.getEntity() instanceof LivingEntity attacker) {
    ItemStack stack = getSoulHunterWeapon(source, attacker);   // = attacker.getMainHandItem()
    if (stack.is(ItemTagRegistry.SOUL_HUNTER_WEAPON) || TetraCompat.hasSoulStrikeModifier(stack)) {
        exposeSoul(target);                                    // exposedSoulDuration = 200（tick）
    }
}
public static void exposeSoul(LivingEntity entity) { ... .exposedSoulDuration = 200; }
public static void livingTick(...) { if (soulData.exposedSoulDuration > 0) soulData.exposedSoulDuration--; }
```

而 `data/malum/tags/items/soul_hunter_weapon.json` 的第一行就是 **`#malum:scythe`**。
所以 **只要写进 `malum:scythe`，灵魂暴露就自动生效**，不需要任何注入。

**由此得到三条结论**：

| 结论 | 依据 |
|---|---|
| **灵魂暴露（L7 前半）走物品标签，不伪造伤害类型** | `exposeSoul` 只查 `SOUL_HUNTER_WEAPON` 物品标签；伤害类型只用于 `can_soul_shatter` 那条独立支线 |
| **熟练度倍率（L8）走伤害类型** —— 所以 Goety 镰刀拿不到 Malum 那份 | `MalumAttributeEventHandler` 里 `if (source.is(DamageTypeTagRegistry.IS_SCYTHE))` 才乘 `scythe_proficiency`；Goety 镰刀打的是 `playerAttack` → **不满足** → 正好由我们自己的 L8 补上，**不会双倍** ✅ |
| **`getScytheWeapon` 会过滤 `instanceof MalumScytheItem`** | 所以 Goety 镰刀不会被 Malum 当成自家镰刀去做横扫/结算，**不会和 Goety 自己的横扫打架** ✅ |

并且 `SpiritHarvestHandler.spawnSpiritsOnDeath` 只在 `soulData.exposedSoulDuration > 0` 时才产精魄，
而且**它自己不把该值清零** —— 所以我们在 `LOWEST` 优先级读到的仍然是 >0，`MalumHarvestCompat` 的判定是对的 ✅

##### 本轮修正：Goety 其实有**三把**镰刀，之前只接了两把

| 注册名 | 英文 | 类（都继承 `DarkScytheItem`） |
|---|---|---|
| `goety:dark_scythe` | Ominous Scythe 不祥镰刀 | `DarkScytheItem` |
| `goety:dark_metal_scythe` | Dark Scythe 暗影镰刀 | `DarkMetalScythe` |
| `goety:death_scythe` | Death Scythe 死亡镰刀 | `DeathScytheItem` |

- 之前 `malum:scythe` 里**只有** `dark_metal_scythe` → 另外两把拿不到 Malum 附魔与灵魂暴露
- 之前 `goety_revenant:scythes` 里少了 `death_scythe`
- **现已三把齐全**（两个标签都补齐；`isGoetyScythe` 的 `instanceof DarkScytheItem` 天然覆盖三个子类）

### 7.7 灵魂感知系统（第五轮需求，**未实现**）

#### 7.7.1 需求（用户原话整理）
1. **灵魂状态不是对所有人隐身** —— **鬼类 / 魂类 / 灵类 / 亡灵类 / 一切能使用诡厄巫法力量的生物，都能看见并攻击灵魂**
2. 点名的：幽灵、边境幽灵、秽灵、收割者、缠魂甲胄、怒鬼、恼鬼……「太多了说不过来」
3. **每个生物有各自的察觉能力，程度不同，需要分类**
4. **灵魂状态有血条**（不是无敌的）
5. **附身规则**：可以附身，但**不能当面附身** ——
   **在「能察觉你的生物」面前附身 → 仇恨保留**（附身完成后它继续敌视你）
6. 灵魂状态的判定是**"非附身状态"**
7. 灵魂状态**不能影响原版旁观者**

#### 7.7.2 ⚠️ 与现有实现的硬冲突（必须先决策）

**目前灵魂状态用的是原版旁观者（`GameType.SPECTATOR`）—— 而旁观者是不可被选中、不可被伤害的。**
这意味着需求 1 和 4 **在当前架构下根本做不出来**：

| 需求 | 旁观者模式下 | 结论 |
|---|---|---|
| 生物能**攻击**灵魂 | 原版索敌用 `EntitySelector.NO_SPECTATORS`，**生物根本不会选中旁观者** | ❌ 做不到 |
| 灵魂**有血条**、会被打 | 旁观者**免疫伤害** | ❌ 做不到 |

**必须把灵魂状态从「原版旁观者」换成「自研灵魂状态」**：

| 特性 | 自研实现方式 |
|---|---|
| 穿墙 | `player.noPhysics = true` |
| 别人看不见 | `setInvisible(true)` + 不参与常规索敌 |
| 不可交互 | 拦截 `PlayerInteractEvent` |
| 禁止指令 | 已有的 `CommandEvent` 拦截 |
| **可被察觉生物攻击** | ✅ 不是旁观者，能被打 |
| **有血条** | ✅ 血条 = 灵魂能量（或独立血量） |
| 原版旁观者不受影响 | ✅ 天然分离 |

**这正是当初选项里的「方案 B」，当时选了旁观者。现在需求变了，建议换过来。**

#### 7.7.3 待定的分类表（等用户确认）

| 档位 | 含义 | 建议归属 |
|---|---|---|
| **完全察觉** | 无视距离，始终能看见灵魂并主动攻击 | 收割者、缠魂甲胄等**本身就是灵体**的 |
| **近距离察觉** | 靠近 N 格内才看得见 | 恼鬼/怒鬼、普通幽灵 |
| **被动** | 看不见灵魂，但**被当面附身会保留仇恨** | 普通亡灵 |

**实现建议（延续既有思路）**：用**数据包标签**而不是硬编码 ——
`goety_revenant:soul_seers`（完全察觉）/ `goety_revenant:soul_sensors`（近距离），
预置 Goety + 原版亡灵 + Enigmatic Legacy，**以后加任何模组改一行 JSON 即可**。

#### 7.7.4 仇恨保留（用户本轮明确）

> **在「能察觉你的生物」面前附身 → 附身完成后它继续敌视你。**

实现：附身瞬间，扫描玩家周围**察觉范围内的察觉型生物** → 对每一个执行 `mob.setTarget(player)`。
（需要先完成 §7.7.2 的状态切换，否则它们打了也没用。）

#### 7.7.5 待做清单
| 项 | 依赖 |
|---|---|
| **灵魂状态改自研**（穿墙/隐身/不可交互/可被攻击/血条） | **先做，其它都等它** |
| 灵魂感知分类（标签 + 默认表） | 上一条 |
| 仇恨保留 | 上一条 |
| 灵魂 HUD（血条 + 契合度 + 弹出倒计时） | 上一条（客户端已能收到灵魂数据） |
| Enigmatic Legacy 兼容（modId `enigmaticlegacy`，项目 `IpUWAlqM`，1.20.1 ✓） | 独立，可并行 |

#### 7.7.6 开工前必须先拿到的东西
1. **`darkScytheSouls` 的实际值** —— javap 读不了 Goety 的配置类（`ItemConfig`/`MainConfig` 都返回空）；
   **最省事的办法：游戏跑起来后看 `config/goety-item.toml`**，里面直接写着这个值和注释
2. **Malum 的熟练度系统**：镰刀熟练度 / 魔法熟练度存在哪（capability？属性？）
3. **精准镰刃项链 / 隐藏锋刃项链**的注册名
4. **Malum 的收割入口**：`SpiritHarvestHandler.spawnSpiritsOnDeath` / `primeItemForShatter` —— 灵魂暴露要怎么和它对接
5. **灵魂暴露状态存哪** —— 疑似在 `MalumLivingEntityDataCapability`，
   但 javap 对它和 `SpiritHarvestHandler` **都返回空**（连 stderr 都没有，命令似乎根本没执行）：
   需要换姿势（`javap -p` 换参数顺序、或直接用压缩包工具读 class 常量池）
   > **已定位规律**：本环境**只要给原生命令加 `>` 重定向或管道，javap 就返回 0 行**；
   > 不加就正常。所以 `javap -c` 这种几千行的反汇编**拿不到** —— 反汇编路线在此环境不可用。
   > 绕过办法：给 Malum 加 `compileOnly`（**已做**），直接看签名即可，不必反汇编。

---

## 8. 网络协议与指令

### 8.1 通道（`RevenantNetwork`，id 0~10）
| id | 方向 | 用途 |
|---|---|---|
| 0 | S2C | 附身皮肤同步 |
| 1 | S2C | 身体状态整份下发 |
| 2 | C2S | 打开/嵌入/取出 |
| 3 | S2C | 命令式打开身体界面 |
| 4 | C2S | 轮盘施放嵌入式聚晶 |
| 5 | S2C | 转化台配置下发（兼开界面） |
| 6 | C2S | 转化台配置回传 |

### 8.2 指令 `/goety_revenant`
| 子指令 | 作用 | 权限 |
|---|---|---|
| `slot <器官> unlock\|lock` | 解锁/封锁槽位（`all` 全选） | OP |
| `unlock [true\|false]` | 整体改造解锁（跳过哭泣之井） | OP |
| `open` | 打开身体界面（自动解锁） | 玩家 |
| `state` | 打印各器官状态 | 玩家 |
| `clear` | 清空嵌入 | OP |

器官名：`heart liver spleen lung kidney spirit_aparture`。

### 8.3 按键
| 键 | 功能 |
|---|---|
| **B** | 改造身体界面 |
| **V** | 聚晶轮盘 |

---

## 9. 待办与已知未验证项

### 9.1 未验证（需要进游戏确认）
| 项 | 说明 |
|---|---|
| **尸体发光** | `isCurrentlyGlowing()` 返回 true 时原版是否真画描边；不亮就改成 `CorpseRenderer` 自绘 |
| **Malum 精魄拦截是否彻底** | Malum 在 `SpiritHarvestHandler.spawnSpiritsOnDeath` 里**直接生成**，可能绕过掉落表 |
| **哭泣之井判定** | 已改成「人进到井方块里面」（脚所在格及上下各一格，对齐 Malum `VoidConduitBlock#entityInside`）；站旁边不算 —— 进游戏跳一次确认解锁提示 |
| **镰刀附魔准入** | `malum:scythe` 标签合并后，Goety 的 `canApplyAtEnchantingTable` 会不会放行 —— **铁砧实测** |
| **灵魂护盾消耗** | `setWarding` 归零后是否自恢复、能否挡住嵌入流程的扣血 |
| **左键附身** | 已改成**左键点身体 + `PossessRequestMessage`**（不再用潜行，旁观者的按键状态不可靠）—— 进游戏确认左键能附身、且不会误触发攻击 |
| **精魄产出的两条线** | 读 Malum 源码确认：**灵魂暴露期间死亡**由 `SpiritHarvestHandler.spawnSpiritsOnDeath` **直接生成精魄实体**（不走掉落表）；我们的拦截只管**未暴露时的普通掉落**那条线 —— 两条线互不重叠，实测确认没有双份精魄 |

### 9.2 待做
| 项 | 说明 |
|---|---|
| ~~Malum 镰刀 ↔ Goety 双向互通~~ | **已做**（读 Malum 源码确认链路后补标签，无需改代码）。见 §7.6.10 |
| ~~Goety 镰刀伤害类型~~ | **已澄清，不再是问题**：Malum 的灵魂暴露走**物品标签**而不是伤害类型，所以 Goety 镰刀不需要伪造 `malum:scythe_melee` 伤害类型；唯一吃伤害类型的是**熟练度倍率**，由我们自己的 L8 负责。见 §7.6.10 |
| **精魂劫掠不允许附魔出来** | 可能本来就不在附魔台池子里（Malum 附魔走精魂灌注）—— **先看附魔台有没有它** |
| ~~嵌入会吞聚晶~~ | **已修**：改成「成功那一刻才拿」，失败/中途挪走都不扣。见 §5.3 |
| ~~转化台 UI 又丑又挤~~ | **已重做**：完全不用原版按钮控件，改成直接点/拖/滚轮 + 实时进度。见 §5.5 |
| ~~嵌入扣血 + 极速消耗~~ | **已删**：整套换成**元素超载 → 去克制地压下去**。见 §5.3 |
| ~~聚晶被动只有十几枚有~~ | **已改**：四级查找，整合包 300+ 枚聚晶**每一枚都有被动**，且都显示在 tooltip。见 §5.4 |
| **开窍魔药的配方** | 物品已做好（6 瓶，走 Goety 制药流程），**配方按你说的没写** —— 等你要的时候补数据包 |
| **元素超载的数值待调** | 现在：超载 5 分钟、连续克制 3 秒、负面每秒续 40 tick。第一次进游戏试完手感可能想改（都在 `ElementOverload` / `ElementCounter` 两个类里） |
| **克制条件待实测** | 尤其「水=埋进土里（头顶见不到天）」「土=走进树林（半径 4）」这两条，可能太严或太松 |
| ~~转化台界面显示笼子灵魂能量~~ | **已做**：界面底部画「笼内灵魂能量」并按够/不够变色，每 20 tick 发 `ConverterQueryMessage`(id 10) 刷新；顺带补上原本**整组缺失**的 `screen.goety_revenant.converter.*` 语言键（之前界面显示的是原始键名） |
| **美术资源** | 回魂者贴图是程序生成的占位图；物品贴图/模型未做（**你已确认不需要我操心**）|
| **配方 / 成就** | 未做（内容缺口，不是 bug）|
| Boss | 你已确认暂缓 |

### 9.11 灵化（已做）+ 成神仪式（**卡在祭坛选型**）
#### 一、灵化 —— 已实现
肉身与灵魂**完全融合**之后解锁的能力。不是灵魂出窍、也不是死，而是**魂已经和肉身长在一起**，
于是肉身可以随时呈现魂的性质。

**解锁条件（两条都要）**
1. **自身契合度满 100%** —— 魂魄和这具身体不再互相排斥
2. **灵魂游离过并成功回归过至少一次** —— 得先分开过，才谈得上"融合"
   （`LingHua.recordSoulWalk` 在附身回自己身体时记一笔）

**效果**：穿墙（`noPhysics`）+ 飞行（`mayfly`）+ **不可被观测**（隐身 + 完全不参与索敌，
连"能看见灵魂"的生物也看不见）。按 **H** 开关。

| | 灵魂游离 | 灵化 |
|---|---|---|
| 身体 | 躺在原地变成尸体 | **还在身上** |
| 限时 | 20 分钟 | **不限时**，随时开关 |
| 被看见 | 「能察觉灵魂的生物」看得见 | **谁也看不见** |

#### 二、成神仪式 —— ⚠️ **选型走不通，等你重选**
用户要「**自己的祭坛 + 复用本体结算**」。**这条做不到**，实测：

```
DarkAltarBlock        flags=0x0002   ← 包内可见，不是 public
DarkAltarBlockEntity  同样不是 public
```

本体的仪式结算是**焊死在它自己的 `DarkAltarBlockEntity` 上的**：
`IRitualType#onPerformRitual(Level, BlockPos, DarkAltarBlockEntity, Player, ItemStack)`
这个签名就要求必须是它的方块实体。我们的祭坛没法既是"自己的方块"又走它的结算。

**能做的只剩两条**：
| 方案 | 说明 |
|---|---|
| **甲** 用本体的黑暗祭坛 + 基座 | 只注册「献祭仪式」这个新 **ritual type**（`RitualType.addRitualType` 是公开 API）。工作量最小、效果最稳，仪式动画/粒子/JEI 全都白送 |
| **乙** 从零写我们自己的仪式系统 | 祭坛 + 基座 + 多方块判定 + 结算 + 表现，全部自己来。外观完全归我们，但没有本体的仪式动画与 JEI |

#### 三、成神 / 血肉仪式 —— **暂停，等用户决定（2026-09-28）**
用户明确说了「这个暂时还未决定，先不制作」。**代码一行都没写**，以下是已经谈拢的部分，
下次接着做时从这里开始。

##### 已经谈拢的设计
**血肉仪式 = 我们自己的仪式种类**（等于选了方案乙：从零写，不走本体祭坛），分两个子类：

| 子类 | 用途 |
|---|---|
| **献祭** | 把五脏献出去 → 成神 |
| **吞合** | 把聚晶"吞"进窍里 —— 五脏要的那几个**特定聚晶**得走这个仪式才能装进去 |

配套要**我们自己的祭坛 + 基座**（多方块）。

**成神流程（拼出来的）**
```
1. 跳哭泣之井              → 七窍可开
2. 喝开窍魔药 + 去克制地    → 开窍（已实现）
3. 拿到五脏各自要的「特定聚晶」
4. 逐个走【血肉仪式 · 吞合】→ 把特定聚晶吞进对应的脏
5. 六窍已开 + 五脏都吞了对的聚晶
6. 走【血肉仪式 · 献祭】
      → 五脏永久清空、不能再嵌
      → 五个聚晶被献祭掉
      → 灵窍里原本的聚晶 → 变成「三我聚晶」
7. 成神
```

##### ⚠️ 开工前必须先问清的三个问题（还没答）
1. **五个「特定聚晶」是什么？** 已有聚晶（按五行对）还是新做五个？具体是哪五个 / 叫什么？
2. **三我聚晶是什么效果？** 法术 / 被动 / 两者都有？
3. **几个基座？** 6 个（对应六窍）/ 5 个（对应五脏）/ 4 个（本体那套）？

##### 工作量提醒
方案乙 = **仪式动画、粒子、JEI 全都要自己写**，是这一整轮里最大的一块。
建议分两步：先做「方块 + 多方块判定 + 两个仪式能跑通」，再补动画与表现。

### 9.10 ★ 改造身体 → **七窍**（改名 + 第七窍 + 血腥界面 + 神识）
#### 一、改名与第七窍
「改造身体」这个名字不再用了，整套体系改叫 **七窍**（`key` / `screen` / 指令 / 提示全改）。

**七窍 = 五脏 + 灵窍 + 神窍**（原来只有 6 个，补上第 7 个）：

| 窍 | 元素 | 接受的派系 | 位置 |
|---|---|---|---|
| 心 肝 脾 肺 肾 | 火 木 土 金 水 | NETHER+STORM / WILD / GEOMANCY / ILL+WIND / FROST+ABYSS+VOID | 躯干（中医方位）|
| **灵窍** | 灵 | NECROMANCY | **头部左位** |
| **神窍**（新）| 神 | `SpellType.NONE` + **所有猜不出五行的外来派系** | **头部右位** |

头部两窍摆成**左右两只眼**，形状上和「七窍」这个说法对得上。
「猜不出五行」的兜底目标从灵窍改到**神窍**（它本来就该收「不属于这个世界的」东西）。

配套：`divine_brew` 开窍魔药 · 神；神窍的**元素超载**克制条件是
**「露天站在雷暴里，让天雷洗练」**（`isThundering() && canSeeSky`）。

#### 二、界面重做：血腥 / 黑暗
原来是用矩形和正圆拼的，太简陋。现在：

| 元素 | 画法 |
|---|---|
| 背景 | 近乎全黑 + 四周**血色暗角** |
| 躯壳 | **剥了皮的**：一层层半透明横条堆出暗红血肉，边缘更深 |
| 七窍 | **七个伤口**：结痂外环 → 血肉 → 中心黑洞 |
| 伤口形状 | 半径按角度扰动（每个窍的扰动值在类加载时按下标算好）→ **每个窍的"撕裂"形状都不同，且不随帧抖动** |
| 已开窍 | 中心一颗**随心跳搏动**的光（神窍是金色，其余是血红）|
| 已嵌入 | 聚晶直接画在伤口里 |
| 未开窍 | 伤口**缝着线**（黑底 + 交叉缝线）|
| 鼠标悬停 | 伤口**开始渗血**，往下滴 |
| 文字 | 血红标题 / 骨白正文 |

#### 三、神识 —— **修仙者那种神识**（不是界面）
> ⚠️ **第一版做错方向了**：我把它当成「一份不用看的 HUD」来做（暗角 + 血点）。
> 用户澄清：句子该断成「**不用看，神识就知道什么情况**」——
> 神识是**一种感知能力**，不是界面。已按这个重做。

**神识强度 = 开了几个窍**（修为就是这个）：

| 已开窍 | 神识半径 |
|---|---|
| 0 | 无知无觉 |
| 1 | 10 格 |
| 4 | 22 格 |
| 7（七窍全开）| **34 格** |

**它感知什么、怎么告诉你**

| 能力 | 行为 |
|---|---|
| **被动感知** | 每 20 tick 扫一次半径内的**活物 + 尸体**；**只在"有新东西进来"时说话**，一切照旧时一个字都不说 —— 所以玩家不用盯任何东西 |
| **锁定躯壳** | 扫到**自己的尸体**会单独点名（方向 + 距离）—— 和回魂咬合：灵魂状态下神识是唯一能帮你找身体的东西 |
| **主动扫视** | 按 **N** 把当前半径内的东西完整报一遍（没开窍会告诉你"神识未生"）|

提示走 **actionbar**（`displayClientMessage(..., true)`），不占聊天栏、不打断操作。

#### 神识 · 第二版（用户要"全套画面"）
第一版只有 actionbar 文字，用户不满意（选了：感知内容太少 / 被动太安静 / 只有文字没画面，
并且把**扩散波纹 / 穿墙虚影 / 描边发光 / 感知照亮周围 / 常驻光点**五个视觉全勾了）。现在：

| 层 | 画什么 | 时机 |
|---|---|---|
| **扩散波纹** | 屏幕中心向外推开 **3 圈**同心涟漪，渐隐 | 主动扫视（N）|
| **穿墙虚影 + 描边** | 每个目标在**真实位置**画**方框准星** + 中心搏动点 | 常驻 |
| **常驻光点（雷达）** | 不在视野里的目标 → 屏幕边缘光点，角度=方位、半径=距离 | 常驻 |
| **感知照亮** | 扫视瞬间惨白闪光 + 服务端给 **3 秒夜视**（真的照亮）| 主动扫视 |
| **血色暗角** | 灵魂能量越低越浓 | 能量 <45% |

**感知内容**：活物 / 玩家 / 尸体 / **自己的躯壳** / **掉在地上的聚晶**。
颜色：自己的躯壳=**金**（额外套一圈金环）、别的尸体=暗红、玩家=白、活物=血红、聚晶=青。

**被动存在感**：神识**一直开着**，每 10 tick 刷新一次快照，画面常驻不再只在"有新东西"时才出现。

##### ★ 关键技巧：**穿墙是免费的**
这些标记画在 **GUI 层**（`IGuiOverlay`），而 GUI 渲染**根本没有深度测试** ——
所以只要把世界坐标**投影成屏幕坐标**再画上去，它天然就穿墙。
**不需要**自己造 no-depth 的 `RenderType`（1.20.1 里 `RenderStateShard` 的常量是 protected，
外部根本拿不到），也**不需要**给原版实体加发光标记（`Entity.isCurrentlyGlowing` 改不了）。

投影用**游戏自己的** `GameRenderer#getProjectionMatrix` + 相机矩阵：
```java
Matrix4f modelView = new Matrix4f().rotate(camera.rotation()).translate(-cam.x, -cam.y, -cam.z);
Vector4f v = new Vector4f(wx, wy, wz, 1).mul(modelView).mul(projection);
if (v.w() <= 0) → 在背后，交给雷达
ndc = v.xy / v.w → 屏幕坐标
```

> **踩到的坑**：JOML 里向量乘矩阵叫 **`mul`**，不是 `transform`（新版 JOML 改的名）。
> 第一次编译就是挂在这。

> ⚠️ **待实测**：准星位置没把握 —— 矩阵推导是按 MC 的渲染顺序来的，
> 但符号/共轭约定没法离线验证。表现不对的话：整体反向 → 给 `camera.rotation()` 加 `.conjugate()`；
> 左右镜像 → 翻 `ndcX` 的符号。都是一行的事。

### 9.9 启示录（Goety: Revelation）联动调研 + 光环 tooltip
#### 启示录到底支不支持第三方联动
**结论：没有 API，但数据层开放 —— 能做，靠注册名 + 标签。**

| | 结论 | 依据 |
|---|---|---|
| **Java API** | ❌ 没有 | 它的包只有 client/config/data/entity/event/goal/item/mixin/tag/util，**没有 `api/`** |
| **它发事件给我们** | ❌ 没有 | `event/` 包里（LivingDeathListener / LivingEntityHurtEvent / LivingTickEvent）都是它**去监听** Forge 事件 |
| **物品标签** | ✅ 可用 | `data/goety_revelation/tags/items/`：tools/{swords,bows,tridents,whips,tools}、armors/{helmets,chestplates,leggings,boots}、spiders、apollyon_ritual_item |
| **实体标签** | ✅ 可用 | `tags/entity_types/apostle`、`prevent_apollyon_discard` |
| **注册名 + Curios** | ✅ 可用 | `AscensionHalo extends Item implements ICurioItem`，注册名稳定 → 能查玩家戴没戴 |
| **`goety_ritual/ritual/*.json`** | ✅ 能加 | 数据驱动仪式：`ritual` id + `iconItem` + `requirements.blocks`（多方块）|
| **`goety_brew/brew/*.json`** | ⚠️ 不确定 | 格式是「**扩展**某个既有酿造插件」（`"plugin": "Goety Revelation"` + capacity/catalysts），**不是产出任意物品的配方** —— 能不能让巫婆大锅产出我们的魔药还得再查 |
| **反向** | ❌ | 它不认识我们，只能靠标签让它"看见" |

#### 成神流程（读它的成就 + 语言文件还原出来的）
| 阶段 | 成就 | 关键物品 |
|---|---|---|
| 1 | 破灭神性 | `broken_halo` **破碎之环** ← 击杀使徒掉落 |
| 2 | 最后的审判日 / 末日之终 | 召唤并击败 **亚波伦** |
| 3 | 神锻技艺 | `god_forging_scroll` **神锻卷轴** |
| 4 | 神性铸魂 | `apocalyptium_ingot` **神灵金属锭** → 神灵金四件套 |
| 5 | 瞬间即永恒 | `eternal_watch` 永恒怀表 |
| 6 | **终末神明** | `halo_of_the_end` **终末之环** —「飞升神域」|

**光环三阶**：`broken_halo` 破碎之环 → `ascension_halo` **晋升之环** → `halo_of_the_end` 终末之环。
它还会**在玩家头顶渲染光环**（`PlayerHaloLayer` / `PlayerHaloModel`）。

#### 学到的：它的文字渲染风格
| 用途 | 颜色码 | ChatFormatting |
|---|---|---|
| 经文 / 叙述 | `§5§o` | DARK_PURPLE + ITALIC |
| 神圣强调 | `§6` | GOLD |
| 指代「祂」 | `§4§L` | DARK_RED + BOLD |
| 能力描述 | `§8§o` | DARK_GRAY + ITALIC |
| 段落标题 | `§7§l` | GRAY + BOLD |
| 隐藏经文 | `§0§k…&r` | OBFUSCATED 装饰 |

而且它的 tooltip 是**按住 SHIFT 才展开**（`tip0` = "按下[SHIFT]显示更多内容"）、ALT 展开能力。

#### 实现：往光环里加我们的内容（零侵入）
`common/compat/RevelationHalo` + `common/RevelationHaloTooltip`：
- 按注册名认三阶光环，Curios 查「戴着哪一阶」（背包里不算）
- 走 **`ItemTooltipEvent`** 往它 tooltip 里追加内容 —— **和我们对所有聚晶做的是同一套办法**，零 Mixin
- **文字风格完全跟着它走**：收起时只留一句 `§5§o` 提示，展开时用 `§7§l` 做标题、`§8§o` 写数据、`§5§o` 写"经文"
- 数据来自 `BodyClientState`（登录时补发一次 `sendBodyState`，否则 tooltip 里没数据）

> 还没做：**用光环当成神门槛**（方案 B）—— 等你定 A / B / C。

### 9.8 ★ 线上崩溃复盘：灵魂能量归零 → 每 tick 死循环 → 崩服
**报告**：`crash-2026-09-27_23.08.28-server.txt` —— `java.lang.LinkageError: MessageSupplier`。

#### 表面现象是误导
崩溃报告里写的是 log4j 类加载器冲突（`EventBus.handleException` 打日志那一步炸的），
所以 `Suspected Mods: NONE`，看起来像别的模组的锅。**真正的原因在崩溃前那一秒的聊天栏里**：

```
23:08:28  「灵魂能量耗尽 —— 这次是真的死了」× 18
```

#### 真相：我们自己的死循环
原来的 `killSoul`：打标记 → 扣血杀人。可是**这次死亡被整合包的死亡保护挡下来了**
（神秘遗物 / 各种图腾 / 一堆防死模组），于是：

```
能量 = 0 → killSoul → 死亡被取消 → isSoul 仍是 true → 下一 tick 能量还是 0 → 又 killSoul → …
```

每秒循环十几遍，最后某一次抛异常 → Forge 事件总线去打日志 → **log4j 那步 LinkageError → 整个服务器没了**。

#### 修法（三层）
| 层 | 做法 |
|---|---|
| **不再循环** | `killSoul` 改成**先脱离灵魂状态**再动手 —— 主循环开头那句「不是灵魂就 return」下一 tick 就把门关上了 |
| **放行真死** | `onLivingDeath` 看到 `KEY_FINAL` 标记就**直接 return**（不取消死亡、也不再把人变成灵魂）。标记由 killSoul 在 `hurt()` 前打上、`finally` 里摘掉（`hurt` 是同步的）|
| **保护层** | `onPlayerTick` / `onLivingDeath` / `ElementOverload.onPlayerTick` 全部包 `try/catch(Throwable)`，**绝不走 Forge 总线那套日志**（那条路在本包里必炸），只自己打一次 stderr |

> **教训**：这个整合包里「监听器抛异常」= 游戏崩溃，因为 Forge 处理异常时打日志那一步是坏的。
> 所以联动模组的代码必须**假设自己的每一段逻辑都可能抛**，宁可吞掉也不能让它冒泡出去。

#### 顺带发现的（未修，你说过贴图不用管）
日志里 15 条 `Unable to load model: 'goety_revenant:xxx#inventory'` ——
除了六瓶魔药和染魂钢图腾，其余物品**都没有模型文件**（这是已知的、你说暂缓的部分）。
但**连有模型的 `fire_brew` 也报 FileNotFound**，而 jar 里那个文件确实存在且内容正确（51B，parent 指向 `opening_brew`），
说明还有别的原因（可能是 Lightspeed 的资源缓存，或者游戏读的是另一个 mods 目录）。**待查**。

### 9.7 灵魂能量统一 + 自主出窍（**大改**）
#### 灵魂能量 = 玩家自己那一份（不再是另开的池子）
原来灵魂状态自带一个 10000 的独立池子（`GRSoulEnergy`），和本体那份**互不相干**。
现在直接**落回本体的灵魂能量**：

```java
SoulData.getEnergy(p)  ->  SEHelper.getSESouls(p)
SoulData.setEnergy(p,n)->  SEHelper.setSESouls(p, n)
```

这样**聚晶、法杖、仪式什么都不用改就能直接用** —— 它们本来就是在花那一份。
（在旁边再养一个平行池子才需要处处做转换，还容易对不上。）

**上限 = 基础量 + 方舟（+ 图腾）**，直接读本体的配置（反射，读不到有兜底）：
| 情况 | 上限 |
|---|---|
| 没绑灵魂方舟 | `MainConfig.MaxSouls` = **10000** |
| 绑了灵魂方舟 | `MainConfig.MaxArcaSouls` = **100000** |

图腾那一档由本体自己叠（它有容器/饰品逻辑），所以只要**基数**读对，
「基础量 + 图腾 + 方舟」就是本体原本的算法。

> **「死了变灵魂 → 能量充满」是刻意的**（已确认）：灵魂从零开始飘，不继承生前攒的量。
> 充的是 **上限** 而不是写死 10000 —— 没绑灵魂方舟的玩家上限本来就是 10000，
> 绑了方舟则是方舟那一档，图腾那一档也照样算进去。
>
> 但**主动出窍不充能**：否则「出窍 → 回身体 → 出窍」就是一个免费回满灵魂能量的循环。
> 只有死亡那条路才充满（那条路没有循环）。

#### ★ 灵魂游离分**两种**（不然容易弄混）
| | **死亡游离** `MODE_DEATH` | **出窍游离** `MODE_DRIFT` |
|---|---|---|
| 怎么进 | 死了 | **主动按 G** |
| 尸体 | **登记 + 追踪** | **完全不登记**（连 `scanForOwned` 那条回退都要拦掉，否则等于变相追踪）|
| HUD 箭头 / 尸体高亮 | 有 | **没有** —— 身体放哪儿自己记住 |
| 附身回自己身体 | **完整复活** | **残破躯体**：血量只有四成 + 虚弱 1 分钟 |
| 灵魂死了 | — | **直接重生**，身体留在原地 |
| 身体 | 就是你的尸体 | 留着，**复活后右键尸体就能把东西拿回来** |

**共用一个入口**：两条路都叫「灵魂游离状态」，只是模式不同（`GRSoulMode`）。
客户端的箭头/高亮要靠 `SoulSyncMessage` 里的 `drifting` 标记来判 —— 客户端读不到 persistent data。

> 右键尸体取物**本来就有**：`CorpseEntity#interact` 只对主人生效，
> 而且已经挡掉了灵魂状态（`!isSoul(player)`），所以「灵魂时拿不了、复活后才能拿」天然成立。

**上限是相加，不是取大**：
```
灵魂能量上限 = 灵魂自己的基础值 10000 + 外物加上去的上限
  什么都没有          → 10000
  绑了灵魂方舟        → 10000 + 100000 = 110000
  ＋染魂钢图腾 × N    → 再 + N × 30000
```

#### ★ 上限改成「照着本体的判断顺序来」
本体源码仓库找到了：**`github.com/Polarice3/Goety-2`（分支 `1.20`）**。读了三个文件之后推翻了我原来的写法：

```java
// TotemFinder.FindTotem —— 本体找图腾的官方顺序
if (CuriosLoaded.CURIOS.isLoaded()) { 先在 Curios 里 findFirstCurio(isTotem) }
if (isTotem(player.getOffhandItem())) { 用副手 }
else { for (int i = 0; i <= 9; i++) { 扫快捷栏 0~9 } }
private static boolean isTotem(ItemStack s) { return s.getItem() instanceof ITotem; }

// SEHelper —— 方舟和图腾是**二选一**，不是相加
public static boolean getSoulsContainer(Player p) {
    if (getSEActive(p)) return true;                 // 绑了方舟 → 用能力值
    else return !TotemFinder.FindTotem(p).isEmpty(); // 否则 → 用图腾
}
```

**所以现在**：
```java
max = SOUL_BASE(10000) + (SEHelper.getSEActive(p)
        ? MaxArcaSouls                     // 绑了方舟 → 方舟那一档
        : TotemFinder 找到的图腾的容量)     // 否则 → 图腾自己报的容量
```

我原来手写的那段「遍历全部 Curios 槽」**整个删掉了** —— 那是重复造轮子，
而且顺序（Curios → 副手 → 快捷栏）和本体不一致，玩家会觉得「为什么本体图腾能用的位置我的不能用」。

> ⚠️ **注意这里是二选一，不是相加**：绑了方舟时图腾不额外加。
> 这是**本体自己的规则**，所以如果你要的是「方舟 + 图腾都算」，得明说，我再改成相加。

#### 染魂钢图腾（新物品）
本体的图腾**不进我们这套上限计算**（它们管的是自己的容器/饰品逻辑），
所以另做一个能真正扩容的：

| 项 | 值 |
|---|---|
| 注册名 | `goety_revenant:soul_stained_steel_totem` |
| 名字 | 染魂钢图腾 |
| 效果 | 灵魂能量上限 **+30000 / 枚**，可叠加 |
| 生效位置 | **本体 `TotemFinder` 的官方顺序**：Curios → 副手 → 快捷栏 0~9 |
| 消耗 | 不消耗 |
| 结构 | **完全照抄本体的 `TotemOfSouls`**（见下）|

它是**真正的本体图腾** —— `implements ITotem, ICurioItem`，所以本体那套
（找图腾 / 灵魂容量 / 不死效果）全都认它。结构照抄 `TotemOfSouls`：

```java
class TotemOfSouls extends Item implements ITotem, ICurioItem {
    public int maxSouls;
    public int getMaxSouls() { return this.maxSouls; }
    public void inventoryTick(...) { this.setTagTick(stack); ... }   // 铺开 / 夹紧 NBT
    public void onCraftedBy(...)   { ITotem.setSoulsAmount / setMaxSoulAmount }
    public boolean isBarVisible / int getBarWidth / int getBarColor   // 装了多少的条
}
```

灵魂**存在图腾自己的 NBT 里**（`Souls` / `Max Souls`），这是本体的做法 ——
所以它同时也是一个正经的「灵魂容器」，tooltip 会显示「已存灵魂：0 / 30000」。

> 走过一段弯路：一开始自己写「遍历背包 + 副手 + Curios」，
> 后来收窄成「只认 Curios」，最后**按本体的 `TotemFinder` 重写** ——
> 位置顺序、判定方式（`instanceof ITotem`）、容量来源（`getMaxSouls()`）全部对齐本体。

#### ★ 灵魂落点 / 真死回归点：**床不作数了**
死亡游离的灵魂**不从倒下的地方开始飘**，而是去两个「回归点」之一：

| 情况 | 灵魂落在哪 |
|---|---|
| **绑了灵魂方舟** | 方舟正上方一格 |
| **没绑** | **世界出生点**（`overworld.getSharedSpawnPos()`）|

**床的重生点被清掉**（`player.setRespawnPosition(null, null, 0, false, false)`），
位置在两处：

1. **死了变灵魂**那一步 —— 灵魂都去世界出生点了，真死时再被拉回床边就自相矛盾
2. **灵魂能量归零真死**（`killSoul`）那一步 —— 这样**出窍游离**也一致：
   主动出窍的灵魂如果熬到能量归零，同样不该回床边

> 两次都清，是因为**只清一处会有漏**：只在「死了变灵魂」清的话，
> 出窍游离这条路根本没经过那一步，熬死时照样回床边。

尸体**永远不跟着走** —— 它留在你倒下的地方，所以「回出生点/方舟」和「回去找身体」
是两件都要跑的事。超时强召（`forceRecall`）走的是同一套落点，前后一致。

#### 有灵魂方舟 → 灵魂从方舟开始飘
`Possession.spawnSoulAtArca(player)`：绑了方舟就把灵魂传送到方舟正上方一格。
**尸体不跟着走** —— 它留在你倒下的地方，所以「回方舟」和「回去找身体」是两件都要跑的事。
没绑方舟（`getArcaBlock` 返回 null）就什么都不做，灵魂留在原地。

#### 自主动作
| 行为 | 说明 |
|---|---|
| **自主恢复** | 灵魂状态**不再有常驻抽干**：每秒 **+20**（基础档约 8 分钟回满，方舟档慢得多）。花能量的地方是放聚晶和附身 —— 乱放还是会把自己抽干 |
| **抽干就死** | 能量归零 = 真死（`Possession.killSoul`）|
| **越低越虚弱** | <60% 虚弱；<35% 再加缓慢；<15% 再加挖掘疲劳 + 失明。都是短时效果每秒续一次，能量回上来自动退 |
| **自主灵魂出窍** | 去过哭泣之井之后**随时可按 `G`**，不需要任何物品。身体原地留下一具装着全部家当的尸体（`CorpseEntity.of` 会清空玩家背包，不会复制东西）|

#### HUD 只显示灵魂能量
**删掉**了契合度条和剩余时间条 —— 灵魂身上只有「灵魂能量」这一个数。
现在只画两样：**能量条**（本体贴图，分母是服务端算好的真实上限）+ **指向身体的箭头**。

### 9.6 哭泣之井：改成「泡到被吐出来」（**逐字对齐 Malum**）
以前是「碰到井方块就解锁」。现在**照抄灵灾自己拿幽影精魄的那套**——
读了 Malum 1.20.1 的 `TouchOfDarknessHandler#entityTick` 源码：

```java
Block block = level.getBlockState(livingEntity.blockPosition()).getBlock();
boolean isInTheGoop = block instanceof PrimordialSoupBlock || block instanceof VoidConduitBlock;
if (!isInTheGoop) {
    block = level.getBlockState(livingEntity.blockPosition().above()).getBlock();
    isInTheGoop = ...;
}
if (isInTheGoop) {
    handler.progressToRejection++;
    if (handler.rejection == 0 && handler.progressToRejection > 60) {
        handler.reject(livingEntity);      // ← 被弹出去
    }
} else {
    handler.progressToRejection = 0;       // ← 离开就清零
}
```

| 逐字对齐的点 | 我们的做法 |
|---|---|
| 认的方块 | 只有 **原初之汤** `primordial_soup` 和 **方舟导管** `void_conduit`；**`weeping_well_core` 不算**（那是结构核心，不是那摊东西）|
| 看哪一格 | **脚下那格 + 身体那格**（`blockPosition()` / `above()`）—— 不再是碰撞箱求交 |
| 泡多久 | 连续 **> 60 tick**（第 **61** tick 触发），中途离开**清零重来**，不是慢慢掉 |
| 什么时候解锁 | **被弹出的那一刻** —— 和幽影精魄同一次判定、同一个阈值 |

判定改成**每 tick** 跑（原来是每 10 tick），否则 61 tick 的阈值最晚会迟到 9 tick 才解锁。
灵窍的**元素超载**克制条件也换成同一个 `isInGoop`，三处（解锁 / 超载 / 幽影精魄）语义完全一致。

### 9.5 灵魂 HUD 重构（**零文字**）
| 元素 | 画什么 |
|---|---|
| **主能量条**（本体贴图） | 灵魂能量 / 10000 —— **放在本体灵魂能量条的位置**（本体那条在灵魂状态被我们藏掉了，所以位置不跳、外观不变，变的只是「这个数是谁的」）|
| 细条 1（绿） | 契合度 |
| 细条 2（紫） | 灵魂状态剩余时间 |
| **指针** | 身体在哪个方向 |

**指针三种形态**：
- **指针** —— 旋转量 = `身体方位角 − 玩家视角 yaw`，跟着视角实时转；颜色按距离 绿(≤16) → 黄(≤64) → 红
- **脉冲圆环** —— 身体在 6 米内，不用指了，画个会呼吸的圈
- **紫色方块** —— 身体在**另一个维度**：没有方向可言，只表示「在那边」

#### 位置提示从「文字」改成「图形」
- **删掉**了原来每秒刷一次的 `soul_hint_dir`（"身体在北边 35 格"）那套文字
- 改成服务端只发**两个数**（距离 + 方位角），客户端自己画箭头 —— 朝向能跟着视角转，而且不用念文字
- `SoulSyncMessage` 新增 `bodyDistance`（-1 = 没身体）与 `bodyBearing`（NaN = 没方向 / 另一个维度）
- **方位角用 MC 自己的 yaw 约定**（`atan2(-dx, dz)`，0 = +Z 南、90 = -X 西），客户端直接减 `player.getYRot()` 就是屏幕上的相对角度
- 「左键附身」的操作提示**只在刚变灵魂的头 10 秒**说一次，不再每 3 秒刷屏

**可见性**：整块 HUD 只在 `ClientSoulState.isSoul()` 时画 —— 而那个标记是**服务端单独发给你自己的**，
所以「只有灵魂状态的人可见」是天然的：你看不到别人的指针，别人也看不到你的。

> ⚠️ **位置是手感的，不是算出来的**：本体的定位参数是 `goety.toml` 里的
> `soulGuiHorizontal = 100 / soulGuiVertical = -5`，但**这两个数到屏幕坐标的换算公式在字节码里，
> 没能还原出来**（javap -c 在本环境不可用）。所以现在是按「右下角」放的：
> 右边缘留 10px、底边留 40px。**进游戏看一眼，偏了就报个方向和像素数，改 `MARGIN_RIGHT / MARGIN_BOTTOM` 即可。**

### 9.4 转化台改造 + 灵魂状态四连修
#### 转化台：改成勾选式多选
| 以前 | 现在 |
|---|---|
| 「起始精魄 + 数字按钮调 1~4 种」 | **九种各自勾选，最多九种一起产** |
| 想同时产不相邻的两种（大地 + 狱火）**做不到** | 直接两个都点上 |
| 一进界面就在按上次的配置产 | **默认一个都没选 → 什么都不产**，必须先点出你要的 |
| 「没有诅咒之笼」提示写了**两遍**（能量条区 + 底部状态行） | 只在底部状态行写一次 |
| 数据结构：`primaryType` + `typeCount` | 数据结构：**9 位掩码** `selectedMask`（存 NBT 的 `SelectedMask`）|

一轮花费 = 5 × 选中数量。潜行右键方块 = 全选/全不选。

#### 灵魂状态四个 bug
| # | 现象 | 真正的原因 | 修法 |
|---|---|---|---|
| 1 | **左键进不了身体** | 原版 `Minecraft#tick` 里 `handleKeybinds()` 会**先把 `keyAttack` 的点击 consume 干净**去挥拳/挖方块，而 `ClientTickEvent` 是在那**之后**才发的 —— 所以在 tick 事件里 `consumeClick()` 永远是 false，那段代码从来没触发过 | 改用 `InputEvent.InteractionKeyMappingTriggered`（在原版处理攻击**之前**发、且可取消）：是左键 + 灵魂状态 → 取消挥拳 + 发附身包 |
| 2 | **穿不了墙** | 服务端设了 `player.noPhysics = true`，但**这个字段不同步给客户端**；客户端的 `LocalPlayer` 仍按原版碰撞走 → 「服务端放你过、客户端把你卡住」 | 客户端每 tick 跟着设一遍（退出时复原；是原版旁观者就不动） |
| 3 | **不是半透明** | 只换 `RenderType.entityTranslucent` **没用** —— 那只管「用带混合的管线画」，真正决定透明度的是**顶点 alpha × shader 颜色**。而原版只在 `flag1`（隐身但对你可见）那条分支里才把 alpha 压到 0.15；我们的 `isBodyVisible` 特意返回 true，**正好绕开了那条分支** → 仍然按 alpha = 1 画 | 在 `render()` 里自己 `setShaderColor(1,1,1,0.45)` + 开启混合 + 关深度写入，画完恢复 |
| 4 | **右下角还挂着灵魂方舟能量** | 那是**本体**的 `SoulEnergyGui`，注册名 `goety:soul_energy_hud`，画的是**你身体里**的能量 —— 可你现在是一缕魂，那个数跟你没关系 | `RenderGuiOverlayEvent.Pre` 里按 id 拦掉（`goety` 命名空间 + 路径含 `soul`）。本体是用 `RegisterGuiOverlaysEvent` 注册的，注册后没法注销，只能在这一层取消 |

### 9.3 之前修的 bug（5 个）
| # | bug | 后果 | 修法 |
|---|---|---|---|
| 1 | **精魄转化台没有掉落表** | **挖掉不掉落自己**，方块白给 | 补 `data/goety_revenant/loot_tables/blocks/spirit_converter.json`（含 `survives_explosion`）|
| 2 | 嵌入快路径**先扣灵魂能量、后核验聚晶** | 玩家中途把聚晶挪走 → **白扣 2000 灵魂能量**，提示还说「聚晶没被消耗」 | 改成**先核验再扣**；抽出 `focusStillThere()` 给两条路共用 |
| 3 | 转化台 `use()` 在**主手拿着物品**时也返回 `SUCCESS` | **贴着转化台放不了方块** | 主手非空 → 返回 `PASS`；客户端只返回 `SUCCESS` 做挥手，服务端 `CONSUME` |
| 4 | 已经开窍还喝同元素魔药 | **白费一瓶**（照扣不误） | 改成**先检查再消耗**：已开窍就直接把瓶子还给玩家 |
| 5 | `MalumCompat` 的护符缓存是**两个只增不减的静态 Map** | 见过的每个玩家 UUID 永远留在内存里（**内存泄漏**）| 改存 `(值, 世界游戏刻)` 的单表 + 超阈值时按世界时钟清理。用 `player.tickCount` 是修不了这个的 —— 那是玩家各自从 0 数的，没法判断**别人**的条目过期没有 |

---

## 10. 构建与验证

### 10.1 本机环境的坑（都已处理）
| 问题 | 处理 |
|---|---|
| 本地代理 `127.0.0.1:10808` 已停用 | 项目 `gradle.properties` 覆盖 `nonProxyHosts=*` + `trustStoreType=jks` |
| `services.gradle.org` 不可达 | wrapper 指向腾讯云镜像 |
| 插件门户慢 | 阿里云镜像 |
| **DSH 沙箱禁止 fork 子进程** | `CreatePipe error=5`，Gradle 起不了守护进程 → **构建需更宽沙箱权限**（每次都弹确认，需点同意） |

### 10.2 两条验证路径
**A. 完整构建**（出 jar，需权限）：`gradlew.bat build`
**B. javac 直编**（不需权限，只验编译）：
```
# build/javac-args.txt：-cp（forge mapped jar + goety deobf jar + 模块缓存全部 jar）+ -d + -encoding UTF-8 + 源文件列表
javac -J-Dfile.encoding=UTF-8 @build/javac-args.txt
```
**坑**：argfile 必须 **ASCII**（中文路径会让 javac 的 GBK 解析炸）；classpath 用**正斜杠**（反斜杠被 argfile 解析吃掉）。

### 10.3 javap 查签名的正确姿势
```powershell
# javap 不支持 @argfile（那是 javac 的）；cmd /c 会吃掉引号 → 用 & 调用
& "$JB\javap.exe" -cp "$FORGE;$GOETY" com.Polarice3.Goety.xxx
```
只用 goety.jar 会因为父类解析不了而**静默退出**，必须带上 forge mapped jar。

### 10.4 ★ 1.20.1 API 差异 —— 编译期才会咬人的坑（已踩）
| 坑 | 真相 | 正确写法 |
|---|---|---|
| `new BlockPos(double, double, double)` | **1.20.1 没有这个构造器**（只有 `(int,int,int)`；接受 double 的版本是后来的） | `new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))`，或 `BlockPos.containing(...)`（**同样不存在，别用**） |
| `Enchantment#checkCompatibility` | **protected**，外部调不了 | `EnchantmentHelper.isEnchantmentCompatible(Collection, Enchantment)` |
| `HumanoidArmorLayer` / `PlayerRenderer` 的包路径 | 不在 `client.renderer.entity` 顶层 | `client.renderer.entity.layers` / `client.renderer.entity.player` |
| `GuiGraphics#renderBackground` | 1.20.1 是 **1 个参数** | `renderBackground(graphics)`，没有 `(int,int,float)` |
| `EnchantmentLevelSetEvent` | **只能改等级**，不能塞新附魔 | 想新增附魔走 `AnvilUpdateEvent` 或物品标签 |
| `EntityType.PLAYER` 泛型 | 是 `EntityType<Player>` | 需要 `AbstractClientPlayer` 时得强转 |
| `Screen#tick()` | 存在且被 `Minecraft#tick` 每帧调用 | 界面轮询刷新直接重写它（本次转化台界面就用这个） |

| `BrewItem(Item.Properties)`（Goety） | **在自己包外不可见**（编译期报「需要: 没有参数」），而 `Item#getMaxStackSize` 又是 `final` | 只能 `super()`，属性全自己处理 |

**教训**：写 1.20.1 代码**不要凭记忆写签名**，写完必须过一次 `gradlew build` 或 `javac` —— 上面每一条都是编译期才报的。
**另一个教训**：光看 class 文件的常量池**看不出访问修饰符** —— `BrewItem(Properties)` 在池子里明明有，
实际却是包内可见。签名要真编译一次才算数。

---

## 11. 关键决策记录
1. **modId 用 `goety_revenant`** —— "Goety: Revenant" 有冒号空格，做不了 modId
2. **亡灵归「灵窍」特殊槽**，不占五脏
3. **附身别人身体时用不了聚晶**（身体不认你的聚晶）
4. **灵魂状态用原版旁观者底层**（穿墙/隐身/禁交互全部原生）
5. **灵魂状态的一万能量与本体彻底分离**（不写进本体灵魂能量）
6. **回魂是最后手段**（图腾/方舟回归优先）
7. **联动一律软依赖 + 运行时注册名查找**，不引别人 jar 进编译
8. **Malum 交互优先找公开 API**，实在没有再考虑 Mixin（目前一处都不需要）
