package com.sci.torcherino.acceleration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoveragePlannerTest {
    @Test
    void packsAndUnpacksSignedCoordinates() {
        int[][] coordinates = new int[][]{
            {0, 0, 0},
            {30_000_000, 255, -30_000_000},
            {-12345, -64, 67890}
        };

        for (int[] coordinate : coordinates) {
            long packed = CoveragePlanner.pack(coordinate[0], coordinate[1], coordinate[2]);
            assertEquals(coordinate[0], CoveragePlanner.unpackX(packed));
            assertEquals(coordinate[1], CoveragePlanner.unpackY(packed));
            assertEquals(coordinate[2], CoveragePlanner.unpackZ(packed));
        }
    }

    @Test
    void buildsExpectedMaximumArea() {
        long pos = CoveragePlanner.pack(10, 64, -10);
        TorchSnapshot torch = new TorchSnapshot(pos, 4, 324);
        CoveragePlan plan = CoveragePlanner.compute(
            new CoverageSnapshot(1, 0, 7, new TorchSnapshot[]{torch})
        );

        assertEquals(243, plan.getCoverage().size());
        assertEquals(324, plan.getCoverage().get(CoveragePlanner.pack(14, 65, -6)));
    }

    @Test
    void sumsAndRemovesOverlappingCoverage() {
        long pos = CoveragePlanner.pack(0, 64, 0);
        TorchSnapshot first = new TorchSnapshot(pos, 1, 4);
        TorchSnapshot second = new TorchSnapshot(pos, 1, 36);
        CoveragePlan plan = CoveragePlanner.compute(
            new CoverageSnapshot(1, 0, 1, new TorchSnapshot[]{first, second})
        );

        assertEquals(40, plan.getCoverage().get(pos));
        CoveragePlanner.apply(plan.getCoverage(), second, -1);
        assertEquals(4, plan.getCoverage().get(pos));
        CoveragePlanner.apply(plan.getCoverage(), first, -1);
        assertFalse(plan.getCoverage().containsKey(pos));
    }

    @Test
    void ignoresInactiveTorch() {
        TorchSnapshot stopped = new TorchSnapshot(CoveragePlanner.pack(0, 64, 0), 0, 324);
        CoveragePlan plan = CoveragePlanner.compute(
            new CoverageSnapshot(1, 0, 1, new TorchSnapshot[]{stopped})
        );
        assertTrue(plan.getCoverage().isEmpty());
    }

    @Test
    void rejectsPlansFromStaleRevisionManagerOrDimension() {
        CoveragePlan plan = CoveragePlanner.compute(
            new CoverageSnapshot(11, -1, 23, new TorchSnapshot[0])
        );

        assertTrue(WorldAccelerationManager.acceptsPlan(plan, 11, -1, 23));
        assertFalse(WorldAccelerationManager.acceptsPlan(plan, 12, -1, 23));
        assertFalse(WorldAccelerationManager.acceptsPlan(plan, 11, 0, 23));
        assertFalse(WorldAccelerationManager.acceptsPlan(plan, 11, -1, 24));
    }
}
