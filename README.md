# 水晶增殖机 (Crystal Multiplier) — Minecraft 1.20.1 Forge

参考原始 NeoForge 1.21.1 版本 `crystal_multiplier-2.1.1.jar` 的思路，用 ForgeGradle 6 从零重写的 1.20.1 移植版。

## 设计目标（来自需求）

- **配方不消耗材料**：种子槽里的物品只作为"识别用配料"，永不消耗、只要求存在。
- **只消耗能量（FE）**：不引入任何流体，纯 FE 驱动，兼容 Mekanism 的 Forge Energy 转换。
- **Mekanism 原生升级支持**：升级槽接受 Mekanism 升级卡（`mekanism:upgrade_*`，也可通过 `crystal_multiplier:upgrades` 物品标签扩展）。
- **工厂分级**：基础 / 高级 / 精英 / 终极四档，档位越高 → 处理速度更快、能量容量更大、输出槽与升级槽更多。

## 机器档位

| 档位 | 方块 ID | 速度倍率 | 能量缓存 | 输出槽 | 升级槽 | 最大输入 |
| --- | --- | --- | --- | --- | --- | --- |
| 基础 | \`crystal_multiplier:crystal_multiplier\` | 1x | 2,000,000 FE | 3 | 4 | 20,000 FE/t |
| 高级 | \`crystal_multiplier:crystal_multiplier_advanced\` | 2x | 8,000,000 FE | 6 | 6 | 80,000 FE/t |
| 精英 | \`crystal_multiplier:crystal_multiplier_elite\` | 4x | 32,000,000 FE | 9 | 8 | 320,000 FE/t |
| 终极 | \`crystal_multiplier:crystal_multiplier_ultimate\` | 8x | 128,000,000 FE | 12 | 10 | 1,280,000 FE/t |

档位只影响「同一份配置下的运行速度、能量缓存/接收上限、输出槽数与升级槽数」；
**任何档位都同时只能增殖一种水晶**，之所以如此是因为种子槽只有一个物品栈，栈里只能是一种物品。

## 种子槽与并行（核心机制）

- 种子槽里放进**同一种水晶**，数量必须是最小单位 \`crystalsPerParallel\`（默认 **16**）的**整数倍**，
  否则机器不工作，GUI 会显示红字「需要 16 的倍数个水晶」。
- **并行数 = 种子数量 ÷ 16**（上限 \`maxParallel\`）。放 16 个 = 1 条并行，32 个 = 2 条……64 个 = 4 条。
  输出量按并行数成倍增加，每 tick 耗能也按并行数成倍增加，**每个水晶的 FE 成本恒定不变**。
- 种子**永不消耗**：只有输出槽被塞满（此时机器自动停机等待）或玩家手动取出时才需要动它。
- 种子槽容量默认 64（= 4 条并行）；**每插一张升级卡 +16**（\`seedCapacityPerUpgrade\`），
  上限 \`maxSeedCapacity\`（默认 64，可配到 127 —— 1.20.1 的物品栈数量是 byte，不能再高）。

## 速度


- 全局基础速度倍率 \`baseSpeedMultiplier\` 默认 **4**：机器按配方 \`ticks / 4\` 完成一次操作，
  **每 tick 耗能同比例上升**，所以「一次操作的总 FE」与原来完全一致，只是干得更快。
- 档位速度倍率与升级卡 ×1.5 都乘在这同一个因子上。
- 并行数**只**乘在产量上，速度只由「配方 ticks ÷ 速度因子」决定，两者互不串味，
  因此不会和任何修改输出速度的附属产生重复计算/冲突。

## 全局配置 \`config/crystal_multiplier-common.toml\`

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| \`crystalsPerParallel\` | 16 | 多少颗同种水晶 = 1 条并行；配方可用 \`crystals_per_parallel\` 覆盖 |
| \`baseSeedCapacity\` | 64 | 无升级卡时种子槽容量 |
| \`seedCapacityPerUpgrade\` | 16 | 每张升级卡增加的种子槽容量 |
| \`maxSeedCapacity\` | 64 | 种子槽容量硬上限（1.20.1 栈数量为 byte，最多 127） |
| \`maxParallel\` | 64 | 单机并行上限 |
| \`baseSpeedMultiplier\` | 4.0 | 全局基础速度倍率（越高越快） |
| \`oneTickOutput\` | **false** | 全局开关：打开后每台机器**每 1 tick 完成一次输出**（忽略配方时长；单次操作的总 FE 在那一 tick 内一次扣完，所以每个水晶的 FE 成本仍不变，但瞬时功率会很高，需要大功率供能） |

## 升级卡效果

- \`mekanism:upgrade_speed\`：每张 ×1.5 处理速度，同时每张 +75% 每 tick 耗能（Mekanism 式取舍）。
- \`mekanism:upgrade_energy\`：每张 +50% 能量利用率（降低每 tick 耗能）。
- 任意升级卡：每张都会给种子槽 **+16 容量**（见上面配置），所以多插卡 = 能放更多水晶 = 更多并行。
- 其它升级卡（muffling/gas/filter/anchor…）可放入，不影响水晶增殖逻辑本身。

## 配方机制

处理配方位于 \`data/crystal_multiplier/recipes/crystal_multiplication/*.json\`，类型 \`crystal_multiplier:crystal_multiplication\`：

\`\`\`json
{
  "type": "crystal_multiplier:crystal_multiplication",
  "seed": { "item": "ae2:certus_quartz_crystal" },
  "result": { "item": "ae2:certus_quartz_crystal", "count": 2 },
  "energy_per_tick": 200,
  "ticks": 200,
  "crystals_per_parallel": 16
}
\`\`\`

- \`seed\` 只做匹配，**不消耗**；要为某种水晶增产，就往种子槽塞它的**16 的倍数**颗，结果进入输出槽。
- 每完成一次操作产出 \`result.count × 并行数\` 个，会一直循环产出直到输出槽填满。
- \`crystals_per_parallel\` 可省略，省略时使用全局配置值。
- 默认包含：赛特斯石英水晶、充能赛特斯石英水晶、福鲁伊克斯水晶（AE2）；红石水晶（AppliedFlux，模组存在时）、拉特拉水晶（ExtendedAE Plus，模组存在时）。
- 1.20.1 的 ExtendedAE（modid \`expatternprovider\`）**没有** \`entro_crystal\`，因此原版的安特罗配方在 1.20.1 不存在于此版本；如需可自行添加同名配方 JSON。

## 构建

```bash
export JAVA_HOME=/opt/jdk/jdk-17.0.2
./gradlew build
# 产物：build/libs/crystal_multiplier-1.0.0.jar
```

依赖的 AE2 / Mekanism / ExtendedAE jar 放在 `libs/` 下作为 `compileOnly`（本模组通过注册名与物品标签访问它们，不需要硬依赖编译）。

## 兼容性说明

- `mods.toml`：forge [47,)、minecraft [1.20.1,1.21)、ae2 [15,)、mekanism [10.4,) 为必需；extendedae / jei 可选。
- JEI 分类未实现（可选功能，不影响机器运行）；如需可后续补充 `compat/jei`。
- 方块模型复用 Mekanism 的 `mekanism:block/machine` 父模型与 `enrichment_chamber` 贴图，正面贴图由参考 jar 提供。
