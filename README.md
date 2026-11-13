# 水晶增殖机 (Crystal Multiplier) — Minecraft 1.20.1 Forge

## 设计目标（来自需求）

- **配方不消耗材料**：种子槽里的水晶只作为「识别用配料」，永不消耗、只要求存在。
- **只消耗能量（FE）**：不引入任何流体，纯 FE 驱动
- **任意档位都只能同时增殖一种水晶**。

## 机器档位

| 档位 | 方块 ID | 速度倍率 | 能量缓存 | 输出槽 | 升级卡 | 最大输入 | 种子槽上限 | 输出槽上限 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 基础 | `crystal_multiplier:crystal_multiplier` | 1x | 200,000 FE | 3 | 8 速度 + 8 能量 | 20,000 FE/t | 64 | 64 |
| 高级 | `crystal_multiplier:crystal_multiplier_advanced` | 2x | 800,000 FE | 6 | 8 速度 + 8 能量 | 80,000 FE/t | 128 | 128 |
| 精英 | `crystal_multiplier:crystal_multiplier_elite` | 4x | 3,200,000 FE | 9 | 8 速度 + 8 能量 | 320,000 FE/t | 256 | 256 |
| 终极 | `crystal_multiplier:crystal_multiplier_ultimate` | 8x | 12,800,000 FE | 12 | 8 速度 + 8 能量 | 1,280,000 FE/t | 512 | 512 |

档位影响运行速度、能量缓存/接收上限、输出槽数与种子槽 / 输出槽的堆叠上限；升级窗口由 Mekanism 统一提供，四档都能装 8 张速度卡 + 8 张能量卡。
四档方块都带 `AttributeTier` / `AttributeUpgradeable`，可直接用 **Mekanism 原版工厂安装器**（基础/高级/精英/终极）升级；升级时保留能量、种子与输出槽内容、升级卡和红石设置，输出槽数量按新档位扩展。
**任何档位同时只能增殖一种水晶**，因为种子槽只有一个物品栈，栈里只能是一种物品。
1.6.0 起本模组附带一个很小的 `ItemStack` / `FriendlyByteBuf` Mixin：存档里额外记录真实数量，网络包里把物品数量写成 VarInt，
因此单个物品栈可以直接保存 128 / 256 / 512 这类超过 127 的数量，槽位容量全部是「真实可见堆叠」，不再需要隐藏溢出
（只有超过 4096 的极端值才会走槽位的溢出兜底，正常档位用不到）。

## 种子槽与并行（核心机制）

- 种子槽里放进**同一种水晶**，最小单位 `crystalsPerParallel`（默认 **16**）构成 1 条并行；数量不足一条并行时
  机器不工作，GUI 会显示红字提示；凑不满一条的余数不影响运行（20 颗 = 1 条并行，照常生产）。
- **基础并行数 = 种子数量 ÷ 16**：16 个 = 1 条并行，32 个 = 2 条……64 个 = 4 条；装速度卡后这个数会被成倍放大
  （见「升级卡效果」），最后受 `maxParallel`（默认 64）封顶。
- 输出与每 tick 耗能都按并行数成倍增加，**每颗水晶的 FE 成本只由配方能耗与全局倍率决定**，并行只是多开几条产线。
- 种子**永不消耗**：只有输出槽被塞满（机器自动停机等待取货）或玩家手动取出时才需要动它。
- 种子槽容量**只由档位决定**（64 / 128 / 256 / 512），升级卡不再增加容量，
  最终被 `seedSlotCapCeiling`（默认 1024）夹住。
- 终极档一次可塞 512 颗种子 = **32 条并行**；再叠 8 张速度卡会算出 320 条，被 `maxParallel`（默认 64）截到 64 条。
- 大堆叠由 Mixin 直接保存：*Shift* 点击整组装入、漏斗与管道输入都不受 127 限制；界面在槽位右下角标出真实总量，
  取出时按物品自身堆叠上限工作。

## 速度与能耗

- 全局基础速度倍率 `baseSpeedMultiplier` 默认 **4**：单批耗时 = `配方 ticks ÷ (档位倍率 × 4)`，
  **只由档位和这个全局倍率决定，与升级卡无关**（速度卡不再压缩耗时）。
- 全局基础能耗倍率 `baseEnergyMultiplier` 默认 **0.5**：所有操作耗能直接打五折（懒人友好）。
  于是基础档一颗赛特斯石英从 20,000 FE 降到 **10,000 FE**。
- 每 tick 耗能 = `配方 FE/t × 并行数 × 档位倍率 × baseSpeedMultiplier × baseEnergyMultiplier × 能量卡系数`：
  并行拉得越高，每 tick 就越费电，而单批耗时不变——所以「更快」是靠**多开产线**换来的，不是靠压缩 tick。
- 并行数只乘在产量 / 耗能上，单批耗时只由「配方 ticks ÷ 速度因子」决定，两者互不串味，
  不会和任何修改输出速度的附属产生重复计算或冲突。
- **不会憋死**：如果一 tick 需要的 FE 超过能量缓存（例如并行被速度卡放大到很高），机器不会停死，
  而是按「这一 tick 能付多少电」推进相应比例的进度（与 Mekanism 的降速处理一致）。

## 全局配置 `config/crystal_multiplier-common.toml`

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `crystalsPerParallel` | 16 | 多少颗同种水晶 = 1 条并行；配方可用 `crystals_per_parallel` 覆盖 |
| `seedSlotCapCeiling` | 1024 | 种子槽容量硬上限（旧键 `maxSeedCapacity` 已废弃） |
| `maxParallel` | 64 | 单机并行上限 |
| `baseSpeedMultiplier` | 4.0 | 全局基础速度倍率（越高越快，耗能同比上升） |
| `baseEnergyMultiplier` | **0.5** | 全局基础能耗倍率（0.5 = 所有操作耗能减半） |
| `maxUpgradeMultiplier` | **10** | 与 Mekanism 自己的 `general.maxUpgradeMultiplier` 完全同款：升级效果 = 该值 ^ (已装卡数 / 可装卡数)，10 就是 Mek 官方默认值 |
| `upgradeCardsPerStep` | **8** | 多少张同类升级卡算一个倍率台阶；默认 8 = Mek 原版单类卡上限，**附属提高上限后会继续叠加**（16 张 = 底数²，24 张 = 底数³） |
| `ejectEveryTick` | **true** | 本机开关：产物槽每 tick 往相邻容器 / 管道弹一次（Mekanism 默认 10 tick 一次）。**不影响生产速度** |
| `globalEjectEveryTick` | **false** | 全局开关：所有 Mekanism 机器（本体机器、工厂、附属机械）的产物槽都每 tick 弹一次。**不影响生产速度** |

「每 tick 出产物」分成两个独立开关：`ejectEveryTick` 只管本机器，**默认开启**；`globalEjectEveryTick` 通过 `EjectorMixin`
把同一条规则施加给**所有 Mekanism 机器**（本体机器、工厂、附属机械），**默认关闭**。两者做的都是把 Mek 内部 `tickDelay`
（默认 10 tick 的弹出冷却）清零：**不改变生产速度**，只让已经做好的成品更早离开机器。

每个键都带翻译键（`crystal_multiplier.configuration.machine.<键名>` 与同名 `.tooltip`），段标题是
`crystal_multiplier.configuration.machine`，所以 **Configured（MrCrayfish）** 与 **Forge Config Screens** 这类界面模组
会直接显示中文名称与说明；所有值都是每次运行时重新读取，改完立刻生效（无需重启，也不用重新拼机器）。

## 升级卡效果

升级窗口是 Mekanism 本体提供的（不是自绘槽位），只接受本机器支持的两类卡：速度与能量，各最多 8 张。
两张卡都用 Mekanism 的曲线（底数 `maxUpgradeMultiplier`，默认 10，即 `fraction = 已装卡数 / 8`、效果 = 底数^fraction），
但在这台机器上它们管的**不是速度**：

- `mekanism:upgrade_speed`：**增加并行产线**。
  `并行数 = clamp(floor(种子数 ÷ crystalsPerParallel) × 底数^(速度卡数/8), 0, maxParallel)`：
  1 张卡约 ×1.33 条产线，4 张约 ×3.16，8 张满卡 ×10。**不再压缩单批耗时**，
  单批耗时只由「档位倍率 × baseSpeedMultiplier」决定，插多少速度卡都不变。
- `mekanism:upgrade_energy`：**只省电**。每 tick 耗能 × 底数^(−能量卡数/8)，8 张满卡压到 1/10；
  **不碰能量缓存**（缓存只由档位决定），也**不增加种子槽容量**。
- 两类卡都**不再增加种子槽容量**：种子槽容量纯粹由档位决定（64 / 128 / 256 / 512）。
- 只有速度与能量卡能进入升级窗口；过滤 / 气体 / 消音 / 锚定等其它 Mek 卡不被本机器接受。
- 并行被放大后每 tick 耗电跟随上涨，超过缓存时由「按电量比例推进」兜住：机器按比例降速，不会憋死。

## 机器合成（Mekanism 同款配方结构）

四台机器的合成配方与 Mekanism 自己的机器配方**完全同一套写法**（对照 `mekanism:enrichment_chamber`）：
图案 `ACA / IXI / ACA`，A = Mekanism 合金标签，C = Forge 电路标签，I = 锭标签，X = 钢机壳或上一档机器。

| 档位 | A（合金标签） | C（电路标签） | I（锭标签） | X（核心） |
| --- | --- | --- | --- | --- |
| 基础 | `#mekanism:alloys/basic`（红石） | `#forge:circuits/basic` | `#forge:ingots/iron` | `mekanism:steel_casing` |
| 高级 | `#mekanism:alloys/infused` | `#forge:circuits/advanced` | `#forge:ingots/osmium` | 基础档机器 |
| 精英 | `#mekanism:alloys/reinforced` | `#forge:circuits/elite` | `#forge:ingots/refined_obsidian` | 高级档机器 |
| 终极 | `#mekanism:alloys/atomic` | `#forge:circuits/ultimate` | `#forge:ingots/refined_obsidian` | 精英档机器 |

用的全部是标签而不是具体物品 id，所以换成别的合金 / 电路（KubeJS、其它附属提供的同标签物品）也能正常合成；
`forge:circuits/*` 与 `mekanism:alloys/*` 这两个标签本身就是 Mekanism 提供的，不额外引入依赖。

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

- `seed` 只做匹配，**不消耗**；要给某种水晶增产，就往种子槽塞它的水晶，**每 16 颗算一条并行**（整除，余数不影响机器运行，20 颗 = 1 条并行，照常生产）。
- 每完成一次操作产出 `result.count × 并行数` 个，循环产出直到输出槽填满。
- `crystals_per_parallel` 可省略，省略时使用全局配置值。
- 默认包含：赛特斯石英水晶、充能赛特斯石英水晶、福鲁伊克斯水晶（AE2）；红石水晶（AppliedFlux，模组存在时）、拉特拉水晶（ExtendedAE Plus，模组存在时）。
- 1.20.1 的 ExtendedAE（modid `expatternprovider`）**没有** `entro_crystal`，因此原版安特罗配方在 1.20.1 不存在；如需可自行添加同名配方 JSON。

## 实际数字（赛特斯石英：200 FE/t、200 ticks、每次 2 个；基础档 64 颗种子 = 4 条并行）

| 升级卡 | 并行条数 | 每批次产出 | 单批耗时 | 每 tick 耗能 | FE/颗 |
| --- | --- | --- | --- | --- | --- |
| 无 | 4 | 8 | 2.5 s（50 tick） | 1,600 | 10,000 |
| 1× 速度 | 5 | 10 | 2.5 s（50 tick） | 2,000 | 10,000 |
| 4× 速度 | 13 | 26 | 2.5 s（50 tick） | 5,200 | 10,000 |
| 8× 速度（满） | 40 | 80 | 2.5 s（50 tick） | 16,000 | 10,000 |
| 2× 能量 | 4 | 8 | 2.5 s（50 tick） | 900 | 5,625 |
| 8× 能量（满） | 4 | 8 | 2.5 s（50 tick） | 160 | 1,000 |
| 8× 速度 + 8× 能量 | 40 | 80 | 2.5 s（50 tick） | 1,600 | 1,000 |

速度卡只加产线、不压耗时：每批产出与每 tick 耗电同倍上涨，**单批耗时一栏始终不动**，FE/颗 只随能量卡变化。
终极档（档位自带 8 倍速）同样 64 颗种子：4 条并行、每批 8 个、0.3 s（6 tick）、每 tick 12,800 FE，FE/颗 9,600；
再叠 8 张速度卡就是 40 条并行、每批 80 个、每 tick 128,000 FE，单批仍是 6 tick；
塞满 512 颗种子（32 条并行）再叠 8 张速度卡会算出 320 条，被 `maxParallel` 截到 64 条：每批 128 个、每 tick 204,800 FE。

## 界面

界面完全使用 **Mekanism 本体 GUI**（`GuiConfigurableTile`）：外框、槽位、标题、升级窗口、侧面配置、能量标签页与红石控制标签页
全部由 Mekanism 提供，本模组只额外绘制竖直能量条、进度箭头与「真实存量」数字。

- 升级窗口：点 GUI 右上角的升级卡图标打开，速度卡与能量卡各最多 8 张，直接拖入即可。
- 侧面配置：点侧面配置图标，可分别设置每个面在物品 / 能量上的输入、输出或禁用；输出槽默认自动弹出。
- 能量标签页显示当前电量、容量与每 tick 耗能；红石标签页支持忽略 / 高电平 / 低电平 / 脉冲。
- 布局：GUI 176×166，种子槽 (26,40)，进度箭头 (52,45) 32×8，输出槽 4 列×3 行（x=88/106/124/142，y=20/38/56），
  能量条 (164,15)，玩家背包 (8,84)，快捷栏 (8,142)。
- 槽内存量直接显示为物品栈数量（超过 127 也正常），悬停显示「种子: 总量 / 容量」或「存量: 总量 / 容量」。
- **Shift 点击**：种子槽整栈搬入，输出槽一次性抽空到玩家背包；大堆叠不会再被 127 截断。
- **JEI**：装了 JEI 会多出「水晶增殖」分类，显示种子（不消耗）、产物、每 tick 耗能、单次耗时；
  四档机器都是该分类的催化剂，点击机器界面里的进度箭头 (52,45,32,8) 可直接跳到对应配方。

## 构建

```bash
export JAVA_HOME=/path/to/jdk-17   # 需要 JDK 17
./gradlew build
# 产物：build/libs/crystal_multiplier-1.7.1.jar
```

AE2 的物品只用注册名（`ae2:certus_quartz_crystal` 等）与标签访问，不需要 AE2 的类；Mekanism 只用到升级卡的公开接口
（`mekanism.api.Upgrade`、`mekanism.common.item.interfaces.IUpgradeItem`），通过 `compileOnly` 依赖提供，不会打进 jar，
运行时由 Mekanism 本体提供。`build.gradle` 里另留了 `compileOnly fileTree(dir: 'libs')` 便于本地离线调试。

若在国内下载 Gradle 过慢，可把 `gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 换成 `https://mirrors.huaweicloud.com/gradle/gradle-8.5-bin.zip`。

## 兼容性说明

- `mods.toml`：forge [47,)、minecraft [1.20.1,1.21)、ae2 [15,)、mekanism [10.4,) 为必需；expatternprovider（ExtendedAE 1.20.1 的真实 modid）、appflux、extendedae_plus、jei 为可选软依赖。
- JEI 为可选：装了就注册「水晶增殖」分类与催化剂，不装则完全不加载相关类（`compileOnly` 依赖，不进入服务端）。
- 方块模型复用 Mekanism 的 `mekanism:block/machine` 父模型（保证与 Mekanism 机器同款外形）。贴图以 1.7.0 的新美术为基础（`新贴图基础/front.png` 与 `front_active.png`），**原图本身不动、只额外点几个像素**：四档在底边分别点 1 / 2 / 3 / 4 个档位点（基础=蓝、高级=绿、精英=紫、终极=红）；`*_front_active.png` 沿用新图自带的 `.mcmeta`，按 7 帧播放。侧 / 顶贴图仍是各档配色。
- 1.6.0 修改了物品数量的网络编码（VarInt），**客户端与服务端都必须安装 1.6.0+**，不能与旧版本混用；与其它修改物品数量上限的模组同时使用时请自行验证。

## 授权

本项目以 **MIT** 协议发布，全文见仓库根目录的 `LICENSE`。

Copyright (c) 2026 woxin。

允许商用、修改、再分发，只需保留版权声明与许可声明；本模组按「现状」提供，不附带任何担保。

## 更新记录

- **1.7.2**：并行/省电倍率不再用 `Upgrade#getMax()`（恒为 8）当分母，改为新增配置 `upgradeCardsPerStep`（默认 8）：附属把单机可插卡上限调到 16 / 24 / 32 张后，并行与省电会**继续按台阶叠加**（底数²、³、⁴），不再停在 10 倍；并行倍率上限从 4096 放宽到 1,048,576。发版流程新增 `tools/api_push.py`：git push 连不上 github.com 时自动改用 api.github.com 推送。
- **1.7.1**：升级卡语义调整：速度卡改为增加并行产线、能量卡只省电；两者都不再增加种子槽容量，删除 `seedCapacityPerUpgrade` 配置项。
- **1.7.0**：机器正面贴图换成新美术，**原图不改，只在底边点 1/2/3/4 个档位点**区分四档；
  新增两个「每 tick 出产物」开关：`ejectEveryTick`（本机，**默认开启**）与 `globalEjectEveryTick`（全局，**默认关闭**，
  通过 `EjectorMixin` 把 Mek 弹射器内部 10 tick 的 `tickDelay` 清零），生产速度不受影响；弹射器改为在产出之后 tick，
  成品同一 tick 就能离开机器；种子数改为**整除**计数，不足一条并行的余数不再停机；附加水晶（lattra / redstone）配方改为运行时自动生成。
  服务端实测：本机开关开时输出槽一直为空、成品直接进下方箱子；关掉后回到 10 tick 一批（0.28~0.54 s）；20 颗种子仍以 1 条并行生产。
- **1.6.1**：修复 Mixin 注入问题。`@Redirect` 回调方法现在显式接收被调用对象（`FriendlyByteBuf`）作为第一个参数，这是 1.6.0 启动即报 `InvalidInjectionException` 的原因；`ItemStack.of` 的 `RETURN` 注入补上 `cancellable = true`，修复大堆叠物品读档时 `Failed to load data for block entity`（`The call m_41712_ is not cancellable`）。存档里同时把原版 `Count` 字节写成 ≤64 的安全值，只由额外的整数键保存真实数量，避免第三方读到负数字节。
  已在真实 Forge 服务端验证：两个 Mixin 均正常注入；侧面配置为「物品输出」的面可以被管道/漏斗抽取，改回「无」后抽取立即停止；200 个物品的大堆叠在存档 → 重启 → 读档后完整保留。
- **1.6.0**：新增 `ItemStack` / `FriendlyByteBuf` Mixin，存档与网络包都能保存超过 127 的物品数量，种子 / 输出槽直接使用真实大堆叠；
  侧面配置只保留物品输出与能量输入，并启用自动输出；修复 GUI 顶部标题显示原始 `container.*` 键 / 重复绘制，恢复玩家背包标签；兼容 Mekanism 原版工厂安装器（保留机器内容升级）；方块贴图在 1.0.0 灰钢底上做可见的按档位换色与档位点。
- **1.5.0**：机器改为 **Mekanism 原生机器**：方块用 `BlockTile` + `BlockTileBuilder`，方块实体继承 `TileEntityMekanism`，
  界面换成 `GuiConfigurableTile`，升级窗口 / 侧面配置 / 能量 / 红石标签页全部由 Mekanism 本体提供；升级卡只接受 Mek 支持的速度与能量卡；
  删除自绘 GUI 贴图，方块贴图回退到 1.0.0 的 Mek 灰钢风格（四档仅少量换色）；修正 GUI 槽位与玩家背包位置；
  Shift 点击输出槽一次抽空整槽（含隐藏溢出），种子槽可以整组装入。
- **1.4.0**：升级与配方按 Mekanism 官方文档对齐。升级算法改成 **Mekanism 本体公式**
  （`fraction = 已装 / Upgrade#getMax()`，耗时 × 底数^(−fraction速度)，耗能 × 底数^(2×fraction速度 − fraction能量)），
  底数就是 Mekanism 自己的 `maxUpgradeMultiplier`（默认 10），本模组用同名配置键而不是自造倍率；
  机器合成配方改成 **Mekanism 机器同款结构**（`#mekanism:alloys/*` + `#forge:circuits/*` + 锭标签 + 钢机壳 / 上一档机器）；
  因此旧的 `speedUpgradeSpeedPerCard`、`energyUpgradeEfficiencyFactor` 两个配置键被 `maxUpgradeMultiplier` 取代。
- **1.3.0**：升级卡改用 **Mekanism 官方接口**（`mekanism.api.Upgrade` / `IUpgradeItem`，卡的注册名与 Mek 完全一致，同种上限即 `Upgrade#getMax()` = 8 张），速度 / 能量卡可在**同一个槽内叠放**（叠 2 张等于插 2 张）；
  新增**按电量比例推进**的降速处理，避免叠太多速度卡把机器憋死；修正界面槽位坐标（种子槽 / 输出槽 / 升级槽 / 玩家背包全部对齐新底图）；
  修正 **Shift 点击**：输出槽一次抽空、种子槽整栈放入（不再卡在 127）；配置项补上段标题与 9 个选项的翻译键，Configured / Forge Config Screens 能直接显示中文名称与说明，改动即时生效。
- **1.2.0**：种子槽 / 输出槽堆叠上限按档位改为 **64 / 128 / 256 / 512**（用「可见栈 + 隐藏溢出计数」实现，绕过 1.20.1 物品栈数量的 127 字节上限，界面会标出真实总量，存档与同步包都会持久化）；
  新增 **JEI 分类**（种子 / 产物 / 耗能 / 耗时，四档机器为催化剂，进度箭头可点击跳转）；贴图与 GUI 改为按 Mekanism 贴图采样生成的新版（四档独立配色，凹陷槽位逐像素对齐 Mek）；
  配置项 `maxSeedCapacity` 改名为 `seedSlotCapCeiling`（默认 1024，范围 64~4096，旧键自动失效，不会把大容量档位卡在 127）；
  所有配置项补上可翻译名称与说明，Configured 等配置界面模组可直接显示中英文。

- **1.1.0**：界面与贴图改为 Mekanism 风格（自绘贴图：亮钢边框、深灰机身、凹陷槽位、带刻线能量槽、填充进度箭头）；速度/能量升级卡改为 Mekanism 语义（速度卡提速不增产、能量卡省电不动能量槽，均为连乘且可配置）；能量缓存整体降到原来的 1/10；种子槽与输出槽的堆叠上限按档位提升（64/80/96/112），种子槽容量由档位决定；新增 `speedUpgradeSpeedPerCard`、`energyUpgradeEfficiencyFactor` 配置项（旧的 `speedUpgradeOutputPerCard`、`energyUpgradeEfficiencyPerCard`、`baseSeedCapacity` 已移除）；修复 `crystals_per_parallel` 配成 0 时的除零风险。
- **1.0.2**：修复能量/容量/单 tick 耗能/每批数量在客户端显示错误（这些数值超过 32767，菜单数据槽只能传 short，改为走方块实体同步包）；修复精英/终极档第三行输出槽与升级槽重叠（界面高度 186 → 198）；修复标题与档位名重叠（档位名改为右对齐）；修复状态文字压在能量条上；进度条 tooltip 重新显示每批产量；破坏机器时掉落槽内物品（原先物品和能量直接消失）；输出槽改为按物品自身堆叠上限工作（不可堆叠物品不再被算到 64）；`mods.toml` 的可选依赖 `extendedae` 改为 1.20.1 的真实 modid `expatternprovider`，并补充 `appflux`、`extendedae_plus`。
- **1.0.1**：修复右键机器打不开界面的问题（打开 GUI 时没有把机器坐标写进网络包，客户端解析失败）；四档机器改为各自配色的独立贴图。
- **1.0.0**：首个版本，四档工厂 + 种子并行增殖 + Mekanism 升级卡 + 全局配置。
