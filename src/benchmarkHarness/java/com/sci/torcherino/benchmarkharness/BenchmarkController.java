package com.sci.torcherino.benchmarkharness;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class BenchmarkController {
    private final Logger logger;
    private final List<BenchmarkReport.ProbeResult> probes =
        Collections.unmodifiableList(BenchmarkEnvironmentProbe.collect());
    private File serverRoot = new File(System.getProperty("user.dir"));
    private Session session;
    private BenchmarkReport lastReport;

    BenchmarkController(Logger logger) {
        this.logger = logger;
    }

    public synchronized void bindServer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        try {
            File root = server.getFile("");
            if (root != null) {
                serverRoot = root.getAbsoluteFile();
            }
        } catch (Throwable failure) {
            logger.warn("Unable to resolve Minecraft server root; using user.dir", failure);
        }
    }

    public synchronized RunRequestResult startFurnaceSuite() {
        return startFurnaceSuite("all");
    }

    public synchronized RunRequestResult startFurnaceSuite(String layer) {
        if (session != null && !session.isFinished()) {
            return RunRequestResult.rejected("A benchmark run is already active");
        }
        final FurnaceSuite suite;
        try {
            suite = FurnaceSuite.create(layer);
        } catch (IllegalArgumentException failure) {
            return RunRequestResult.rejected(failure.getMessage());
        }
        session = new Session(suite);
        return RunRequestResult.accepted(
            "Torcherino benchmark queued: furnace-suite " + layer + ". "
                + "Use /torcherino-bench status for progress."
        );
    }

    public synchronized List<String> describeStatus() {
        List<String> lines = new ArrayList<String>();
        lines.add("Torcherino benchmark harness");
        if (session == null) {
            lines.add(lastReport == null ? "State: idle" : "State: " + lastReport.getStatus());
            if (lastReport != null && lastReport.getReason() != null) {
                lines.add("Reason: " + lastReport.getReason());
            }
            return lines;
        }
        lines.add("State: " + session.getStatus());
        lines.add(
            "Scenario: " + session.currentScenarioLabel()
                + " (" + session.currentScenarioIndex() + "/"
                + session.scenarioCount() + ")"
        );
        lines.add(
            "Progress: warmup " + session.warmupRemaining()
                + "/" + session.suite.getWarmupTicks()
                + ", measured " + session.measuredRemaining()
                + "/" + session.suite.getSampleTicks()
        );
        if (session.blockedReason() != null) {
            lines.add("Reason: " + session.blockedReason());
        }
        if (session.lastServerTickNanos() >= 0L) {
            lines.add("Last server tick: " + formatMillis(session.lastServerTickNanos()) + " ms");
        }
        if (session.lastWorldTickNanos() >= 0L) {
            lines.add("Last world tick: " + formatMillis(session.lastWorldTickNanos()) + " ms");
        }
        lines.add("Arena: " + session.arenaDescription());
        return lines;
    }

    public synchronized ExportResult export(File target) {
        BenchmarkReport report = snapshotReport();
        if (report == null) {
            return ExportResult.rejected("No benchmark report is available yet");
        }
        try {
            BenchmarkReportWriter.write(report, resolveExportTarget(target));
            lastReport = report;
            return ExportResult.accepted(
                "Wrote benchmark report to " + resolveExportTarget(target).getAbsolutePath()
            );
        } catch (IOException failure) {
            logger.warn("Failed to export Torcherino benchmark report", failure);
            return ExportResult.rejected("Export failed: " + failure.getMessage());
        }
    }

    public synchronized File defaultExportDirectory() {
        return new File(serverRoot, "export");
    }

    private File resolveExportTarget(File target) {
        File requested = target == null ? defaultExportDirectory() : target;
        if (requested.isAbsolute()) {
            return requested;
        }
        return new File(serverRoot, requested.getPath());
    }

    public synchronized void shutdown() {
        if (session != null) {
            session.interrupt("server stopping");
            lastReport = session.snapshot();
            session = null;
        }
    }

    @SubscribeEvent
    public synchronized void onServerTick(TickEvent.ServerTickEvent event) {
        if (session == null) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            session.onServerTickStart();
        } else {
            session.onServerTickEnd();
            if (session.isFinished()) {
                lastReport = session.snapshot();
                session = null;
            }
        }
    }

    @SubscribeEvent
    public synchronized void onWorldTick(TickEvent.WorldTickEvent event) {
        if (session == null
            || event.side != Side.SERVER
            || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        if (event.phase == TickEvent.Phase.START) {
            session.onWorldTickStart(world);
        } else {
            session.onWorldTickEnd(world);
        }
    }

    private BenchmarkReport snapshotReport() {
        return session == null ? lastReport : session.snapshot();
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    public static final class RunRequestResult {
        private final boolean accepted;
        private final String message;

        private RunRequestResult(boolean accepted, String message) {
            this.accepted = accepted;
            this.message = message;
        }

        static RunRequestResult accepted(String message) {
            return new RunRequestResult(true, message);
        }

        static RunRequestResult rejected(String message) {
            return new RunRequestResult(false, message);
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class ExportResult {
        private final boolean accepted;
        private final String message;

        private ExportResult(boolean accepted, String message) {
            this.accepted = accepted;
            this.message = message;
        }

        static ExportResult accepted(String message) {
            return new ExportResult(true, message);
        }

        static ExportResult rejected(String message) {
            return new ExportResult(false, message);
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getMessage() {
            return message;
        }
    }

    private final class Session {
        private final BenchmarkSuite suite;
        private final List<BenchmarkSuite.Scenario> scenarios;
        private final List<BenchmarkReport.ScenarioResult> completedScenarios =
            new ArrayList<BenchmarkReport.ScenarioResult>();
        private final List<BenchmarkReport.SampleResult> samples =
            new ArrayList<BenchmarkReport.SampleResult>();
        private final TickWindow tickWindow = new TickWindow();

        private BenchmarkSuite.Scenario currentScenario;
        private WorldArena arena;
        private ProfilerBridge profiler;
        private WorldServer benchmarkWorld;
        private WorldArena.MachineObservation beforeObservation;
        private WorldArena.MachineObservation afterObservation;
        private String currentStatus = "preparing";
        private String blockedReason;
        private boolean scenePrepared;
        private boolean skipCurrentTick = true;
        private boolean pendingReset;
        private boolean worldTickStarted;
        private boolean finished;
        private boolean currentScenarioRecorded;
        private int scenarioIndex;
        private int warmupRemaining;
        private int measuredRemaining;
        private long currentSetupNanos;
        private long sceneRestoreNanos;
        private long lastResetNanos;
        private long lastServerTickNanos = -1L;
        private long lastWorldTickNanos = -1L;

        private Session(BenchmarkSuite suite) {
            this.suite = suite;
            this.scenarios = suite.getScenarios();
            this.currentScenario = scenarios.get(0);
            this.warmupRemaining = suite.getWarmupTicks();
            this.measuredRemaining = suite.getSampleTicks();
            this.profiler = ProfilerBridge.discover();
        }

        private boolean isFinished() {
            return finished;
        }

        private String getStatus() {
            return finished ? "completed" : currentStatus;
        }

        private String blockedReason() {
            return blockedReason;
        }

        private String currentScenarioLabel() {
            return currentScenario == null ? "n/a" : currentScenario.getDisplayName();
        }

        private int currentScenarioIndex() {
            return Math.min(scenarioIndex + 1, scenarios.size());
        }

        private int scenarioCount() {
            return scenarios.size();
        }

        private int warmupRemaining() {
            return warmupRemaining;
        }

        private int measuredRemaining() {
            return measuredRemaining;
        }

        private long lastServerTickNanos() {
            return lastServerTickNanos;
        }

        private long lastWorldTickNanos() {
            return lastWorldTickNanos;
        }

        private String arenaDescription() {
            return arena == null ? "not prepared" : arena.describeOrigin();
        }

        private void onServerTickStart() {
            if (finished) {
                return;
            }
            tickWindow.beginServer();
            if (!scenePrepared) {
                return;
            }
            if (pendingReset) {
                long resetStart = System.nanoTime();
                try {
                    arena.resetToBaseline();
                    lastResetNanos = System.nanoTime() - resetStart;
                    pendingReset = false;
                } catch (BenchmarkEnvironmentException failure) {
                    blockCurrentScenario(failure.getMessage());
                    return;
                }
            }
            if (skipCurrentTick) {
                return;
            }
        }

        private void onWorldTickStart(WorldServer world) {
            if (finished) {
                return;
            }
            if (!scenePrepared) {
                prepareScenario(world);
                return;
            }
            if (world != benchmarkWorld || skipCurrentTick || worldTickStarted) {
                return;
            }
            try {
                beforeObservation = arena.captureMachineObservation();
                tickWindow.beginWorld();
                worldTickStarted = true;
            } catch (BenchmarkEnvironmentException failure) {
                blockCurrentScenario(failure.getMessage());
            }
        }

        private void onWorldTickEnd(WorldServer world) {
            if (finished || world != benchmarkWorld || !worldTickStarted) {
                return;
            }
            try {
                afterObservation = arena.captureMachineObservation();
                tickWindow.endWorld();
                lastWorldTickNanos = tickWindow.getWorldTickNanos();
                worldTickStarted = false;
            } catch (BenchmarkEnvironmentException failure) {
                worldTickStarted = false;
                blockCurrentScenario(failure.getMessage());
            }
        }

        private void onServerTickEnd() {
            if (finished) {
                return;
            }
            if (!scenePrepared) {
                tickWindow.reset();
                return;
            }
            if (skipCurrentTick) {
                skipCurrentTick = false;
                pendingReset = true;
                tickWindow.reset();
                return;
            }
            if (worldTickStarted || beforeObservation == null || afterObservation == null) {
                blockCurrentScenario("Benchmark world tick did not complete");
                tickWindow.reset();
                return;
            }
            tickWindow.endServer();
            lastServerTickNanos = tickWindow.getServerTickNanos();
            if (warmupRemaining > 0) {
                warmupRemaining--;
            } else if (measuredRemaining > 0) {
                WorldArena.MachineMetrics metrics = arena.measure(
                    beforeObservation,
                    afterObservation
                );
                samples.add(new BenchmarkReport.SampleResult(
                    samples.size(),
                    lastResetNanos,
                    tickWindow.getWorldTickNanos(),
                    tickWindow.getServerTickNanos(),
                    tickWindow.getWorldAllocatedBytes(),
                    tickWindow.getServerAllocatedBytes(),
                    tickWindow.getGcCollectionCountDelta(),
                    tickWindow.getGcCollectionTimeMillisDelta(),
                    tickWindow.getHeapUsedDeltaBytes(),
                    metrics
                ));
                measuredRemaining--;
            }
            beforeObservation = null;
            afterObservation = null;
            pendingReset = true;
            lastResetNanos = 0L;
            tickWindow.reset();
            if (measuredRemaining <= 0) {
                completeCurrentScenario();
            }
        }

        private void prepareScenario(WorldServer world) {
            long setupStart = System.nanoTime();
            try {
                BenchmarkEnvironmentProbe.requireCoreEnvironment();
                arena = new WorldArena(world);
                currentScenario.build(arena);
                benchmarkWorld = world;
                scenePrepared = true;
                currentStatus = "warming-up";
                blockedReason = null;
                pendingReset = false;
                skipCurrentTick = true;
                currentSetupNanos = System.nanoTime() - setupStart;
                sceneRestoreNanos = 0L;
                lastResetNanos = 0L;
                lastServerTickNanos = -1L;
                lastWorldTickNanos = -1L;
                samples.clear();
                tickWindow.reset();
                profiler = ProfilerBridge.discover();
                profiler.start();
            } catch (BenchmarkEnvironmentException failure) {
                blockCurrentScenario(failure.getMessage());
            } catch (Throwable failure) {
                blockCurrentScenario(
                    "Scenario setup failed: " + failure.getClass().getSimpleName()
                        + ": " + String.valueOf(failure.getMessage())
                );
            }
        }

        private void completeCurrentScenario() {
            currentStatus = "completed";
            finishProfiler();
            teardownArena();
            recordCurrentScenario("completed", null);
            if (!advanceScenario()) {
                finished = true;
                logger.info("Torcherino furnace suite completed");
            }
        }

        private void blockCurrentScenario(String reason) {
            if (currentScenario == null || currentScenarioRecorded) {
                return;
            }
            blockedReason = reason == null ? "environment-blocked" : reason;
            currentStatus = "environment-blocked";
            finishProfiler();
            teardownArena();
            recordCurrentScenario(currentStatus, blockedReason);
            if (!advanceScenario()) {
                finished = true;
                logger.info("Torcherino furnace suite completed");
            }
        }

        private void finishProfiler() {
            if (profiler != null) {
                profiler.snapshot();
                profiler.stop();
            }
        }

        private void teardownArena() {
            if (arena != null) {
                sceneRestoreNanos = arena.restoreOriginal();
            } else {
                sceneRestoreNanos = 0L;
            }
            arena = null;
            benchmarkWorld = null;
            scenePrepared = false;
            worldTickStarted = false;
            beforeObservation = null;
            afterObservation = null;
            pendingReset = false;
        }

        private boolean advanceScenario() {
            scenarioIndex++;
            if (scenarioIndex >= scenarios.size()) {
                currentScenario = null;
                return false;
            }
            currentScenario = scenarios.get(scenarioIndex);
            currentScenarioRecorded = false;
            blockedReason = null;
            currentStatus = "preparing";
            skipCurrentTick = true;
            warmupRemaining = suite.getWarmupTicks();
            measuredRemaining = suite.getSampleTicks();
            currentSetupNanos = 0L;
            sceneRestoreNanos = 0L;
            lastResetNanos = 0L;
            lastServerTickNanos = -1L;
            lastWorldTickNanos = -1L;
            samples.clear();
            tickWindow.reset();
            return true;
        }

        private void recordCurrentScenario(String status, String detail) {
            if (currentScenarioRecorded || currentScenario == null) {
                return;
            }
            completedScenarios.add(buildScenarioResult(status, detail));
            currentScenarioRecorded = true;
        }

        private BenchmarkReport.ScenarioResult buildScenarioResult(
            String status,
            String detail
        ) {
            List<java.util.Map<String, Object>> profilerSnapshot =
                profiler == null ? Collections.<java.util.Map<String, Object>>emptyList()
                    : profiler.snapshot();
            return new BenchmarkReport.ScenarioResult(
                currentScenario.getId(),
                currentScenario.getDisplayName(),
                currentScenario.getKind().name(),
                currentScenario.getMachineCount(),
                currentScenario.getTorchCount(),
                currentScenario.getTorchVariant(),
                currentScenario.getExpectedMultiplier(),
                suite.getWarmupTicks(),
                suite.getSampleTicks(),
                currentSetupNanos,
                sceneRestoreNanos,
                status,
                detail,
                profiler == null ? "unavailable" : profiler.getStatus(),
                profiler == null ? "not-started" : profiler.getDetail(),
                profilerSnapshot,
                new ArrayList<BenchmarkReport.SampleResult>(samples)
            );
        }

        private BenchmarkReport.ScenarioResult currentScenarioSnapshot() {
            return buildScenarioResult(currentStatus, blockedReason);
        }

        private BenchmarkReport snapshot() {
            List<BenchmarkReport.ScenarioResult> scenarioResults =
                new ArrayList<BenchmarkReport.ScenarioResult>(completedScenarios);
            if (currentScenario != null && !currentScenarioRecorded) {
                scenarioResults.add(currentScenarioSnapshot());
            }
            return new BenchmarkReport(
                suite.getId(),
                suite.getDisplayName(),
                finished ? "completed" : "running",
                blockedReason,
                System.currentTimeMillis(),
                System.getProperty("java.version"),
                "1.12.2",
                BenchmarkHarnessMod.VERSION,
                serverRoot.getAbsolutePath(),
                probes,
                scenarioResults
            );
        }

        private void interrupt(String reason) {
            if (finished) {
                return;
            }
            finishProfiler();
            teardownArena();
            currentStatus = "interrupted";
            if (currentScenario != null && !currentScenarioRecorded) {
                recordCurrentScenario(currentStatus, reason);
            }
            finished = true;
        }
    }

    private static final class TickWindow {
        private final List<GarbageCollectorMXBean> collectors =
            ManagementFactory.getGarbageCollectorMXBeans();
        private final com.sun.management.ThreadMXBean threadBean;
        private final long threadId;
        private long serverStartNanos = -1L;
        private long worldStartNanos = -1L;
        private long serverAllocStart = -1L;
        private long worldAllocStart = -1L;
        private long gcCountStart = -1L;
        private long gcTimeStart = -1L;
        private long heapStart = -1L;
        private long serverTickNanos = -1L;
        private long worldTickNanos = -1L;
        private long serverAllocatedBytes = -1L;
        private long worldAllocatedBytes = -1L;
        private long gcCollectionCountDelta = -1L;
        private long gcCollectionTimeMillisDelta = -1L;
        private long heapUsedDeltaBytes = -1L;

        private TickWindow() {
            java.lang.management.ThreadMXBean platform =
                ManagementFactory.getThreadMXBean();
            if (platform instanceof com.sun.management.ThreadMXBean) {
                threadBean = (com.sun.management.ThreadMXBean) platform;
                try {
                    if (threadBean.isThreadAllocatedMemorySupported()
                        && !threadBean.isThreadAllocatedMemoryEnabled()) {
                        threadBean.setThreadAllocatedMemoryEnabled(true);
                    }
                } catch (Throwable ignored) {
                    // Allocation metrics remain -1 when the JVM denies this optional API.
                }
            } else {
                threadBean = null;
            }
            threadId = Thread.currentThread().getId();
        }

        private void beginServer() {
            serverStartNanos = System.nanoTime();
            serverAllocStart = allocatedBytes();
            gcCountStart = gcCount();
            gcTimeStart = gcTime();
            heapStart = heapUsed();
        }

        private void beginWorld() {
            worldStartNanos = System.nanoTime();
            worldAllocStart = allocatedBytes();
        }

        private void endWorld() {
            if (worldStartNanos < 0L) {
                return;
            }
            worldTickNanos = System.nanoTime() - worldStartNanos;
            worldAllocatedBytes = delta(worldAllocStart, allocatedBytes());
        }

        private void endServer() {
            if (serverStartNanos < 0L) {
                return;
            }
            serverTickNanos = System.nanoTime() - serverStartNanos;
            serverAllocatedBytes = delta(serverAllocStart, allocatedBytes());
            gcCollectionCountDelta = delta(gcCountStart, gcCount());
            gcCollectionTimeMillisDelta = delta(gcTimeStart, gcTime());
            heapUsedDeltaBytes = delta(heapStart, heapUsed());
        }

        private void reset() {
            serverStartNanos = -1L;
            worldStartNanos = -1L;
            serverAllocStart = -1L;
            worldAllocStart = -1L;
            gcCountStart = -1L;
            gcTimeStart = -1L;
            heapStart = -1L;
            serverTickNanos = -1L;
            worldTickNanos = -1L;
            serverAllocatedBytes = -1L;
            worldAllocatedBytes = -1L;
            gcCollectionCountDelta = -1L;
            gcCollectionTimeMillisDelta = -1L;
            heapUsedDeltaBytes = -1L;
        }

        private long getServerTickNanos() {
            return serverTickNanos;
        }

        private long getWorldTickNanos() {
            return worldTickNanos;
        }

        private long getServerAllocatedBytes() {
            return serverAllocatedBytes;
        }

        private long getWorldAllocatedBytes() {
            return worldAllocatedBytes;
        }

        private long getGcCollectionCountDelta() {
            return gcCollectionCountDelta;
        }

        private long getGcCollectionTimeMillisDelta() {
            return gcCollectionTimeMillisDelta;
        }

        private long getHeapUsedDeltaBytes() {
            return heapUsedDeltaBytes;
        }

        private long allocatedBytes() {
            if (threadBean == null) {
                return -1L;
            }
            try {
                return threadBean.getThreadAllocatedBytes(threadId);
            } catch (Throwable ignored) {
                return -1L;
            }
        }

        private long heapUsed() {
            return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        }

        private long gcCount() {
            long total = 0L;
            for (GarbageCollectorMXBean collector : collectors) {
                long value = collector.getCollectionCount();
                if (value >= 0L) {
                    total += value;
                }
            }
            return total;
        }

        private long gcTime() {
            long total = 0L;
            for (GarbageCollectorMXBean collector : collectors) {
                long value = collector.getCollectionTime();
                if (value >= 0L) {
                    total += value;
                }
            }
            return total;
        }

        private long delta(long start, long end) {
            return start < 0L || end < 0L ? -1L : end - start;
        }
    }
}
