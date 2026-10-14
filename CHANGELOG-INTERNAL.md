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

### ⚠️ 失败记录：一次性搬移地层绘制（2026-10-13）

**做法**：写脚本把 9 个地层方法 + 13 个常量一次性搬到新类 `TerrainPainter`，
用正则把 canvas 原语加 `c.` 前缀。

**结果**：**编译失败**（14 处"找不到符号"）。原因：

1. `build()` 里的调用点写法和我预期的正则不一致 → **替换没生效**（脚本报告里那行
   "调用点已替换: 否" 已经是警示，我当时没有停下来）。
2. 被搬走的方法内部还有**对其它未搬走成员**的引用，脚本的前缀替换只覆盖了白名单里的原语。
3. 常量搬走后，**别处还在用它们**（树/根/种子也引用了 STARLIGHT 等）。

**已回滚**（`git checkout` 恢复 `TreeTexture.java`，删掉 `TerrainPainter.java`）。

**正确做法**（下次按这个来）：
- **一次只搬一个方法**，立刻构建验证，绿了再搬下一个。
- 搬之前先确认"这个方法引用的每个符号，搬完之后在哪" —— 列清单，不要靠替换规则推断。
- 脚本报告里的异常（如"替换未生效"）**必须当场处理**，不能继续往下做。


### 架构拆分 · 起步（2026-10-13）

**新增 `client/tree/` 包**，魂树界面开始按职责拆分：

| 文件 | 职责 | 结果 |
|---|---|---|
| `TreeCanvas.java` | 像素缓冲 + 绘制原语（put/get/blendDot/disc/mix/abgr/ink）+ 内存直写 | `TreeTexture` 变私有→protected，**调用点零改动** |
| `TreeLayout.java` | 内容图分区常量（CONTENT_W/H、宇宙/星空/夜空/土壤/过渡/血肉 六段） | 消灭了"同一比例在 TreeTexture / TreeScreen / SoulBackdrop 三处手抄" |

**`TreeTexture.java`：3855 → 2796 行**（死代码 1032 + 抽取约 160 + 过时注释 77）。

**顺带清理**：三段互相矛盾的过时注释块（历史遗留的"各层比例"说明，
内容已被 `TreeLayout` 的类注释取代）—— 这种堆积注释会和代码打架，必须删。

**⚠️ 这次踩的坑**：脚本"向上吃掉注释"的逻辑把常量上面的 javadoc 切断了，
留下裸注释文字导致编译失败。**教训**：删常量时注释块要按"整块匹配"删，
不能按"向上吃注释行"删。


### 架构拆分 · 第一批画笔（2026-10-13）

`client/tree/` 现在有四个文件：

| 文件 | 职责 | 行数 |
|---|---|---|
| `TreeCanvas` | 像素缓冲 + 绘制原语 + 内存直写 + **共用状态（rng）与工具（shift / capillary / 共用配色）** | ~330 |
| `TreeLayout` | 内容图分区常量 | ~55 |
| `TerrainPainter` | 土壤 / 过渡层 / 血肉土壤 | ~690 |
| `SeedPainter` | 种子（橡子）/ 裂开的种子 / 幼苗 | ~350 |

**`TreeTexture.java`：3855 → 约 1600 行。**

**这次踩的坑（都记在这里，避免再犯）**：
1. **脚本"删常量 + 另处插入"两步法会丢内容** —— 这次三个配色（STARLIGHT/MOSS/MOSS_LIT）
   被删掉后没插进去，编译才发现。**教训**：删之前先把内容取出来并**验证取到了**再删。
2. **共用的东西要一次上提到共同父类** —— `capillary` 被"根"和"地层"同时用，
   `shift` 被两者共用，配色被三层共用。**先查"还有谁用"，再决定放哪一层**，
   不然会出现"搬到 A 之后 B 编译不过"的来回折腾。
3. **painter 不是画布的子类**，访问父类成员要写全限定名（`TreeCanvas.XXX`）。
4. **回滚会丢掉未提交的改动** —— `extends TreeCanvas` 就是因为回滚时还没提交而消失的。
   **教训**：重要改动先提交，再做下一步。


### 架构拆分 · 第二批（2026-10-13）

**共用物进一步上提到 `TreeCanvas`**（拆画笔的必要前提 —— 不然每搬一块都要修一圈引用）：

| 上提的 | 之前散在 | 为什么 |
|---|---|---|
| `stage` / `grow()` | TreeTexture | 树、根、种子都要读阶数 |
| `bark` / `BARK` / `BARK_LIGHT` | TreeTexture | 树和根都要树皮色 |
| `limbShaded()` | TreeTexture | 画"一条带阴影的枝干"，根也是枝 |
| `LIGHT_X` / `LIGHT_Y` | TreeTexture | 受光面算法共用 |
| `MEAT_*` / `WET` | TreeTexture | 根在血肉层里要这些色 |
| `ss` / `SS_MAX` | TreeTexture | 超采样倍率，所有画笔都要 |
| **`slots` / `leaves` / `wayNodes` / `wayAlive`** | TreeTexture | **绘制产物** —— 画的时候填、界面之后读。放在画布层，各画笔才能往同一个容器写 |

**新增 `RootPainter`**（369 行）：主根 / 侧根（三条路性格）/ 细根 / 根毛 / 三路分叉与节点。

**`TreeTexture.java`：3855 → 约 1100 行**（累计 −71%）。

**⚠️ 这次踩的坑（重要）**：
**PowerShell 的 `-replace` 里 `$1` 会在双引号中被解析成空** ——
`"private ... int (A|B)", "protected ... int $1"` 的结果是**变量名整个消失**，
只剩 `protected static final int  = 0xFF4A1418;`。
**教训**：批量改代码用 `edit` 工具或 Node 脚本，别用 PowerShell 的 `-replace` 做事后替换；
非要用就写单引号 `'...$1'`。


### ⚠️ 失败记录二：多行数组的边界判断（2026-10-13）

**做法**：脚本把 `LEAF`（多行 int 数组常量）、`leaf`、`choices`、`rampCount` 从
`TreeTexture` 搬到 `TreeCanvas`。

**结果**：`TreeTexture` 从第 81 行起大面积语法错误。原因：判断"常量块结束在哪"的
条件是 `!该行含 ';' && !该行含 '{'` —— 而 `LEAF = {` 这行**含 `{`**，
于是循环不推进，**只删掉了第一行**，数组剩下的元素全留在文件里。

**已回滚**（`git checkout` 恢复两个文件）。

**教训（和前两次是同一类）**：
- **多行字面量（数组/对象）的边界必须按括号配平去数**，不能只看分号。
- **搬移脚本每加一条规则就多一个出错面**。剩下这些搬移**改用 `edit` 工具逐块做**——
  工具会告诉我"文本没匹配到"，而脚本只会安静地做错。
- **每步构建 + 及时提交** 这两条救了我三次：这三次失败都只丢了"当前一步"，没有波及已完成的。


### ⚠️ 失败记录三：脚本搬移的"定位"不可靠（2026-10-13）

**做法**：第三次用脚本把树相关的 11 个方法搬到 `TreePainter`。
提取方法文本后用 `text.indexOf(方法文本)` 定位再删除。

**结果**：`TreeTexture` 被写成 **241 万行**（文件被破坏）。
原因：`indexOf` 找到的是**内容相同的另一处**（或子串），删除位置错位，
拼接后产生大量重复。

**已回滚**。

**结论（三次失败的共同点）**：
- 一、脚本正则匹配不到实际的写法（调用点被计时调用分隔）
- 二、多行字面量的边界判断错（只看分号不看括号）
- 三、**文本定位不可靠**（`indexOf` 命中了别处）

**所以：方法搬移一律改用 `edit` 工具逐块做。**
`edit` 要求 `old_string` **唯一出现**，不唯一就报错 —— 这正好堵住了上面三类问题。
脚本只用于"统计、扫描、验证"这类**只读**用途。


### 附身机制逐条核对（2026-10-13，目标 ④）

按设计 §4 的 12 条逐条对代码：

| 设计条款 | 实现位置 | 状态 |
|---|---|---|
| §4.1 灵魂穿墙 | `Possession.enterSoulForm` `noPhysics` | ✓ |
| §4.1 灵魂隐身 | 同上 `setInvisible` | ✓ |
| §4.1 灵魂禁指令 | 事件拦截 8 处 | ✓ |
| §4.2 可附身目标（标签） | `PossessProfile` + `possessable` 标签 | ✓ |
| §4.3 契合度表 | `Attunement` | ✓ |
| §4.3 时长 = 20min × 契合度 | `Possession` `soulDurationTicks() * attunement` | ✓ |
| §4.4 亡灵白天燃烧 | `RevenantEvents:211` `Traits.undeadDaylight` | ✓ |
| §4.4 亡灵能量恢复 25% | 同上 `Traits.limitUndeadSoulRegen` | ✓ |
| §4.5 自身契合度惩罚 | `SoulData.penalizeSelfAttunement` | ✓ |
| §4.6 图腾/方舟优先 | `RevenantEvents` `isCanceled` + `LOWEST` | ✓ |
| §4.7 附身在别人身体里用不了聚晶 | `EmbeddedCasting:43` `isPossessing` | ✓ |
| §4.7 灵魂状态禁止指令 | 事件拦截 | ✓ |

**12/12 都有实现。**

**⚠️ 核对方法的教训**：第一次搜 `BURN|burn` 得出"§4.4 没实现"是**误判** ——
代码里叫 `undeadDaylight` / `limitUndeadSoulRegen`。
**核对要按语义找，不能按自己猜的英文词找。**

**另外记一条文档矛盾**：§4.5 写着「< 100% 时回到自己身体 → 只能待 20分钟×契合度，
到点被挤出、重新留下一具尸体」，但代码里是 **永远彻底复活**（不设限、不会被弹出）。
**以用户 2026-10-13 的原话为准**：**"我回自己的身体还不能做到和正常游玩一样吗"** ——
限时弹出与"正常游玩"冲突，所以代码是对的，§4.5 那条已作废。


### 真实缺陷：RevenantEvents 双重注册（2026-10-13）

**发现方式**：拆事件类之前先确认注册方式，结果看到同一个类既有
`@Mod.EventBusSubscriber(modid = ...)`（注解注册）**又**在 `GoetyRevenant` 里
`MinecraftForge.EVENT_BUS.register(RevenantEvents.class)`（手动注册）。

**后果**（每个处理器被调用两次）：
- `onPlayerTick` → `tickPlayer` 跑两遍 → **附身倒计时以双倍速度消耗**、
  附身能力维持逻辑走两遍
- `onSoulHurt` → **受伤扣双倍灵魂能量**
- 死亡接管那一套也会走两遍

**已修**：删掉手动注册，保留注解。

**⚠️ 同一轮里的误判（记下来）**：
我写了个脚本扫"注解 + 手动注册"的类，它把 `FocusTooltip` 也报了出来 ——
但那个类**没有活注解**，它里面只有一行**注释**提到了 `@Mod.EventBusSubscriber`。
我按报告把它的手动注册删了，然后发现不对、恢复。

**教训**：**静态检查要排除注释行**。`//` 和 `*` 开头的行不能算证据。
（和之前"搜不到关键词就断定没实现"是同一类错误：**把工具的粗糙输出当成事实**。）


### 事件层拆分（2026-10-13，目标 ② 收尾）

`RevenantEvents` **456 → 85 行**：

| 新文件 | 职责 | 行数 |
|---|---|---|
| `SoulDeathHandler` | 死亡接管（§4.6 优先级：图腾/方舟优先于回魂） | 113 |
| `SoulTickHandler` | 灵魂与附身状态的每 tick 维持（含附身倒计时） | 175 |
| `SoulCombatHandler` | 灵魂受伤 → 扣灵魂能量 | 73 |
| `SoulInteractionGuard` | 禁指令 / 禁右键 / 指令注册 | 79 |
| `SoulTraits`（从私有嵌套类独立） | 能量低虚弱 / 亡灵白天燃烧 / 能量恢复 25% | 75 |

**累计成果**（本轮目标全部相关）：
```
TreeTexture     3855 → 841 行  (−78%)
Possession       502 → 220 行  (−56%)
RevenantEvents   456 →  85 行  (−81%)
死代码清除                −1032 行
新增职责单一的文件          14 个
真实缺陷修复                5 个
```


### ⚠️ 「改等于没改」：isSoul 在附身时是 false（2026-10-13）

**上一轮我改的**：把 `onSoulHurt` 的条件从 `if (!isSoul || isPossessing) return;`
改成 `if (!isSoul) return;`，并在 CHANGELOG 里记成"修好了：附身中受伤改为扣灵魂能量"。

**实际没改**：`possess()` 的流程里会调 `endSoul()`，而它执行 `setSoul(player, false)` ——
**附身时 isSoul 本来就是 false**，所以新条件照样直接放行，照样扣血。
（用户实测后问"为什么扣血"，才发现。）

**正确的条件**：`if (!isSoul && !isPossessing) return;` ——
灵魂状态**或**附身状态都算"灵体"，挨打都扣灵魂能量。

**教训**：
- **改条件之前，先把每个条件的取值在"所有相关状态下"列一遍**（灵魂 / 附身 / 活人），
  而不是只看它字面上写了什么。
- "删掉一个条件"不等于"改成我想要的那个条件"。
- **记进 CHANGELOG 的每一句"已修"，都要能被实测验证**，否则就是自我安慰。

### 同类修复：HUD 在灵魂状态下不显示（2026-10-13）

`SoulHudOverlay.render` 的守卫是 `if (!BodyClientState.unlocked()) return;` ——
**没跳井解锁的玩家变成灵魂后看不到能量条**，而那条能量恰恰是灵魂状态唯一的血条。

**已修**：灵魂状态下无论如何都画（解锁后再扩展到"活人也画"）。

**这个缺口也已修**：客户端拿不到 `isPossessing`（`SoulSyncMessage` 没带那个字段），
但带了 `possessTicks`（服务端在附身开始时设成时长、每 tick 减一）。
所以 HUD 用 `ClientSoulState.possessTicks() > 0` 判"正在附身" —— **不用改协议**。

**现在能量条会在三种情况下都画**：
① 灵魂状态（唯一的那条血）② 正在附身（挨打扣的就是它）③ 已解锁的活人（要随灵魂温度变色）。
指针只在①画 —— 附身时你已经找到身体了。


### 灵灾 × 诡厄巫法：镰刀附魔互通（2026-10-14）

**用户需求**（2026-09-27 首次提出，2026-10-14 重申）：
「我需要让两个模组的联动附魔可以做到互通，**两方的镰刀都能附魔对面的镰刀独特附魔**」

#### 反编译查证的结论（关键）

**Malum 的四个自定义附魔类别全是【标签判定】，不是类判定**：

```java
// com/sammy/malum/registry/common/item/EnchantmentRegistry
SCYTHE_ONLY                 = create("scythe_only",   i -> i.is(ItemTagRegistry.SCYTHE))              // #malum:scythe
STAFF_ONLY                  = create("staff_only",    i -> i.is(ItemTagRegistry.STAFF))               // #malum:staff
SOUL_SHATTER_CAPABLE_WEAPON = create(..., i -> i.is(ItemTagRegistry.SOUL_HUNTER_WEAPON))              // #malum:soul_hunter_weapon
MAGIC_CAPABLE_WEAPON        = create(..., i -> i.is(ItemTagRegistry.MAGIC_CAPABLE_WEAPON))
```

**→ 所以 Malum 侧一个数据包就能打通，不需要 Mixin。**

**Goety 侧不同**：`SoulEaterEnchantment` 用的是**原版 `EnchantmentCategory.WEAPON`**
（判定 `item instanceof WeaponItem`）—— **标签对它无效**，所以反向要靠铁砧。

#### 已做

**① 数据包（4 个标签文件）** —— 让 Goety 镰刀获得 Malum 的"镰刀身份"：

| 文件 | 作用 |
|---|---|
| `data/malum/tags/items/scythe.json` | 缚灵 / 舞刃 / 飞腾 / 回旋 → 能上我们的镰刀 |
| `data/malum/tags/items/soul_hunter_weapon.json` | 精魂收割类附魔 |
| `data/malum/tags/items/magic_capable_weapon.json` | 魔法系附魔 |
| `data/forge/tags/items/tools/swords.json` | 把 Malum 镰刀标成"剑"（尽力而为） |

七把镰刀全部收录：`goety:dark_scythe` / `dark_metal_scythe` / `death_scythe` ·
`malum:crude_scythe` / `soul_stained_steel_scythe` / `creative_scythe` ·
`born_in_chaos_v1:nightmare_scythe`（都带 `required: false`，缺席不报错）

**② 铁砧放开**（`ScytheCompat.onAnvil`）：

- **删掉 `!isNativeScythe(left)` 限制** —— 原来两边的原生镰刀被排除在铁砧互附之外，那正是用户要的互通
- **新增 `isCrossModEnchantment`**：命名空间是 `malum` 或 `goety` 的附魔一律放行，
  **唯独排除 `malum:spirit_plunder`（精魂劫掠）** —— 用户 2026-09-27 明确要求
  「灵灾的附魔这个附魔**不允许附魔出来**」，它的效果由我们的"+20% 灵魂能量获取"接管

#### 双向现状

| 方向 | 附魔台 | 铁砧 |
|---|---|---|
| **Malum 附魔 → Goety 镰刀** | ✅ 数据包已打通 | ✅ |
| **Goety 附魔 → Malum 镰刀** | ⚠️ 取决于 Malum 镰刀是否 `WeaponItem`（反编译未取得该类，待实测） | ✅ 已打通 |


### 镰刀附魔互通：自检数据 + 真正的原因（2026-10-14）

**用户跑了一次游戏，日志里两段自检给出了决定性数据**：

```
===== 标签自检 =====
  #malum:scythe                共 8 项  ｜ [在] goety:dark_scythe [在] goety:dark_metal_scythe …
  #malum:soul_hunter_weapon    共 28 项 ｜ [在] goety:dark_scythe …
  #malum:magic_capable_weapon  共 11 项 ｜ [在] goety:dark_scythe …
  #goety_revenant:scythes      共 8 项  ｜ [在] goety:dark_scythe …

===== Mixin 自检 =====
  malum/animated  → goety/dark_scythe  : canEnchant=false  附魔台=false
  malum/rebound   → goety/dark_scythe  : canEnchant=false  附魔台=false
  malum/ascension → malum/crude_scythe : canEnchant=true   附魔台=true    ← 对照组
```

**结论**：

1. **标签完全正常** —— `#malum:scythe` 里确实有 `goety:dark_scythe`。
   **我此前"跨命名空间追加标签没生效"的判断是错的。**
2. **对照组正常** —— Malum 附魔 → Malum 镰刀 = true，附魔本身没有任何问题。
3. **我们的镰刀 false** —— 如果 Mixin 生效了，它判 `#goety_revenant:scythes`
   （日志证明那里也有 `goety:dark_scythe`），**必然返回 true**。所以 **Mixin 根本没注入**。
4. **日志里没有任何一行提到 `goety_revenant.mixins.json`** ——
   而别的模组的 mixin 配置都有加载记录（连"缺 minVersion"的 ERROR 都有）。
   **我们的配置连"被读取"都没发生。**

**修法**：改用 Mixin 自己的标准入口 —— `jar` 任务的 manifest 里写 `MixinConfigs`：

```gradle
manifest {
    attributes([
        'MixinConfigs': 'goety_revenant.mixins.json',
        …
    ])
}
```

Forge 的 `mods.toml` `[[mixins]]` 保留着当双保险，不再依赖它。
