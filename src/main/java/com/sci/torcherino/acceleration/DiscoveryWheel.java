package com.sci.torcherino.acceleration;

final class DiscoveryWheel {
    private int interval = 1;
    private int slices = 1;
    private int phase;
    private int batchStart;
    private int batchEnd;
    private boolean completedCycle;
    private boolean scheduledBatch;
    private boolean forced = true;

    void advance(int targetCount, int intervalTicks, int requestedSlices) {
        int normalizedInterval = Math.max(1, intervalTicks);
        int normalizedSlices = Math.max(
            1,
            Math.min(normalizedInterval, requestedSlices)
        );
        int normalizedTargetCount = Math.max(0, targetCount);
        if (forced
            || interval != normalizedInterval
            || slices != normalizedSlices) {
            interval = normalizedInterval;
            slices = normalizedSlices;
            phase = 0;
            forced = false;
        }

        int slice = (int) ((long) phase * slices / interval);
        int previousSlice = phase == 0
            ? -1
            : (int) ((long) (phase - 1) * slices / interval);
        scheduledBatch = phase == 0 || slice != previousSlice;
        if (scheduledBatch) {
            batchStart = partition(normalizedTargetCount, slice, slices);
            batchEnd = partition(normalizedTargetCount, slice + 1, slices);
        } else {
            batchStart = batchEnd;
        }
        int nextPhase = phase + 1;
        completedCycle = nextPhase >= interval;
        phase = completedCycle ? 0 : nextPhase;
    }

    void force() {
        phase = 0;
        batchStart = 0;
        batchEnd = 0;
        completedCycle = false;
        scheduledBatch = false;
        forced = true;
    }

    int getTicksUntilCycleComplete() {
        if (forced) {
            return 0;
        }
        return phase == 0 ? interval - 1 : interval - phase;
    }

    int getCursor() {
        return forced ? 0 : batchEnd;
    }

    int getSlices() {
        return slices;
    }

    int getBatchStart() {
        return batchStart;
    }

    int getBatchEnd() {
        return batchEnd;
    }

    int getBatchSize() {
        return batchEnd - batchStart;
    }

    boolean completedCycle() {
        return completedCycle;
    }

    boolean hasScheduledBatch() {
        return scheduledBatch;
    }

    private static int partition(int targetCount, int phase, int interval) {
        return (int) ((long) targetCount * phase / interval);
    }
}
