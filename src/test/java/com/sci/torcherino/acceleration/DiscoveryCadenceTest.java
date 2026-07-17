package com.sci.torcherino.acceleration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscoveryCadenceTest {
    @Test
    void intervalOneDiscoversEveryTick() {
        DiscoveryCadence cadence = new DiscoveryCadence();

        assertTrue(cadence.beginTick(1));
        assertTrue(cadence.beginTick(1));
        assertTrue(cadence.beginTick(1));
    }

    @Test
    void intervalTwentyDiscoversOnFirstAndTwentyFirstTick() {
        DiscoveryCadence cadence = new DiscoveryCadence();

        assertTrue(cadence.beginTick(20));
        for (int tick = 1; tick < 20; tick++) {
            assertFalse(cadence.beginTick(20), "tick " + tick);
        }
        assertTrue(cadence.beginTick(20));
    }

    @Test
    void forceMakesNextTickDiscoverImmediately() {
        DiscoveryCadence cadence = new DiscoveryCadence();

        assertTrue(cadence.beginTick(20));
        assertFalse(cadence.beginTick(20));
        cadence.force();
        assertTrue(cadence.beginTick(20));
    }
}
