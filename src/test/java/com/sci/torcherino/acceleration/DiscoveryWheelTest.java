package com.sci.torcherino.acceleration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscoveryWheelTest {
    @Test
    void intervalOneScansEveryTargetEveryTick() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.advance(243, 1, 20);
        assertEquals(0, wheel.getBatchStart());
        assertEquals(243, wheel.getBatchEnd());
        assertTrue(wheel.completedCycle());

        wheel.advance(243, 1, 20);
        assertEquals(243, wheel.getBatchSize());
        assertTrue(wheel.completedCycle());
    }

    @Test
    void intervalTwentySpreadsAllTargetsAcrossExactlyTwentyTicks() {
        DiscoveryWheel wheel = new DiscoveryWheel();
        int scanned = 0;
        int previousEnd = 0;

        for (int tick = 0; tick < 20; tick++) {
            wheel.advance(243, 20, 20);
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
            wheel.advance(3, 20, 20);
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

        wheel.advance(243, 20, 20);
        int firstEnd = wheel.getBatchEnd();
        wheel.advance(243, 20, 20);
        assertFalse(wheel.completedCycle());
        assertTrue(wheel.getBatchStart() > 0);

        wheel.force();
        assertEquals(0, wheel.getTicksUntilCycleComplete());
        assertEquals(0, wheel.getCursor());

        wheel.advance(243, 20, 20);
        assertEquals(0, wheel.getBatchStart());
        assertEquals(firstEnd, wheel.getBatchEnd());
    }

    @Test
    void cursorAndCountdownTrackTheCurrentCycle() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.advance(243, 20, 20);

        assertEquals(12, wheel.getCursor());
        assertEquals(19, wheel.getTicksUntilCycleComplete());
    }

    @Test
    void changingIntervalRestartsFromTheBeginning() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.advance(243, 20, 20);
        wheel.advance(243, 20, 20);
        wheel.advance(243, 10, 10);

        assertEquals(0, wheel.getBatchStart());
        assertEquals(24, wheel.getBatchEnd());
        assertFalse(wheel.completedCycle());
    }

    @Test
    void nextCycleStartsAtZeroAfterCompletion() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        for (int tick = 0; tick < 20; tick++) {
            wheel.advance(243, 20, 20);
        }
        wheel.advance(243, 20, 20);

        assertEquals(0, wheel.getBatchStart());
        assertEquals(12, wheel.getBatchEnd());
    }

    @Test
    void fourSlicesAreEvenlySpacedAcrossTwentyTicks() {
        DiscoveryWheel wheel = new DiscoveryWheel();
        int scanned = 0;
        int scheduled = 0;
        int idle = 0;

        for (int tick = 0; tick < 20; tick++) {
            wheel.advance(243, 20, 4);
            if (wheel.hasScheduledBatch()) {
                scheduled++;
                scanned += wheel.getBatchSize();
                assertTrue(
                    wheel.getBatchSize() == 60
                        || wheel.getBatchSize() == 61
                );
            } else {
                idle++;
                assertEquals(0, wheel.getBatchSize());
            }
        }

        assertEquals(4, scheduled);
        assertEquals(16, idle);
        assertEquals(243, scanned);
        assertTrue(wheel.completedCycle());
    }

    @Test
    void singleSliceScansOnlyAtTheStartOfEachCycle() {
        DiscoveryWheel wheel = new DiscoveryWheel();
        int scanned = 0;
        int scheduled = 0;

        for (int tick = 0; tick < 20; tick++) {
            wheel.advance(243, 20, 1);
            if (wheel.hasScheduledBatch()) {
                scheduled++;
                scanned += wheel.getBatchSize();
                assertEquals(0, wheel.getBatchStart());
                assertEquals(243, wheel.getBatchEnd());
            } else {
                assertEquals(0, wheel.getBatchSize());
            }
        }

        assertEquals(1, scheduled);
        assertEquals(243, scanned);
        assertTrue(wheel.completedCycle());
    }

    @Test
    void slicesAreClampedToTheInterval() {
        DiscoveryWheel wheel = new DiscoveryWheel();

        wheel.advance(12, 3, 20);

        assertEquals(3, wheel.getSlices());
        assertEquals(4, wheel.getBatchSize());
    }
}
