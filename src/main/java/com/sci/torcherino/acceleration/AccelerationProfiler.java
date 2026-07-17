package com.sci.torcherino.acceleration;

import com.sci.torcherino.api.AdapterClassification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class AccelerationProfiler {
    private static final AccelerationProfiler INSTANCE = new AccelerationProfiler();

    private final ConcurrentHashMap<String, MutableStats> stats =
        new ConcurrentHashMap<String, MutableStats>();
    private volatile boolean enabled;

    private AccelerationProfiler() {
    }

    public static AccelerationProfiler getInstance() {
        return INSTANCE;
    }

    public void start() {
        stats.clear();
        enabled = true;
    }

    public void stop() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void record(
        String tileClass,
        String adapterId,
        AdapterClassification classification,
        int virtualTicks,
        int fallbackTicks,
        long nanos
    ) {
        if (!enabled) {
            return;
        }
        String key = adapterId + "|" + tileClass;
        MutableStats value = stats.get(key);
        if (value == null) {
            MutableStats created = new MutableStats(adapterId, tileClass, classification);
            MutableStats raced = stats.putIfAbsent(key, created);
            value = raced == null ? created : raced;
        }
        value.calls.increment();
        value.virtualTicks.add(virtualTicks);
        value.fallbackTicks.add(fallbackTicks);
        value.nanos.add(nanos);
    }

    public void recordBlacklisted(
        String tileClass,
        String adapterId,
        int skippedTicks,
        long nanos
    ) {
        if (!enabled) {
            return;
        }
        String key = adapterId + "|" + tileClass;
        MutableStats value = stats.get(key);
        if (value == null) {
            MutableStats created = new MutableStats(
                adapterId,
                tileClass,
                AdapterClassification.BLACKLIST
            );
            MutableStats raced = stats.putIfAbsent(key, created);
            value = raced == null ? created : raced;
        }
        value.calls.increment();
        value.skippedTicks.add(skippedTicks);
        value.nanos.add(nanos);
    }

    public void recordSkipped(
        String targetClass,
        String reason,
        int skippedTicks,
        long nanos
    ) {
        if (!enabled) {
            return;
        }
        String adapterId = "skip-" + reason;
        String key = adapterId + "|" + targetClass;
        MutableStats value = stats.get(key);
        if (value == null) {
            MutableStats created = new MutableStats(
                adapterId,
                targetClass,
                AdapterClassification.BLACKLIST
            );
            MutableStats raced = stats.putIfAbsent(key, created);
            value = raced == null ? created : raced;
        }
        value.calls.increment();
        value.skippedTicks.add(skippedTicks);
        value.nanos.add(nanos);
    }

    public List<Map<String, Object>> snapshot(int limit) {
        List<MutableStats> values = new ArrayList<MutableStats>(stats.values());
        Collections.sort(values, new Comparator<MutableStats>() {
            @Override
            public int compare(MutableStats left, MutableStats right) {
                return Long.compare(right.nanos.sum(), left.nanos.sum());
            }
        });

        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        int count = Math.min(Math.max(limit, 0), values.size());
        for (int i = 0; i < count; i++) {
            MutableStats value = values.get(i);
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("adapter", value.adapterId);
            row.put("class", value.tileClass);
            row.put("classification", value.classification.name());
            row.put("calls", value.calls.sum());
            row.put("virtualTicks", value.virtualTicks.sum());
            row.put("fallbackTicks", value.fallbackTicks.sum());
            row.put("skippedTicks", value.skippedTicks.sum());
            row.put("millis", value.nanos.sum() / 1_000_000.0D);
            result.add(row);
        }
        return result;
    }

    private static final class MutableStats {
        private final String adapterId;
        private final String tileClass;
        private final AdapterClassification classification;
        private final LongAdder calls = new LongAdder();
        private final LongAdder virtualTicks = new LongAdder();
        private final LongAdder fallbackTicks = new LongAdder();
        private final LongAdder skippedTicks = new LongAdder();
        private final LongAdder nanos = new LongAdder();

        private MutableStats(
            String adapterId,
            String tileClass,
            AdapterClassification classification
        ) {
            this.adapterId = adapterId;
            this.tileClass = tileClass;
            this.classification = classification;
        }
    }
}
