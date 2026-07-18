package com.sci.torcherino.acceleration;

final class DiscoveryWheel {
    private int interval = 1;
    private int phase;
    private int batchStart;
    private int batchEnd;
    private boolean completedCycle;
    private boolean forced = true;

    void advance(int targetCount, int intervalTicks) {
        int normalizedInterval = Math.max(1, intervalTicks);
        int normalizedTargetCount = Math.max(0, targetCount);
        if (forced || interval != normalizedInterval) {
            interval = normalizedInterval;
            phase = 0;
            forced = false;
        }

        batchStart = partition(normalizedTargetCount, phase, interval);
        int nextPhase = phase + 1;
        batchEnd = partition(normalizedTargetCount, nextPhase, interval);
        completedCycle = nextPhase >= interval;
        phase = completedCycle ? 0 : nextPhase;
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

    private static int partition(int targetCount, int phase, int interval) {
        return (int) ((long) targetCount * phase / interval);
    }
}
