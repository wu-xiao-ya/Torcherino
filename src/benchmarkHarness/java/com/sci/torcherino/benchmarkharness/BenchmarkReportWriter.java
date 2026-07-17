package com.sci.torcherino.benchmarkharness;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BenchmarkReportWriter {
    private BenchmarkReportWriter() {
    }

    public static void write(BenchmarkReport report, File targetDirectory) throws IOException {
        ensureDirectory(targetDirectory);
        writeText(
            new File(targetDirectory, "benchmark-report.json"),
            jsonReport(report)
        );
        writeText(
            new File(targetDirectory, "benchmark-report.csv"),
            csvReport(report)
        );
        writeText(
            new File(targetDirectory, "benchmark-report.md"),
            markdownReport(report)
        );
    }

    private static String jsonReport(BenchmarkReport report) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("{\n");
        jsonField(sb, 1, "suiteId", report.getSuiteId(), true);
        jsonField(sb, 1, "suiteName", report.getSuiteName(), true);
        jsonField(sb, 1, "status", report.getStatus(), true);
        jsonField(sb, 1, "reason", report.getReason(), true);
        jsonField(sb, 1, "createdAtMillis", Long.valueOf(report.getCreatedAtMillis()), true);
        jsonField(sb, 1, "javaVersion", report.getJavaVersion(), true);
        jsonField(sb, 1, "minecraftVersion", report.getMinecraftVersion(), true);
        jsonField(sb, 1, "harnessVersion", report.getHarnessVersion(), true);
        jsonField(sb, 1, "serverRoot", report.getServerRoot(), true);
        appendIndent(sb, 1).append("\"probes\": [\n");
        for (int i = 0; i < report.getProbes().size(); i++) {
            BenchmarkReport.ProbeResult probe = report.getProbes().get(i);
            appendIndent(sb, 2).append("{\n");
            jsonField(sb, 3, "id", probe.getId(), true);
            jsonField(sb, 3, "available", Boolean.valueOf(probe.isAvailable()), true);
            jsonField(sb, 3, "signature", probe.getSignature(), true);
            jsonField(sb, 3, "detail", probe.getDetail(), false);
            appendIndent(sb, 2).append('}');
            if (i + 1 < report.getProbes().size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        appendIndent(sb, 1).append("],\n");
        appendIndent(sb, 1).append("\"scenarios\": [\n");
        for (int i = 0; i < report.getScenarios().size(); i++) {
            appendJsonScenario(sb, report.getScenarios().get(i), 2);
            if (i + 1 < report.getScenarios().size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        appendIndent(sb, 1).append("]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void appendJsonScenario(
        StringBuilder sb,
        BenchmarkReport.ScenarioResult scenario,
        int indent
    ) {
        appendIndent(sb, indent).append("{\n");
        jsonField(sb, indent + 1, "id", scenario.getId(), true);
        jsonField(sb, indent + 1, "displayName", scenario.getDisplayName(), true);
        jsonField(sb, indent + 1, "kind", scenario.getKind(), true);
        jsonField(sb, indent + 1, "machineCount", Integer.valueOf(scenario.getMachineCount()), true);
        jsonField(sb, indent + 1, "torchCount", Integer.valueOf(scenario.getTorchCount()), true);
        jsonField(sb, indent + 1, "torchVariant", scenario.getTorchVariant(), true);
        jsonField(
            sb,
            indent + 1,
            "expectedMultiplier",
            Integer.valueOf(scenario.getExpectedMultiplier()),
            true
        );
        jsonField(sb, indent + 1, "warmupTicks", Integer.valueOf(scenario.getWarmupTicks()), true);
        jsonField(sb, indent + 1, "sampleTicks", Integer.valueOf(scenario.getSampleTicks()), true);
        jsonField(sb, indent + 1, "setupNanos", Long.valueOf(scenario.getSetupNanos()), true);
        jsonField(sb, indent + 1, "restoreNanos", Long.valueOf(scenario.getRestoreNanos()), true);
        jsonField(sb, indent + 1, "status", scenario.getStatus(), true);
        jsonField(sb, indent + 1, "detail", scenario.getDetail(), true);
        jsonField(sb, indent + 1, "profilerStatus", scenario.getProfilerStatus(), true);
        jsonField(sb, indent + 1, "profilerDetail", scenario.getProfilerDetail(), true);
        appendIndent(sb, indent + 1).append("\"profilerSnapshot\": ");
        appendJsonValue(sb, scenario.getProfilerSnapshot(), indent + 1);
        sb.append(",\n");
        appendIndent(sb, indent + 1).append("\"timing\": {\n");
        appendTiming(sb, "world", scenario.getWorldTickStats(), indent + 2, true);
        appendTiming(sb, "server", scenario.getServerTickStats(), indent + 2, false);
        appendIndent(sb, indent + 1).append("},\n");
        appendIndent(sb, indent + 1).append("\"samples\": [\n");
        for (int i = 0; i < scenario.getSamples().size(); i++) {
            appendJsonSample(sb, scenario.getSamples().get(i), indent + 2);
            if (i + 1 < scenario.getSamples().size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        appendIndent(sb, indent + 1).append("]\n");
        appendIndent(sb, indent).append('}');
    }

    private static void appendTiming(
        StringBuilder sb,
        String name,
        BenchmarkReport.TimingStats stats,
        int indent,
        boolean comma
    ) {
        appendIndent(sb, indent).append('"').append(name).append("\": {\n");
        jsonField(sb, indent + 1, "count", Integer.valueOf(stats.getCount()), true);
        jsonField(sb, indent + 1, "p50Nanos", Long.valueOf(stats.getP50Nanos()), true);
        jsonField(sb, indent + 1, "p95Nanos", Long.valueOf(stats.getP95Nanos()), true);
        jsonField(sb, indent + 1, "p99Nanos", Long.valueOf(stats.getP99Nanos()), true);
        jsonField(sb, indent + 1, "maxNanos", Long.valueOf(stats.getMaxNanos()), true);
        jsonField(
            sb,
            indent + 1,
            "over50ms",
            Integer.valueOf(stats.getOver50Millis()),
            false
        );
        appendIndent(sb, indent).append('}');
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendJsonSample(
        StringBuilder sb,
        BenchmarkReport.SampleResult sample,
        int indent
    ) {
        appendIndent(sb, indent).append("{\n");
        jsonField(sb, indent + 1, "index", Integer.valueOf(sample.getIndex()), true);
        jsonField(sb, indent + 1, "resetNanos", Long.valueOf(sample.getResetNanos()), true);
        jsonField(sb, indent + 1, "worldTickNanos", Long.valueOf(sample.getWorldTickNanos()), true);
        jsonField(sb, indent + 1, "serverTickNanos", Long.valueOf(sample.getServerTickNanos()), true);
        jsonField(
            sb,
            indent + 1,
            "worldAllocatedBytes",
            Long.valueOf(sample.getWorldAllocatedBytes()),
            true
        );
        jsonField(
            sb,
            indent + 1,
            "serverAllocatedBytes",
            Long.valueOf(sample.getServerAllocatedBytes()),
            true
        );
        jsonField(
            sb,
            indent + 1,
            "gcCollectionCountDelta",
            Long.valueOf(sample.getGcCollectionCountDelta()),
            true
        );
        jsonField(
            sb,
            indent + 1,
            "gcCollectionTimeMillisDelta",
            Long.valueOf(sample.getGcCollectionTimeMillisDelta()),
            true
        );
        jsonField(
            sb,
            indent + 1,
            "heapUsedDeltaBytes",
            Long.valueOf(sample.getHeapUsedDeltaBytes()),
            true
        );
        jsonField(
            sb,
            indent + 1,
            "baselineRestored",
            Boolean.valueOf(sample.isBaselineRestored()),
            true
        );
        jsonField(sb, indent + 1, "inventoryBefore", Long.valueOf(sample.getInventoryBefore()), true);
        jsonField(sb, indent + 1, "inventoryAfter", Long.valueOf(sample.getInventoryAfter()), true);
        jsonField(sb, indent + 1, "fuelBefore", Long.valueOf(sample.getFuelBefore()), true);
        jsonField(sb, indent + 1, "fuelAfter", Long.valueOf(sample.getFuelAfter()), true);
        jsonField(sb, indent + 1, "energyBefore", Long.valueOf(sample.getEnergyBefore()), true);
        jsonField(sb, indent + 1, "energyAfter", Long.valueOf(sample.getEnergyAfter()), true);
        jsonField(sb, indent + 1, "progressBefore", Long.valueOf(sample.getProgressBefore()), true);
        jsonField(sb, indent + 1, "progressAfter", Long.valueOf(sample.getProgressAfter()), true);
        jsonField(sb, indent + 1, "outputBefore", Long.valueOf(sample.getOutputBefore()), true);
        jsonField(sb, indent + 1, "outputAfter", Long.valueOf(sample.getOutputAfter()), true);
        jsonField(sb, indent + 1, "inputConsumed", Long.valueOf(sample.getInputConsumed()), true);
        jsonField(sb, indent + 1, "fuelConsumed", Long.valueOf(sample.getFuelConsumed()), true);
        jsonField(sb, indent + 1, "energyConsumed", Long.valueOf(sample.getEnergyConsumed()), true);
        jsonField(sb, indent + 1, "progressDelta", Long.valueOf(sample.getProgressDelta()), true);
        jsonField(sb, indent + 1, "outputDelta", Long.valueOf(sample.getOutputDelta()), true);
        jsonField(sb, indent + 1, "throughput", Double.valueOf(sample.getThroughput()), true);
        jsonField(sb, indent + 1, "unitFuelCost", Double.valueOf(sample.getUnitFuelCost()), true);
        jsonField(sb, indent + 1, "unitEnergyCost", Double.valueOf(sample.getUnitEnergyCost()), false);
        appendIndent(sb, indent).append('}');
    }

    private static void jsonField(
        StringBuilder sb,
        int indent,
        String name,
        Object value,
        boolean comma
    ) {
        appendIndent(sb, indent).append('"').append(escapeJson(name)).append("\": ");
        appendJsonValue(sb, value, indent);
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendJsonValue(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String || value instanceof Character) {
            sb.append('"').append(escapeJson(String.valueOf(value))).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(String.valueOf(value));
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            sb.append("{\n");
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                jsonField(
                    sb,
                    indent + 1,
                    String.valueOf(entry.getKey()),
                    entry.getValue(),
                    index + 1 < map.size()
                );
                index++;
            }
            appendIndent(sb, indent).append('}');
        } else if (value instanceof Iterable) {
            Iterable<?> iterable = (Iterable<?>) value;
            sb.append("[");
            int index = 0;
            for (Object item : iterable) {
                if (index > 0) {
                    sb.append(", ");
                }
                appendJsonValue(sb, item, indent);
                index++;
            }
            sb.append(']');
        } else {
            sb.append('"').append(escapeJson(String.valueOf(value))).append('"');
        }
    }

    private static String csvReport(BenchmarkReport report) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        csvRow(
            sb,
            "row_type",
            "suite_id",
            "suite_name",
            "report_status",
            "report_reason",
            "created_at_millis",
            "java_version",
            "minecraft_version",
            "harness_version",
            "server_root",
            "scenario_id",
            "scenario_name",
            "scenario_kind",
            "scenario_status",
            "scenario_detail",
            "machine_count",
            "torch_count",
            "torch_variant",
            "expected_multiplier",
            "warmup_ticks",
            "sample_ticks",
            "setup_nanos",
            "restore_nanos",
            "sample_index",
            "reset_nanos",
            "world_tick_nanos",
            "server_tick_nanos",
            "world_allocated_bytes",
            "server_allocated_bytes",
            "gc_count_delta",
            "gc_time_millis_delta",
            "heap_used_delta_bytes",
            "baseline_restored",
            "inventory_before",
            "inventory_after",
            "fuel_before",
            "fuel_after",
            "energy_before",
            "energy_after",
            "progress_before",
            "progress_after",
            "output_before",
            "output_after",
            "input_consumed",
            "fuel_consumed",
            "energy_consumed",
            "progress_delta",
            "output_delta",
            "throughput",
            "unit_fuel_cost",
            "unit_energy_cost"
        );
        csvRow(
            sb,
            "report",
            report.getSuiteId(),
            report.getSuiteName(),
            report.getStatus(),
            report.getReason(),
            report.getCreatedAtMillis(),
            report.getJavaVersion(),
            report.getMinecraftVersion(),
            report.getHarnessVersion(),
            report.getServerRoot()
        );
        for (BenchmarkReport.ScenarioResult scenario : report.getScenarios()) {
            for (BenchmarkReport.SampleResult sample : scenario.getSamples()) {
                csvRow(
                    sb,
                    "sample",
                    report.getSuiteId(),
                    report.getSuiteName(),
                    report.getStatus(),
                    report.getReason(),
                    report.getCreatedAtMillis(),
                    report.getJavaVersion(),
                    report.getMinecraftVersion(),
                    report.getHarnessVersion(),
                    report.getServerRoot(),
                    scenario.getId(),
                    scenario.getDisplayName(),
                    scenario.getKind(),
                    scenario.getStatus(),
                    scenario.getDetail(),
                    scenario.getMachineCount(),
                    scenario.getTorchCount(),
                    scenario.getTorchVariant(),
                    scenario.getExpectedMultiplier(),
                    scenario.getWarmupTicks(),
                    scenario.getSampleTicks(),
                    scenario.getSetupNanos(),
                    scenario.getRestoreNanos(),
                    sample.getIndex(),
                    sample.getResetNanos(),
                    sample.getWorldTickNanos(),
                    sample.getServerTickNanos(),
                    sample.getWorldAllocatedBytes(),
                    sample.getServerAllocatedBytes(),
                    sample.getGcCollectionCountDelta(),
                    sample.getGcCollectionTimeMillisDelta(),
                    sample.getHeapUsedDeltaBytes(),
                    sample.isBaselineRestored(),
                    sample.getInventoryBefore(),
                    sample.getInventoryAfter(),
                    sample.getFuelBefore(),
                    sample.getFuelAfter(),
                    sample.getEnergyBefore(),
                    sample.getEnergyAfter(),
                    sample.getProgressBefore(),
                    sample.getProgressAfter(),
                    sample.getOutputBefore(),
                    sample.getOutputAfter(),
                    sample.getInputConsumed(),
                    sample.getFuelConsumed(),
                    sample.getEnergyConsumed(),
                    sample.getProgressDelta(),
                    sample.getOutputDelta(),
                    sample.getThroughput(),
                    sample.getUnitFuelCost(),
                    sample.getUnitEnergyCost()
                );
            }
            if (scenario.getSamples().isEmpty()) {
                csvRow(
                    sb,
                    "scenario",
                    report.getSuiteId(),
                    report.getSuiteName(),
                    report.getStatus(),
                    report.getReason(),
                    report.getCreatedAtMillis(),
                    report.getJavaVersion(),
                    report.getMinecraftVersion(),
                    report.getHarnessVersion(),
                    report.getServerRoot(),
                    scenario.getId(),
                    scenario.getDisplayName(),
                    scenario.getKind(),
                    scenario.getStatus(),
                    scenario.getDetail(),
                    scenario.getMachineCount(),
                    scenario.getTorchCount(),
                    scenario.getTorchVariant(),
                    scenario.getExpectedMultiplier(),
                    scenario.getWarmupTicks(),
                    scenario.getSampleTicks(),
                    scenario.getSetupNanos(),
                    scenario.getRestoreNanos()
                );
            }
        }
        return sb.toString();
    }

    private static void csvRow(StringBuilder sb, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String value = values[i] == null ? "" : String.valueOf(values[i]);
            boolean quote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
            if (quote) {
                sb.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(value);
            }
        }
        sb.append('\n');
    }

    private static String markdownReport(BenchmarkReport report) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("# Torcherino Benchmark Report\n\n");
        sb.append("- Status: `").append(md(report.getStatus())).append("`\n");
        sb.append("- Suite: ").append(md(report.getSuiteName())).append(" (`")
            .append(md(report.getSuiteId())).append("`)\n");
        sb.append("- Minecraft: `").append(md(report.getMinecraftVersion())).append("`\n");
        sb.append("- Java: `").append(md(report.getJavaVersion())).append("`\n");
        sb.append("- Server root: `").append(md(report.getServerRoot())).append("`\n\n");
        sb.append("## Probes\n\n");
        sb.append("| id | available | signature | detail |\n");
        sb.append("| --- | --- | --- | --- |\n");
        for (BenchmarkReport.ProbeResult probe : report.getProbes()) {
            sb.append("| ").append(md(probe.getId())).append(" | ")
                .append(probe.isAvailable() ? "yes" : "no").append(" | ")
                .append(md(probe.getSignature())).append(" | ")
                .append(md(probe.getDetail())).append(" |\n");
        }
        sb.append("\n## Scenarios\n\n");
        sb.append("| id | machines | torches | multiplier | status | samples | world P50/P95/P99/max ms | server P50/P95/P99/max ms | >50ms world/server |\n");
        sb.append("| --- | ---: | ---: | ---: | --- | ---: | --- | --- | ---: |\n");
        for (BenchmarkReport.ScenarioResult scenario : report.getScenarios()) {
            BenchmarkReport.TimingStats world = scenario.getWorldTickStats();
            BenchmarkReport.TimingStats server = scenario.getServerTickStats();
            sb.append("| ").append(md(scenario.getId())).append(" | ")
                .append(scenario.getMachineCount()).append(" | ")
                .append(scenario.getTorchCount()).append(" | ")
                .append(scenario.getExpectedMultiplier()).append(" | ")
                .append(md(scenario.getStatus())).append(" | ")
                .append(scenario.getSamples().size()).append(" | ")
                .append(timing(world)).append(" | ")
                .append(timing(server)).append(" | ")
                .append(world.getOver50Millis()).append("/")
                .append(server.getOver50Millis()).append(" |\n");
        }
        for (BenchmarkReport.ScenarioResult scenario : report.getScenarios()) {
            sb.append("\n### ").append(md(scenario.getDisplayName())).append("\n\n");
            if (scenario.getDetail() != null && !scenario.getDetail().isEmpty()) {
                sb.append("`").append(md(scenario.getDetail())).append("`\n\n");
            }
            sb.append("| sample | baseline | world ms | server ms | inventory before/after | fuel before/after | energy before/after | progress before/after | output before/after | throughput | unit fuel | unit energy | GC count/time ms |\n");
            sb.append("| ---: | --- | ---: | ---: | --- | --- | --- | --- | --- | ---: | ---: | ---: | --- |\n");
            for (BenchmarkReport.SampleResult sample : scenario.getSamples()) {
                sb.append("| ").append(sample.getIndex()).append(" | ")
                    .append(sample.isBaselineRestored() ? "yes" : "NO").append(" | ")
                    .append(ms(sample.getWorldTickNanos())).append(" | ")
                    .append(ms(sample.getServerTickNanos())).append(" | ")
                    .append(sample.getInventoryBefore()).append("/")
                    .append(sample.getInventoryAfter()).append(" | ")
                    .append(sample.getFuelBefore()).append("/")
                    .append(sample.getFuelAfter()).append(" | ")
                    .append(sample.getEnergyBefore()).append("/")
                    .append(sample.getEnergyAfter()).append(" | ")
                    .append(sample.getProgressBefore()).append("/")
                    .append(sample.getProgressAfter()).append(" | ")
                    .append(sample.getOutputBefore()).append("/")
                    .append(sample.getOutputAfter()).append(" | ")
                    .append(sample.getThroughput()).append(" | ")
                    .append(sample.getUnitFuelCost()).append(" | ")
                    .append(sample.getUnitEnergyCost()).append(" | ")
                    .append(sample.getGcCollectionCountDelta()).append("/")
                    .append(sample.getGcCollectionTimeMillisDelta()).append(" |\n");
            }
        }
        return sb.toString();
    }

    private static String timing(BenchmarkReport.TimingStats stats) {
        return ms(stats.getP50Nanos()) + "/"
            + ms(stats.getP95Nanos()) + "/"
            + ms(stats.getP99Nanos()) + "/"
            + ms(stats.getMaxNanos());
    }

    private static String ms(long nanos) {
        if (nanos < 0L) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static String md(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", "<br>");
    }

    private static void ensureDirectory(File targetDirectory) throws IOException {
        if (targetDirectory.exists()) {
            if (!targetDirectory.isDirectory()) {
                throw new IOException(
                    "Target path is not a directory: " + targetDirectory.getAbsolutePath()
                );
            }
            return;
        }
        if (!targetDirectory.mkdirs()) {
            throw new IOException(
                "Unable to create export directory: " + targetDirectory.getAbsolutePath()
            );
        }
    }

    private static void writeText(File targetFile, String text) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(targetFile),
            StandardCharsets.UTF_8
        ));
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static StringBuilder appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
        return sb;
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value == null ? 4 : value.length() + 8);
        if (value == null) {
            return "";
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
            }
        }
        return escaped.toString();
    }
}
