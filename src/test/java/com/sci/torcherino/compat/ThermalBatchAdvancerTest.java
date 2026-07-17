package com.sci.torcherino.compat;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ThermalBatchAdvancerTest {
    @Test
    void matchesTickByTickEnergyCurveAcrossRandomStates() {
        Random random = new Random(0x544845524d414cL);
        for (int iteration = 0; iteration < 100000; iteration++) {
            int minPower = 1 + random.nextInt(200);
            int maxPower = minPower + random.nextInt(800);
            int minPowerLevel = random.nextInt(10000);
            int maxPowerLevel =
                minPowerLevel + random.nextInt(50000 - minPowerLevel);
            int energyRamp = 1 + random.nextInt(5000);
            int processRemaining = 1 + random.nextInt(100000);
            int energyStored = 1 + random.nextInt(50000);
            int ticks = 1 + random.nextInt(1000);

            ThermalBatchAdvancer.EnergyParameters parameters =
                new ThermalBatchAdvancer.EnergyParameters(
                    minPower,
                    maxPower,
                    minPowerLevel,
                    maxPowerLevel,
                    energyRamp
                );
            Expected expected = advanceOneTickAtATime(
                processRemaining,
                energyStored,
                parameters,
                ticks
            );
            ThermalBatchAdvancer.Segment actual = ThermalBatchAdvancer.advance(
                processRemaining,
                energyStored,
                parameters,
                ticks
            );
            String context =
                " at " + iteration
                    + " minPower=" + minPower
                    + " maxPower=" + maxPower
                    + " minLevel=" + minPowerLevel
                    + " maxLevel=" + maxPowerLevel
                    + " ramp=" + energyRamp
                    + " remaining=" + processRemaining
                    + " stored=" + energyStored
                    + " ticks=" + ticks;

            assertEquals(expected.ticks, actual.ticks, "ticks" + context);
            assertEquals(
                expected.processRemaining,
                actual.processRemaining,
                "process remaining" + context
            );
            assertEquals(
                expected.energyStored,
                actual.energyStored,
                "energy stored" + context
            );
        }
    }

    @Test
    void preservesNegativeProcessRemainderForThermalRefund() {
        ThermalBatchAdvancer.Segment result = ThermalBatchAdvancer.advance(
            5,
            1000,
            new ThermalBatchAdvancer.EnergyParameters(2, 20, 100, 900, 45),
            10
        );

        assertEquals(1, result.ticks);
        assertEquals(-15, result.processRemaining);
        assertEquals(980, result.energyStored);
    }

    @Test
    void stopsWhenEnergyRunsOutBeforeRecipeBoundary() {
        ThermalBatchAdvancer.Segment result = ThermalBatchAdvancer.advance(
            100,
            7,
            new ThermalBatchAdvancer.EnergyParameters(4, 20, 10, 100, 5),
            10
        );

        assertEquals(2, result.ticks);
        assertEquals(93, result.processRemaining);
        assertEquals(0, result.energyStored);
    }

    private static Expected advanceOneTickAtATime(
        int processRemaining,
        int energyStored,
        ThermalBatchAdvancer.EnergyParameters parameters,
        int maxTicks
    ) {
        int ticks = 0;
        int remaining = processRemaining;
        int stored = energyStored;
        while (ticks < maxTicks && remaining > 0 && stored > 0) {
            int energy;
            if (stored >= parameters.maxPowerLevel) {
                energy = parameters.maxPower;
            } else if (stored < parameters.minPowerLevel) {
                energy = Math.min(parameters.minPower, stored);
            } else {
                energy = stored / parameters.energyRamp;
            }
            if (energy <= 0) {
                break;
            }
            stored = Math.max(0, stored - energy);
            remaining -= energy;
            ticks++;
        }
        return new Expected(ticks, remaining, stored);
    }

    private static final class Expected {
        private final int ticks;
        private final int processRemaining;
        private final int energyStored;

        private Expected(int ticks, int processRemaining, int energyStored) {
            this.ticks = ticks;
            this.processRemaining = processRemaining;
            this.energyStored = energyStored;
        }
    }
}
