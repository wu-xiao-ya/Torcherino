# CatRoom Furnace Benchmark

This benchmark compares Torcherino 7.6, the scheduler-only refactor baseline,
and the current alpha.6 implementation on the CatRoom/Cleanroom server supplied
for this project.

## Locked Environment

- Core: `cleanroom/cleanroom-0.1.0.jar`
- Core SHA-256:
  `DDDF6FE1F26FF9CA6EE70483C5B2B72C653E6FCF1573CEF0E91E3D7AADDF8962`
- Java: JDK 21
- Server root: `cleanroom/benchmark-server`
- Reports: `build/reports/torcherino/catroom-furnace-benchmark`

The server world is disposable. The runner does not use a production world and
does not modify or repackage CatRoom, Thermal Expansion, Ender IO, EnderCore, or
their dependencies.

## Artifacts

Build the production mod and the independent harness:

```powershell
& 'C:\Users\zpyn1\Downloads\zulu21-win_x64\bin\java.exe' `
  '-Dorg.gradle.appname=gradlew' `
  -classpath '.\gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain `
  remapJar verifyPublishedJarManifest `
  remapBenchmarkHarnessJar verifyBenchmarkHarnessJar
```

The harness is emitted as
`build/libs/torcherino-benchmark-harness-8.0.0-alpha.6.jar`. It is not included
in the Torcherino release Jar.

## Candidate Baseline

Commit `c8bcaae` is not independently buildable: it references API classes
introduced by the next commit. The runner therefore creates a synthetic,
adapter-free build closure:

- Scheduler logic marker: `c8bcaae`
- Current `BlockPos.toLong()` coordinate-layout correction
- Required API closure: `93a16c9`
- Modern build infrastructure: `e66d257`
- Compatibility catalog: generated empty implementation

This candidate is reported as `c8bcaae-scheduler-baseline`. It contains no
machine adapters. Reports must not describe it as a byte-for-byte build of the
unbuildable `c8bcaae` commit. The coordinate correction is required because the
original planner packed `y` and `z` in the wrong bit ranges; without it, planned
targets decode into unrelated or unloaded chunks and the candidate does not
exercise machine fallback at all.

## Scenarios

The `furnace-suite` command runs 20 warmup samples and 100 measured samples for
each scenario. It accepts an optional layer selector:

```text
/torcherino-bench run furnace-suite all
/torcherino-bench run furnace-suite vanilla
/torcherino-bench run furnace-suite thermal
/torcherino-bench run furnace-suite thermal80
/torcherino-bench run furnace-suite enderio
/torcherino-bench run furnace-suite thermal80 timing
```

`diagnostic` is the default mode and enables the current Torcherino profiler
when available. `timing` keeps the profiler disabled for all candidates so
server-tick comparisons with Torcherino 7.6 do not include current-only
diagnostic bookkeeping.

The matrix contains:

- 64 vanilla furnaces with one maximum-range torch at multipliers
  `1`, `4`, `36`, and `324`
- One vanilla furnace with `1`, `16`, and `64` distinct overlapping torches
- 16 Thermal Expansion Redstone Furnaces at `1`, `4`, `36`, and `324`
- 80 Thermal Expansion Redstone Furnaces in a centered 9x9 stress layout at
  `1`, `4`, `36`, and `324` when the `thermal80` layer is selected
- 16 Ender IO Alloy Smelters at `1`, `4`, `36`, and `324`

Optional-mod machines are found through the Forge registry and accessed through
reflection or capabilities. The harness requires the exact target machine
class, a writable deterministic input path, and a precharged energy capability.
A machine scene that cannot be constructed safely is exported as
`environment-blocked`; the target mod Jar is never patched.

## Running

Dry-run and preflight:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\catroom-benchmark\Run-CatroomFurnaceBenchmark.ps1 `
  -DryRun

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\catroom-benchmark\Run-CatroomFurnaceBenchmark.ps1 `
  -Preflight
```

Full three-candidate run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\catroom-benchmark\Run-CatroomFurnaceBenchmark.ps1
```

The runner defaults to the project CatRoom core, Torcherino 7.6 and target-mod
Jars in the Starlight Tech instance, and the current alpha.6/Harness artifacts.
Each candidate is started separately for the `vanilla`, `thermal`, and
`enderio` layers, so an optional-mod startup failure cannot invalidate another
layer. Use `-BenchmarkLayers vanilla`, for example, to select a subset. All
paths can be overridden with script parameters.

## Output

The harness exports raw Markdown, CSV, and JSON. The runner adds:

- per-candidate `run.json`
- CatRoom, Torcherino, Harness, target-mod, Java, and configuration hashes
- server logs and crash reports
- a three-candidate `summary.md`

Tick time and Torcherino machine-path time are reported separately. Scheduler
improvements and machine-adapter improvements must also remain separate:

- `7.6` to scheduler baseline measures centralized scheduling
- scheduler baseline to alpha.6 measures exact machine adapters

Ender IO startup failure caused by the supplied CatRoom/EnderCore combination is
reported as `environment-blocked`; the runner does not repair or replace the
third-party Jar.

## Formal Environment Result

Zulu OpenJDK `21.0.1` starts the locked CatRoom core and Thermal Expansion
successfully. JDK 17 cannot load the Java 21 bytecode in the core, while JDK 25
fails during Forge capability initialization because the core calls removed
`jdk.internal.misc.Unsafe.putObject/getObject` methods. JDK 21 is therefore the
formal runtime.

Thermal Expansion `5.5.7.1` and CodeChickenLib `3.2.4.1` pass the isolated
startup probe on JDK 21. Ender IO remains independently blocked: EnderCore
`0.5.81` fails its transformer under the core's ASM 9.6 with
`V1_5 or less must use F_NEW frames`. The original Ender IO and EnderCore Jars
remain unchanged.

## Verified Results

Reports created before the `BlockPos.toLong()` coordinate correction are
invalid for acceleration conclusions because their planner decoded targets into
unloaded chunks. The first valid current vanilla report is:

```text
build/reports/torcherino/catroom-furnace-benchmark/
20260717-172437-alpha.6-current-vanilla/
```

It records `2,509,056` vanilla-furnace fallback ticks in the x324 array scenario,
but the 64 furnaces advance by only 64 aggregate progress units. The CatRoom
configuration has `enableSkipTileEntityTick: false`, so this is not the optional
global tile-skip setting. In this environment, repeated direct vanilla furnace
updates in one world tick do not produce matching virtual progress. Vanilla
timings are therefore diagnostic fallback-cost data, not an acceleration
speedup result.

The valid Thermal comparison uses:

```text
20260717-174337-c8bcaae-scheduler-baseline-thermal/
20260717-174641-alpha.6-current-thermal/
```

Each scenario has 100 measured samples and restores the same active-machine NBT
before every sample. All samples report `baselineRestored=true`. The current
Thermal adapter calls the public CoFH `updateAccelerable()` path for continuous
progress and invokes the original machine `update()` only at processing
boundaries or when the fast path cannot consume energy.

| Multiplier | State comparison | Scheduler P50/P95/P99 ms | Current P50/P95/P99 ms |
| ---: | --- | --- | --- |
| 1 | energy and progress equal | 0.986 / 1.454 / 1.802 | 0.928 / 1.674 / 2.420 |
| 4 | energy and progress equal | 0.838 / 2.501 / 48.459 | 0.760 / 1.314 / 2.342 |
| 36 | energy and progress equal | 0.692 / 0.933 / 1.027 | 0.630 / 0.862 / 0.964 |
| 324 | energy, progress, and 32 outputs equal | 0.856 / 1.123 / 1.162 | 0.738 / 1.009 / 1.068 |

The 16-machine Thermal array shows a measurable reduction, but not a tenfold
whole-server improvement: at x324 the current P50 is about 13.8% lower and P95
about 10.2% lower than the scheduler-only fallback baseline. Larger machine
arrays are still required before making a production-scale performance claim.

### Torcherino 7.6 Upgrade Comparison

The direct player-upgrade comparison uses:

```text
20260717-173853-torcherino-7.6-thermal/
20260717-174641-alpha.6-current-thermal/
```

At x36 and x324, the old compressed Torcherino tile does not execute Thermal
acceleration during every measured CatRoom tick. The x36 scenario has 14 active
samples out of 100, while x324 has 5. Reporting all-sample P50 would therefore
make 7.6 look faster by counting mostly idle ticks. The table below compares
ticks that performed equivalent machine work and separately reports total work
over the same 100-tick window.

| Multiplier | Active samples, 7.6/current | 7.6/current active P50 ms | Current active-tick reduction | Total work ratio |
| ---: | --- | --- | ---: | ---: |
| 1 | 100 / 100 | 1.007 / 0.928 | 7.8% | 1.00x |
| 4 | 100 / 100 | 0.744 / 0.760 | -2.2% | 1.00x |
| 36 | 14 / 100 | 0.730 / 0.630 | 13.7% | 7.01x |
| 324 | 5 / 100 | 1.261 / 0.738 | 41.5% | 19.95x |

For x324, each active sample processes approximately the same energy and emits
32 outputs. Torcherino 7.6 produces 160 items across the 100 measured ticks;
the current scheduler produces 3,200, a 20x effective-throughput difference in
this CatRoom test. This combines the centralized scheduler's reliable dispatch
with the Thermal boundary-aware fast loop, so it represents the practical
7.6-to-current upgrade rather than adapter-only CPU savings.

### Thermal 80-Machine Stress Result

The `thermal80` layer places 80 Redstone Furnaces in a centered 9x9 layout under
one maximum-range torch. Valid reports:

```text
20260717-185836-torcherino-7.6-thermal80/
20260717-190205-c8bcaae-scheduler-baseline-thermal80/
20260717-190913-alpha.6-current-thermal80/
```

| Multiplier | Active samples, 7.6/current | 7.6/current active P50 ms | Current P50 change | Total work ratio |
| ---: | --- | --- | ---: | ---: |
| 1 | 100 / 100 | 1.679 / 1.785 | 6.3% slower | 1.00x |
| 4 | 100 / 100 | 1.557 / 1.666 | 7.0% slower | 1.00x |
| 36 | 13 / 100 | 1.499 / 1.610 | 7.4% slower | 7.51x |
| 324 | 5 / 100 | 2.847 / 1.888 | 33.7% faster | 19.95x |

The 80-machine result confirms that the low-multiplier difference is real fixed
scheduler overhead rather than only measurement noise. At x4, the current
adapter is effectively tied with the scheduler-only baseline
(`1.666 ms` versus `1.675 ms`), but the centralized scheduler remains about 7%
slower than 7.6's direct single-torch path in this non-overlapping layout.

At x36, the current version spends slightly more time on each active tick but
executes on every measured tick, producing 7.51x the total processing work. At
x324 it is both faster per active tick and reliable every tick: 7.6 emits 800
items across the window, while the current version emits 16,000. One current
x324 run contains a roughly 49 ms P99 outlier, so the P50 comparison is the
stable result; additional process-level repetitions are required for a strong
tail-latency claim.

### Profiler-Off Timing Comparison

The fair cross-version timing pass disables the profiler for every candidate.
All exported scenarios report `profilerStatus=disabled-timing` and an empty
profiler snapshot:

```text
20260717-192812-torcherino-7.6-thermal80/
20260717-192851-alpha.6-current-thermal80/
20260717-192930-c8bcaae-scheduler-baseline-thermal80/
```

| Multiplier | 7.6 active P50 ms | Scheduler P50 ms | Current P50 ms | Current versus 7.6 |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 1.493 | 1.932 | 1.524 | 2.1% slower |
| 4 | 1.393 | 1.626 | 1.463 | 5.0% slower |
| 36 | 1.544 | 1.510 | 1.440 | 6.7% faster per active tick |
| 324 | 1.990 | 2.194 | 1.971 | approximately equal |

Disabling the profiler reduces the apparent low-multiplier regression: the x1
gap falls from 6.3% to 2.1%, and x4 falls from 7.0% to 5.0%. The remaining x4
cost is the centralized scheduler's fixed target-dispatch overhead. The
adapter is still useful relative to the same scheduler architecture: current
x4 is about 10.0% faster than the adapter-free scheduler baseline.

Torcherino 7.6 performs only 5 active samples at x36 and x324 in this run.
Current processes every sample, resulting in 19.53x the x36 work and 19.95x the
x324 work. At x324, 7.6 emits 800 items and current emits 16,000. Thus the
profiler-off result no longer supports a large per-active-tick CPU claim against
7.6 at x324; the verified gain is reliable every-tick dispatch and 20x effective
throughput, plus about 10% lower P50 than the scheduler-only fallback.

### Thermal Batch and Target Cache Follow-up

The next pass replaces per-virtual-tick `updateAccelerable()` calls with a
version-locked continuous-processing batch for Thermal Expansion 5.5.7
metadata / 5.5.7.1 jar structure. It advances `processRem` and internal RF with
the original integer energy curve, writes each continuous segment once, and
returns to the original machine `update()` at recipe and stalled-energy
boundaries. A 100,000-state randomized differential test compares the batch
recurrence with tick-by-tick `calcEnergy()` behavior.

The scheduler follow-up also caches immutable `BlockPos` and
`AccelerationContext` values per covered coordinate, avoids the second block
state lookup for non-random-tick blocks, and skips adapter timing calls while
the profiler is disabled. Loaded-chunk, block state, TileEntity identity,
invalidation, and replacement checks remain on the server thread.

Verified reports:

```text
20260717-200506-alpha.6-current-thermal80/  batch repeat 1
20260717-200625-alpha.6-current-thermal80/  batch repeat 2 and adapter probe
20260717-201041-alpha.6-current-thermal80/  batch plus target cache
```

The adapter probe reports:

```text
thermalexpansion:official-accelerable [EXACT_FAST_LOOP] enabled
thermalexpansion@5.5.7/.../batch#fb4510656f72
verified Thermal continuous-process batch advancement
```

| Multiplier | 7.6 P50 ms | Scheduler P50 ms | Pre-batch current P50 ms | Batch + cache P50 ms | Change from pre-batch |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 1.493 | 1.932 | 1.524 | 1.518 | 0.4% faster |
| 4 | 1.393 | 1.626 | 1.463 | 1.410 | 3.6% faster |
| 36 | 1.544 | 1.510 | 1.440 | 1.415 | 1.8% faster |
| 324 | 1.990 | 2.194 | 1.971 | 1.468 | 25.5% faster |

The two batch-only x324 repeats measured `1.597 ms` and `1.557 ms` P50.
Adding the immutable target cache reduced the next run to `1.468 ms`. At x1
and x4 the current implementation is now within about 2% of Torcherino 7.6's
direct single-torch path while retaining centralized overlap handling and
reliable every-tick dispatch. At x36 and x324 it is respectively about 8% and
26% faster per active server tick than 7.6 in this 80-machine layout.

All 400 measured samples in the final run report `baselineRestored=true`.
Energy, progress, and output remain consistent. At x36 and x324, 5 of 100
samples include one additional normal world-machine tick; the remaining 95
contain only the requested virtual multiplier. This is the harness's server
event ordering and accounts exactly for the one-tick state difference.

### Low-Multiplier Dispatch Follow-up

The low-multiplier pass compiles stable coverage into parallel
`TargetExecutionContext[]` and multiplier arrays whenever the coverage
revision changes. Steady server ticks no longer iterate the coverage hash map
or look up immutable coordinate contexts by hash. Empty and non-tickable
positions return before entering the reentrancy set or slow-target timer.

Adapter selection is also cached for the exact TileEntity instance when the
adapter explicitly declares its support decision stable. External adapters
remain non-cacheable by default. Tile replacement, invalidation, chunk unload,
runtime adapter disable, or registry changes invalidate the route.

Common immutable `AdvanceResult` values and stable `AdapterDispatch` values are
reused. A fully consumed adapter result no longer allocates a fallback-validity
closure when its fallback count is zero. Stable block-state metadata caches
the block, random-tick flag, TileEntity flag, and blacklist result; a blacklist
revision counter invalidates that metadata immediately when a block is added
to the runtime blacklist.

Absolute timings were variable during this later host session, so the final
result uses two directly adjacent processes rather than comparing with an
earlier baseline:

```text
20260717-233833-alpha.6-current-thermal80/
20260717-233953-torcherino-7.6-thermal80/
```

| Multiplier | 7.6 P50 ms | Current P50 ms | Current change |
| ---: | ---: | ---: | ---: |
| 1 | 1.744 | 1.670 | 4.2% faster |
| 4 | 1.672 | 1.549 | 7.3% faster |
| 36 | 1.607 active | 1.564 | 2.7% faster per active tick |
| 324 | 2.664 active | 1.893 | 28.9% faster per active tick |

All current samples report `baselineRestored=true`. P95 and P99 remain noisy
because of unrelated host scheduling spikes, so the low-load acceptance result
is based on paired-process P50. The x1 and x4 paths are now measurably below
Torcherino 7.6 in the same 80-machine, one-torch layout while retaining
centralized overlap semantics.

Current artifacts:

```text
407AE8D5E7944FFCA951D84AC3E5030340A90F107B7CCC25B18F2E24EE5A501E
build/libs/torcherino-8.0.0-alpha.6.jar

05AB93F803C6E08A7DE486D12800C5BA1B18CB6D73F9BEADA02B9CCDE35F9B42
build/libs/torcherino-benchmark-harness-8.0.0-alpha.6.jar
```

### Discovery-Interval Sparse Follow-up

The discovery-interval branch adds a `thermal1` layer containing one active
Thermal Redstone Furnace inside one maximum-range Torcherino coverage volume.
The other 242 covered positions are empty. This isolates the cost of checking
empty positions from the cost of executing a dense machine array.

Run the same current jar twice in timing mode, changing only
`discoveryIntervalTicks` between `1` and `20`:

```powershell
.\tools\catroom-benchmark\Run-CatroomFurnaceBenchmark.ps1 `
  -JavaHome 'C:\Users\zpyn1\Downloads\zulu21-win_x64' `
  -Candidate current `
  -BenchmarkLayers thermal1 `
  -BenchmarkMode timing `
  -SkipEnderIoEnvironmentProbe `
  -SkipThermalEnvironmentProbe `
  -ServerPort 25566 `
  -RconPort 25576
```

The interval does not delay removal safety. Cached targets are validated every
server tick and are discarded immediately when their block, TileEntity,
loaded-chunk state, blacklist status, or adapter execution becomes invalid.
Only discovery of a newly placed target in a previously empty covered position
can wait for the next full scan.

The CatRoom timing comparison used the same remapped Torcherino jar
(`E2258D5363529CA96AC78FF6E4C2FD50F82511BAE4CB397A82A9FF7A0F351440`)
for both runs. Only `discoveryIntervalTicks` changed.

| Multiplier | Interval 1 world P50 ms | Interval 20 world P50 ms | World P50 change | Interval 1 server P50 ms | Interval 20 server P50 ms | Server P50 change | Median world allocation change |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0.4312 | 0.3923 | 9.0% lower | 0.7750 | 0.7395 | 4.6% lower | 8.4% lower |
| 4 | 0.3310 | 0.2965 | 10.4% lower | 0.5768 | 0.5311 | 7.9% lower | 8.6% lower |
| 36 | 0.2667 | 0.2564 | 3.9% lower | 0.4687 | 0.4461 | 4.8% lower | 8.6% lower |
| 324 | 0.3041 | 0.2674 | 12.1% lower | 0.4759 | 0.4503 | 5.4% lower | 8.2% lower |

All 800 measured samples restored the same baseline and produced identical
inventory, energy, progress, and output state signatures. A full discovery
tick is deliberately more expensive than a cached tick, so sparse P95 can
include the periodic scan. The optimization targets steady low-load cost and
allocation rather than hiding that scan.

The removal probe used one configured maximum-range Torcherino and one vanilla
furnace in a loaded benchmark chunk:

```text
before removal:             targets=1 discoveryIn=18 discoveryScans=32
about two server ticks later: targets=0 discoveryIn=14 discoveryScans=32
replacement before scan:    targets=0 discoveryIn=10 discoveryScans=32
replacement after scan:     targets=1 discoveryIn=7  discoveryScans=33
```

This confirms that removal does not wait for the discovery interval. A newly
placed machine in an empty covered position is intentionally admitted by the
next discovery scan.
