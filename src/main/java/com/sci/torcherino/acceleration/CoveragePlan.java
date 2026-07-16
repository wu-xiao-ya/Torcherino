package com.sci.torcherino.acceleration;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public final class CoveragePlan {
    private final long managerId;
    private final int dimension;
    private final long revision;
    private final Long2IntOpenHashMap coverage;

    public CoveragePlan(long managerId, int dimension, long revision, Long2IntOpenHashMap coverage) {
        this.managerId = managerId;
        this.dimension = dimension;
        this.revision = revision;
        this.coverage = coverage;
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

    public Long2IntOpenHashMap getCoverage() {
        return coverage;
    }
}
