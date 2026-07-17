package com.sci.torcherino.benchmarkharness;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProfilerBridge {
    private final Object instance;
    private final Method start;
    private final Method snapshot;
    private final Method stop;
    private String status = "unavailable";
    private String detail = "AccelerationProfiler is absent or incompatible";

    private ProfilerBridge(
        Object instance,
        Method start,
        Method snapshot,
        Method stop
    ) {
        this.instance = instance;
        this.start = start;
        this.snapshot = snapshot;
        this.stop = stop;
    }

    static ProfilerBridge discover() {
        try {
            Class<?> type = Class.forName(
                "com.sci.torcherino.acceleration.AccelerationProfiler",
                false,
                ProfilerBridge.class.getClassLoader()
            );
            Method getInstance = type.getMethod("getInstance");
            Object instance = getInstance.invoke(null);
            return new ProfilerBridge(
                instance,
                type.getMethod("start"),
                type.getMethod("snapshot", int.class),
                type.getMethod("stop")
            );
        } catch (Throwable failure) {
            return new ProfilerBridge(null, null, null, null);
        }
    }

    void start() {
        if (instance == null) {
            return;
        }
        try {
            start.invoke(instance);
            status = "running";
            detail = "start invoked";
        } catch (Throwable failure) {
            status = "error";
            detail = "start failed: " + failure.getClass().getSimpleName();
        }
    }

    List<Map<String, Object>> snapshot() {
        if (instance == null || snapshot == null) {
            return Collections.emptyList();
        }
        try {
            Object value = snapshot.invoke(instance, Integer.valueOf(256));
            if (!(value instanceof List)) {
                status = "error";
                detail = "snapshot returned " + String.valueOf(value);
                return Collections.emptyList();
            }
            List<?> rows = (List<?>) value;
            List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
            for (Object row : rows) {
                if (row instanceof Map) {
                    Map<?, ?> source = (Map<?, ?>) row;
                    Map<String, Object> copy = new LinkedHashMap<String, Object>();
                    for (Map.Entry<?, ?> entry : source.entrySet()) {
                        copy.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(copy);
                } else {
                    Map<String, Object> copy = new LinkedHashMap<String, Object>();
                    copy.put("value", String.valueOf(row));
                    result.add(copy);
                }
            }
            status = "available";
            detail = "snapshot invoked";
            return result;
        } catch (Throwable failure) {
            status = "error";
            detail = "snapshot failed: " + failure.getClass().getSimpleName();
            return Collections.emptyList();
        }
    }

    void stop() {
        if (instance == null || stop == null) {
            return;
        }
        try {
            stop.invoke(instance);
            if (!"error".equals(status)) {
                status = "stopped";
            }
        } catch (Throwable failure) {
            status = "error";
            detail = "stop failed: " + failure.getClass().getSimpleName();
        }
    }

    String getStatus() {
        return status;
    }

    String getDetail() {
        return detail;
    }

    static List<String> describePlans() {
        try {
            Class<?> type = Class.forName(
                "com.sci.torcherino.acceleration.AccelerationService",
                false,
                ProfilerBridge.class.getClassLoader()
            );
            Object value = type.getMethod("describePlans").invoke(null);
            if (!(value instanceof List)) {
                return Collections.singletonList("unexpected-plan-result=" + value);
            }
            List<String> result = new ArrayList<String>();
            for (Object row : (List<?>) value) {
                result.add(String.valueOf(row));
            }
            return result;
        } catch (Throwable failure) {
            return Collections.singletonList(
                "plan-diagnostics-unavailable=" + failure.getClass().getSimpleName()
            );
        }
    }
}
