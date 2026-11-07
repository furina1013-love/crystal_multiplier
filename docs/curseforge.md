# Crystal Multiplier

**Grow crystals instead of crafting them.** Put a single crystal into the seed slot and the machine keeps duplicating it — the seed is *never* consumed, no items or fluids are used, only Forge Energy (FE).

- Minecraft **1.20.1**, Forge **47.x**
- Requires: **Mekanism 10.4+**, **Applied Energistics 2 (AE2) 15+**
- Optional: AppliedFlux, ExtendedAE Plus (their crystals are auto-enabled), JEI (a "Crystal Multiplication" recipe category is included)

---

## What it does

An AE2-flavoured multiplication machine for crystal production lines. Instead of burning materials, it runs purely on FE and keeps your seed crystal intact forever.

- **No material cost** — the crystal in the seed slot is only used as a recipe key and is never consumed.
- **FE only** — no water, no gas, no other fluids. Works fine on Mekanism cables and energy cubes.
- **Native Mekanism upgrades** — the upgrade slots accept standard Mekanism upgrade cards.
- **Four machine tiers** — higher tiers are faster, hold more energy and have more slots.
- **One crystal type per machine at a time** — vanilla-like behaviour, no hidden limits.

---

## Machine tiers

| Tier | Speed | Energy buffer | Max input | Output slots | Upgrade slots | Seed stack limit | Output stack limit |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Basic | 1x | 200,000 FE | 20,000 FE/t | 3 | 4 | 64 | 64 |
| Advanced | 2x | 800,000 FE | 80,000 FE/t | 6 | 6 | 128 | 128 |
| Elite | 4x | 3,200,000 FE | 320,000 FE/t | 9 | 8 | 256 | 256 |
| Ultimate | 8x | 12,800,000 FE | 1,280,000 FE/t | 12 | 10 | 512 | 512 |

Tiers change speed, energy handling, slot counts and stack limits. Every tier still multiplies only one crystal type at a time.
1.20.1 stores item counts in a signed byte (127 max), so any capacity above 127 is implemented as **visible stack (up to 127) + hidden overflow counter**:
a slot holding 300 crystals shows 127 on the icon, prints the real amount in the slot corner and shows "Stored: 300 / 512" on hover. Nothing is lost or truncated.

---

## Parallelism: the seed stack size IS your throughput

1. Put one crystal type in the seed slot. The amount must be a **multiple of 16** (configurable), otherwise the machine idles and the GUI shows a hint.
2. **Parallel lanes = seed count / 16** — 16 crystals = 1 lane, 32 = 2 lanes, 64 = 4 lanes.
3. Output *and* energy draw scale with the lane count, so the **FE cost per crystal stays the same** — more seeds simply means more production lines.
4. Seeds are **never consumed**. The machine only stalls when the output slots are full.
5. Seed slot capacity comes from the tier (64 / 128 / 256 / 512) and gains **+16 per inserted upgrade card** (global ceiling `seedSlotCapCeiling`, 1024 by default).
6. Seed and output stacks may exceed 127, so a fully loaded Ultimate machine holds 512 seeds = **32 parallel lanes**.
7. Everything above 127 lives in a hidden overflow counter: shift-click whole stacks, hoppers or pipes to fill a slot completely; extraction gives at most one stack (<=127) at a time.

---

## Upgrade cards

| Card | Effect |
| --- | --- |
| `mekanism:upgrade_speed` | **Faster processing**, exactly like Mekanism. Each card **halves the batch time** (stacking: 2 cards = 4x speed, 3 cards = 8x). FE per tick rises by the same factor, so FE per crystal is unchanged. |
| `mekanism:upgrade_energy` | **Cheaper processing**, exactly like Mekanism. Each card **halves the energy per operation** (2 cards = 1/4 cost) and **never touches any energy buffer** (the buffer is tier-only). |
| Any upgrade card | **+16 seed slot capacity** each, so more cards means more crystals and more lanes. |
| Others (muffling, gas, filter, anchor) | Accepted, they just do not affect multiplication. |

Cards are recognised through Mekanisms official API (`mekanism.api.Upgrade` plus `IUpgradeItem`), the ids are identical to Mekanisms own cards
and the per-kind limit is taken straight from `Upgrade#getMax()` (8 for speed and energy). Cards stack inside a single upgrade slot just like in a Mekanism machine:
2 speed cards in one slot behave exactly like 1 card in each of two slots and anything above the limit is refused.

The maths is Mekanism's own (the `MekanismUtils#getTicks` / `#getEnergyPerTick` formulas from Mekanism 10), with Mekanism's
`maxUpgradeMultiplier` config value (default 10) as the base - this mod exposes the same key under the same name:

- `fraction = installed cards / Upgrade#getMax()` (8 for both speed and energy)
- batch time = base time x multiplier ^ (-fraction_speed)
- FE per tick = base FE x multiplier ^ (2 * fraction_speed - fraction_energy)

In short: speed cards are an *overclock* (8 cards = 10x faster but 100x the draw per tick, exactly like a Mekanism machine),
energy cards make the job *cheaper* (8 cards = a tenth of the draw), and every card lets you *stack more seeds*.
The extra draw of overclocked machines is handled by proportional throttling: when one tick costs more than the internal
buffer holds, the machine simply runs proportionally slower instead of stalling.

---

## Crafting

The recipes use exactly the same layout as Mekanism's own machines (compare `mekanism:enrichment_chamber`):
pattern `ACA / IXI / ACA`, where A = a Mekanism alloy tag, C = a Forge circuit tag, I = an ingot tag and
X = a steel casing or the previous tier machine.

| Tier | A (alloy) | C (circuit) | I (ingot) | X (core) |
| --- | --- | --- | --- | --- |
| Basic | `#mekanism:alloys/basic` (redstone) | `#forge:circuits/basic` | `#forge:ingots/iron` | `mekanism:steel_casing` |
| Advanced | `#mekanism:alloys/infused` | `#forge:circuits/advanced` | `#forge:ingots/osmium` | Basic machine |
| Elite | `#mekanism:alloys/reinforced` | `#forge:circuits/elite` | `#forge:ingots/refined_obsidian` | Advanced machine |
| Ultimate | `#mekanism:alloys/atomic` | `#forge:circuits/ultimate` | `#forge:ingots/refined_obsidian` | Elite machine |

Everything is tag based, so KubeJS or other addons that register their own alloys/circuits under the same tags work too;
both tag families are shipped by Mekanism itself, so no extra dependency is pulled in.

---

## Multiplication recipes (datapack friendly)

Recipes live in `data/crystal_multiplier/recipes/crystal_multiplication/` with type `crystal_multiplier:crystal_multiplication`:

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

- `seed` is only matched, never consumed.
- One operation yields `result.count x lanes` items, repeating until the output slots are full.
- `crystals_per_parallel` is optional and falls back to the global config value.

### Bundled recipes

| Crystal | Mod | Baseline (count 2) |
| --- | --- | --- |
| Certus Quartz Crystal | AE2 | 200 FE/t, 200 ticks |
| Charged Certus Quartz Crystal | AE2 | 240 FE/t, 220 ticks |
| Fluix Crystal | AE2 | 280 FE/t, 240 ticks |
| Redstone Crystal | AppliedFlux (if installed) | 320 FE/t, 280 ticks |
| Lattra Crystal | ExtendedAE Plus (if installed) | 340 FE/t, 300 ticks |

Note: ExtendedAE for 1.20.1 (modid `expatternprovider`) does not ship `entro_crystal`, so the original Entro Crystal recipe does not exist in this version. Add your own JSON recipe if you want it.

---

## Configuration (`config/crystal_multiplier-common.toml`)

| Key | Default | Meaning |
| --- | --- | --- |
| `crystalsPerParallel` | 16 | Crystals required per parallel lane |
| `seedCapacityPerUpgrade` | 16 | Capacity added per upgrade card (base capacity is tier-driven) |
| `seedSlotCapCeiling` | 1024 | Ceiling for seed slot capacity (was `maxSeedCapacity` in 1.1.0) |
| `maxParallel` | 64 | Parallel lane cap per machine |
| `baseSpeedMultiplier` | 4.0 | Global base speed multiplier (raises FE/t, total cost stays the same) |
| `baseEnergyMultiplier` | 0.5 | Global base energy multiplier (0.5 = half cost) |
| `maxUpgradeMultiplier` | 10 | Same option as Mekanism's `general.maxUpgradeMultiplier`: effect = value ^ (installed / possible) |
| `oneTickOutput` | false | Finish one batch every tick (cheat-level, off by default) |

Every key carries translation keys (`crystal_multiplier.configuration.machine.<key>` plus a matching `.tooltip`, section title
`crystal_multiplier.configuration.machine`), so config screen mods such as **Configured** and **Forge Config Screens** show localised
names and descriptions, and all values are re-read at runtime so a change applies immediately.

---

## Example numbers (Basic tier, 64 Certus seeds = 4 lanes)

| Upgrades | Items per batch | Batch time | Draw | FE per crystal |
| --- | --- | --- | --- | --- |
| none | 8 | 2.5 s (50 ticks) | 1,600 FE/t | 10,000 |
| 2x speed | 8 | 1.4 s (28 ticks) | 5,060 FE/t | ~17,700 |
| 8x speed (max) | 8 | 0.25 s (5 ticks) | 160,000 FE/t | 100,000 |
| 2x energy | 8 | 2.5 s (50 ticks) | 900 FE/t | 5,625 |
| 8x energy (max) | 8 | 2.5 s (50 ticks) | 160 FE/t | 1,000 |
| 8 speed + 8 energy | 8 | 0.25 s (5 ticks) | 16,000 FE/t | 10,000 |

Ultimate tier with the same 64 seeds: 8 items every 0.3 s (6 ticks) at 12,800 FE/t, 9,600 FE per crystal;
adding 8 speed cards makes it one batch per tick at 1,280,000 FE/t (160,000 FE per crystal - pure overclock).

Ultimate tier fully loaded with 512 seeds = 32 lanes: 64 items every 0.3 s at 102,400 FE/t (measured in-game).

---

## GUI

The screen is drawn in Mekanism's machine style: light steel frame, dark grey body, inset slots, a tick-marked energy gauge and a filling progress arrow.
The palette and slot/gauge textures were sampled directly from Mekanism's own GUI assets.
Hover the gauge for energy / capacity / draw per tick, hover the arrow for progress, duration, lanes, items per batch and the number of upgrade cards.
Slots holding more than 127 items print the real amount in the bottom-right corner.
Slot layout: seed (28,24), outputs from (84,18) in 4 columns, upgrades from (26,76) in 5 columns, player inventory (8,118), hotbar (8,176).
**Shift click** moves a whole seed stack (including the hidden overflow above 127) and empties output slots completely in one go.

### JEI

With JEI installed a "Crystal Multiplication" category lists every recipe: seed on the left (marked as not consumed, with how many crystals make one lane),
the output on the right, and the energy per tick plus the batch time in the middle. All four tiers are catalysts for the category, and clicking the
progress arrow inside the machine opens the matching recipe.

---

## Changelog

- **1.4.0** — Upgrades and crafting follow Mekanisms official conventions now. The upgrade maths is Mekanism's own
  (batch time x multiplier ^ (-fraction_speed), FE per tick x multiplier ^ (2 * fraction_speed - fraction_energy),
  fraction = installed / `Upgrade#getMax()`), with Mekanism's `maxUpgradeMultiplier` (default 10) as the base; the old
  `speedUpgradeSpeedPerCard` and `energyUpgradeEfficiencyFactor` keys are replaced by `maxUpgradeMultiplier`.
  Machine recipes were rewritten to Mekanism's machine layout (alloy tag + circuit tag + ingot tag + steel casing /
  previous tier machine).
- **1.3.0** — Upgrade cards now go through Mekanisms official API (`mekanism.api.Upgrade` / `IUpgradeItem`, per-kind limit `Upgrade#getMax()` = 8) and stack inside one slot;
  added proportional throttling so an overclocked machine never stalls dead when one tick costs more FE than the buffer holds; fixed GUI slot coordinates and shift click
  (output slots empty in one go, seed stacks move entirely instead of stopping at 127); every config key got a section title and translation keys so Configured and
  Forge Config Screens show localised names and descriptions.
- **1.2.0** — Seed and output stack limits changed to **64 / 128 / 256 / 512** per tier (visible stack + hidden overflow counter to get around the 1.20.1 signed-byte limit);
  JEI recipe category added; textures and GUI regenerated from Mekanism-sampled colours; `maxSeedCapacity` renamed to `seedSlotCapCeiling` (default 1024);
  all config keys now have translatable names and descriptions so in-game config editors can display them.
- **1.1.0** — Mekanism-style GUI and textures; speed and energy cards follow Mekanism semantics (faster / cheaper, both configurable and multiplicative); energy buffers reduced to 1/10;
  seed and output stack limits raised per tier (64 / 80 / 96 / 112) with the seed capacity now tier-driven.
- **1.0.2** — Fixed client-side numbers (energy, capacity, draw, items per batch), overlapping slots on Elite/Ultimate, overlapping title, status text over the energy bar, and items being lost when the machine is broken.
- **1.0.1** — Fixed the machine GUI not opening on right click; each tier got its own texture.

---

## FAQ

- **Nothing happens?** Check three things: seed count is a multiple of 16, output slots are not full, and the machine has power.
- **Can I multiply several crystal types at once?** No — one stack, one crystal type. Stack more of it to add lanes.
- **Will addons that change output speed conflict?** No. Speed and yield are computed entirely inside this mod and never read modified values from other mods.
- **Automatable?** Yes. Output slots can be extracted with hoppers or pipes; the seed slot accepts input and never loses its contents.
- **How do upgrade cards stack?** Multiplicatively. Speed cards: 2 cards = 4x faster. Energy cards: 2 cards = 1/4 energy. Caps at 4096x, adjustable in the config.
- **Why does the slot show 127 when the tooltip says 512?** Minecraft 1.20.1 cannot store more than 127 items in one stack, so the rest is kept in a hidden overflow counter and printed in the slot corner. The machine counts the real total.

---

## License

MIT. Source, issues and changelog: https://github.com/furina1013-love/crystal_multiplier
