package com.sci.torcherino.api;

public final class AdvanceResult {
    private static final int CACHE_LIMIT = 512;
    private static final AdvanceResult[] CONSUMED_CACHE =
        new AdvanceResult[CACHE_LIMIT + 1];
    private static final AdvanceResult[] FALLBACK_CACHE =
        new AdvanceResult[CACHE_LIMIT + 1];
    private static final AdvanceResult INVALIDATED_ZERO =
        new AdvanceResult(0, 0, true, false);

    static {
        for (int ticks = 0; ticks <= CACHE_LIMIT; ticks++) {
            CONSUMED_CACHE[ticks] =
                new AdvanceResult(ticks, 0, false, false);
            FALLBACK_CACHE[ticks] =
                new AdvanceResult(0, ticks, false, false);
        }
    }

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
        if (!syncRequired && ticks >= 0 && ticks <= CACHE_LIMIT) {
            return CONSUMED_CACHE[ticks];
        }
        return new AdvanceResult(ticks, 0, false, syncRequired);
    }

    public static AdvanceResult fallback(int ticks) {
        if (ticks >= 0 && ticks <= CACHE_LIMIT) {
            return FALLBACK_CACHE[ticks];
        }
        return new AdvanceResult(0, ticks, false, false);
    }

    public static AdvanceResult invalidated(int consumedTicks) {
        if (consumedTicks == 0) {
            return INVALIDATED_ZERO;
        }
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
