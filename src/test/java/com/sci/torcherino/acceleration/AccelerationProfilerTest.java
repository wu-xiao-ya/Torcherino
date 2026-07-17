package com.sci.torcherino.acceleration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AccelerationProfilerTest {
    @Test
    void blacklistedTicksAreCountedOnlyAsSkipped() {
        AccelerationProfiler profiler = AccelerationProfiler.getInstance();
        profiler.start();
        try {
            profiler.recordBlacklisted(
                "example.Tile",
                "example:guard",
                324,
                1000L
            );

            List<Map<String, Object>> rows = profiler.snapshot(1);
            Map<String, Object> row = rows.get(0);
            assertEquals(0L, row.get("virtualTicks"));
            assertEquals(0L, row.get("fallbackTicks"));
            assertEquals(324L, row.get("skippedTicks"));
        } finally {
            profiler.stop();
        }
    }
}
