package com.sci.torcherino.benchmarkharness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class BenchmarkReport {
    private final String suiteId;
    private final String suiteName;
    private final String status;
    private final String reason;
    private final long createdAtMillis;
    private final String javaVersion;
    private final String minecraftVersion;
    private final String harnessVersion;
    private final String serverRoot;
    private final List<ProbeResult> probes;
    private final List<ScenarioResult> scenarios;

    public BenchmarkReport(
        String suiteId,
        String suiteName,
        String status,
        String reason,
        long createdAtMillis,
        String javaVersion,
        String minecraftVersion,
        String harnessVersion,
        String serverRoot,
        List<ProbeResult> probes,
        List<ScenarioResult> scenarios
    ) {
        this.suiteId = suiteId;
        this.suiteName = suiteName;
        this.status = status;
        this.reason = reason;
        this.createdAtMillis = createdAtMillis;
        this.javaVersion = javaVersion;
        this.minecraftVersion = minecraftVersion;
        this.harnessVersion = harnessVersion;
        this.serverRoot = serverRoot;
        this.probes = Collections.unmodifiableList(new ArrayList<ProbeResult>(probes));
        this.scenarios = Collections.unmodifiableList(new ArrayList<ScenarioResult>(scenarios));
    }

    public String getSuiteId() {
        return suiteId;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public String getHarnessVersion() {
        return harnessVersion;
    }

    public String getServerRoot() {
        return serverRoot;
    }

    public List<ProbeResult> getProbes() {
        return probes;
    }

    public List<ScenarioResult> getScenarios() {
        return scenarios;
    }

    public static final class ProbeResult {
        private final String id;
        private final boolean available;
        private final String signature;
        private final String detail;

        public ProbeResult(String id, boolean available, String signature, String detail) {
            this.id = id;
            this.available = available;
            this.signature = signature;
            this.detail = detail;
        }

        public String getId() {
            return id;
        }

        public boolean isAvailable() {
            return available;
        }

        public String getSignature() {
            return signature;
        }

        public String getDetail() {
            return detail;
        }
    }

    public static final class ScenarioResult {
        private final String id;
        private final String displayName;
        private final String kind;
        private final int machineCount;
        private final int torchCount;
        private final String torchVariant;
        private final int expectedMultiplier;
        private final int warmupTicks;
        private final int sampleTicks;
        private final long setupNanos;
        private final long restoreNanos;
        private final String status;
        private final String detail;
        private final String profilerStatus;
        private final String profilerDetail;
        private final List<Map<String, Object>> profilerSnapshot;
        private final List<SampleResult> samples;

        public ScenarioResult(
            String id,
            String displayName,
            String kind,
            int machineCount,
            int torchCount,
            String torchVariant,
            int expectedMultiplier,
            int warmupTicks,
            int sampleTicks,
            long setupNanos,
            long restoreNanos,
            String status,
            String detail,
            String profilerStatus,
            String profilerDetail,
            List<Map<String, Object>> profilerSnapshot,
            List<SampleResult> samples
        ) {
            this.id = id;
            this.displayName = displayName;
            this.kind = kind;
            this.machineCount = machineCount;
            this.torchCount = torchCount;
            this.torchVariant = torchVariant;
            this.expectedMultiplier = expectedMultiplier;
            this.warmupTicks = warmupTicks;
            this.sampleTicks = sampleTicks;
            this.setupNanos = setupNanos;
            this.restoreNanos = restoreNanos;
            this.status = status;
            this.detail = detail;
            this.profilerStatus = profilerStatus;
            this.profilerDetail = profilerDetail;
            this.profilerSnapshot = Collections.unmodifiableList(
                new ArrayList<Map<String, Object>>(profilerSnapshot)
            );
            this.samples = Collections.unmodifiableList(new ArrayList<SampleResult>(samples));
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getKind() {
            return kind;
        }

        public int getMachineCount() {
            return machineCount;
        }

        public int getTorchCount() {
            return torchCount;
        }

        public String getTorchVariant() {
            return torchVariant;
        }

        public int getExpectedMultiplier() {
            return expectedMultiplier;
        }

        public int getWarmupTicks() {
            return warmupTicks;
        }

        public int getSampleTicks() {
            return sampleTicks;
        }

        public long getSetupNanos() {
            return setupNanos;
        }

        public long getRestoreNanos() {
            return restoreNanos;
        }

        public String getStatus() {
            return status;
        }

        public String getDetail() {
            return detail;
        }

        public String getProfilerStatus() {
            return profilerStatus;
        }

        public String getProfilerDetail() {
            return profilerDetail;
        }

        public List<Map<String, Object>> getProfilerSnapshot() {
            return profilerSnapshot;
        }

        public List<SampleResult> getSamples() {
            return samples;
        }

        public TimingStats getServerTickStats() {
            return TimingStats.from(samples, Value.SERVER);
        }

        public TimingStats getWorldTickStats() {
            return TimingStats.from(samples, Value.WORLD);
        }
    }

    public static final class SampleResult {
        private final int index;
        private final long resetNanos;
        private final long worldTickNanos;
        private final long serverTickNanos;
        private final long worldAllocatedBytes;
        private final long serverAllocatedBytes;
        private final long gcCollectionCountDelta;
        private final long gcCollectionTimeMillisDelta;
        private final long heapUsedDeltaBytes;
        private final boolean baselineRestored;
        private final long inventoryBefore;
        private final long inventoryAfter;
        private final long fuelBefore;
        private final long fuelAfter;
        private final long energyBefore;
        private final long energyAfter;
        private final long progressBefore;
        private final long progressAfter;
        private final long outputBefore;
        private final long outputAfter;
        private final long inputConsumed;
        private final long fuelConsumed;
        private final long energyConsumed;
        private final long progressDelta;
        private final long outputDelta;
        private final double throughput;
        private final double unitFuelCost;
        private final double unitEnergyCost;

        public SampleResult(
            int index,
            long resetNanos,
            long worldTickNanos,
            long serverTickNanos,
            long worldAllocatedBytes,
            long serverAllocatedBytes,
            long gcCollectionCountDelta,
            long gcCollectionTimeMillisDelta,
            long heapUsedDeltaBytes,
            WorldArena.MachineMetrics metrics
        ) {
            this.index = index;
            this.resetNanos = resetNanos;
            this.worldTickNanos = worldTickNanos;
            this.serverTickNanos = serverTickNanos;
            this.worldAllocatedBytes = worldAllocatedBytes;
            this.serverAllocatedBytes = serverAllocatedBytes;
            this.gcCollectionCountDelta = gcCollectionCountDelta;
            this.gcCollectionTimeMillisDelta = gcCollectionTimeMillisDelta;
            this.heapUsedDeltaBytes = heapUsedDeltaBytes;
            this.baselineRestored = metrics.isBaselineRestored();
            this.inventoryBefore = metrics.getInventoryBefore();
            this.inventoryAfter = metrics.getInventoryAfter();
            this.fuelBefore = metrics.getFuelBefore();
            this.fuelAfter = metrics.getFuelAfter();
            this.energyBefore = metrics.getEnergyBefore();
            this.energyAfter = metrics.getEnergyAfter();
            this.progressBefore = metrics.getProgressBefore();
            this.progressAfter = metrics.getProgressAfter();
            this.outputBefore = metrics.getOutputBefore();
            this.outputAfter = metrics.getOutputAfter();
            this.inputConsumed = metrics.getInputConsumed();
            this.fuelConsumed = metrics.getFuelConsumed();
            this.energyConsumed = metrics.getEnergyConsumed();
            this.progressDelta = metrics.getProgressDelta();
            this.outputDelta = metrics.getOutputDelta();
            this.throughput = metrics.getThroughput();
            this.unitFuelCost = metrics.getUnitFuelCost();
            this.unitEnergyCost = metrics.getUnitEnergyCost();
        }

        public int getIndex() {
            return index;
        }

        public long getResetNanos() {
            return resetNanos;
        }

        public long getWorldTickNanos() {
            return worldTickNanos;
        }

        public long getServerTickNanos() {
            return serverTickNanos;
        }

        public long getWorldAllocatedBytes() {
            return worldAllocatedBytes;
        }

        public long getServerAllocatedBytes() {
            return serverAllocatedBytes;
        }

        public long getGcCollectionCountDelta() {
            return gcCollectionCountDelta;
        }

        public long getGcCollectionTimeMillisDelta() {
            return gcCollectionTimeMillisDelta;
        }

        public long getHeapUsedDeltaBytes() {
            return heapUsedDeltaBytes;
        }

        public boolean isBaselineRestored() {
            return baselineRestored;
        }

        public long getInventoryBefore() {
            return inventoryBefore;
        }

        public long getInventoryAfter() {
            return inventoryAfter;
        }

        public long getFuelBefore() {
            return fuelBefore;
        }

        public long getFuelAfter() {
            return fuelAfter;
        }

        public long getEnergyBefore() {
            return energyBefore;
        }

        public long getEnergyAfter() {
            return energyAfter;
        }

        public long getProgressBefore() {
            return progressBefore;
        }

        public long getProgressAfter() {
            return progressAfter;
        }

        public long getOutputBefore() {
            return outputBefore;
        }

        public long getOutputAfter() {
            return outputAfter;
        }

        public long getInputConsumed() {
            return inputConsumed;
        }

        public long getFuelConsumed() {
            return fuelConsumed;
        }

        public long getEnergyConsumed() {
            return energyConsumed;
        }

        public long getProgressDelta() {
            return progressDelta;
        }

        public long getOutputDelta() {
            return outputDelta;
        }

        public double getThroughput() {
            return throughput;
        }

        public double getUnitFuelCost() {
            return unitFuelCost;
        }

        public double getUnitEnergyCost() {
            return unitEnergyCost;
        }
    }

    public static final class TimingStats {
        private final int count;
        private final long p50Nanos;
        private final long p95Nanos;
        private final long p99Nanos;
        private final long maxNanos;
        private final int over50Millis;

        private TimingStats(
            int count,
            long p50Nanos,
            long p95Nanos,
            long p99Nanos,
            long maxNanos,
            int over50Millis
        ) {
            this.count = count;
            this.p50Nanos = p50Nanos;
            this.p95Nanos = p95Nanos;
            this.p99Nanos = p99Nanos;
            this.maxNanos = maxNanos;
            this.over50Millis = over50Millis;
        }

        private static TimingStats from(List<SampleResult> samples, Value value) {
            List<Long> durations = new ArrayList<Long>();
            int over50 = 0;
            for (SampleResult sample : samples) {
                long nanos = value.get(sample);
                if (nanos < 0L) {
                    continue;
                }
                durations.add(Long.valueOf(nanos));
                if (nanos > 50_000_000L) {
                    over50++;
                }
            }
            if (durations.isEmpty()) {
                return new TimingStats(0, -1L, -1L, -1L, -1L, 0);
            }
            Collections.sort(durations);
            return new TimingStats(
                durations.size(),
                percentile(durations, 0.50D),
                percentile(durations, 0.95D),
                percentile(durations, 0.99D),
                durations.get(durations.size() - 1).longValue(),
                over50
            );
        }

        private static long percentile(List<Long> values, double percentile) {
            int index = (int) Math.ceil(percentile * values.size()) - 1;
            index = Math.max(0, Math.min(values.size() - 1, index));
            return values.get(index).longValue();
        }

        public int getCount() {
            return count;
        }

        public long getP50Nanos() {
            return p50Nanos;
        }

        public long getP95Nanos() {
            return p95Nanos;
        }

        public long getP99Nanos() {
            return p99Nanos;
        }

        public long getMaxNanos() {
            return maxNanos;
        }

        public int getOver50Millis() {
            return over50Millis;
        }
    }

    private enum Value {
        WORLD {
            @Override
            long get(SampleResult sample) {
                return sample.getWorldTickNanos();
            }
        },
        SERVER {
            @Override
            long get(SampleResult sample) {
                return sample.getServerTickNanos();
            }
        };

        abstract long get(SampleResult sample);
    }
}
