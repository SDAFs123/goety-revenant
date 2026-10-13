# 架构翻新方案

> 依据：对 165 个源文件 / 24,238 行的全量普查，以及外部审查报告
> （D:\goety-revenant-client-review.md）指出的底层问题。
> 原则：**按功能分类，不堆一处；每一步都能构建通过；不留死代码。**

---

## 一、现状：问题在哪

### 1.1 单文件承担整个子系统

| 文件 | 行数 | 它一个人干了多少件事 |
|---|---|---|
| `client/screen/TreeTexture.java` | **2887** | 纹理生命周期 + 缓存 + 视口变换 + 内容图分区 + 土壤绘制 + 过渡层 + 血肉土壤 + 树骨架 + 枝叶 + 根（三条路/细根）+ 种子与破开 + 图像素读写原语 + 槽位/叶子数据 + 星象点缀 |
| `client/screen/TreeScreen.java` | 784 | 界面 + 视口 + 拖动缩放 + 节点绘制 + 星象 + 生长动画 + 诊断 |
| `common/soul/Possession.java` | 459 | 附身 + 目标查找 + 灵魂形态进出 + 弹出 + 强制召回 + 真死 |
| `common/event/RevenantEvents.java` | 407 | 死亡接管 + 灵魂 tick + 攻击结算 + 指令注册 + 禁指令 + 图腾优先级 |

**症状**：改一处要读三千行；两件事之间隔着几十个不相干的方法；同一个"分区比例"要在多个文件里手抄。

### 1.2 死代码长期堆积

本次已清 **1032 行**：整套被替换掉的旧绘制实现（`seedOld` / `blendZoneOld2` / `soakedSoilOld` /
`fleshLayerOld` / `muscleZone` / `fleshShallow` / `legacyRoots` / `routeRoot` …）
+ 旧针叶树分支 + `USE_OLD_TREE` 开关 + 若干零调用私有方法。

**根因**：改动时把旧实现"留着对照"，但**没有任何机制清理**。→ 见第五节。

### 1.3 状态散落、清理缺失

客户端 6 个类各自用静态字段缓存"从服务器同步来的状态"，**没有统一的生命周期**。
断线/换世界时残留（`seenStage` 会让新存档的生长动画误判成"已看过"）。
已补 `ClientSessionReset`，但**这是补救，不是设计**。

### 1.4 事件总线混用

`RenderPlayerEvent`（FORGE 总线）曾被写进 `Bus.MOD` 的类里 → 模组加载直接失败。
两条总线的边界没有在结构上体现出来。

### 1.5 领域边界缺失

现在是"按技术分层"（common / client / network / compat），**领域被切散**：
附身的逻辑同时出现在 `soul/Possession`、`event/RevenantEvents`、
`client/render/RevenantPlayerRenderer`、`client/PossessedAppearance`、`network/PossessRequestMessage`。
想弄清"附身到底怎么走"要翻五个目录。

---

## 二、目标结构

```
com.habaiwa.goetyrevenant
├── GoetyRevenant.java          模组入口（只做注册与装配）
├── core/                       注册、配置、工具（不再是"什么都往里塞"）
├── soul/                       【灵魂领域】灵魂状态本身
│     SoulData / SoulEnergy / SoulConstants / SoulMode(死亡游离|出窍游离)
│     SoulPerception（谁能看见灵魂）/ SoulCollapse（溃散）/ SoulHud 数据
├── possession/                 【附身领域】独立成一个包
│     PossessionTarget（可附身判定 + 目标查找）
│     PossessionSession（附身中：时长 / 契合度 / 类型 / 清退）
│     PossessionEffects（身体特性 BodyTraits / 能力 PossessAbilities）
│     PossessionRecall（强制召回 / 真死）
├── body/                       【七窍领域】窍 / 嵌入 / 被动 / 施法
├── flesh/                      【血肉线】三条路 / 阶段
├── sequence/                   【序列】觉醒 / 七问答
├── ritual/  spell/  item/  block/  entity/    内容
├── compat/                     联动（Goety / Malum / 启示录 / Carry On / EL）
├── network/                    协议（每条消息一个类，保持现状）
├── command/                    指令
└── client/
      ├── screen/<每个界面一个子包>
      ├── render/   实体渲染
      ├── overlay/  HUD
      ├── fx/       特效
      └── tree/     ★ 魂树界面拆成 8 个文件（见下）
```

### 2.1 魂树界面的拆分（最急，因为它最大）

```
client/tree/
  TreeCanvas.java       像素缓冲与绘制原语：width/height/pixels/stride、
                        put/get/putAbgr/putRaw/disc/blendDot/mix/abgr、ink 测量
  TreeLayout.java      内容图分区（星空/夜空/树/土壤/过渡/血肉土壤）与坐标换算
  TerrainPainter.java  土壤、星光照土、过渡层、血肉土壤（含血管/深部/血光）
  TreePainter.java     主干、分枝、冠、叶簇、树影
  RootPainter.java     主根、侧根（三条路性格）、细根、根毛、三路分叉与节点
  SeedPainter.java     种子（橡子）、裂开的种子、幼苗
  NodesPainter.java    嵌入槽位、强化叶、三条路的节点与连线
  TreeTexture.java     纹理生命周期 + 缓存 + 生成线程 + drawView（入口）
```

**迁移方式**：`TreeCanvas` 作为`TreeTexture` 的父类先落地（字段 protected，调用点零改动），
再把各画笔逐个抽成`以 TreeCanvas 为参数`的静态工具类。**每抽一个就构建一次。**

### 2.2 附身领域的重组（用户点名要"完全实现"）

现在附身散在五处，目标结构：

| 新文件 | 职责 | 从哪来 |
|---|---|---|
| `possession/PossessionTarget.java` | 可附身判定（黑名单 / 标签 / 契合度查询） | Possession 的 isPossessable / findTarget |
| `possession/PossessionSession.java` | 一次附身的开始/维持/结束；时长与契合度；清退 | Possession 的 possess / eject / clearPossession 调用点 |
| `possession/PossessionEffects.java` | 身体特性与能力（继承/还原） | BodyTraits + PossessAbilities |
| `possession/PossessionRecall.java` | 强制召回方舟/出生点、真死 | Possession 的 forceRecall / recallCorpse / killSoul |

**关键不变量**（写进代码注释，作为验收标准）：
- **附身回自己的身体 = 和从没死过完全一样**（用户原话）。
  位置、物品、GameMode、身体特性、附身能力、`isPossessing` 残留 —— 一项都不能差。
- **附身在别人身体里 = 用不了聚晶/法杖**（§4.7）。
- **两种游离**（死亡 / 出窍）在"回自己身体"时的区别只有一项：出窍要给残破躯体（§1105）。

---

## 三、实施顺序（每步独立可构建）

| 阶段 | 内容 | 风险 |
|---|---|---|
| **1** | 清死代码（**已完成**） | — |
| **2** | 魂树界面抽 `TreeCanvas`（像素层先落地） | 低 |
| **3** | 逐个抽画笔：Terrain → Tree → Root → Seed → Nodes | 中（每步构建验证） |
| **4** | `TreeLayout` 收拢分区常量（消灭手抄的 0.36/0.46/0.60） | 低 |
| **5** | 附身领域重组为 `possession/` 四件套 | 中高 |
| **6** | `RevenantEvents` 按事件类型拆（死亡接管 / 灵魂 tick / 战斗结算 / 禁指令） | 中 |
| **7** | 事件总线归属标注（MOD / FORGE 在类注释里写明） | 低 |
| **8** | 客户端状态统一生命周期（`ClientSessionReset` 升级为唯一入口 + 自动清理） | 低 |

---

## 四、验收标准

1. **没有任何单文件超过 800 行**（当前 `TreeTexture` 2887 → 目标 ≤ 600）
2. **没有任何私有方法零调用**（用固定脚本复查，脚本要去除掉方法引用的误报）
3. **每个包有明确的领域职责**，注释里一句话说得清
4. **附身不变量**：回自己身体后与正常游玩无差别（含 `isPossessing` 必须为假）
5. **构建通过 + 进游戏实测**（编译通过不等于功能对）

---

## 五、防止再次堆积（机制）

1. `MISTAKES.md` —— 犯错记录，只增不改
2. `CHANGELOG-INTERNAL.md` —— 改动记录，写清"依据设计哪一条"
3. **替换旧实现时，同一次改动内删掉旧的** —— 不留"留着对照"的代码
4. **每次大改后跑一次零调用扫描**，误报已总结在 CHANGELOG 里
