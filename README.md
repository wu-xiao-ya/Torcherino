# Torcherino 1.12.2 Refactor

This branch preserves the original `torcherino` mod id, block registry names,
TileEntity ids, recipes, and legacy NBT while replacing per-torch world scans
with one acceleration manager per server world.

## Execution model

- Torcherino tiles publish immutable range and multiplier snapshots.
- Coverage is updated immediately on the server thread and rebuilt by one
  daemon planner from primitive-only snapshots.
- The planner never receives a `World`, `TileEntity`, capability, inventory, or
  mutable game object.
- All block and TileEntity updates remain on the logical server thread.
- Overlapping torches retain legacy summed multipliers.
- Unloaded chunks are skipped and are never loaded by acceleration.
- Unknown machines execute their original `ITickable.update()` once per
  requested virtual tick.
- Covered-position discovery runs once per 20-tick cycle by default. Already
  discovered machines and random-tick blocks are still validated and
  accelerated every tick, so removal, invalidation, replacement, and chunk
  unload stop the cached target immediately. Newly placed targets can wait up
  to one discovery cycle. Optional discovery slices can spread the scan across
  the cycle when lower scan spikes matter more than median tick cost.

## Exact adapters

Third-party mods can implement `IBatchedAcceleratable` or register an
`IExactAccelerationAdapter` through `TorcherinoAPI`. An adapter must account for
every requested virtual tick. Any unconsumed ticks are executed through the
legacy update path in the same server tick.

Built-in structural audits cover Thermal Expansion, Ender IO, Mekanism,
Actually Additions, and IC2 Experimental. Unknown class signatures and
world-interacting machines deliberately remain on the exact legacy fallback
until a differential test proves a faster path equivalent.

Thermal Expansion uses its public `IAccelerable` entry point. Ender IO CEu
5.4.2 powered-task machines use their original protected task processor without
repeating whole-machine passive energy loss, network sync, or automatic IO for
every virtual tick. Other Ender IO versions fail closed to the legacy path.

Mekanism CE Unofficial 10.0.1.455 deliberately rejects repeated `update()` calls
in the same world tick and schedules processing through its own task executor.
Torcherino therefore blacklists its restricted-tick tiles instead of issuing
hundreds of ineffective or potentially concurrent calls.

Actually Additions r152 Canola Presses and Fermenting Barrels use
boundary-aware exact batches without repeating neighboring energy or fluid
sharing. Grinders and Double Furnaces remain on the legacy path until their
sound, block-state, and random-output events pass differential testing.

IC2 Experimental 2.8.188 standard processing machines use their original
recipe, energy, operation, and network-event methods when their upgrade slots
are empty. Machines with ejector, pulling, overclocker, or other upgrades
immediately return to the legacy path so upgrade and neighboring-inventory
behavior is not folded into virtual ticks.

## Administration

- `/torcherino adapters` shows adapter availability and structural signatures.
- `/torcherino plan` shows active dimensions, torches, positions, and revisions.
- `/torcherino profile start`
- `/torcherino profile status`
- `/torcherino profile stop`

Configuration remains at `config/sci4me/Torcherino.cfg`. New defaults are
`strictExecution=true`, `overlapMode=LEGACY_SUM`, `asyncPlanner=true`,
`adapterMode=EXACT_ONLY`, `loadChunks=false`, and
`discoveryIntervalTicks=20`, `discoverySlices=1`. Set
`discoveryIntervalTicks=1` to scan every covered position on every server
tick. With the default interval, set `discoverySlices=4` to scan one quarter
of the coverage every five ticks, or `discoverySlices=20` to scan one
twentieth every tick. Valid values are `1..1200`; values above the interval
are clamped.

The strict, overlap, adapter, and chunk-loading semantics are fixed by this
branch. Diagnostic thresholds remain configurable.

## Building

The project uses Gradle, Unimined, and the Cleanroom toolchain:

```powershell
.\gradlew.bat build
```

GitHub Actions builds with Java 25 and uploads the remapped jar and reports.
