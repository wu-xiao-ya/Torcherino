# Torcherino 1.12.2 Adapter Compatibility Matrix

This matrix records enabled behavior, not planned behavior. A machine remains
on same-tick `ITickable.update()` fallback until differential tests prove a
faster path equivalent.

| Mod | Current classification | Enabled optimization | Forced legacy fallback |
|---|---|---|---|
| Thermal Expansion | `EXACT_FAST_LOOP` where `cofh.api.core.IAccelerable` is present | Multipliers below 5 use an adapter-local exact direct loop; higher multipliers call the public `updateAccelerable()` contract and return to original `update()` at recipe-completion or stalled-energy boundaries | Devices, dynamos, cells, unknown signatures, and machines outside the public contract |
| Ender IO CEu 5.4.2 | `EXACT_FAST_LOOP` for verified PoweredTask bases | Calls the original protected `processTasks(boolean)` path without repeating whole-machine passive loss, automatic IO, or sync | Conduits, obelisks, teleportation, spawners, entity interaction, other versions, and unknown signatures |
| Mekanism CE Unofficial 10.0.1.455 | `BLACKLIST` for `TileEntityRestrictedTick` | None; repeated full updates are rejected by the mod's same-world-tick guard and processing uses its asynchronous task executor | All restricted-tick tiles; no guard reset, task-executor bypass, or target-jar patching |
| Actually Additions r152 | `LEGACY_FALLBACK` for audited processing machines | None; Grinder, Double Furnace, Canola Press, and Fermenting Barrel have no isolated processing entry point | Their `updateEntity()` path also invokes neighboring energy or fluid sharing; world-interacting machines remain fallback |
| IC2 Experimental 2.8.188-ex112 | `LEGACY_FALLBACK` for audited standard machines | None; `TileEntityStandardMachine.updateEntityServer()` also calls `upgradeSlot.tickNoMark()` | Ejector and pulling upgrades can touch neighboring inventories, so the protected method is not an isolated processing entry point; energy net, cables, reactors, crops, miners, teleporters, and personal machines remain fallback |

The runtime probe reports the loaded mod version and a short SHA-256 structural
signature for the first recognized base class. Unknown signatures are never
interpreted as known fields.
