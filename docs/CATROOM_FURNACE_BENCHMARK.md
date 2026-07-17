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
```

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

Current artifacts:

```text
B8F059575DEA76A9CEA2D721849A076E29937D18EC2F3FB77B873ADA6B0128C4
build/libs/torcherino-8.0.0-alpha.6.jar

1B8BC1E8188FFFDACB574FDFE7095B988FEF153C8CA4103815369005273FC874
build/libs/torcherino-benchmark-harness-8.0.0-alpha.6.jar
```
