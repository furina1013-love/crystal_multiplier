# 水晶增殖机 (Crystal Multiplier) — Minecraft 1.20.1 Forge

参考原始 NeoForge 1.21.1 版本 `crystal_multiplier-2.1.1.jar` 的思路，用 ForgeGradle 6 从零重写的 1.20.1 移植版。

## 设计目标（来自需求）

- **配方不消耗材料**：种子槽里的水晶只作为"识别用配料"，永不消耗、只要求存在。
- **只消耗能量（FE）**：不引入任何流体，纯 FE 驱动，兼容 Mekanism 的 Forge Energy 转换。
- **Mekanism 原生升级支持**：升级槽接受 Mekanism 升级卡（`mekanism:upgrade_*`，也可通过 `crystal_multiplier:upgrades` 物品标签扩展）。
- **工厂分级**：基础 / 高级 / 精英 / 终极四档，档位越高 → 能量缓存/接收更大、输出槽与升级槽更多、批次耗时更短。
- **任意档位都只能同时增殖一种水晶**。

## 机器档位

| 档位 | 方块 ID | 速度倍率 | 能量缓存 | 输出槽 | 升级槽 | 最大输入 |
| --- | --- | --- | --- | --- | --- | --- |
| 基础 | `crystal_multiplier:crystal_multiplier` | 1x | 2,000,000 FE | 3 | 4 | 20,000 FE/t |
| 高级 | `crystal_multiplier:crystal_multiplier_advanced` | 2x | 8,000,000 FE | 6 | 6 | 80,000 FE/t |
| 精英 | `crystal_multiplier:crystal_multiplier_elite` | 4x | 32,000,000 FE | 9 | 8 | 320,000 FE/t |
| 终极 | `crystal_multiplier:crystal_multiplier_ultimate` | 8x | 128,000,000 FE | 12 | 10 | 1,280,000 FE/t |

档位只影响运行速度、能量缓存/接收上限、输出槽数与升级槽数。**任何档位同时只能增殖一种水晶**，
因为种子槽只有一个物品栈，栈里只能是一种物品。

## 种子槽与并行（核心机制）

- 种子槽里放进**同一种水晶**，数量必须是最小单位 `crystalsPerParallel`（默认 **16**）的**整数倍**，
  否则机器不工作，GUI 会显示红字「需要 16 的倍数个水晶」。
- **并行数 = 种子数量 ÷ 16**（上限 `maxParallel`）。16 个 = 1 条并行，32 个 = 2 条……64 个 = 4 条。
- 输出与每 tick 耗能都按并行数成倍增加，**每颗水晶的 FE 成本恒定**，并行只是多开几条产线。
- 种子**永不消耗**：只有输出槽被塞满（机器自动停机等待取货）或玩家手动取出时才需要动它。
- 种子槽容量默认 64（= 4 条并行）；**每插一张升级卡 +16**（`seedCapacityPerUpgrade`），
  上限 `maxSeedCapacity`（默认 64，可配到 127 —— 1.20.1 物品栈数量是 byte，不能再高）。

## 速度与能耗

- 全局基础速度倍率 `baseSpeedMultiplier` 默认 **4**：耗时 = `配方 ticks ÷ (档位倍率 × 4)`。
- 全局基础能耗倍率 `baseEnergyMultiplier` 默认 **0.5**：所有操作耗能直接打五折（懒人友好）。
  于是基础档一颗赛特斯石英从 20,000 FE 降到 **10,000 FE**。
- 档位速度倍率只影响 tick 数；速度升级卡**不再加速**，而是让一批次多出物品（见下）。
- 并行数只乘在产量/耗能上，速度只由「配方 ticks ÷ 速度因子」决定，两者互不串味，
  不会和任何修改输出速度的附属产生重复计算或冲突。

## 全局配置 `config/crystal_multiplier-common.toml`

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `crystalsPerParallel` | 16 | 多少颗同种水晶 = 1 条并行；配方可用 `crystals_per_parallel` 覆盖 |
| `baseSeedCapacity` | 64 | 无升级卡时种子槽容量 |
| `seedCapacityPerUpgrade` | 16 | 每张升级卡增加的种子槽容量 |
| `maxSeedCapacity` | 64 | 种子槽容量硬上限（1.20.1 栈数量为 byte，最多 127） |
| `maxParallel` | 64 | 单机并行上限 |
| `baseSpeedMultiplier` | 4.0 | 全局基础速度倍率（越高越快） |
| `baseEnergyMultiplier` | **0.5** | 全局基础能耗倍率（0.5 = 所有操作耗能减半） |
| `speedUpgradeOutputPerCard` | 0.5 | 每张速度升级卡额外增加的「每批次产出比例」 |
| `energyUpgradeEfficiencyPerCard` | 0.5 | 每张能量升级卡提升的能量利用率比例 |
| `oneTickOutput` | **false** | 全局开关：每 1 tick 完成一次输出（单次操作的总 FE 在该 tick 内一次扣完） |

## 升级卡效果

- `mekanism:upgrade_speed`：**不再加速**。每张让「一批次产出」+50%（2 张 = 2 倍，4 张 = 3 倍），
  批次耗能同倍增加，所以**每颗水晶的 FE 成本不变**，只是每次结算给得更多。
- `mekanism:upgrade_energy`：**不动任何能量缓存/能量槽**，只降耗能：能量利用率 = (1 + 0.5n)，
  也就是耗能 ÷1.5、÷2、÷2.5……
- 任意升级卡：每张都给种子槽 **+16 容量**，所以多插卡 = 能放更多水晶 = 更多并行。
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
- 每完成一次操作产出 `result.count × 并行数 × (1 + 0.5×速度卡数)` 个，循环产出直到输出槽填满。
- `crystals_per_parallel` 可省略，省略时使用全局配置值。
- 默认包含：赛特斯石英水晶、充能赛特斯石英水晶、福鲁伊克斯水晶（AE2）；红石水晶（AppliedFlux，模组存在时）、拉特拉水晶（ExtendedAE Plus，模组存在时）。
- 1.20.1 的 ExtendedAE（modid `expatternprovider`）**没有** `entro_crystal`，因此原版安特罗配方在 1.20.1 不存在；如需可自行添加同名配方 JSON。

## 实际数字（赛特斯石英：200 FE/t、200 ticks、每次 2 个；基础档 64 颗种子 = 4 并行）

| 升级卡 | 每批次产出 | 单次耗时 | 每 tick 耗能 | FE/颗 |
| --- | --- | --- | --- | --- |
| 无 | 8 | 2.5 s | 1,600 | 10,000 |
| 2× 速度 | 16 | 2.5 s | 3,200 | 10,000 |
| 2× 速度 + 2× 能量 | 16 | 2.5 s | 1,600 | 5,000 |

终极档（8× 速度）同样 64 颗种子：每批次 8 个、单次 0.3 s、每 tick 12,800 FE，FE/颗约 9,600（tick 取整）。

## 构建

```bash
export JAVA_HOME=/path/to/jdk-17   # 需要 JDK 17
./gradlew build
# 产物：build/libs/crystal_multiplier-1.0.0.jar
```

本模组不 import 任何 AE2 / Mekanism 类，全部通过注册名与物品标签访问，因此 `libs/` 目录可以为空（`build.gradle` 里预留了 `compileOnly fileTree(dir: 'libs')` 以便本地调试）。

若在国内下载 Gradle 过慢，可把 `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 换成 `https://mirrors.huaweicloud.com/gradle/gradle-8.5-bin.zip`。

## 兼容性说明

- `mods.toml`：forge [47,)、minecraft [1.20.1,1.21)、ae2 [15,)、mekanism [10.4,) 为必需；extendedae / jei 可选。
- JEI 分类未实现（可选功能，不影响机器运行）。
- 方块模型复用 Mekanism 的 `mekanism:block/machine` 父模型与 `enrichment_chamber` 贴图，正面贴图由参考 jar 提供。
