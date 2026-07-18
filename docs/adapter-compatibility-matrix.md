# Torcherino 1.12.2 Adapter Compatibility Matrix

This matrix records enabled behavior, not planned behavior. A machine remains
on same-tick `ITickable.update()` fallback until differential tests prove a
faster path equivalent.

| Mod | Current classification | Enabled optimization | Forced legacy fallback |
|---|---|---|---|
| Thermal Expansion | `EXACT_FAST_LOOP` where `cofh.api.core.IAccelerable` is present | Multipliers below 5 use an adapter-local exact direct loop. The verified 5.5.7 metadata / 5.5.7.1 file structure batches continuous `processRem` and RF advancement, then returns to original `update()` at recipe-completion or stalled-energy boundaries. Unknown structures retain the public `updateAccelerable()` exact loop | Devices, dynamos, cells, unknown machine bases, and machines outside the public contract |
| Ender IO CEu 5.4.2 | `EXACT_FAST_LOOP` for verified PoweredTask bases; CatRoom runtime validation is `environment-blocked` | Calls the original protected `processTasks(boolean)` path without repeating whole-machine passive loss, automatic IO, or sync | Conduits, obelisks, teleportation, spawners, entity interaction, other versions, and unknown signatures |
| Mekanism CE Unofficial 10.0.1.455 | `BLACKLIST` for `TileEntityRestrictedTick` | None; repeated full updates are rejected by the mod's same-world-tick guard and processing uses its asynchronous task executor | All restricted-tick tiles; no guard reset, task-executor bypass, or target-jar patching |
| Actually Additions r152 | `EXACT_BATCH` for Canola Press and Fermenting Barrel; CatRoom differential verification passed | Advances progress to the next recipe boundary, batches energy or fluid movement, and uses the original AA inventories and Forge tanks at completion. Neighboring energy/fluid sharing remains real-tick only | Grinder and Double Furnace remain fallback until sound, active block state, auto-split, and random secondary-output events pass differential tests; world-interacting machines remain fallback |
| IC2 Experimental 2.8.188-ex112 | `EXACT_BATCH` for the Macerator with an empty upgrade slot; CatRoom differential verification passed | Batches continuous progress and EU consumption to the next recipe or energy boundary, then calls the original private `operate()` and IC2 network events. Discharge-slot, component, and upgrade-slot ticks remain real-tick only | Compressor and other standard machines remain fallback because they did not retain the audited adapter route after a real server tick. Any installed upgrade also forces immediate legacy fallback. Energy net, cables, reactors, crops, miners, teleporters, and personal machines remain fallback |

The runtime probe reports the loaded mod version and a short SHA-256 structural
signature for the first recognized base class. Unknown signatures are never
interpreted as known fields.

The Thermal batch path additionally verifies `TileMachineBase`, `TilePowered`,
`EnergyConfig`, and `EnergyStorage` class hashes before binding field access.
It does not modify or repackage any CoFH jar.

The Actually Additions adapter verifies the complete r152 Canola Press and
Fermenting Barrel class hashes before binding fields and original helper
methods. The IC2 adapter verifies the standard-machine, electric-machine, and
Macerator class hashes, rechecks the exact runtime class and upgrade slot for
every dispatch, and never caches support for a machine whose upgrade contents
can change.

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

| Machine | Virtual ticks | Legacy update path | Exact adapter | P50 speedup |
|---|---:|---:|---:|---:|
| AA Canola Press | 4 | `0.575 / 1.117` | `2.092 / 4.131` | `0.27x` |
| AA Canola Press | 36 | `4.612 / 6.312` | `3.137 / 4.375` | `1.47x` |
| AA Canola Press | 324 | `51.800 / 106.925` | `11.525 / 13.775` | `4.49x` |
| AA Fermenting Barrel | 4 | `0.312 / 0.354` | `1.051 / 1.610` | `0.30x` |
| AA Fermenting Barrel | 36 | `2.356 / 2.693` | `1.056 / 1.781` | `2.23x` |
| AA Fermenting Barrel | 324 | `24.000 / 27.450` | `1.775 / 2.300` | `13.52x` |
| IC2 Macerator | 4 | `3.790 / 5.668` | `12.725 / 15.240` | `0.30x` |
| IC2 Macerator | 36 | `23.056 / 28.493` | `12.500 / 15.243` | `1.84x` |
| IC2 Macerator | 324 | `177.775 / 244.550` | `23.625 / 37.725` | `7.52x` |

At 4 virtual ticks the fixed adapter and reflective dispatch cost is larger
than the saved work. At 36 ticks all three supported machine paths are faster.
At 324 ticks the measured machine-path CPU reduction is about 77.8% for the
Canola Press, 92.6% for the Fermenting Barrel, and 86.7% for the IC2 Macerator.
These figures are not whole-server TPS multipliers.

Verified artifacts:

| Artifact | SHA-256 |
|---|---|
| `torcherino-8.0.0-alpha.10.jar` (GitHub Actions) | `3688F87FA108B99E3D0CC4F26828ECF70D5702056FCC1603642DE097F0F47CF8` |
| `torcherino-benchmark-harness-8.0.0-alpha.10.jar` | `A0010EB5AB7CD258E2443F4A33E6A48495C38D97BA41DF5C2AE47761AA93338A` |
| `ActuallyAdditions-1.12.2-r152.jar` | `21311E401F36A58222A50B6F0D66AC29614D7AB039C961DCC307FE499DA030B6` |
| `IndustrialCraft 2.jar` (`2.8.188-ex112`) | `9AED86A1B91ED929CCD21D1AE61CF63C8C232DF81F4FF7B9611E008D0DF1B00B` |

Ender IO CEu `5.4.2` remains blocked before mod initialization on the locked
CatRoom core. EnderCore `0.5.81` fails inside its own transformer against ASM
`9.6` with `Class versions V1_5 or less must use F_NEW frames`. Torcherino,
Ender IO, and EnderCore are not reached far enough for an adapter test, and no
target-mod Jar is patched or repackaged.
