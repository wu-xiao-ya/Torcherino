package com.sci.torcherino.diagnostics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sci.torcherino.Torcherino;
import com.sci.torcherino.acceleration.AccelerationMetricsSnapshot;
import com.sci.torcherino.acceleration.AccelerationProfiler;
import com.sci.torcherino.acceleration.AccelerationRouteSnapshot;
import com.sci.torcherino.acceleration.AccelerationService;
import com.sci.torcherino.acceleration.AdapterRegistry;
import com.sci.torcherino.acceleration.AdapterReport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TorcherinoDiagnostics {
    private static final int SCHEMA_VERSION = 1;
    private static final String[] TARGET_MODS = new String[]{
        "thermalexpansion",
        "enderio",
        "mekanism",
        "actuallyadditions",
        "ic2"
    };

    private static DiagnosticLogWriter writer;
    private static Path configuredPath;
    private static String sessionId;
    private static String failure;
    private static long intervalNanos;
    private static long intervalStartedAt;
    private static long nextSnapshotAt;
    private static long serverTicks;
    private static long sequence;

    private TorcherinoDiagnostics() {
    }

    public static synchronized void start(
        File logsDirectory,
        MinecraftServer server
    ) {
        closeWriter();
        failure = null;
        configuredPath = new File(
            logsDirectory,
            "torcherino-performance.log"
        ).toPath().toAbsolutePath().normalize();
        if (!Torcherino.separateLogEnabled) {
            return;
        }

        try {
            writer = new DiagnosticLogWriter(
                configuredPath,
                Torcherino.separateLogMaxSizeMb * 1024L * 1024L,
                Torcherino.separateLogBackups
            );
            sessionId = UUID.randomUUID().toString();
            intervalNanos =
                Torcherino.separateLogIntervalSeconds * 1_000_000_000L;
            intervalStartedAt = System.nanoTime();
            nextSnapshotAt = intervalStartedAt + intervalNanos;
            serverTicks = 0L;
            sequence = 0L;
            Torcherino.logger.info(
                "Torcherino separate diagnostics log: {}",
                configuredPath
            );
            writeSessionStart(server);
            writeAdapters();
            writeSnapshot("server-start");
        } catch (IOException e) {
            fail("open failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            fail("open failed: " + e.getMessage(), e);
        }
    }

    public static synchronized void onServerTick() {
        if (writer == null) {
            return;
        }
        serverTicks++;
        long now = System.nanoTime();
        if (now >= nextSnapshotAt) {
            try {
                writeSnapshot("interval");
            } catch (RuntimeException e) {
                fail("summary failed: " + e.getMessage(), e);
                return;
            }
            intervalStartedAt = now;
            nextSnapshotAt = now + intervalNanos;
            serverTicks = 0L;
        }
    }

    public static synchronized boolean snapshot(String reason) {
        if (writer == null) {
            return false;
        }
        try {
            writeSnapshot(reason);
        } catch (RuntimeException e) {
            fail("snapshot failed: " + e.getMessage(), e);
            return false;
        }
        intervalStartedAt = System.nanoTime();
        nextSnapshotAt = intervalStartedAt + intervalNanos;
        serverTicks = 0L;
        return writer != null;
    }

    public static synchronized boolean flush() {
        if (writer == null) {
            return false;
        }
        try {
            writer.flush();
            return true;
        } catch (IOException e) {
            fail("flush failed: " + e.getMessage(), e);
            return false;
        }
    }

    public static synchronized boolean mark(String sender, String message) {
        if (writer == null) {
            return false;
        }
        JsonObject event = event("mark");
        event.addProperty("sender", sender);
        event.addProperty("message", truncate(message, 512));
        write(event);
        return writer != null;
    }

    public static synchronized void recordProfileSnapshot(
        String reason,
        List<Map<String, Object>> rows
    ) {
        if (writer == null) {
            return;
        }
        JsonObject event = event("profile_snapshot");
        event.addProperty("reason", reason);
        event.add("rows", profileRows(rows));
        write(event);
    }

    public static synchronized void recordSlowManager(
        int dimension,
        long elapsedNanos,
        int coveredPositions,
        int discoveredTargets
    ) {
        if (writer == null) {
            return;
        }
        JsonObject event = event("slow_manager");
        event.addProperty("dimension", dimension);
        event.addProperty("elapsedNanos", elapsedNanos);
        event.addProperty("coveredPositions", coveredPositions);
        event.addProperty("discoveredTargets", discoveredTargets);
        write(event);
    }

    public static synchronized void recordSlowTarget(
        int dimension,
        long packedPos,
        long elapsedNanos
    ) {
        if (writer == null) {
            return;
        }
        JsonObject event = event("slow_target");
        event.addProperty("dimension", dimension);
        event.addProperty("packedPos", packedPos);
        BlockPos pos = BlockPos.fromLong(packedPos);
        event.addProperty("x", pos.getX());
        event.addProperty("y", pos.getY());
        event.addProperty("z", pos.getZ());
        event.addProperty("elapsedNanos", elapsedNanos);
        write(event);
    }

    public static synchronized void stop() {
        if (writer == null) {
            return;
        }
        try {
            writeSnapshot("server-stop");
            JsonObject event = event("session_stop");
            write(event);
        } catch (RuntimeException e) {
            fail("stop snapshot failed: " + e.getMessage(), e);
        } finally {
            closeWriter();
        }
    }

    public static synchronized boolean isEnabled() {
        return writer != null;
    }

    public static synchronized String getPath() {
        return configuredPath == null
            ? "not-configured"
            : configuredPath.toString();
    }

    public static synchronized String getFailure() {
        return failure;
    }

    public static int getIntervalSeconds() {
        return Torcherino.separateLogIntervalSeconds;
    }

    private static void writeSessionStart(MinecraftServer server) {
        JsonObject event = event("session_start");
        event.addProperty("schema", SCHEMA_VERSION);
        event.addProperty("torcherinoVersion", Torcherino.VERSION);
        event.addProperty("minecraftVersion", ForgeVersion.mcVersion);
        event.addProperty("serverModName", server.getServerModName());
        event.addProperty("forgeVersion", ForgeVersion.getVersion());
        event.addProperty("javaVersion", System.getProperty("java.version"));
        event.addProperty("javaVendor", System.getProperty("java.vendor"));
        event.addProperty("osName", System.getProperty("os.name"));
        event.addProperty("osArch", System.getProperty("os.arch"));

        JsonObject config = new JsonObject();
        config.addProperty("strictExecution", Torcherino.strictExecution);
        config.addProperty("overlapMode", Torcherino.overlapMode);
        config.addProperty("asyncPlanner", Torcherino.asyncPlanner);
        config.addProperty(
            "discoveryIntervalTicks",
            Torcherino.discoveryIntervalTicks
        );
        config.addProperty("discoverySlices", Torcherino.discoverySlices);
        config.addProperty("adapterMode", Torcherino.adapterMode);
        config.addProperty(
            "separateLogIntervalSeconds",
            Torcherino.separateLogIntervalSeconds
        );
        event.add("config", config);

        JsonObject mods = new JsonObject();
        Map<String, ModContainer> loaded =
            Loader.instance().getIndexedModList();
        for (String modId : TARGET_MODS) {
            ModContainer container = loaded.get(modId);
            if (container != null) {
                mods.addProperty(modId, container.getVersion());
            }
        }
        event.add("targetMods", mods);
        write(event);
    }

    private static void writeAdapters() {
        JsonObject event = event("adapters");
        JsonArray adapters = new JsonArray();
        for (AdapterReport report :
            AdapterRegistry.getInstance().getReports()) {
            JsonObject adapter = new JsonObject();
            adapter.addProperty("id", report.getId());
            adapter.addProperty(
                "classification",
                report.getClassification().name()
            );
            adapter.addProperty("enabled", report.isEnabled());
            adapter.addProperty("signature", report.getSignature());
            adapter.addProperty("detail", report.getDetail());
            adapters.add(adapter);
        }
        event.add("adapters", adapters);
        write(event);
    }

    private static void writeSnapshot(String reason) {
        JsonObject event = event("summary");
        event.addProperty("reason", reason);
        event.addProperty(
            "wallIntervalNanos",
            Math.max(0L, System.nanoTime() - intervalStartedAt)
        );
        event.addProperty("serverTicks", serverTicks);
        JsonArray dimensions = new JsonArray();
        for (AccelerationMetricsSnapshot snapshot :
            AccelerationService.drainDiagnosticSnapshots()) {
            dimensions.add(dimension(snapshot));
        }
        event.add("dimensions", dimensions);

        AccelerationProfiler profiler = AccelerationProfiler.getInstance();
        event.addProperty("profilerEnabled", profiler.isEnabled());
        if (profiler.isEnabled()) {
            event.add("profileTop", profileRows(profiler.snapshot(25)));
        }
        write(event);
    }

    private static JsonObject dimension(
        AccelerationMetricsSnapshot snapshot
    ) {
        JsonObject result = new JsonObject();
        result.addProperty("dimension", snapshot.getDimension());
        result.addProperty("torches", snapshot.getTorchCount());
        result.addProperty(
            "coveredPositions",
            snapshot.getCoveredPositions()
        );
        result.addProperty(
            "discoveredTargets",
            snapshot.getDiscoveredTargets()
        );
        result.addProperty("revision", snapshot.getRevision());
        result.addProperty(
            "appliedRevision",
            snapshot.getAppliedRevision()
        );
        result.addProperty(
            "discoveryBatches",
            snapshot.getDiscoveryBatches()
        );
        result.addProperty(
            "discoveryScans",
            snapshot.getDiscoveryScans()
        );
        result.addProperty("managerTicks", snapshot.getManagerTicks());
        result.addProperty("managerNanos", snapshot.getManagerNanos());
        result.addProperty(
            "managerMaxNanos",
            snapshot.getManagerMaxNanos()
        );
        result.addProperty("targetVisits", snapshot.getTargetVisits());
        result.addProperty(
            "requestedVirtualTicks",
            snapshot.getRequestedVirtualTicks()
        );
        result.addProperty(
            "consumedVirtualTicks",
            snapshot.getConsumedVirtualTicks()
        );
        result.addProperty("fallbackTicks", snapshot.getFallbackTicks());
        result.addProperty("skippedTicks", snapshot.getSkippedTicks());
        result.addProperty(
            "randomBlockTicks",
            snapshot.getRandomBlockTicks()
        );
        result.addProperty(
            "exactBatchCalls",
            snapshot.getExactBatchCalls()
        );
        result.addProperty(
            "exactFastLoopCalls",
            snapshot.getExactFastLoopCalls()
        );
        result.addProperty("legacyCalls", snapshot.getLegacyCalls());
        result.addProperty(
            "blacklistedCalls",
            snapshot.getBlacklistedCalls()
        );
        JsonArray routes = new JsonArray();
        for (AccelerationRouteSnapshot route :
            snapshot.getActiveRoutes()) {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("class", route.getTileClass());
            encoded.addProperty("adapter", route.getAdapterId());
            encoded.addProperty(
                "classification",
                route.getClassification().name()
            );
            encoded.addProperty("targets", route.getTargets());
            encoded.addProperty(
                "summedMultiplier",
                route.getSummedMultiplier()
            );
            encoded.addProperty(
                "minimumMultiplier",
                route.getMinimumMultiplier()
            );
            encoded.addProperty(
                "maximumMultiplier",
                route.getMaximumMultiplier()
            );
            routes.add(encoded);
        }
        result.add("activeRoutes", routes);
        return result;
    }

    private static JsonArray profileRows(List<Map<String, Object>> rows) {
        JsonArray result = new JsonArray();
        for (Map<String, Object> row : rows) {
            JsonObject encoded = new JsonObject();
            for (Map.Entry<String, Object> value : row.entrySet()) {
                addValue(encoded, value.getKey(), value.getValue());
            }
            result.add(encoded);
        }
        return result;
    }

    private static void addValue(
        JsonObject target,
        String key,
        Object value
    ) {
        if (value == null) {
            target.add(key, null);
        } else if (value instanceof Number) {
            target.addProperty(key, (Number) value);
        } else if (value instanceof Boolean) {
            target.addProperty(key, (Boolean) value);
        } else if (value instanceof Character) {
            target.addProperty(key, (Character) value);
        } else if (value instanceof JsonElement) {
            target.add(key, (JsonElement) value);
        } else {
            target.addProperty(key, String.valueOf(value));
        }
    }

    private static JsonObject event(String type) {
        JsonObject event = new JsonObject();
        event.addProperty("schema", SCHEMA_VERSION);
        event.addProperty("sequence", sequence++);
        event.addProperty("time", Instant.now().toString());
        event.addProperty("type", type);
        if (sessionId != null) {
            event.addProperty("session", sessionId);
        }
        return event;
    }

    private static void write(JsonObject event) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(event);
        } catch (IOException e) {
            fail("write failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            fail("serialization failed: " + e.getMessage(), e);
        }
    }

    private static void fail(String reason, Throwable exception) {
        failure = reason;
        closeWriter();
        if (Torcherino.logger != null) {
            Torcherino.logger.warn(
                "Torcherino separate diagnostics log disabled: {}",
                reason,
                exception
            );
        }
    }

    private static void closeWriter() {
        DiagnosticLogWriter closing = writer;
        writer = null;
        if (closing == null) {
            return;
        }
        try {
            closing.close();
        } catch (IOException e) {
            failure = "close failed: " + e.getMessage();
        }
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit
            ? value
            : value.substring(0, limit);
    }
}
