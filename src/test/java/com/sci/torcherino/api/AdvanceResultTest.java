package com.sci.torcherino.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class AdvanceResultTest {
    @Test
    void cachesCommonImmutableResults() {
        assertSame(
            AdvanceResult.consumed(324, false),
            AdvanceResult.consumed(324, false)
        );
        assertSame(
            AdvanceResult.fallback(36),
            AdvanceResult.fallback(36)
        );
        assertSame(
            AdvanceResult.invalidated(0),
            AdvanceResult.invalidated(0)
        );
    }

    @Test
    void doesNotCacheSyncOrOversizedResults() {
        assertNotSame(
            AdvanceResult.consumed(4, true),
            AdvanceResult.consumed(4, true)
        );
        assertNotSame(
            AdvanceResult.consumed(513, false),
            AdvanceResult.consumed(513, false)
        );
    }

    @Test
    void cachedResultsPreserveTickCounts() {
        AdvanceResult consumed = AdvanceResult.consumed(4, false);
        AdvanceResult fallback = AdvanceResult.fallback(36);

        assertEquals(4, consumed.getConsumedTicks());
        assertEquals(0, consumed.getFallbackTicks());
        assertEquals(0, fallback.getConsumedTicks());
        assertEquals(36, fallback.getFallbackTicks());
    }
}
