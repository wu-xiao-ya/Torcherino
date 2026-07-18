# Torcherino 1.12.2 Adapter Compatibility Matrix

This matrix records enabled behavior, not planned behavior. A machine remains
on same-tick `ITickable.update()` fallback until differential tests prove a
faster path equivalent.

| Mod | Current classification | Enabled optimization | Forced legacy fallback |
|---|---|---|---|
| Thermal Expansion | `EXACT_FAST_LOOP` where `cofh.api.core.IAccelerable` is present | Multipliers below 5 use an adapter-local exact direct loop. The verified 5.5.7 metadata / 5.5.7.1 file structure batches continuous `processRem` and RF advancement, then returns to original `update()` at recipe-completion or stalled-energy boundaries. Unknown structures retain the public `updateAccelerable()` exact loop | Devices, dynamos, cells, unknown machine bases, and machines outside the public contract |
| Ender IO CEu 5.4.2 | `EXACT_FAST_LOOP` for verified PoweredTask bases; CatRoom runtime validation is `environment-blocked` | Calls the original protected `processTasks(boolean)` path without repeating whole-machine passive loss, automatic IO, or sync | Conduits, obelisks, teleportation, spawners, entity interaction, other versions, and unknown signatures |
| Mekanism CE Unofficial 10.0.1.455 | `BLACKLIST` for `TileEntityRestrictedTick` | None; repeated full updates are rejected by the mod's same-world-tick guard and processing uses its asynchronous task executor | All restricted-tick tiles; no guard reset, task-executor bypass, or target-jar patching |
| Actually Additions r152 | Hybrid exact path for Canola Press and Fermenting Barrel; CatRoom differential verification passed | `x1-x4` uses the original same-tick `update()` loop directly, avoiding batch-dispatch overhead. `x5+` advances to recipe boundaries, batches energy or fluid movement, and uses the original AA inventories and Forge tanks at completion. Neighbor sharing remains real-tick only on the batch path | Grinder and Double Furnace remain fallback until sound, active block state, auto-split, and random secondary-output events pass differential tests; world-interacting machines remain fallback |
| IC2 Experimental 2.8.188-ex112 | `EXACT_BATCH` for the Macerator with an empty upgrade slot; CatRoom differential verification passed | Caches the stable Macerator route, checks the upgrade slot on every dispatch, batches continuous progress and EU consumption, then calls the original private `operate()` and IC2 network events. Discharge-slot, component, and upgrade-slot ticks remain real-tick only | Compressor and other standard machines remain fallback because they did not retain the audited adapter route after a real server tick. Any installed upgrade forces immediate legacy execution. Energy net, cables, reactors, crops, miners, teleporters, and personal machines remain fallback |

The runtime probe reports the loaded mod version and a short SHA-256 structural
signature for the first recognized base class. Unknown signatures are never
interpreted as known fields.

The Thermal batch path additionally verifies `TileMachineBase`, `TilePowered`,
`EnergyConfig`, and `EnergyStorage` class hashes before binding field access.
It does not modify or repackage any CoFH jar.

The Actually Additions adapter verifies the complete r152 Canola Press and
Fermenting Barrel class hashes before binding fields and original helper
methods. The IC2 adapter verifies the standard-machine, electric-machine, and
Macerator class hashes. It caches only the stable runtime-class route and
rechecks the upgrade slot inside every dispatch, so installing or removing an
upgrade takes effect without rebuilding the route.

Adapter target matching is cached only when an adapter explicitly returns
`true` from `canCacheSupportForInstance()`. The default is `false` for external
adapters. Cached routes are tied to the exact TileEntity identity and registry
generation, and are discarded when the tile disappears, is replaced, becomes
invalid, its chunk unloads, or the adapter registry changes.

## CatRoom adapter verification

The benchmark-only command below compares the original machine processing body
against the production adapter from identical initial states:

```text
/torcherino-bench verify adapters
```

The July 18, 2026 run on the locked CatRoom/Cleanroom server passed all 13
checks:

- Actually Additions Canola Press at `1`, `4`, `36`, and `324` virtual ticks.
- Actually Additions Fermenting Barrel at `1`, `4`, `36`, and `324` virtual ticks.
- IC2 Macerator at `1`, `4`, `36`, and `324` virtual ticks.
- IC2 with an overclocker installed selected legacy fallback instead of the
  exact adapter.

The comparison includes processing progress, input/output inventory, stored
energy, fluid amounts, and active state where applicable. The verifier is
contained only in the benchmark harness and is not packaged into the
Torcherino release Jar.

The benchmark-only command below measures only the extra machine-processing
path. Scene reset, state comparison, world tick overhead, and Torcherino
coverage scheduling are excluded:

```text
/torcherino-bench measure adapters
```

Each run uses 8 warmup samples and 40 measured samples. The table reports the
median of three independent CatRoom process runs; each cell is `P50 / P95` in
microseconds.

| Machine | Virtual ticks | Legacy update path | Current route | P50 speedup |
|---|---:|---:|---:|---:|
| AA Canola Press | 4 | `0.387 / 0.889` | `0.395 / 0.564` | `0.98x` |
| AA Canola Press | 36 | `4.700 / 5.443` | `4.318 / 8.656` | `1.09x` |
| AA Canola Press | 324 | `43.475 / 65.300` | `7.300 / 10.775` | `5.96x` |
| AA Fermenting Barrel | 4 | `0.285 / 0.314` | `0.312 / 0.375` | `0.91x` |
| AA Fermenting Barrel | 36 | `2.562 / 2.993` | `1.150 / 2.656` | `2.23x` |
| AA Fermenting Barrel | 324 | `25.550 / 28.725` | `1.225 / 1.700` | `20.86x` |
| IC2 Macerator | 4 | `3.317 / 5.654` | `2.904 / 4.017` | `1.14x` |
| IC2 Macerator | 36 | `19.043 / 26.668` | `2.562 / 3.487` | `7.43x` |
| IC2 Macerator | 324 | `125.000 / 176.825` | `11.175 / 181.225` | `11.19x` |

At 4 virtual ticks the AA paths are now effectively at legacy-loop cost. The
remaining P50 difference is about `0.008 us` for the Canola Press and `0.027
us` for the Fermenting Barrel. The IC2 Macerator is faster at both P50 and P95.
Compared with alpha.10, the measured x4 current-route cost fell by about 81%
for the Canola Press, 70% for the Fermenting Barrel, and 77% for the Macerator.

The Canola x36 P95 and IC2 x324 P95 remained sensitive to host contention, so
P50 is the more stable comparison for those rows. At x324 the P50 CPU
reduction is about 83.2% for the Canola Press, 95.2% for the Fermenting Barrel,
and 91.1% for the IC2 Macerator. These figures are not whole-server TPS
multipliers.

Verified artifacts:

| Artifact | SHA-256 |
|---|---|
| `torcherino-8.0.0-alpha.11.jar` (GitHub Actions) | `09C86EED79AD5B33381C972452AE84B1BCBB8EDEA457EAD9454C3AF6F29675E5` |
| `torcherino-benchmark-harness-8.0.0-alpha.11.jar` | `174D8D5F30619F7D167D1FACA2A4905792D94B4974BF737D1E7C999AB4A57D43` |
| `ActuallyAdditions-1.12.2-r152.jar` | `21311E401F36A58222A50B6F0D66AC29614D7AB039C961DCC307FE499DA030B6` |
| `IndustrialCraft 2.jar` (`2.8.188-ex112`) | `9AED86A1B91ED929CCD21D1AE61CF63C8C232DF81F4FF7B9611E008D0DF1B00B` |

Ender IO CEu `5.4.2` remains blocked before mod initialization on the locked
CatRoom core. EnderCore `0.5.81` fails inside its own transformer against ASM
`9.6` with `Class versions V1_5 or less must use F_NEW frames`. Torcherino,
Ender IO, and EnderCore are not reached far enough for an adapter test, and no
target-mod Jar is patched or repackaged.
