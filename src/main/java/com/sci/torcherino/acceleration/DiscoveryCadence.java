package com.sci.torcherino.acceleration;

final class DiscoveryCadence {
    private int ticksUntilDiscovery;

    boolean beginTick(int intervalTicks) {
        int interval = Math.max(1, intervalTicks);
        if (ticksUntilDiscovery <= 0) {
            ticksUntilDiscovery = interval - 1;
            return true;
        }
        ticksUntilDiscovery--;
        return false;
    }

    void force() {
        ticksUntilDiscovery = 0;
    }

    int getTicksUntilDiscovery() {
        return ticksUntilDiscovery;
    }
}
