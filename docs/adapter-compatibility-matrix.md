# Torcherino 1.12.2 Adapter Compatibility Matrix

This matrix records enabled behavior, not planned behavior. A machine remains
on same-tick `ITickable.update()` fallback until differential tests prove a
faster path equivalent.

| Mod | Current classification | Enabled optimization | Forced legacy fallback |
|---|---|---|---|
| Thermal Expansion | `EXACT_FAST_LOOP` where `cofh.api.core.IAccelerable` is present | Multipliers below 5 use an adapter-local exact direct loop. The verified 5.5.7 metadata / 5.5.7.1 file structure batches continuous `processRem` and RF advancement, then returns to original `update()` at recipe-completion or stalled-energy boundaries. Unknown structures retain the public `updateAccelerable()` exact loop | Devices, dynamos, cells, unknown machine bases, and machines outside the public contract |
| Ender IO CEu 5.4.2 | `EXACT_FAST_LOOP` for verified PoweredTask bases | Calls the original protected `processTasks(boolean)` path without repeating whole-machine passive loss, automatic IO, or sync | Conduits, obelisks, teleportation, spawners, entity interaction, other versions, and unknown signatures |
| Mekanism CE Unofficial 10.0.1.455 | `BLACKLIST` for `TileEntityRestrictedTick` | None; repeated full updates are rejected by the mod's same-world-tick guard and processing uses its asynchronous task executor | All restricted-tick tiles; no guard reset, task-executor bypass, or target-jar patching |
| Actually Additions r152 | `EXACT_BATCH` for Canola Press and Fermenting Barrel | Advances progress to the next recipe boundary, batches energy or fluid movement, and uses the original AA inventories and Forge tanks at completion. Neighboring energy/fluid sharing remains real-tick only | Grinder and Double Furnace remain fallback until sound, active block state, auto-split, and random secondary-output events pass differential tests; world-interacting machines remain fallback |
| IC2 Experimental 2.8.188-ex112 | `EXACT_FAST_LOOP` for standard machines with empty upgrade slots | Calls the original `getOutput()`, `useEnergy()`, private `operate()`, active-state methods, and IC2 network events without repeating discharge-slot or upgrade-slot ticks | Any installed upgrade forces immediate legacy fallback. Subclasses overriding `updateEntityServer()`, energy net, cables, reactors, crops, miners, teleporters, and personal machines remain fallback |

The runtime probe reports the loaded mod version and a short SHA-256 structural
signature for the first recognized base class. Unknown signatures are never
interpreted as known fields.

The Thermal batch path additionally verifies `TileMachineBase`, `TilePowered`,
`EnergyConfig`, and `EnergyStorage` class hashes before binding field access.
It does not modify or repackage any CoFH jar.

The Actually Additions adapter verifies the complete r152 Canola Press and
Fermenting Barrel class hashes before binding fields and original helper
methods. The IC2 adapter verifies both standard and electric machine base
hashes, rechecks the upgrade slot for every dispatch, and never caches support
for a machine whose upgrade contents can change.

Adapter target matching is cached only when an adapter explicitly returns
`true` from `canCacheSupportForInstance()`. The default is `false` for external
adapters. Cached routes are tied to the exact TileEntity identity and registry
generation, and are discarded when the tile disappears, is replaced, becomes
invalid, its chunk unloads, or the adapter registry changes.
