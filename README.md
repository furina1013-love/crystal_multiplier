# 水晶增殖机 (Crystal Multiplier) — Minecraft 1.20.1 Forge

参考原始 NeoForge 1.21.1 版本 `crystal_multiplier-2.1.1.jar` 的思路，用 ForgeGradle 6 从零重写的 1.20.1 移植版。

## 设计目标（来自需求）

- **配方不消耗材料**：种子槽里的物品只作为"识别用配料"，永不消耗、只要求存在。
- **只消耗能量（FE）**：不引入任何流体，纯 FE 驱动，兼容 Mekanism 的 Forge Energy 转换。
- **Mekanism 原生升级支持**：升级槽接受 Mekanism 升级卡（`mekanism:upgrade_*`，也可通过 `crystal_multiplier:upgrades` 物品标签扩展）。
- **工厂分级**：基础 / 高级 / 精英 / 终极四档，档位越高 → 处理速度更快、能量容量更大、输出槽与升级槽更多。

## 机器档位

| 档位 | 方块 ID | 速度倍率 | 能量容量 | 输出槽 | 升级槽 | 最大输入 |
| --- | --- | --- | --- | --- | --- | --- |
| 基础 | `crystal_multiplier:crystal_multiplier` | 1x | 200,000 FE | 3 | 4 | 40 FE/t |
| 高级 | `crystal_multiplier:crystal_multiplier_advanced` | 2x | 1,000,000 FE | 6 | 6 | 70 FE/t |
| 精英 | `crystal_multiplier:crystal_multiplier_elite` | 4x | 4,000,000 FE | 9 | 8 | 100 FE/t |
| 终极 | `crystal_multiplier:crystal_multiplier_ultimate` | 8x | 20,000,000 FE | 12 | 10 | 140 FE/t |

## 升级卡效果

- `mekanism:upgrade_speed`：每张 ×1.5 处理速度，同时每张 +75% 每 tick 耗能（Mekanism 式取舍）。
- `mekanism:upgrade_energy`：每张 +50% 能量利用率（降低每 tick 耗能）。
- 其它升级卡（muffling/gas/filter/anchor…）可放入但不影响水晶增殖逻辑。

## 配方机制

处理配方位于 `data/crystal_multiplier/recipes/crystal_multiplication/*.json`，类型 `crystal_multiplier:crystal_multiplication`：

```json
{
  "type": "crystal_multiplier:crystal_multiplication",
  "seed": { "item": "ae2:certus_quartz_crystal" },
  "result": { "item": "ae2:certus_quartz_crystal", "count": 2 },
  "energy_per_tick": 200,
  "ticks": 200
}
```

- `seed` 只做匹配，**不消耗**；结果进入输出槽。
- 每次完成产出一份 `result`，会一直循环产出直到输出槽填满。
- 默认包含：赛特斯石英水晶、充能赛特斯石英水晶、福鲁伊克斯水晶（AE2）；红石水晶（AppliedFlux，模组存在时）、拉特拉水晶（ExtendedAE Plus，模组存在时）。
- 1.20.1 的 ExtendedAE（modid `expatternprovider`）**没有** `entro_crystal`，因此原版的安特罗配方在 1.20.1 不存在于此版本；如需可自行添加同名配方 JSON。

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
