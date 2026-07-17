package com.sci.torcherino.benchmarkharness;

import java.util.List;

public interface BenchmarkSuite {
    enum ScenarioKind {
        VANILLA_FURNACE,
        THERMAL_REDSTONE_FURNACE,
        ENDERIO_ALLOY_SMELTER
    }

    String getId();

    String getDisplayName();

    int getWarmupTicks();

    int getSampleTicks();

    List<Scenario> getScenarios();

    interface Scenario {
        String getId();

        String getDisplayName();

        ScenarioKind getKind();

        int getMachineCount();

        int getTorchCount();

        String getTorchVariant();

        int getExpectedMultiplier();

        void build(WorldArena arena) throws BenchmarkEnvironmentException;
    }
}
