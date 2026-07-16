package com.sci.torcherino.acceleration;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

public final class CoveragePlanner {
    private static final long X_MASK = 0x3FFFFFFL;
    private static final long Y_MASK = 0xFFFL;
    private static final long Z_MASK = 0x3FFFFFFL;

    private CoveragePlanner() {
    }

    public static CoveragePlan compute(CoverageSnapshot snapshot) {
        Long2IntOpenHashMap coverage = new Long2IntOpenHashMap();
        coverage.defaultReturnValue(0);

        for (TorchSnapshot torch : snapshot.getTorches()) {
            apply(coverage, torch, 1);
        }

        return new CoveragePlan(
            snapshot.getManagerId(),
            snapshot.getDimension(),
            snapshot.getRevision(),
            coverage
        );
    }

    public static void apply(Long2IntOpenHashMap coverage, TorchSnapshot torch, int direction) {
        if (torch == null || !torch.isActive()) {
            return;
        }

        int x = unpackX(torch.getPos());
        int y = unpackY(torch.getPos());
        int z = unpackZ(torch.getPos());
        int delta = direction * torch.getMultiplier();

        for (int dx = -torch.getRange(); dx <= torch.getRange(); dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -torch.getRange(); dz <= torch.getRange(); dz++) {
                    long target = pack(x + dx, y + dy, z + dz);
                    int current = coverage.get(target);
                    int next = saturatedAdd(current, delta);
                    if (next <= 0) {
                        coverage.remove(target);
                    } else {
                        coverage.put(target, next);
                    }
                }
            }
        }
    }

    static long pack(int x, int y, int z) {
        return ((long) x & X_MASK) << 38
            | ((long) z & Z_MASK) << 12
            | ((long) y & Y_MASK);
    }

    static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    static int unpackY(long packed) {
        int y = (int) (packed & Y_MASK);
        return y >= 2048 ? y - 4096 : y;
    }

    static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }

    private static int saturatedAdd(int current, int delta) {
        long result = (long) current + delta;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < 0) {
            return 0;
        }
        return (int) result;
    }
}
