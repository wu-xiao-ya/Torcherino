package com.sci.torcherino.acceleration;

import java.util.Collections;
import java.util.List;

public final class AccelerationMetricsSnapshot {
    private final int dimension;
    private final int torchCount;
    private final int coveredPositions;
    private final int discoveredTargets;
    private final long revision;
    private final long appliedRevision;
    private final long discoveryBatches;
    private final long discoveryScans;
    private final long managerTicks;
    private final long managerNanos;
    private final long managerMaxNanos;
    private final long targetVisits;
    private final long requestedVirtualTicks;
    private final long consumedVirtualTicks;
    private final long fallbackTicks;
    private final long skippedTicks;
    private final long randomBlockTicks;
    private final long exactBatchCalls;
    private final long exactFastLoopCalls;
    private final long legacyCalls;
    private final long blacklistedCalls;
    private final List<AccelerationRouteSnapshot> activeRoutes;

    AccelerationMetricsSnapshot(
        int dimension,
        int torchCount,
        int coveredPositions,
        int discoveredTargets,
        long revision,
        long appliedRevision,
        long discoveryBatches,
        long discoveryScans,
        long managerTicks,
        long managerNanos,
        long managerMaxNanos,
        long targetVisits,
        long requestedVirtualTicks,
        long consumedVirtualTicks,
        long fallbackTicks,
        long skippedTicks,
        long randomBlockTicks,
        long exactBatchCalls,
        long exactFastLoopCalls,
        long legacyCalls,
        long blacklistedCalls,
        List<AccelerationRouteSnapshot> activeRoutes
    ) {
        this.dimension = dimension;
        this.torchCount = torchCount;
        this.coveredPositions = coveredPositions;
        this.discoveredTargets = discoveredTargets;
        this.revision = revision;
        this.appliedRevision = appliedRevision;
        this.discoveryBatches = discoveryBatches;
        this.discoveryScans = discoveryScans;
        this.managerTicks = managerTicks;
        this.managerNanos = managerNanos;
        this.managerMaxNanos = managerMaxNanos;
        this.targetVisits = targetVisits;
        this.requestedVirtualTicks = requestedVirtualTicks;
        this.consumedVirtualTicks = consumedVirtualTicks;
        this.fallbackTicks = fallbackTicks;
        this.skippedTicks = skippedTicks;
        this.randomBlockTicks = randomBlockTicks;
        this.exactBatchCalls = exactBatchCalls;
        this.exactFastLoopCalls = exactFastLoopCalls;
        this.legacyCalls = legacyCalls;
        this.blacklistedCalls = blacklistedCalls;
        this.activeRoutes = Collections.unmodifiableList(activeRoutes);
    }

    public int getDimension() {
        return dimension;
    }

    public int getTorchCount() {
        return torchCount;
    }

    public int getCoveredPositions() {
        return coveredPositions;
    }

    public int getDiscoveredTargets() {
        return discoveredTargets;
    }

    public long getRevision() {
        return revision;
    }

    public long getAppliedRevision() {
        return appliedRevision;
    }

    public long getDiscoveryBatches() {
        return discoveryBatches;
    }

    public long getDiscoveryScans() {
        return discoveryScans;
    }

    public long getManagerTicks() {
        return managerTicks;
    }

    public long getManagerNanos() {
        return managerNanos;
    }

    public long getManagerMaxNanos() {
        return managerMaxNanos;
    }

    public long getTargetVisits() {
        return targetVisits;
    }

    public long getRequestedVirtualTicks() {
        return requestedVirtualTicks;
    }

    public long getConsumedVirtualTicks() {
        return consumedVirtualTicks;
    }

    public long getFallbackTicks() {
        return fallbackTicks;
    }

    public long getSkippedTicks() {
        return skippedTicks;
    }

    public long getRandomBlockTicks() {
        return randomBlockTicks;
    }

    public long getExactBatchCalls() {
        return exactBatchCalls;
    }

    public long getExactFastLoopCalls() {
        return exactFastLoopCalls;
    }

    public long getLegacyCalls() {
        return legacyCalls;
    }

    public long getBlacklistedCalls() {
        return blacklistedCalls;
    }

    public List<AccelerationRouteSnapshot> getActiveRoutes() {
        return activeRoutes;
    }
}
