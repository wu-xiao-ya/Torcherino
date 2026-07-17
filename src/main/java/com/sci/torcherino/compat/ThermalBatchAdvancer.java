package com.sci.torcherino.compat;

final class ThermalBatchAdvancer {
    private ThermalBatchAdvancer() {
    }

    static Segment advance(
        int processRemaining,
        int energyStored,
        EnergyParameters parameters,
        int maxTicks
    ) {
        if (processRemaining <= 0 || energyStored <= 0 || maxTicks <= 0
            || !parameters.isValid()) {
            return new Segment(0, processRemaining, energyStored);
        }

        int ticks = 0;
        int remaining = processRemaining;
        int stored = energyStored;
        while (ticks < maxTicks && remaining > 0 && stored > 0) {
            int energy;
            long sameRateTicks;
            if (stored >= parameters.maxPowerLevel) {
                energy = parameters.maxPower;
                sameRateTicks =
                    ((long) stored - parameters.maxPowerLevel) / energy + 1L;
            } else if (stored < parameters.minPowerLevel) {
                energy = Math.min(parameters.minPower, stored);
                sameRateTicks = stored >= parameters.minPower
                    ? stored / parameters.minPower
                    : 1L;
            } else {
                energy = stored / parameters.energyRamp;
                if (energy <= 0) {
                    break;
                }
                long lowerBound = (long) energy * parameters.energyRamp;
                long quotientTicks =
                    ((long) stored - lowerBound) / energy + 1L;
                long middleRegionTicks =
                    ((long) stored - parameters.minPowerLevel) / energy + 1L;
                sameRateTicks = Math.min(quotientTicks, middleRegionTicks);
            }

            long boundaryTicks = ((long) remaining + energy - 1L) / energy;
            long runTicks = Math.min(
                Math.min(sameRateTicks, boundaryTicks),
                (long) maxTicks - ticks
            );
            if (runTicks <= 0L) {
                break;
            }

            long energyUsed = runTicks * energy;
            remaining = (int) ((long) remaining - energyUsed);
            stored = (int) Math.max(0L, (long) stored - energyUsed);
            ticks += (int) runTicks;
        }
        return new Segment(ticks, remaining, stored);
    }

    static final class EnergyParameters {
        final int minPower;
        final int maxPower;
        final int minPowerLevel;
        final int maxPowerLevel;
        final int energyRamp;

        EnergyParameters(
            int minPower,
            int maxPower,
            int minPowerLevel,
            int maxPowerLevel,
            int energyRamp
        ) {
            this.minPower = minPower;
            this.maxPower = maxPower;
            this.minPowerLevel = minPowerLevel;
            this.maxPowerLevel = maxPowerLevel;
            this.energyRamp = energyRamp;
        }

        private boolean isValid() {
            return minPower > 0
                && maxPower > 0
                && minPowerLevel >= 0
                && maxPowerLevel >= minPowerLevel
                && energyRamp > 0;
        }
    }

    static final class Segment {
        final int ticks;
        final int processRemaining;
        final int energyStored;

        Segment(int ticks, int processRemaining, int energyStored) {
            this.ticks = ticks;
            this.processRemaining = processRemaining;
            this.energyStored = energyStored;
        }
    }
}
