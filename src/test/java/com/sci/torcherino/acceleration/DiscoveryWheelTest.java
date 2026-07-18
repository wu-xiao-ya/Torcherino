package com.sci.torcherino.acceleration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscoveryWheelTest {
    @Test
    void intervalOneScansEveryTargetEveryTick() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        DiscoveryWheel.Batch first = wheel.nextBatch(243, 1);
        DiscoveryWheel.Batch second = wheel.nextBatch(243, 1);

        assertEquals(0, first.getStart());
        assertEquals(243, first.getEnd());
        assertTrue(first.completedCycle());
        assertEquals(243, second.size());
        assertTrue(second.completedCycle());
    }

    @Test
    void intervalTwentySpreadsAllTargetsAcrossExactlyTwentyTicks() {
        DiscoveryWheel wheel = new DiscoveryWheel();
        int scanned = 0;
        int previousEnd = 0;

        for (int tick = 0; tick < 20; tick++) {
            DiscoveryWheel.Batch batch = wheel.nextBatch(243, 20);
            assertEquals(previousEnd, batch.getStart());
            assertTrue(batch.size() == 12 || batch.size() == 13);
            assertEquals(tick == 19, batch.completedCycle());
            scanned += batch.size();
            previousEnd = batch.getEnd();
        }

        assertEquals(243, scanned);
        assertEquals(243, previousEnd);
    }

    @Test
    void intervalLongerThanTargetCountDoesNotDuplicatePositions() {
        DiscoveryWheel wheel = new DiscoveryWheel();
        int scanned = 0;

        for (int tick = 0; tick < 20; tick++) {
            DiscoveryWheel.Batch batch = wheel.nextBatch(3, 20);
            assertTrue(batch.size() == 0 || batch.size() == 1);
            scanned += batch.size();
        }

        assertEquals(3, scanned);
    }

    @Test
    void forceRestartsAtTheBeginningOnTheNextTick() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        DiscoveryWheel.Batch first = wheel.nextBatch(243, 20);
        DiscoveryWheel.Batch second = wheel.nextBatch(243, 20);
        assertFalse(second.completedCycle());
        assertTrue(second.getStart() > first.getStart());

        wheel.force();
        assertEquals(0, wheel.getTicksUntilCycleComplete());
        assertEquals(0, wheel.getCursor(243));

        DiscoveryWheel.Batch restarted = wheel.nextBatch(243, 20);
        assertEquals(0, restarted.getStart());
        assertEquals(first.getEnd(), restarted.getEnd());
    }

    @Test
    void cursorAndCountdownTrackTheCurrentCycle() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.nextBatch(243, 20);

        assertEquals(12, wheel.getCursor(243));
        assertEquals(19, wheel.getTicksUntilCycleComplete());
    }

    @Test
    void changingIntervalRestartsFromTheBeginning() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.nextBatch(243, 20);
        wheel.nextBatch(243, 20);
        DiscoveryWheel.Batch restarted = wheel.nextBatch(243, 10);

        assertEquals(0, restarted.getStart());
        assertEquals(24, restarted.getEnd());
        assertFalse(restarted.completedCycle());
    }

    @Test
    void nextCycleStartsAtZeroAfterCompletion() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        for (int tick = 0; tick < 20; tick++) {
            wheel.nextBatch(243, 20);
        }
        DiscoveryWheel.Batch nextCycle = wheel.nextBatch(243, 20);

        assertEquals(0, nextCycle.getStart());
        assertEquals(12, nextCycle.getEnd());
    }
}
