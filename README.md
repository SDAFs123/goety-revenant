# 诡厄巫法：回魂 / Goety: Revenant

诡厄巫法（Goety - The Dark Arts）的**整合包特供**附属。作者：habaiwa。

- Minecraft **1.20.1** / Forge **47.3.0**
- modId `goety_revenant`｜包名 `com.habaiwa.goetyrevenant`
- 模型/动画全部使用 **GeckoLib**

**完整设计与实现说明见 [PLAN.md](PLAN.md)。**

## 玩法

玩家不会真的死：死亡时身体留在原地（保留皮肤与装备），灵魂被弹出来。

- **20 分钟**内找一具身体附身：自己的身体 / 其他玩家的尸体 / 存活亡灵 / 存活村民
- 灵魂状态：穿墙、别人看不见、**禁止指令**
- 附身自己的身体 = 复活并拿回全部物品
- 附身别的身体 = 继承对方物品，但有**契合度**：契合度越低能待的时间越短，到点被弹回灵魂
- 附身在亡灵身体里：**白天没头盔会燃烧**
- 20 分钟没找到 → 强制召回**灵魂方舟**（Goety 本体方块）并恢复灵魂能量
- **回魂聚晶**：插在回魂法杖上，消耗 **10000 灵魂能量**，把队友的尸体召到身边（多人模式）

## 依赖

| 依赖 | 坐标 |
|---|---|
| Goety 本体 | `maven.modrinth:goety:2.5.58.4` |
| **GeckoLib** | `software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4` |
| Curios（硬前置） | `top.theillusivec4.curios:curios-forge:5.14.1+1.20.1` |
| Patchouli（硬前置） | `vazkii.patchouli:Patchouli:1.20.1-84-FORGE` |
| JEI | `mezz.jei:jei-1.20.1-forge:15.62.0.216`（仅 API） |

## 构建

```powershell
$env:JAVA_HOME = "C:\Users\Administrator\.gradle\jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.19+10"
$env:HTTP_PROXY=""; $env:HTTPS_PROXY=""; $env:ALL_PROXY=""   # 本机代理已停用，必须清掉
.\gradlew.bat build
```

产物：`build/libs/goety_revenant-0.1.0.jar`（reobf 后可直接进 `mods/`）。

> 本机环境三个坑（代理停用 / Gradle 官方站不可达 / DSH 沙箱禁止 Gradle fork 子进程）
> 见 [PLAN.md](PLAN.md) §7；同一节还写了不依赖权限的 javac 直编验证方式。

## 资源状态

- 语言文件 `zh_cn` / `en_us` 已齐。
- `textures/entity/revenant.png` 是**程序生成的占位贴图**，需要换成正式美术。
- 物品贴图/模型、配方、成就尚未添加（物品在游戏内会显示紫黑格）。
