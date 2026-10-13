# 内部改动记录

> 规则：**只增不改**。每次改动写清：改了什么、为什么改、依据是设计哪一条。
> 与 `git log` 的区别：git 记"代码变了什么"，这里记"**为什么**变"，以及设计依据。

---

## 2026-10-13 审查与修复

**背景**：外部代码审查（D:\goety-revenant-client-review.md）指出 5 个问题；用户要求
按设计与历史对话做完全审查并翻新底层架构。

### 已修

| # | 改动 | 依据 | 原因 |
|---|---|---|---|
| 1 | `TreeTexture` 后台生成的**竞态**：加请求号 + AtomicReference 交接 + 被顶掉的图像显式 close | 外部审查 #2 | 旧任务能覆盖新任务；被覆盖的 NativeImage 没人关（原生内存泄漏） |
| 2 | 生成线程**同时只跑一个**，中间请求合并 | 外部审查 #3 | 拖窗口时会连开多个线程；把运算搬后台只解决主线程响应，没解决总量 |
| 3 | 种子/裂开种子两层**用完释放**（`release()`：关图+注销纹理+清尺寸） | 外部审查 #3 | 只在未觉醒和生长动画那几秒需要，之后白占两份全尺寸图 |
| 4 | 客户端**会话状态清理**（`ClientSessionReset`，挂 LoggingOut） | 外部审查 #4 | 六个类的静态字段跨世界残留；`BodyClientState.seenStage` 会让新存档的生长动画被误判成"已看过" |
| 5 | 附身**代理实体缓存**（`PossessedProxyCache`）+ 补 `tickCount`/`walkAnimation.update`/上一帧旋转 | 外部审查 #5 | 原来每帧新建实体、重读 NBT、重搬装备；动画状态也没维护 |
| 6 | 玩家渲染接入改为 **`RenderPlayerEvent.Pre` 接管** | 外部审查 #1 | `registerEntityRenderer(EntityType.PLAYER,…)` 分发不到皮肤映射，等于从没生效 |
| 7 | `onSoulHurt` 去掉 `|| isPossessing` | **设计 §7.7.1 需求 4** | 灵体状态那条血就是灵魂能量；附身中受伤也该扣能量 |
| 8 | `possess()` 的 corpse 分支补 `teleportTo` | 设计意图（回自己身体＝回到身体里） | 活着的东西那条有 teleport，corpse 这条没有 → 复活后停在灵魂那一格 |
| 9 | `endSoul()` 补 `clearPossession` / 清 soulMode / ticks / `PossessAbilities.clear` | **设计 §4.7 + 用户"回自己身体要像正常游玩"** | `isPossessing` 残留为真 → `EmbeddedCasting` 拒绝施法 → 回自己身体后**【永远用不了聚晶/法杖】** |
| 10 | `possess()` 的 corpse(!own) 分支标记 possession + 契合度 0.95 | **设计 §4.2 / §4.7** | 附身别人尸体反而能用聚晶，和 §4.7 正相反 |

### 文档更正（代码本来就对）

| 节 | 文档写的 | 实际 |
|---|---|---|
| §7.7 | 整节"未实现" | 自研灵魂状态 / 感知分类 / 仇恨保留 / 灵魂 HUD / EL 兼容 **全部已做** |
| §6 | 灵魂能量"独立池 10000" | 就是本体那一份（SEHelper）—— §9.7 已推翻该条 |
| §6 | "每 12 tick 扣 5 常驻抽干" | 已删除（§1215：改成每秒 +20 自主恢复） |
| §8.1 | 通道 "id 0~10" | 实际 23 个 |
| §5.6 | 六瓶魔药"物品已做好" | **用户要求删除**，未注册是对的 |

### 待办（本次审查未完成部分）

- §9 剩余节逐条核对（9.11 / 9.10 / 9.9 / 9.6 / 9.5 / 9.4）
- §5 剩余（槽位 / 开窍流程 / 被动四级查找 / 界面）
- §7 联动接口复核（Goety / Malum / Carry On）
- **架构翻新**：`TreeTexture.java` 3699 行拆分（现正进行）
- **死代码清除**：`TreeTexture` 里 5 个 `*Old` 方法 + `USE_OLD_TREE` 分支

### 死代码清除（2026-10-13）

`TreeTexture.java` 里堆着整套被替换掉的旧实现，全部删除：

| 删除项 | 行数 | 说明 |
|---|---|---|
| `seedEllipseOld` / `seedOld` | 117 | 旧种子画法（已被橡子形态取代） |
| `blendZoneOld` | 80 | 旧过渡层画法 |
| `soakedSoilOld` | 94 | 旧血肉土壤画法 |
| `fleshLayerOld` | 64 | 旧血肉层 |
| `USE_OLD_TREE` 开关 + 旧针叶树分支 | 83 | 整块旧树逻辑（沿主干左右交替挂枝） |
| `trunk` / `ring` | 96 | 只有旧树在调，删完分支后 0 调用 |

**结果**：`TreeTexture.java` 3855 → **3328 行**。构建通过，jar 1,442,502 字节。

### 架构拆分（进行中）

`TreeTexture.java` 3328 行仍然是一个文件干所有事。目标结构：

```
client/screen/tree/
  TreeTexture.java     纹理管理 / 缓存 / 视口变换 / 绘制入口
  TreeCanvas.java      像素缓冲与绘制原语（put/get/blend/dot/disc/mix/abgr）
  TreeLayout.java      内容图分区与坐标计算
  TerrainPainter.java  土壤 / 过渡 / 血肉土壤
  TreePainter.java     树骨架 / 枝 / 冠 / 影
  RootPainter.java     主根 / 侧根 / 三条路 / 细根
  SeedPainter.java     种子 / 裂开的种子 / 幼苗
  TreeNodes.java       槽位 / 叶子 / 三条路节点
```


### 死代码清除 · 第二轮（2026-10-13）

第一轮之后又扫出 12 个零调用方法，全部核实后删除：

| 文件 | 删除 | 行数 |
|---|---|---|
| TreeTexture | `blendZonePlainColor` / `blendZoneOld2` / `muscleZone` / `fleshShallow` / `routeRoot` / `legacyRoots` | 447 |
| AwakenOverlay | `withAlpha` | 5 |
| BodyScreen | `renderVignette` / `renderBody` / `stitch` | 49 |
| RevelationHalo | `item` | 10 |

**合计删除 1032 行**（两轮）。`TreeTexture.java` **3855 → 2887 行**。构建通过。

**扫描方法**：全项目 165 个文件，找"零调用的 private 方法"。
**踩过的坑**：正则需要 `(?<![A-Za-z0-9_$])name\s*\(` —— 只排除标识符字符、**不能排除句点**，
否则 `this.soil(` 会被当成没调用（第二版修正的正是这个）。
另外必须把**方法引用**（`ClassName::method`）算进调用计数，否则指令回调全被误判成死代码。
