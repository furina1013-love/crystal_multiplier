# 水晶增殖机 / Crystal Multiplier

**一句话介绍**：只要把一颗水晶放进种子槽，它就会一直“自我复制”——种子永不消耗，只吃 FORGE 能量（FE），不消耗任何材料、不消耗任何流体。

支持版本：**Minecraft 1.20.1 / Forge 47.x**　｜　前置：**Mekanism 10.4+**、**Applied Energistics 2 (AE2) 15+**

---

## 一、这是什么

把 AE2 体系里的核心水晶（赛特斯石英、充能赛特斯石英、福鲁伊克斯）以及部分附属水晶，丢进机器里“增殖”。

- **不消耗材料**：种子槽里的水晶只是“配方识别用”，机器运行期间**一个都不会少**。
- **只消耗能量**：纯 FE 驱动，可接 Mekanism 的通用线缆 / 能量立方，不使用水和任何流体。
- **Mekanism 原生机器**：方块与方块实体基于 Mekanism 的 `BlockTile` / `TileEntityMekanism`，界面直接使用 Mek 本体 GUI，
  升级窗口、侧面配置、能量与红石标签页全部由 Mekanism 提供；升级卡走官方 `Upgrade` 接口。
- **工厂分级**：基础 / 高级 / 精英 / 终极四档，档次决定速度、能量缓存和槽位数量。

---

## 二、机器档位

| 档位 | 速度倍率 | 能量缓存 | 最大输入 | 输出槽 | 升级卡 | 种子槽上限 | 输出槽上限 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 基础 Basic | 1x | 200,000 FE | 20,000 FE/t | 3 | 8 速度 + 8 能量 | 64 | 64 |
| 高级 Advanced | 2x | 800,000 FE | 80,000 FE/t | 6 | 8 速度 + 8 能量 | 128 | 128 |
| 精英 Elite | 4x | 3,200,000 FE | 320,000 FE/t | 9 | 8 速度 + 8 能量 | 256 | 256 |
| 终极 Ultimate | 8x | 12,800,000 FE | 1,280,000 FE/t | 12 | 8 速度 + 8 能量 | 512 | 512 |

档位只影响“多快、能存多少电、有多少输出槽、能堆多少物品”——升级窗口由 Mekanism 统一提供，四档都能装 8 张速度卡 + 8 张能量卡；**任何档位一次都只能增殖一种水晶**。
四档方块都注册了 `AttributeTier` 与 `AttributeUpgradeable`，可直接使用 **Mekanism 原版工厂安装器**（基础/高级/精英/终极）逐级升级；升级会保留能量、种子/输出槽内容、升级卡和红石设置，输出槽数量按新档位扩展。
1.6.0 起附带 `ItemStack` / `FriendlyByteBuf` Mixin：存档额外记录真实数量，网络包把物品数量写成 VarInt，
所以单个物品栈可以直接保存 128 / 256 / 512 这类超过 127 的数量，种子 / 输出槽全部是真实大堆叠（超过 4096 才走溢出兜底）。
由于网络格式已改变，**客户端与服务端都必须安装 1.6.0+**，旧版本客户端/服务端不能混连。

---

## 三、核心机制：种子数量 = 并行数

1. 种子槽里放**同一种**水晶，每 **16 颗**（可在配置里改）组成一条并行产线；数量不足一条并行时机器不工作，GUI 会显示红字提示。
2. **基础并行数 = 种子数量 ÷ 16（向下取整）**：16 颗 = 1 条产线，32 颗 = 2 条，20 颗 = 1 条（余数不影响运行）；装速度卡后这个数会被成倍放大，最后受 `maxParallel` 封顶。
3. 并行数会同时放大“产量”和“耗电”，所以**每颗水晶消耗的 FE 基本恒定**，插水晶（或插速度卡）都只是多开产线。
4. **种子永不消耗**：只有输出槽被塞满（机器自动停机等取货）时，机器才需要你动手清理输出。
5. 种子槽容量**只由档位决定**（64 / 128 / 256 / 512），升级卡不再增加容量；全局天花板是 `seedSlotCapCeiling`（默认 1024）。
6. 种子槽和输出槽都支持超过 64 的堆叠（终极档 512），所以终极机一次能塞 512 颗种子 = **32 条并行**，叠满速度卡可顶到 `maxParallel` 的 64 条。
7. 大堆叠由 Mixin 直接保存：*Shift* 点击整组、漏斗、管道都能装满整槽，取出时按物品自身堆叠上限工作。

---

## 四、升级卡效果

升级窗口是 Mekanism 本体提供的（不是自绘槽位），本机器只支持两类卡：速度与能量，各最多 **8 张**。
两张卡都走 Mekanism 的曲线（底数 `maxUpgradeMultiplier`，默认 10，即 `fraction = 已装卡数 / 8`、效果 = 底数^fraction），
但在这台机器上它们管的**不是速度**：

| 升级卡 | 效果 |
| --- | --- |
| 速度升级 `mekanism:upgrade_speed` | **增加并行产线**。`并行数 = clamp(floor(种子数 ÷ crystalsPerParallel) × 底数^(速度卡数/8), 0, maxParallel)`：1 张约 ×1.33 条、4 张约 ×3.16、8 张满卡 ×10。**不再压缩单批耗时** |
| 能量升级 `mekanism:upgrade_energy` | **只省电**。每 tick 耗能 × 底数^(−能量卡数/8)，8 张满卡 = 1/10；**不碰能量缓存**（缓存只由档位决定） |
| 两类卡共同点 | **都不再增加种子槽容量**，种子槽容量纯粹由档位决定（64 / 128 / 256 / 512） |
| 其它卡（消音 / 气体 / 过滤 / 锚定） | **不被接受**，升级窗口会拒绝放入 |

- 单批耗时 = 配方 ticks ÷ (档位速度倍率 × `baseSpeedMultiplier`)，**只由档位和全局倍率决定，与卡片无关**。
- 每 tick 耗能 = 配方 FE/t × 并行数 × 档位速度倍率 × `baseSpeedMultiplier` × `baseEnergyMultiplier` × 底数^(−能量卡数/8)。

通俗点说：**速度卡 = 多开产线（8 张满卡约 10 倍产线，每 tick 电费同比上涨，但单批耗时不变），
能量卡 = 省电（8 张满卡把每 tick 电费压到 1/10）；两类卡都不会让种子槽变大**。
并行拉高后的高耗电由「按电量比例推进」兜住，一 tick 需要的电超过缓存时机器按比例降速，不会憋死。

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
| `seedSlotCapCeiling` | 1024 | 种子槽容量天花板（旧键已废弃） |
| `maxParallel` | 64 | 单机并行上限 |
| `baseSpeedMultiplier` | 4.0 | 全局基础速度倍率（提高会同步提高耗电/t，单次总电费不变） |
| `baseEnergyMultiplier` | 0.5 | 全局基础耗电倍率（0.5 = 耗电减半） |
| `maxUpgradeMultiplier` | 10 | 与 Mekanism 的 `general.maxUpgradeMultiplier` 同款：效果 = 该值^(已装/可装) |
| `ejectEveryTick` | true | 产物槽每 tick 往相邻容器 / 管道弹一次（Mek 默认 10 tick 一次），不影响生产速度 |
| `globalEjectEveryTick` | false | 所有 Mekanism 机器都每 tick 弹一次产物，不影响生产速度 |

每个键都带翻译键（`crystal_multiplier.configuration.machine.<键名>` 与同名 `.tooltip`，段标题是 `crystal_multiplier.configuration.machine`），
所以 **Configured**、**Forge Config Screens** 等界面模组会直接显示中文名称与说明；所有值都在运行时实时读取，改完立刻生效。

---

## 八、实测算例（基础档、64 颗赛特斯石英种子 = 4 条并行）

| 升级卡 | 并行条数 | 每批次产出 | 单批耗时 | 耗电 | FE/颗 |
| --- | --- | --- | --- | --- | --- |
| 无 | 4 | 8 | 2.5 s（50 tick） | 1,600 FE/t | 10,000 |
| 1 张速度卡 | 5 | 10 | 2.5 s（50 tick） | 2,000 FE/t | 10,000 |
| 4 张速度卡 | 13 | 26 | 2.5 s（50 tick） | 5,200 FE/t | 10,000 |
| 8 张速度卡（满） | 40 | 80 | 2.5 s（50 tick） | 16,000 FE/t | 10,000 |
| 2 张能量卡 | 4 | 8 | 2.5 s（50 tick） | 900 FE/t | 5,625 |
| 8 张能量卡（满） | 4 | 8 | 2.5 s（50 tick） | 160 FE/t | 1,000 |
| 8 速度 + 8 能量 | 40 | 80 | 2.5 s（50 tick） | 1,600 FE/t | 1,000 |

速度卡只加产线、不压耗时：每批产出与每 tick 耗电同倍上涨，单批耗时一栏始终不动，FE/颗 只随能量卡变化。

终极档（档位自带 8 倍速）同样 64 颗种子：4 条并行、8 个 / 0.3 s（6 tick）/ 12,800 FE/t，9,600 FE 一颗；
再叠 8 张速度卡变成 40 条并行：80 个 / 6 tick / 128,000 FE/t；
塞满 512 颗种子（32 条并行）再叠 8 张速度卡会算出 320 条，被 `maxParallel` 截到 64 条：128 个 / 6 tick / 204,800 FE/t。

---

## 九、常见问题

- **为什么不工作了？** 检查三件事：种子是不是至少 16 颗（够一条并行）、输出槽是不是满了、电够不够。种子按整除算并行，20 颗就是 1 条并行加 4 颗余数，**不会**让机器停摆。
- **机器一次能产几种水晶？** 一种。种子槽只有一个物品栈，本来就是“一次一种”，堆数量就是加并行。
- **可以和其它模组共存吗？** 速度/产量完全由本模组自己算，不读取也不会被其它附属修改的“输出速度”影响。
- **能自动化吗？** 输出槽支持漏斗 / 管道抽取，种子槽支持输入（但种子不会被吃掉，塞进去就一直在）。

---

## 十、界面说明

界面完全使用 **Mekanism 本体 GUI**（`GuiConfigurableTile`）：外框、槽位、标题、升级窗口、侧面配置、能量标签页与红石控制标签页
全部由 Mekanism 提供，本模组只额外绘制竖直能量条、进度箭头与「真实存量」数字。

- **升级窗口**：点 GUI 右上角的升级卡图标打开，速度卡与能量卡各最多 8 张，直接拖入即可。
- **侧面配置**：点侧面配置图标；物品侧只有「输出」，能量侧只有「输入」，输出槽默认自动弹出。
- **能量标签页**：显示当前电量、容量与每 tick 耗能。**红石标签页**：忽略 / 高电平 / 低电平 / 脉冲。
- 布局：GUI 176×166，种子槽 (26,40)，进度箭头 (52,45) 32×8，输出槽 4 列×3 行（x=88/106/124/142，y=20/38/56），
  能量条 (164,15)，玩家背包 (8,84)，快捷栏 (8,142)。
- 槽内存量直接显示为物品栈数量（超过 127 也正常），悬停显示「种子: 总量 / 容量」或「存量: 总量 / 容量」。
- **Shift 点击**：种子槽整栈搬入，输出槽一次性抽空到玩家背包，不会卡在 127 个。

### JEI

装了 JEI 后会多出「水晶增殖」分类：左边种子（标注不消耗、多少颗 = 1 条并行线）、右边产物，中间是每 tick 耗能与单次耗时；
四档机器都是该分类的催化剂，在机器界面里直接点进度箭头（52,45,32,8）就能跳到对应配方。

---

## 十一、更新记录

- **1.7.1**：升级卡语义调整：速度卡改为增加并行产线、能量卡只省电；两者都不再增加种子槽容量，删除 `seedCapacityPerUpgrade` 配置项。
- **1.7.0**：机器正面贴图换成新美术，原图不动、只在底边点 1/2/3/4 个档位点区分四档；新增两个「每 tick 出产物」开关：`ejectEveryTick`（本机，**默认开启**）与 `globalEjectEveryTick`（**默认关闭**，通过 `EjectorMixin` 把 Mek 弹射器内部 10 tick 的 `tickDelay` 清零），生产速度不受影响，弹射器改到产出之后 tick，成品同一 tick 即可离开；种子改整除计并行；附加水晶（lattra / redstone）配方改为运行时自动生成。
- **1.6.1**：修复 `@Redirect` 回调缺少被调用对象参数导致的 Mixin 注入失败；`ItemStack.of` 的 `RETURN` 注入补上 `cancellable = true`，修复大堆叠读档报错；存档 `Count` 写成 ≤64 的安全值。已用真实服务端验证注入、侧面输出抽取与 200 堆叠存档往返。
- **1.6.0**：新增 `ItemStack` / `FriendlyByteBuf` Mixin，存档与网络包都能保存超过 127 的物品数量，种子 / 输出槽直接使用真实大堆叠；
  侧面配置只保留物品输出与能量输入并启用自动输出；修复 GUI 顶部标题显示原始 `container.*` 键 / 重复绘制，恢复玩家背包标签；兼容 Mekanism 原版工厂安装器（保留机器内容升级）；方块贴图在 1.0.0 灰钢底上做可见的按档位换色与档位点。
- **1.5.0**：机器改为 **Mekanism 原生机器**——方块用 `BlockTile` + `BlockTileBuilder`，方块实体继承 `TileEntityMekanism`，
  界面换成 `GuiConfigurableTile`，升级窗口 / 侧面配置 / 能量 / 红石标签页全部由 Mekanism 本体提供；升级窗口只接受速度与能量卡；
  删除自绘 GUI 贴图，方块贴图回退到 1.0.0 的 Mek 灰钢风格（四档仅少量换色）；修正 GUI 槽位与玩家背包位置；
  Shift 点击输出槽一次抽空整槽（含隐藏溢出）。
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
