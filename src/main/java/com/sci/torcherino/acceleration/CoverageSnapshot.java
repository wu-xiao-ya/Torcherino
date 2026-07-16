package com.sci.torcherino.acceleration;

public final class CoverageSnapshot {
    private final long managerId;
    private final int dimension;
    private final long revision;
    private final TorchSnapshot[] torches;

    public CoverageSnapshot(long managerId, int dimension, long revision, TorchSnapshot[] torches) {
        this.managerId = managerId;
        this.dimension = dimension;
        this.revision = revision;
        this.torches = torches.clone();
    }

    public long getManagerId() {
        return managerId;
    }

    public int getDimension() {
        return dimension;
    }

    public long getRevision() {
        return revision;
    }

    public TorchSnapshot[] getTorches() {
        return torches.clone();
    }
}
