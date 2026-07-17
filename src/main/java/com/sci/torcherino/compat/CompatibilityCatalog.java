package com.sci.torcherino.compat;

import com.sci.torcherino.acceleration.AdapterRegistry;
import com.sci.torcherino.api.AdapterClassification;

public final class CompatibilityCatalog {
    private CompatibilityCatalog() {
    }

    public static void register(AdapterRegistry registry) {
        registry.register(new ThermalAccelerableAdapter());
        registry.register(new EnderIoPoweredTaskAdapter());
        registry.register(new MekanismRestrictedTickGuardAdapter());
        registry.register(new ActuallyAdditionsProcessingAuditAdapter());
        registry.register(new Ic2StandardMachineAdapter());
        registerMod(
            registry,
            "thermalexpansion",
            new String[]{"cofh.thermalexpansion."},
            new String[]{"device", "dynamo", "cell"},
            new String[]{"cofh.thermalexpansion.block.machine.TileMachineBase"}
        );
        registerMod(
            registry,
            "enderio",
            new String[]{"crazypants.enderio."},
            new String[]{"conduit", "obelisk", "teleport", "spawner", "killer"},
            new String[]{"crazypants.enderio.base.machine.base.te.AbstractCapabilityPoweredTaskEntity"}
        );
        registerMod(
            registry,
            "mekanism",
            new String[]{"mekanism."},
            new String[]{"transmitter", "miner", "reactor", "turbine", "boiler", "induction"},
            new String[]{"mekanism.common.tile.prefab.TileEntityOperationalMachine"}
        );
        registerMod(
            registry,
            "actuallyadditions",
            new String[]{"de.ellpeck.actuallyadditions."},
            new String[]{"laserrelay", "farmer", "miner", "breaker", "placer", "fishing", "reconstructor"},
            new String[]{"de.ellpeck.actuallyadditions.mod.tile.TileEntityBase"}
        );
        registerMod(
            registry,
            "ic2",
            new String[]{"ic2."},
            new String[]{"reactor", "cable", "crop", "miner", "tesla", "teleporter", "personal"},
            new String[]{"ic2.core.block.machine.tileentity.TileEntityStandardMachine"}
        );
    }

    private static void registerMod(
        AdapterRegistry registry,
        String modId,
        String[] packagePrefixes,
        String[] unsafeTokens,
        String[] probeClasses
    ) {
        registry.register(new PatternCompatibilityAdapter(
            modId + ":unsafe-world-interaction",
            modId,
            1000,
            AdapterClassification.LEGACY_FALLBACK,
            packagePrefixes,
            unsafeTokens,
            probeClasses
        ));
        registry.register(new PatternCompatibilityAdapter(
            modId + ":legacy-exact-fallback",
            modId,
            100,
            AdapterClassification.LEGACY_FALLBACK,
            packagePrefixes,
            new String[0],
            probeClasses
        ));
    }
}
