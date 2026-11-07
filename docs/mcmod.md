# 水晶增殖机 / Crystal Multiplier

**一句话介绍**：只要把一颗水晶放进种子槽，它就会一直“自我复制”——种子永不消耗，只吃 FORGE 能量（FE），不消耗任何材料、不消耗任何流体。

支持版本：**Minecraft 1.20.1 / Forge 47.x**　｜　前置：**Mekanism 10.4+**、**Applied Energistics 2 (AE2) 15+**

---

## 一、这是什么

把 AE2 体系里的核心水晶（赛特斯石英、充能赛特斯石英、福鲁伊克斯）以及部分附属水晶，丢进机器里“增殖”。

- **不消耗材料**：种子槽里的水晶只是“配方识别用”，机器运行期间**一个都不会少**。
- **只消耗能量**：纯 FE 驱动，可接 Mekanism 的通用线缆 / 能量立方，不使用水和任何流体。
- **Mekanism 风格升级**：升级槽直接插 Mekanism 升级卡，档位越高输出槽 / 升级槽越多。
- **工厂分级**：基础 / 高级 / 精英 / 终极四档，档次决定速度、能量缓存和槽位数量。

---

## 二、机器档位

| 档位 | 速度倍率 | 能量缓存 | 最大输入 | 输出槽 | 升级槽 | 种子槽上限 | 输出槽上限 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 基础 Basic | 1x | 200,000 FE | 20,000 FE/t | 3 | 4 | 64 | 64 |
| 高级 Advanced | 2x | 800,000 FE | 80,000 FE/t | 6 | 6 | 128 | 128 |
| 精英 Elite | 4x | 3,200,000 FE | 320,000 FE/t | 9 | 8 | 256 | 256 |
| 终极 Ultimate | 8x | 12,800,000 FE | 1,280,000 FE/t | 12 | 10 | 512 | 512 |

档位只影响“多快、能存多少电、有多少槽位、能堆多少物品”——**任何档位一次都只能增殖一种水晶**。
1.20.1 的单个物品栈数量用有符号 byte 保存（上限 127），所以 128 / 256 / 512 的容量用「可见栈（≤127）+ 隐藏溢出计数」实现：
槽里放 300 颗时图标显示 127，右下角标出真实数量 300，悬停显示「存量: 300 / 512」。这样既不丢物品，也不会被原版截断。

---

## 三、核心机制：种子数量 = 并行数

1. 种子槽里放**同一种**水晶，数量必须是 **16 的整数倍**（可在配置里改），否则机器不工作，GUI 会显示红字提示。
2. **并行数 = 种子数量 ÷ 16**：16 颗 = 1 条产线，32 颗 = 2 条，64 颗 = 4 条……
3. 并行数会同时放大“产量”和“耗电”，所以**每颗水晶消耗的 FE 基本恒定**，插水晶只是多开产线。
4. **种子永不消耗**：只有输出槽被塞满（机器自动停机等取货）时，机器才需要你动手清理输出。
5. 种子槽容量由档位决定（64 / 128 / 256 / 512），**每插一张升级卡再 +16 容量**（全局天花板 seedSlotCapCeiling 默认 1024）。
6. 种子槽和输出槽都支持超过 64 的堆叠（终极档 512），所以终极机一次能塞 512 颗种子 = **32 条并行**。
7. 超过 127 的部分存在槽位的隐藏溢出计数里：*Shift* 点击整组、漏斗、管道都能装满整槽；取出时一次最多一组（≤127）。

---

## 四、升级卡效果

| 升级卡 | 效果 |
| --- | --- |
| 速度升级 `mekanism:upgrade_speed` | **提速**（与 Mekanism 一致）。每张卡把批次时间 **÷2**（连乘：2 张 = 4 倍速、3 张 = 8 倍速），每 tick 耗电同倍增加，因此每颗水晶的 FE 成本不变 |
| 能量升级 `mekanism:upgrade_energy` | **省电**（与 Mekanism 一致）。每张卡把单次操作耗电 **÷2**（2 张 = 1/4 耗电），**完全不碰能量槽**（能量缓存只由档位决定） |
| 任意升级卡 | 每张都给种子槽 **+16 容量**（能放更多水晶 = 更多并行） |
| 其它卡（消音 / 气体 / 过滤 / 锚定） | 可插入，不影响增殖逻辑 |

升级卡通过 **Mekanism 官方接口**识别（`mekanism.api.Upgrade` 与 `IUpgradeItem`），卡的注册名和 Mek 完全一致，
同种卡的数量上限直接取自 `Upgrade#getMax()`（速度 / 能量都是 **8 张**）。卡可以像 Mek 机器里一样**叠在同一个槽内**：
一个槽放 2 张速度卡，与两个槽各放 1 张效果完全相同，超出上限会被拒绝放入。

算法用 **Mekanism 本体公式**（Mekanism 10 的 `MekanismUtils#getTicks` / `#getEnergyPerTick`），底数就是 Mekanism 自己的
配置项 `maxUpgradeMultiplier`（默认 10），本模组用同名配置键对齐：

- `fraction = 已装卡数 / Upgrade#getMax()`（速度、能量都是 8）
- 批次耗时 = 基础耗时 × 底数^(−fraction速度)
- 每 tick 耗能 = 基础耗能 × 底数^(2×fraction速度 − fraction能量)

通俗点说：**速度卡 = 超频（8 张满卡快 10 倍，但每 tick 电费 ×100，和 Mek 机器一样不是免费的），
能量卡 = 省电（8 张满卡把每 tick 电费压到 1/10），多插卡 = 能塞更多水晶**。速度卡带来的高耗电由「按电量比例推进」兜住，
一 tick 需要的电超过缓存时机器按比例降速，不会憋死。

---

## 五、机器合成

配方与 Mekanism 自己的机器**同一套写法**（对照 `mekanism:enrichment_chamber`）：图案 `ACA / IXI / ACA`，
A = Mekanism 合金标签、C = Forge 电路标签、I = 锭标签、X = 钢机壳或上一档机器。

| 档位 | A 合金 | C 电路 | I 锭 | X 核心 |
| --- | --- | --- | --- | --- |
| 基础 | `#mekanism:alloys/basic`（红石） | `#forge:circuits/basic` | `#forge:ingots/iron` | `mekanism:steel_casing` |
| 高级 | `#mekanism:alloys/infused` | `#forge:circuits/advanced` | `#forge:ingots/osmium` | 基础档机器 |
| 精英 | `#mekanism:alloys/reinforced` | `#forge:circuits/elite` | `#forge:ingots/refined_obsidian` | 高级档机器 |
| 终极 | `#mekanism:alloys/atomic` | `#forge:circuits/ultimate` | `#forge:ingots/refined_obsidian` | 精英档机器 |

全部用标签而不是具体物品 id：换成 KubeJS / 其它附属的同类合金、电路也能合成；这两个标签本身就是 Mekanism 提供的，不额外引入依赖。

---

## 六、增殖配方（可用数据包自定义）

配方放在 `data/crystal_multiplier/recipes/crystal_multiplication/`，类型 `crystal_multiplier:crystal_multiplication`：

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

- `seed` 只做匹配，**不会被消耗**。
- 完成一次操作产出 `result.count × 并行数` 个，之后循环产出直到输出槽填满。
- `crystals_per_parallel` 可省略，省略时用全局配置。

### 自带配方

| 水晶 | 模组 | 单次 2 个基准 |
| --- | --- | --- |
| 赛特斯石英水晶 | AE2 | 200 FE/t、200 ticks |
| 充能赛特斯石英水晶 | AE2 | 240 FE/t、220 ticks |
| 福鲁伊克斯水晶 | AE2 | 280 FE/t、240 ticks |
| 红石水晶 | AppliedFlux（装了就生效） | 320 FE/t、280 ticks |
| 拉特拉水晶 | ExtendedAE Plus（装了就生效） | 340 FE/t、300 ticks |

> 1.20.1 的 ExtendedAE（modid `expatternprovider`）没有 `entro_crystal`，所以原版的安特罗水晶配方在这个版本里不存在；想用的话自己加一个同名 JSON 配方即可。

---

## 七、配置文件（`config/crystal_multiplier-common.toml`）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `crystalsPerParallel` | 16 | 多少颗水晶 = 1 条并行 |
| `seedCapacityPerUpgrade` | 16 | 每张升级卡增加的种子槽容量（基础容量由档位决定） |
| `seedSlotCapCeiling` | 1024 | 种子槽容量天花板（旧键已废弃） |
| `maxParallel` | 64 | 单机并行上限 |
| `baseSpeedMultiplier` | 4.0 | 全局基础速度倍率（提高会同步提高耗电/t，单次总电费不变） |
| `baseEnergyMultiplier` | 0.5 | 全局基础耗电倍率（0.5 = 耗电减半） |
| `maxUpgradeMultiplier` | 10 | 与 Mekanism 的 `general.maxUpgradeMultiplier` 同款：效果 = 该值^(已装/可装) |
| `oneTickOutput` | false | 每 1 tick 完成一次输出（作弊级速度，默认关闭） |

每个键都带翻译键（`crystal_multiplier.configuration.machine.<键名>` 与同名 `.tooltip`，段标题是 `crystal_multiplier.configuration.machine`），
所以 **Configured**、**Forge Config Screens** 等界面模组会直接显示中文名称与说明；所有值都在运行时实时读取，改完立刻生效。

---

## 八、实测算例（基础档、64 颗赛特斯石英种子 = 4 并行）

| 升级卡 | 每批次产出 | 单次耗时 | 耗电 | FE/颗 |
| --- | --- | --- | --- | --- |
| 无 | 8 | 2.5 s（50 tick） | 1,600 FE/t | 10,000 |
| 2 张速度卡 | 8 | 1.4 s（28 tick） | 5,060 FE/t | 约 17,700 |
| 8 张速度卡（满） | 8 | 0.25 s（5 tick） | 160,000 FE/t | 100,000 |
| 2 张能量卡 | 8 | 2.5 s（50 tick） | 900 FE/t | 5,625 |
| 8 张能量卡（满） | 8 | 2.5 s（50 tick） | 160 FE/t | 1,000 |
| 8 速度 + 8 能量 | 8 | 0.25 s（5 tick） | 16,000 FE/t | 10,000 |

终极档（档位自带 8 倍速）同样 64 颗种子：8 个 / 0.3 s（6 tick）/ 12,800 FE/t，9,600 FE 一颗；
再叠 8 张速度卡就是 1 tick 一批、每 tick 1,280,000 FE、FE/颗 160,000（纯超频）。

---

## 九、常见问题

- **为什么不工作了？** 检查三件事：种子数量是不是 16 的倍数、输出槽是不是满了、电够不够。
- **机器一次能产几种水晶？** 一种。种子槽只有一个物品栈，本来就是“一次一种”，堆数量就是加并行。
- **可以和其它模组共存吗？** 速度/产量完全由本模组自己算，不读取也不会被其它附属修改的“输出速度”影响。
- **能自动化吗？** 输出槽支持漏斗 / 管道抽取，种子槽支持输入（但种子不会被吃掉，塞进去就一直在）。

---

## 十、界面说明

- 界面按 Mekanism 机器风格绘制：亮钢边框 + 深灰机身、凹陷式槽位、带刻线的能量槽、填充式进度箭头；配色直接采样自 Mekanism 的 GUI 贴图。
- 左侧竖直能量槽（悬停看电量 / 容量 / 每 tick 耗电）；中间进度箭头（悬停看进度、耗时、并行数、每批产量、升级卡数量）；
  右上角显示档位名；种子槽下方显示当前并行数，种子数量不对时显示红字「需 ×16 颗」。
- 槽内存量超过 127 时右下角显示真实数量，悬停显示「存量: 总量 / 容量」。
- 槽位布局：种子槽 (28,24)、输出槽 4 列起 (84,18)、升级槽 5 列起 (26,76)、玩家背包 (8,118)、快捷栏 (8,176)。
- **Shift 点击**：种子槽整栈搬入（含超过 127 的隐藏溢出部分），输出槽一次性抽空到玩家背包，不会卡在 127 个。

### JEI

装了 JEI 后会多出「水晶增殖」分类：左边种子（标注不消耗、多少颗 = 1 条并行线）、右边产物，中间是每 tick 耗能与单次耗时；
四档机器都是该分类的催化剂，在机器界面里直接点进度箭头就能跳到对应配方。

---

## 十一、更新记录

- **1.4.0**：按 Mekanism 官方文档对齐升级与合成。升级算法改为 Mekanism 本体公式（耗时 × 底数^(−fraction速度)，
  耗能 × 底数^(2×fraction速度 − fraction能量)，fraction = 已装 / Upgrade#getMax()），底数为 Mekanism 自己的
  `maxUpgradeMultiplier`（默认 10），本模组用同名配置键替代原有的 `speedUpgradeSpeedPerCard` /
  `energyUpgradeEfficiencyFactor`；机器合成配方改为 Mekanism 机器同款结构（合金标签 + 电路标签 + 锭标签 + 钢机壳 / 上一档机器）。
- **1.3.0**：升级卡改用 Mekanism 官方接口（`mekanism.api.Upgrade` / `IUpgradeItem`，同种上限即 `Upgrade#getMax()` = 8 张），速度 / 能量卡可在同一槽内叠放；
  新增按电量比例推进的降速处理（一 tick 要的电超过缓存时按比例推进，不会憋死）；修正界面槽位坐标与 Shift 点击（输出槽一次抽空、种子槽整栈放入，不再卡在 127）；
  配置项补上段标题与 9 个选项的翻译键，Configured / Forge Config Screens 直接显示中文名称与说明。
- **1.2.0**：种子槽 / 输出槽堆叠上限按档位改为 **64 / 128 / 256 / 512**（可见栈 + 隐藏溢出计数，绕过 1.20.1 的 127 字节上限）；
  新增 JEI 分类；贴图与 GUI 换成为本模组按 Mekanism 采样生成的新版；配置项 maxSeedCapacity 改名 seedSlotCapCeiling（默认 1024）；
  全部配置项补上可翻译名称与说明（兼容 Configured 一类配置界面模组）。

- **1.1.0**：界面与贴图改为 Mekanism 风格；速度 / 能量升级卡改为 Mekanism 语义（提速 / 省电，连乘且可配置）；
  能量缓存降到原来的 1/10；种子槽与输出槽堆叠上限按档位提升（64 / 80 / 96 / 112），种子槽容量由档位决定。
- **1.0.2**：修复客户端数值显示（能量、容量、耗电、每批产量）、精英 / 终极档槽位重叠、标题重叠、状态文字压条、破坏机器不掉落物品等问题。
- **1.0.1**：修复右键打不开界面；四档机器改为各自独立配色贴图。

---

## 十二、依赖与授权

- 必需：Minecraft 1.20.1、Forge 47.x、Mekanism 10.4+、AE2 15+
- 可选：AppliedFlux、ExtendedAE Plus（有则自动启用对应配方）、JEI（已提供「水晶增殖」分类）
- 授权：MIT，源码与 Issue：https://github.com/furina1013-love/crystal_multiplier
