package com.sci.torcherino.benchmark;

import com.sci.torcherino.acceleration.CoveragePlan;
import com.sci.torcherino.acceleration.CoveragePlanner;
import com.sci.torcherino.acceleration.CoverageSnapshot;
import com.sci.torcherino.acceleration.TorchSnapshot;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CoverageBenchmarkMain {
    private static final int WARMUP_SAMPLES = 20;
    private static final int MEASURED_SAMPLES = 100;
    private static volatile Object objectSink;
    private static volatile long longSink;

    private CoverageBenchmarkMain() {
    }

    public static void main(String[] args) throws IOException {
        Path output = Paths.get(args[0]);
        Files.createDirectories(output.getParent());

        List<String> lines = new ArrayList<String>();
        lines.add("# Torcherino Scheduler Microbenchmark");
        lines.add("");
        lines.add("Generated with Java " + System.getProperty("java.version") + ".");
        lines.add(
            "This benchmark isolates steady-state coordinate traversal and allocation. "
                + "It excludes world access and machine update cost."
        );
        lines.add("");
        lines.add("| Scenario | Torches | Legacy positions | Central positions | Position reduction | Legacy avg us | Central avg us | Legacy P95 us | Central P95 us | Legacy P99 us | Central P99 us | Legacy alloc B | Central alloc B |");
        lines.add("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");

        int[] counts = new int[]{1, 16, 64};
        for (int count : counts) {
            append(lines, "empty", empty(count));
            append(lines, "dense-disjoint", dense(count));
            append(lines, "fully-overlapped", overlapped(count));
        }

        lines.add("");
        lines.add(
            "Allocation values use the current JVM thread-allocation counter when available; "
                + "-1 means the counter is unavailable."
        );
        Files.write(output, lines, StandardCharsets.UTF_8);
        System.out.println("Wrote " + output.toAbsolutePath());
    }

    private static void append(List<String> lines, String scenario, Fixture fixture) {
        Result legacy = measure(new LegacyTraversal(fixture.torches));
        Result central = measure(new CentralTraversal(fixture.plan));
        long legacyPositions = fixture.legacyPositions;
        long centralPositions = fixture.plan.getCoverage().size();
        double reduction = legacyPositions == 0L
            ? 0.0D
            : 100.0D * (legacyPositions - centralPositions) / legacyPositions;

        lines.add(
            "| " + scenario
                + " | " + fixture.torches.length
                + " | " + legacyPositions
                + " | " + centralPositions
                + " | " + format(reduction) + "%"
                + " | " + format(legacy.averageNanos / 1_000.0D)
                + " | " + format(central.averageNanos / 1_000.0D)
                + " | " + format(legacy.p95Nanos / 1_000.0D)
                + " | " + format(central.p95Nanos / 1_000.0D)
                + " | " + format(legacy.p99Nanos / 1_000.0D)
                + " | " + format(central.p99Nanos / 1_000.0D)
                + " | " + legacy.averageAllocatedBytes
                + " | " + central.averageAllocatedBytes
                + " |"
        );
    }

    private static Fixture empty(int count) {
        TorchSnapshot[] torches = new TorchSnapshot[count];
        for (int i = 0; i < count; i++) {
            torches[i] = new TorchSnapshot(pack(i * 16, 64, 0), 0, 324);
        }
        return fixture(torches);
    }

    private static Fixture dense(int count) {
        TorchSnapshot[] torches = new TorchSnapshot[count];
        for (int i = 0; i < count; i++) {
            torches[i] = new TorchSnapshot(pack(i * 16, 64, 0), 4, 324);
        }
        return fixture(torches);
    }

    private static Fixture overlapped(int count) {
        TorchSnapshot[] torches = new TorchSnapshot[count];
        for (int i = 0; i < count; i++) {
            torches[i] = new TorchSnapshot(pack(0, 64, 0), 4, 324);
        }
        return fixture(torches);
    }

    private static Fixture fixture(TorchSnapshot[] torches) {
        CoveragePlan plan = CoveragePlanner.compute(
            new CoverageSnapshot(1, 0, 1, torches)
        );
        long legacyPositions = 0L;
        for (TorchSnapshot torch : torches) {
            if (torch.isActive()) {
                int width = torch.getRange() * 2 + 1;
                legacyPositions += (long) width * width * 3L;
            }
        }
        return new Fixture(torches, plan, legacyPositions);
    }

    private static Result measure(Runnable traversal) {
        for (int i = 0; i < WARMUP_SAMPLES; i++) {
            traversal.run();
        }

        long[] nanos = new long[MEASURED_SAMPLES];
        long[] allocated = new long[MEASURED_SAMPLES];
        AllocationCounter counter = AllocationCounter.create();
        for (int i = 0; i < MEASURED_SAMPLES; i++) {
            long allocationStart = counter.currentBytes();
            long start = System.nanoTime();
            traversal.run();
            nanos[i] = System.nanoTime() - start;
            long allocationEnd = counter.currentBytes();
            allocated[i] = allocationStart < 0L || allocationEnd < 0L
                ? -1L
                : allocationEnd - allocationStart;
        }
        Arrays.sort(nanos);
        Arrays.sort(allocated);
        return new Result(
            average(nanos),
            percentile(nanos, 95),
            percentile(nanos, 99),
            allocated[0] < 0L ? -1L : Math.round(average(allocated))
        );
    }

    private static long percentile(long[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0D * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static double average(long[] values) {
        double sum = 0.0D;
        for (long value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
            | ((long) z & 0x3FFFFFFL) << 12
            | ((long) y & 0xFFFL);
    }

    private static final class LegacyTraversal implements Runnable {
        private final TorchSnapshot[] torches;

        private LegacyTraversal(TorchSnapshot[] torches) {
            this.torches = torches;
        }

        @Override
        public void run() {
            long checksum = 0L;
            for (TorchSnapshot torch : torches) {
                if (!torch.isActive()) {
                    continue;
                }
                int x = (int) (torch.getPos() >> 38);
                int y = unpackY(torch.getPos());
                int z = (int) (torch.getPos() << 26 >> 38);
                for (int dx = -torch.getRange(); dx <= torch.getRange(); dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -torch.getRange(); dz <= torch.getRange(); dz++) {
                            LegacyPosition position = new LegacyPosition(x + dx, y + dy, z + dz);
                            objectSink = position;
                            checksum += position.packed;
                        }
                    }
                }
            }
            longSink = checksum;
        }
    }

    private static final class CentralTraversal implements Runnable {
        private final CoveragePlan plan;

        private CentralTraversal(CoveragePlan plan) {
            this.plan = plan;
        }

        @Override
        public void run() {
            long checksum = 0L;
            java.util.Iterator<Long2IntMap.Entry> iterator =
                Long2IntMaps.fastIterator(plan.getCoverage());
            while (iterator.hasNext()) {
                Long2IntMap.Entry entry = iterator.next();
                LegacyPosition position = LegacyPosition.fromLong(entry.getLongKey());
                objectSink = position;
                checksum += position.packed + entry.getIntValue();
            }
            longSink = checksum;
        }
    }

    private static final class LegacyPosition {
        private final long packed;

        private LegacyPosition(int x, int y, int z) {
            packed = pack(x, y, z);
        }

        private static LegacyPosition fromLong(long packed) {
            return new LegacyPosition(
                (int) (packed >> 38),
                unpackY(packed),
                (int) (packed << 26 >> 38)
            );
        }
    }

    private static int unpackY(long packed) {
        int y = (int) (packed & 0xFFFL);
        return y >= 2048 ? y - 4096 : y;
    }

    private static final class Fixture {
        private final TorchSnapshot[] torches;
        private final CoveragePlan plan;
        private final long legacyPositions;

        private Fixture(TorchSnapshot[] torches, CoveragePlan plan, long legacyPositions) {
            this.torches = torches;
            this.plan = plan;
            this.legacyPositions = legacyPositions;
        }
    }

    private static final class Result {
        private final double averageNanos;
        private final long p95Nanos;
        private final long p99Nanos;
        private final long averageAllocatedBytes;

        private Result(
            double averageNanos,
            long p95Nanos,
            long p99Nanos,
            long averageAllocatedBytes
        ) {
            this.averageNanos = averageNanos;
            this.p95Nanos = p95Nanos;
            this.p99Nanos = p99Nanos;
            this.averageAllocatedBytes = averageAllocatedBytes;
        }
    }

    private static final class AllocationCounter {
        private final com.sun.management.ThreadMXBean bean;
        private final long threadId;

        private AllocationCounter(com.sun.management.ThreadMXBean bean) {
            this.bean = bean;
            this.threadId = Thread.currentThread().getId();
        }

        private static AllocationCounter create() {
            java.lang.management.ThreadMXBean platform = ManagementFactory.getThreadMXBean();
            if (!(platform instanceof com.sun.management.ThreadMXBean)) {
                return new AllocationCounter(null);
            }
            com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) platform;
            if (!bean.isThreadAllocatedMemorySupported()) {
                return new AllocationCounter(null);
            }
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            return new AllocationCounter(bean);
        }

        private long currentBytes() {
            return bean == null ? -1L : bean.getThreadAllocatedBytes(threadId);
        }
    }
}
