# Crystal Multiplier

**Grow crystals instead of crafting them.** Put a single crystal into the seed slot and the machine keeps duplicating it — the seed is *never* consumed, no items or fluids are used, only Forge Energy (FE).

- Minecraft **1.20.1**, Forge **47.x**
- Requires: **Mekanism 10.4+**, **Applied Energistics 2 (AE2) 15+**
- Optional: AppliedFlux, ExtendedAE Plus (their crystals are auto-enabled), JEI

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

| Tier | Speed | Energy buffer | Max input | Output slots | Upgrade slots |
| --- | --- | --- | --- | --- | --- |
| Basic | 1x | 2,000,000 FE | 20,000 FE/t | 3 | 4 |
| Advanced | 2x | 8,000,000 FE | 80,000 FE/t | 6 | 6 |
| Elite | 4x | 32,000,000 FE | 320,000 FE/t | 9 | 8 |
| Ultimate | 8x | 128,000,000 FE | 1,280,000 FE/t | 12 | 10 |

Tiers only change speed, energy handling and slot counts. Every tier still multiplies only one crystal type at a time.

---

## Parallelism: the seed stack size IS your throughput

1. Put one crystal type in the seed slot. The amount must be a **multiple of 16** (configurable), otherwise the machine idles and the GUI shows a hint.
2. **Parallel lanes = seed count / 16** — 16 crystals = 1 lane, 32 = 2 lanes, 64 = 4 lanes.
3. Output *and* energy draw scale with the lane count, so the **FE cost per crystal stays the same** — more seeds simply means more production lines.
4. Seeds are **never consumed**. The machine only stalls when the output slots are full.
5. Seed slot capacity is 64 by default and gains **+16 per inserted upgrade card** (up to a configurable maximum).

---

## Upgrade cards

| Card | Effect |
| --- | --- |
| `mekanism:upgrade_speed` | **Does not speed up processing.** Each card adds **+50% items per finished batch** (2 cards = 2x, 4 cards = 3x). Energy per batch scales with it, so FE per crystal is unchanged. |
| `mekanism:upgrade_energy` | **Does not add any energy buffer.** Each card raises energy efficiency: consumption is divided by (1 + 0.5 x cards). |
| Any upgrade card | **+16 seed slot capacity** each, so more cards means more crystals and more lanes. |
| Others (muffling, gas, filter, anchor) | Accepted, they just do not affect multiplication. |

In short: speed cards give you *more items per batch*, energy cards make it *cheaper*, and every card lets you *stack more seeds*.

---

## Crafting

The machine uses a Mekanism-style casing (steel casing + glass + iron ingots), upgrading tier by tier:

- **Basic**: 3x3 ring of iron ingots (`#forge:ingots/iron`) and glass (`#forge:glass`) around a **Steel Casing**.
- **Advanced**: iron replaced with **Infused Alloy Ingots**, four corners = Basic machine, center = Steel Casing.
- **Elite**: alloy replaced with **Reinforced Alloy Ingots**, four corners = Advanced machine.
- **Ultimate**: alloy replaced with **Atomic Alloy Ingots**, four corners = Elite machine.

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
- One operation yields `result.count x lanes x (1 + 0.5 x speed cards)` items, repeating until the output slots are full.
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
| `baseSeedCapacity` | 64 | Seed slot capacity without upgrade cards |
| `seedCapacityPerUpgrade` | 16 | Capacity added per upgrade card |
| `maxSeedCapacity` | 64 | Hard cap for seed slot capacity (max 127) |
| `maxParallel` | 64 | Parallel lane cap per machine |
| `baseSpeedMultiplier` | 4.0 | Global base speed multiplier |
| `baseEnergyMultiplier` | 0.5 | Global base energy multiplier (0.5 = half cost) |
| `speedUpgradeOutputPerCard` | 0.5 | Items-per-batch bonus per speed card |
| `energyUpgradeEfficiencyPerCard` | 0.5 | Efficiency bonus per energy card |
| `oneTickOutput` | false | Finish one batch every tick (cheat-level, off by default) |

---

## Example numbers (Basic tier, 64 Certus seeds = 4 lanes)

| Upgrades | Items per batch | Batch time | Draw | FE per crystal |
| --- | --- | --- | --- | --- |
| none | 8 | 2.5 s | 1,600 FE/t | 10,000 |
| 2x speed | 16 | 2.5 s | 3,200 FE/t | 10,000 |
| 2x speed + 2x energy | 16 | 2.5 s | 1,600 FE/t | 5,000 |

Ultimate tier with the same 64 seeds: 8 items every 0.3 s at 12,800 FE/t, roughly 9,600 FE per crystal.

---

## FAQ

- **Nothing happens?** Check three things: seed count is a multiple of 16, output slots are not full, and the machine has power.
- **Can I multiply several crystal types at once?** No — one stack, one crystal type. Stack more of it to add lanes.
- **Will addons that change output speed conflict?** No. Speed and yield are computed entirely inside this mod and never read modified values from other mods.
- **Automatable?** Yes. Output slots can be extracted with hoppers or pipes; the seed slot accepts input and never loses its contents.

---

## License

MIT. Source, issues and changelog: https://github.com/furina1013-love/crystal_multiplier
