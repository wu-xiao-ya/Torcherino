package com.sci.torcherino.benchmarkharness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FurnaceSuite implements BenchmarkSuite {
    private final List<Scenario> scenarios;

    private FurnaceSuite(List<Scenario> scenarios) {
        this.scenarios = Collections.unmodifiableList(scenarios);
    }

    public static FurnaceSuite create() {
        return create("all");
    }

    public static FurnaceSuite create(String layer) {
        String selectedLayer = layer == null ? "all" : layer.toLowerCase(java.util.Locale.ROOT);
        if (!"all".equals(selectedLayer)
            && !"vanilla".equals(selectedLayer)
            && !"thermal".equals(selectedLayer)
            && !"enderio".equals(selectedLayer)) {
            throw new IllegalArgumentException("Unknown furnace benchmark layer: " + layer);
        }

        List<Scenario> scenarios = new ArrayList<Scenario>();

        if ("all".equals(selectedLayer) || "vanilla".equals(selectedLayer)) {
            addVanillaScenarios(scenarios);
        }
        if ("all".equals(selectedLayer) || "thermal".equals(selectedLayer)) {
            addMachineMatrix(
                scenarios,
                "thermal-redstone-furnace",
                "Thermal Expansion 16 Redstone Furnaces",
                ScenarioKind.THERMAL_REDSTONE_FURNACE
            );
        }
        if ("all".equals(selectedLayer) || "enderio".equals(selectedLayer)) {
            addMachineMatrix(
                scenarios,
                "enderio-alloy-smelter",
                "Ender IO 16 Alloy Smelters",
                ScenarioKind.ENDERIO_ALLOY_SMELTER
            );
        }
        return new FurnaceSuite(scenarios);
    }

    private static void addVanillaScenarios(List<Scenario> scenarios) {
        addMachineScenario(
            scenarios,
            "vanilla-array",
            "Vanilla 64 furnaces",
            ScenarioKind.VANILLA_FURNACE,
            64,
            1
        );

        scenarios.add(vanilla(
            "vanilla-single-overlap-1",
            "Vanilla single furnace with 1 overlapping torch",
            1,
            1,
            "blocktorcherino",
            4
        ));
        scenarios.add(vanilla(
            "vanilla-single-overlap-16",
            "Vanilla single furnace with 16 overlapping torches",
            1,
            16,
            "blocktorcherino",
            64
        ));
        scenarios.add(vanilla(
            "vanilla-single-overlap-64",
            "Vanilla single furnace with 64 overlapping torches",
            1,
            64,
            "blocktorcherino",
            256
        ));
    }

    private static void addMachineMatrix(
        List<Scenario> scenarios,
        String idPrefix,
        String displayPrefix,
        ScenarioKind kind
    ) {
        addMachineScenario(scenarios, idPrefix, displayPrefix, kind, 16, 1);
    }

    private static void addMachineScenario(
        List<Scenario> scenarios,
        String idPrefix,
        String displayPrefix,
        ScenarioKind kind,
        int machineCount,
        int torchCount
    ) {
        int[] multipliers = new int[]{1, 4, 36, 324};
        String[] variants = new String[]{
            "blocktorcherino",
            "blocktorcherino",
            "blockcompressedtorcherino",
            "blockdoublecompressedtorcherino"
        };
        for (int index = 0; index < multipliers.length; index++) {
            int multiplier = multipliers[index];
            scenarios.add(new MachineScenario(
                idPrefix + "-x" + multiplier,
                displayPrefix + " x" + multiplier,
                kind,
                machineCount,
                torchCount,
                variants[index],
                multiplier
            ));
        }
    }

    @Override
    public String getId() {
        return "furnace-suite";
    }

    @Override
    public String getDisplayName() {
        return "Furnace Suite";
    }

    @Override
    public int getWarmupTicks() {
        return 20;
    }

    @Override
    public int getSampleTicks() {
        return 100;
    }

    @Override
    public List<Scenario> getScenarios() {
        return scenarios;
    }

    private static Scenario vanilla(
        String id,
        String name,
        int machineCount,
        int torchCount,
        String torchVariant,
        int expectedMultiplier
    ) {
        return new MachineScenario(
            id,
            name,
            ScenarioKind.VANILLA_FURNACE,
            machineCount,
            torchCount,
            torchVariant,
            expectedMultiplier
        );
    }

    private static final class MachineScenario implements Scenario {
        private final String id;
        private final String displayName;
        private final ScenarioKind kind;
        private final int machineCount;
        private final int torchCount;
        private final String torchVariant;
        private final int expectedMultiplier;

        private MachineScenario(
            String id,
            String displayName,
            ScenarioKind kind,
            int machineCount,
            int torchCount,
            String torchVariant,
            int expectedMultiplier
        ) {
            this.id = id;
            this.displayName = displayName;
            this.kind = kind;
            this.machineCount = machineCount;
            this.torchCount = torchCount;
            this.torchVariant = torchVariant;
            this.expectedMultiplier = expectedMultiplier;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public ScenarioKind getKind() {
            return kind;
        }

        @Override
        public int getMachineCount() {
            return machineCount;
        }

        @Override
        public int getTorchCount() {
            return torchCount;
        }

        @Override
        public String getTorchVariant() {
            return torchVariant;
        }

        @Override
        public int getExpectedMultiplier() {
            return expectedMultiplier;
        }

        @Override
        public void build(WorldArena arena) throws BenchmarkEnvironmentException {
            arena.buildMachineArray(
                kind,
                machineCount,
                torchVariant,
                torchCount,
                expectedMultiplier
            );
        }
    }
}
