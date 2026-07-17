# Torcherino 1.12.2 Adapter Compatibility Matrix

This matrix records enabled behavior, not planned behavior. A machine remains
on same-tick `ITickable.update()` fallback until differential tests prove a
faster path equivalent.

| Mod | Current classification | Enabled optimization | Forced legacy fallback |
|---|---|---|---|
| Thermal Expansion | `EXACT_FAST_LOOP` where `cofh.api.core.IAccelerable` is present | Cached `MethodHandle` calls the public `updateAccelerable()` contract | Devices, dynamos, cells, unknown signatures, and machines outside the public contract |
| Ender IO CEu 5.4.2 | `EXACT_FAST_LOOP` for verified PoweredTask bases | Calls the original protected `processTasks(boolean)` path without repeating whole-machine passive loss, automatic IO, or sync | Conduits, obelisks, teleportation, spawners, entity interaction, other versions, and unknown signatures |
| Mekanism | `LEGACY_FALLBACK` | None yet | Networks, miners, reactors, turbines, boilers, induction systems, multiblocks, and all unverified processing layouts |
| Actually Additions | `LEGACY_FALLBACK` | None yet | Laser relays, farmers, miners, breakers, placers, fishing, reconstructors, and all unverified processing layouts |
| IC2 Experimental | `LEGACY_FALLBACK` | None yet | Energy net, cables, reactors, crops, miners, teleporters, personal machines, and all unverified processing components |

The runtime probe reports the loaded mod version and a short SHA-256 structural
signature for the first recognized base class. Unknown signatures are never
interpreted as known fields.
