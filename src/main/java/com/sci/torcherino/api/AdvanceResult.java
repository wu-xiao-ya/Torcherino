package com.sci.torcherino.api;

public final class AdvanceResult {
    private final int consumedTicks;
    private final int fallbackTicks;
    private final boolean invalidated;
    private final boolean syncRequired;

    public AdvanceResult(int consumedTicks, int fallbackTicks, boolean invalidated, boolean syncRequired) {
        if (consumedTicks < 0 || fallbackTicks < 0) {
            throw new IllegalArgumentException("Tick counts must be non-negative");
        }
        this.consumedTicks = consumedTicks;
        this.fallbackTicks = fallbackTicks;
        this.invalidated = invalidated;
        this.syncRequired = syncRequired;
    }

    public static AdvanceResult consumed(int ticks, boolean syncRequired) {
        return new AdvanceResult(ticks, 0, false, syncRequired);
    }

    public static AdvanceResult fallback(int ticks) {
        return new AdvanceResult(0, ticks, false, false);
    }

    public static AdvanceResult invalidated(int consumedTicks) {
        return new AdvanceResult(consumedTicks, 0, true, false);
    }

    public int getConsumedTicks() {
        return consumedTicks;
    }

    public int getFallbackTicks() {
        return fallbackTicks;
    }

    public boolean isInvalidated() {
        return invalidated;
    }

    public boolean isSyncRequired() {
        return syncRequired;
    }
}
