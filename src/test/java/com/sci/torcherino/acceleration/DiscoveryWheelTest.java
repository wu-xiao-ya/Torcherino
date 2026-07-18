package com.sci.torcherino.acceleration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscoveryWheelTest {
    @Test
    void intervalOneScansEveryTargetEveryTick() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.advance(243, 1);
        assertEquals(0, wheel.getBatchStart());
        assertEquals(243, wheel.getBatchEnd());
        assertTrue(wheel.completedCycle());

        wheel.advance(243, 1);
        assertEquals(243, wheel.getBatchSize());
        assertTrue(wheel.completedCycle());
    }

    @Test
    void intervalTwentySpreadsAllTargetsAcrossExactlyTwentyTicks() {
        DiscoveryWheel wheel = new DiscoveryWheel();
        int scanned = 0;
        int previousEnd = 0;

        for (int tick = 0; tick < 20; tick++) {
            wheel.advance(243, 20);
            assertEquals(previousEnd, wheel.getBatchStart());
            assertTrue(
                wheel.getBatchSize() == 12 || wheel.getBatchSize() == 13
            );
            assertEquals(tick == 19, wheel.completedCycle());
            scanned += wheel.getBatchSize();
            previousEnd = wheel.getBatchEnd();
        }

        assertEquals(243, scanned);
        assertEquals(243, previousEnd);
    }

    @Test
    void intervalLongerThanTargetCountDoesNotDuplicatePositions() {
        DiscoveryWheel wheel = new DiscoveryWheel();
        int scanned = 0;

        for (int tick = 0; tick < 20; tick++) {
            wheel.advance(3, 20);
            assertTrue(
                wheel.getBatchSize() == 0 || wheel.getBatchSize() == 1
            );
            scanned += wheel.getBatchSize();
        }

        assertEquals(3, scanned);
    }

    @Test
    void forceRestartsAtTheBeginningOnTheNextTick() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.advance(243, 20);
        int firstEnd = wheel.getBatchEnd();
        wheel.advance(243, 20);
        assertFalse(wheel.completedCycle());
        assertTrue(wheel.getBatchStart() > 0);

        wheel.force();
        assertEquals(0, wheel.getTicksUntilCycleComplete());
        assertEquals(0, wheel.getCursor(243));

        wheel.advance(243, 20);
        assertEquals(0, wheel.getBatchStart());
        assertEquals(firstEnd, wheel.getBatchEnd());
    }

    @Test
    void cursorAndCountdownTrackTheCurrentCycle() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.advance(243, 20);

        assertEquals(12, wheel.getCursor(243));
        assertEquals(19, wheel.getTicksUntilCycleComplete());
    }

    @Test
    void changingIntervalRestartsFromTheBeginning() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.advance(243, 20);
        wheel.advance(243, 20);
        wheel.advance(243, 10);

        assertEquals(0, wheel.getBatchStart());
        assertEquals(24, wheel.getBatchEnd());
        assertFalse(wheel.completedCycle());
    }

    @Test
    void nextCycleStartsAtZeroAfterCompletion() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        for (int tick = 0; tick < 20; tick++) {
            wheel.advance(243, 20);
        }
        wheel.advance(243, 20);

        assertEquals(0, wheel.getBatchStart());
        assertEquals(12, wheel.getBatchEnd());
    }
}
