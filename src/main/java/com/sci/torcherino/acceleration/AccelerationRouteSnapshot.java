package com.sci.torcherino.acceleration;

import com.sci.torcherino.api.AdapterClassification;

public final class AccelerationRouteSnapshot {
    private final String tileClass;
    private final String adapterId;
    private final AdapterClassification classification;
    private final int targets;
    private final long summedMultiplier;
    private final int minimumMultiplier;
    private final int maximumMultiplier;

    AccelerationRouteSnapshot(
        String tileClass,
        String adapterId,
        AdapterClassification classification,
        int targets,
        long summedMultiplier,
        int minimumMultiplier,
        int maximumMultiplier
    ) {
        this.tileClass = tileClass;
        this.adapterId = adapterId;
        this.classification = classification;
        this.targets = targets;
        this.summedMultiplier = summedMultiplier;
        this.minimumMultiplier = minimumMultiplier;
        this.maximumMultiplier = maximumMultiplier;
    }

    public String getTileClass() {
        return tileClass;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public AdapterClassification getClassification() {
        return classification;
    }

    public int getTargets() {
        return targets;
    }

    public long getSummedMultiplier() {
        return summedMultiplier;
    }

    public int getMinimumMultiplier() {
        return minimumMultiplier;
    }

    public int getMaximumMultiplier() {
        return maximumMultiplier;
    }
}
