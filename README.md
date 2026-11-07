# 水晶增殖机 (Crystal Multiplier) — Minecraft 1.20.1 Forge

参考原始 NeoForge 1.21.1 版本 `crystal_multiplier-2.1.1.jar` 的思路，用 ForgeGradle 6 从零重写的 1.20.1 移植版。

## 设计目标（来自需求）

- **配方不消耗材料**：种子槽里的水晶只作为「识别用配料」，永不消耗、只要求存在。
- **只消耗能量（FE）**：不引入任何流体，纯 FE 驱动，兼容 Mekanism 的 Forge Energy 转换。
- **Mekanism 原生升级支持**：升级槽接受 Mekanism 升级卡（`mekanism:upgrade_*`，也可通过 `crystal_multiplier:upgrades` 物品标签扩展）。
- **Mekanism 风格升级语义**：速度卡提高处理速度（缩短批次时间），能量卡降低单次操作耗能，与 Mekanism 机器一致。
- **工厂分级**：基础 / 高级 / 精英 / 终极四档，档位越高 → 能量缓存/接收更大、输出槽与升级槽更多、批次耗时更短、堆叠上限更高。
- **任意档位都只能同时增殖一种水晶**。

## 机器档位

| 档位 | 方块 ID | 速度倍率 | 能量缓存 | 输出槽 | 升级槽 | 最大输入 | 种子槽上限 | 输出槽上限 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 基础 | `crystal_multiplier:crystal_multiplier` | 1x | 200,000 FE | 3 | 4 | 20,000 FE/t | 64 | 64 |
| 高级 | `crystal_multiplier:crystal_multiplier_advanced` | 2x | 800,000 FE | 6 | 6 | 80,000 FE/t | 128 | 128 |
| 精英 | `crystal_multiplier:crystal_multiplier_elite` | 4x | 3,200,000 FE | 9 | 8 | 320,000 FE/t | 256 | 256 |
| 终极 | `crystal_multiplier:crystal_multiplier_ultimate` | 8x | 12,800,000 FE | 12 | 10 | 1,280,000 FE/t | 512 | 512 |

档位影响运行速度、能量缓存/接收上限、输出槽数与升级槽数、种子槽与输出槽的堆叠上限。
**任何档位同时只能增殖一种水晶**，因为种子槽只有一个物品栈，栈里只能是一种物品。
1.20.1 的**单个物品栈**数量用有符号 byte 保存（存档与网络包里都是如此），上限 127，
所以槽位容量按「可见栈（≤127）+ 隐藏溢出计数」实现：一个槽里放了 300 颗水晶时，物品图标上显示 127，
右下角额外标出真实数量 300，悬停显示「存量: 300 / 512」。这样 128 / 256 / 512 的容量既不丢物品也不会被原版截断
（直接把这么大的数量塞进物品栈会被截断成负数，存档、发包时都会丢东西，所以必须分流）。

## 种子槽与并行（核心机制）

- 种子槽里放进**同一种水晶**，数量必须是最小单位 `crystalsPerParallel`（默认 **16**）的**整数倍**，
  否则机器不工作，GUI 会显示红字「需 ×16 颗」。
- **并行数 = 种子数量 ÷ 16**（上限 `maxParallel`）。16 个 = 1 条并行，32 个 = 2 条……64 个 = 4 条。
- 输出与每 tick 耗能都按并行数成倍增加，**每颗水晶的 FE 成本恒定**，并行只是多开几条产线。
- 种子**永不消耗**：只有输出槽被塞满（机器自动停机等待取货）或玩家手动取出时才需要动它。
- 种子槽容量由档位决定（64 / 128 / 256 / 512），**每插一张升级卡再 +16**（`seedCapacityPerUpgrade`），
  最终被 `seedSlotCapCeiling`（默认 1024）夹住。
- 终极档一次可塞 512 颗种子 = **32 条并行**（`maxParallel` 默认上限 64，够用）。
- 超过 127 的部分走槽位的隐藏溢出计数：*Shift* 点击整组装入、漏斗与管道输入都不受 127 限制，
  界面在槽位右下角标出真实总量；取出时每次最多给出一组（≤127），对原版界面与其他模组都安全。

## 速度与能耗

- 全局基础速度倍率 `baseSpeedMultiplier` 默认 **4**：耗时 = `配方 ticks ÷ (档位倍率 × 4 × 速度卡倍率)`。
- 全局基础能耗倍率 `baseEnergyMultiplier` 默认 **0.5**：所有操作耗能直接打五折（懒人友好）。
  于是基础档一颗赛特斯石英从 20,000 FE 降到 **10,000 FE**。
- **速度只压缩 tick 数**，不改变一批次的产出数量；每 tick 耗能同倍上升，因此单次操作总耗能不变。
- 并行数只乘在产量/耗能上，速度只由「配方 ticks ÷ 速度因子」决定，两者互不串味，
  不会和任何修改输出速度的附属产生重复计算或冲突。

## 全局配置 `config/crystal_multiplier-common.toml`

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `crystalsPerParallel` | 16 | 多少颗同种水晶 = 1 条并行；配方可用 `crystals_per_parallel` 覆盖 |
| `seedCapacityPerUpgrade` | 16 | 每张升级卡给种子槽增加的容量 |
| `seedSlotCapCeiling` | 1024 | 种子槽容量硬上限（旧键 `maxSeedCapacity` 已废弃） |
| `maxParallel` | 64 | 单机并行上限 |
| `baseSpeedMultiplier` | 4.0 | 全局基础速度倍率（越高越快，耗能同比上升） |
| `baseEnergyMultiplier` | **0.5** | 全局基础能耗倍率（0.5 = 所有操作耗能减半） |
| `speedUpgradeSpeedPerCard` | **2.0** | Mek 式速度卡：每张卡的加速度倍率（2.0 = 每张卡批次时间减半） |
| `energyUpgradeEfficiencyFactor` | **2.0** | Mek 式能量卡：每张卡的省电倍率（2.0 = 每张卡耗能减半） |
| `oneTickOutput` | **false** | 全局开关：每 1 tick 完成一次输出（单次操作的总 FE 在该 tick 内一次扣完） |

## 升级卡效果（Mekanism 语义）

- `mekanism:upgrade_speed`：**提速**。每张卡把批次时间 ÷2（连乘，2 张 = 4 倍速，3 张 = 8 倍速），
  每 tick 耗能同倍上升，所以**每颗水晶的 FE 成本不变**；倍率上限 4096 倍。
- `mekanism:upgrade_energy`：**省电**。每张卡把单次操作耗能 ÷2（连乘，2 张 = 1/4 耗能），
  **完全不碰能量缓存**（能量槽只由档位决定）；倍率上限同样是 4096 倍。
- 任意升级卡：每张都给种子槽 **+16 容量**，多插卡 = 能放更多水晶 = 更多并行。
- 其它升级卡（muffling/gas/filter/anchor…）可放入，不影响水晶增殖逻辑本身。

## 配方机制

处理配方位于 `data/crystal_multiplier/recipes/crystal_multiplication/*.json`，类型 `crystal_multiplier:crystal_multiplication`：

```json
{
  "type": "crystal_multiplier:crystal_multiplication",
  "seed": { "item": "ae2:certus_quartz_crystal" },
  "result": { "item": "ae2:certus_quartz_crystal", "count": 2 },
  "energy_per_tick": 200,
  "ticks": 200,
  "crystals_per_parallel": 16
}
```

- `seed` 只做匹配，**不消耗**；要给某种水晶增产，就往种子槽塞它的**16 的倍数**颗，结果进入输出槽。
- 每完成一次操作产出 `result.count × 并行数` 个，循环产出直到输出槽填满。
- `crystals_per_parallel` 可省略，省略时使用全局配置值。
- 默认包含：赛特斯石英水晶、充能赛特斯石英水晶、福鲁伊克斯水晶（AE2）；红石水晶（AppliedFlux，模组存在时）、拉特拉水晶（ExtendedAE Plus，模组存在时）。
- 1.20.1 的 ExtendedAE（modid `expatternprovider`）**没有** `entro_crystal`，因此原版安特罗配方在 1.20.1 不存在；如需可自行添加同名配方 JSON。

## 实际数字（赛特斯石英：200 FE/t、200 ticks、每次 2 个；基础档 64 颗种子 = 4 并行）

| 升级卡 | 每批次产出 | 单次耗时 | 每 tick 耗能 | FE/颗 |
| --- | --- | --- | --- | --- |
| 无 | 8 | 2.5 s | 1,600 | 10,000 |
| 2× 速度 | 8 | 0.65 s | 6,400 | ≈10,400（取整） |
| 2× 能量 | 8 | 2.5 s | 400 | 2,500 |
| 2× 速度 + 2× 能量 | 8 | 0.65 s | 1,600 | ≈2,600（取整） |

终极档（8× 速度）同样 64 颗种子：每批次 8 个、单次 0.3 s、每 tick 12,800 FE，FE/颗约 9,600（tick 取整）。

## 界面

界面按 Mekanism 机器风格自绘：亮钢边框 + 深灰机身、凹陷式槽位、带刻线的能量槽、填充式进度箭头，
左上能量槽 12×54，中间进度箭头 26×16，右上档位名，种子槽下方显示当前并行数（种子不合格时红字提示）。

- 配色直接从 Mekanism 的 GUI 贴图采样（外框 190,190,190 / 机身 67,67,67 / 凹陷槽 139,139,139），与 Mek 机器并排没有画风差。
- 槽位存量超过 127 时右下角显示真实数量，悬停 tooltip 显示「存量: 总量 / 容量」。
- **JEI**：装了 JEI 会多出「水晶增殖」分类，显示种子（不消耗）、产物、每 tick 耗能、单次耗时；
  四档机器都是该分类的催化剂，机器界面里点击进度箭头可直接跳到对应配方。

## 构建

```bash
export JAVA_HOME=/path/to/jdk-17   # 需要 JDK 17
./gradlew build
# 产物：build/libs/crystal_multiplier-1.2.0.jar
```

本模组不 import 任何 AE2 / Mekanism 类，全部通过注册名与物品标签访问，因此 `libs/` 目录可以为空（`build.gradle` 里预留了 `compileOnly fileTree(dir: 'libs')` 以便本地调试）。

若在国内下载 Gradle 过慢，可把 `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 换成 `https://mirrors.huaweicloud.com/gradle/gradle-8.5-bin.zip`。

## 兼容性说明

- `mods.toml`：forge [47,)、minecraft [1.20.1,1.21)、ae2 [15,)、mekanism [10.4,) 为必需；expatternprovider（ExtendedAE 1.20.1 的真实 modid）、appflux、extendedae_plus、jei 为可选软依赖。
- JEI 为可选：装了就注册「水晶增殖」分类与催化剂，不装则完全不加载相关类（`compileOnly` 依赖，不进入服务端）。
- 方块模型复用 Mekanism 的 `mekanism:block/machine` 父模型（保证与 Mekanism 机器同款外形），贴图为本模组自绘，四档各有独立配色：基础=钢灰蓝、高级=翠绿、精英=紫、终极=赤红；正面水晶在运行时按 active 状态切换发光贴图。

## 更新记录

- **1.2.0**：种子槽 / 输出槽堆叠上限按档位改为 **64 / 128 / 256 / 512**（用「可见栈 + 隐藏溢出计数」实现，绕过 1.20.1 物品栈数量的 127 字节上限，界面会标出真实总量，存档与同步包都会持久化）；
  新增 **JEI 分类**（种子 / 产物 / 耗能 / 耗时，四档机器为催化剂，进度箭头可点击跳转）；贴图与 GUI 改为按 Mekanism 贴图采样生成的新版（四档独立配色，凹陷槽位逐像素对齐 Mek）；
  配置项 `maxSeedCapacity` 改名为 `seedSlotCapCeiling`（默认 1024，范围 64~4096，旧键自动失效，不会把大容量档位卡在 127）；
  所有配置项补上可翻译名称与说明，Configured 等配置界面模组可直接显示中英文。

- **1.1.0**：界面与贴图改为 Mekanism 风格（自绘贴图：亮钢边框、深灰机身、凹陷槽位、带刻线能量槽、填充进度箭头）；速度/能量升级卡改为 Mekanism 语义（速度卡提速不增产、能量卡省电不动能量槽，均为连乘且可配置）；能量缓存整体降到原来的 1/10；种子槽与输出槽的堆叠上限按档位提升（64/80/96/112），种子槽容量由档位决定；新增 `speedUpgradeSpeedPerCard`、`energyUpgradeEfficiencyFactor` 配置项（旧的 `speedUpgradeOutputPerCard`、`energyUpgradeEfficiencyPerCard`、`baseSeedCapacity` 已移除）；修复 `crystals_per_parallel` 配成 0 时的除零风险。
- **1.0.2**：修复能量/容量/单 tick 耗能/每批数量在客户端显示错误（这些数值超过 32767，菜单数据槽只能传 short，改为走方块实体同步包）；修复精英/终极档第三行输出槽与升级槽重叠（界面高度 186 → 198）；修复标题与档位名重叠（档位名改为右对齐）；修复状态文字压在能量条上；进度条 tooltip 重新显示每批产量；破坏机器时掉落槽内物品（原先物品和能量直接消失）；输出槽改为按物品自身堆叠上限工作（不可堆叠物品不再被算到 64）；`mods.toml` 的可选依赖 `extendedae` 改为 1.20.1 的真实 modid `expatternprovider`，并补充 `appflux`、`extendedae_plus`。
- **1.0.1**：修复右键机器打不开界面的问题（打开 GUI 时没有把机器坐标写进网络包，客户端解析失败）；四档机器改为各自配色的独立贴图。
- **1.0.0**：首个版本，四档工厂 + 种子并行增殖 + Mekanism 升级卡 + 全局配置。
