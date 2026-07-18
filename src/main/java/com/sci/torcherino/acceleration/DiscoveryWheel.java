package com.sci.torcherino.acceleration;

final class DiscoveryWheel {
    private int interval = 1;
    private int phase;
    private boolean forced = true;

    Batch nextBatch(int targetCount, int intervalTicks) {
        int normalizedInterval = Math.max(1, intervalTicks);
        int normalizedTargetCount = Math.max(0, targetCount);
        if (forced || interval != normalizedInterval) {
            interval = normalizedInterval;
            phase = 0;
            forced = false;
        }

        int start = partition(normalizedTargetCount, phase, interval);
        int nextPhase = phase + 1;
        int end = partition(normalizedTargetCount, nextPhase, interval);
        boolean completedCycle = nextPhase >= interval;
        phase = completedCycle ? 0 : nextPhase;
        return new Batch(start, end, completedCycle);
    }

    void force() {
        phase = 0;
        forced = true;
    }

    int getTicksUntilCycleComplete() {
        if (forced) {
            return 0;
        }
        return phase == 0 ? interval - 1 : interval - phase;
    }

    int getCursor(int targetCount) {
        if (forced) {
            return 0;
        }
        return partition(Math.max(0, targetCount), phase, interval);
    }

    private static int partition(int targetCount, int phase, int interval) {
        return (int) ((long) targetCount * phase / interval);
    }

    static final class Batch {
        private final int start;
        private final int end;
        private final boolean completedCycle;

        private Batch(int start, int end, boolean completedCycle) {
            this.start = start;
            this.end = end;
            this.completedCycle = completedCycle;
        }

        int getStart() {
            return start;
        }

        int getEnd() {
            return end;
        }

        int size() {
            return end - start;
        }

        boolean completedCycle() {
            return completedCycle;
        }
    }
}
