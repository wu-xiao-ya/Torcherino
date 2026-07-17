# CatRoom Furnace Benchmark

This benchmark compares Torcherino 7.6, the scheduler-only refactor baseline,
and the current alpha.6 implementation on the CatRoom/Cleanroom server supplied
for this project.

## Locked Environment

- Core: `cleanroom/cleanroom-0.1.0.jar`
- Core SHA-256:
  `DDDF6FE1F26FF9CA6EE70483C5B2B72C653E6FCF1573CEF0E91E3D7AADDF8962`
- Java: JDK 25
- Server root: `cleanroom/benchmark-server`
- Reports: `build/reports/torcherino/catroom-furnace-benchmark`

The server world is disposable. The runner does not use a production world and
does not modify or repackage CatRoom, Thermal Expansion, Ender IO, EnderCore, or
their dependencies.

## Artifacts

Build the production mod and the independent harness:

```powershell
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe' `
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
- Required API closure: `93a16c9`
- Modern build infrastructure: `e66d257`
- Compatibility catalog: generated empty implementation

This candidate is reported as `c8bcaae-scheduler-baseline`. It contains no
machine adapters. Reports must not describe it as a byte-for-byte build of the
unbuildable `c8bcaae` commit.

## Scenarios

The `furnace-suite` command runs 20 warmup samples and 100 measured samples for
each scenario. It accepts an optional layer selector:

```text
/torcherino-bench run furnace-suite all
/torcherino-bench run furnace-suite vanilla
/torcherino-bench run furnace-suite thermal
/torcherino-bench run furnace-suite enderio
```

The matrix contains:

- 64 vanilla furnaces with one maximum-range torch at multipliers
  `1`, `4`, `36`, and `324`
- One vanilla furnace with `1`, `16`, and `64` distinct overlapping torches
- 16 Thermal Expansion Redstone Furnaces at `1`, `4`, `36`, and `324`
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

The locked CatRoom core cannot currently enter a server world on JDK 25. Forge
capability initialization fails before Torcherino code runs because the core
calls the removed `jdk.internal.misc.Unsafe.putObject` method.

The target-mod probes also stop before machine benchmarking:

- Thermal Expansion requires CodeChickenLib 3.2.4.1, which calls the removed
  `jdk.internal.misc.Unsafe.getObject` method on this core/JDK combination.
- EnderCore 0.5.81 fails its transformer under the core's ASM 9.6 with
  `V1_5 or less must use F_NEW frames`.

These are reported as `environment-blocked`, with original Jars and hashes
preserved. No performance multiplier is emitted for the formal CatRoom
environment until the locked core/JDK combination can reach normal world ticks.
