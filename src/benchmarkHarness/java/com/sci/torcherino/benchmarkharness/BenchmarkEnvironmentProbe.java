package com.sci.torcherino.benchmarkharness;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class BenchmarkEnvironmentProbe {
    private BenchmarkEnvironmentProbe() {
    }

    public static List<BenchmarkReport.ProbeResult> collect() {
        List<BenchmarkReport.ProbeResult> results = new ArrayList<BenchmarkReport.ProbeResult>();
        results.add(probeBlock("minecraft:furnace", "minecraft-furnace", true));
        results.add(probeBlock("torcherino:blocktorcherino", "torcherino-normal", true));
        results.add(probeBlock(
            "torcherino:blockcompressedtorcherino",
            "torcherino-compressed",
            true
        ));
        results.add(probeBlock(
            "torcherino:blockdoublecompressedtorcherino",
            "torcherino-doublecompressed",
            true
        ));
        results.add(probeOptionalMod(
            "thermalexpansion",
            "thermal-redstone-furnace",
            new String[]{
                "thermalexpansion:machine",
                "thermalexpansion:machine2"
            },
            new String[]{"redstonefurnace", "redstone_furnace"}
        ));
        results.add(probeOptionalMod(
            "enderio",
            "enderio-alloy-smelter",
            new String[]{
                "enderio:block_alloy_smelter",
                "enderio:block_machine",
                "enderio:block_machine1",
                "enderio:block_machine2"
            },
            new String[]{"alloysmelter", "alloy_smelter"}
        ));
        results.add(probeProfiler());
        return results;
    }

    public static void requireCoreEnvironment() throws BenchmarkEnvironmentException {
        requireAvailable(probeBlock("minecraft:furnace", "minecraft-furnace", true));
        requireAvailable(probeBlock("torcherino:blocktorcherino", "torcherino-normal", true));
    }

    private static void requireAvailable(BenchmarkReport.ProbeResult result)
        throws BenchmarkEnvironmentException {
        if (!result.isAvailable()) {
            throw new BenchmarkEnvironmentException(result.getDetail());
        }
    }

    private static BenchmarkReport.ProbeResult probeBlock(
        String registryName,
        String id,
        boolean required
    ) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(registryName));
        if (block == null) {
            return new BenchmarkReport.ProbeResult(
                id,
                false,
                "missing",
                (required ? "Required" : "Optional") + " block " + registryName
                    + " is not registered"
            );
        }
        return new BenchmarkReport.ProbeResult(id, true, registryName, "registered");
    }

    private static BenchmarkReport.ProbeResult probeOptionalMod(
        String modId,
        String id,
        String[] blockNames,
        String[] tileHints
    ) {
        ModContainer container = Loader.instance().getIndexedModList().get(modId);
        if (container == null) {
            return new BenchmarkReport.ProbeResult(
                id,
                false,
                "unloaded",
                modId + " is not loaded; scenario will be environment-blocked"
            );
        }

        List<String> registered = new ArrayList<String>();
        for (String blockName : blockNames) {
            if (ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName)) != null) {
                registered.add(blockName);
            }
        }
        StringBuilder detail = new StringBuilder();
        detail.append(modId).append('@').append(container.getVersion());
        detail.append("/registry=").append(registered);
        detail.append("/tileHints=");
        boolean hintClassFound = false;
        for (String hint : tileHints) {
            if (containsLoadedClassHint(modId, hint)) {
                hintClassFound = true;
                break;
            }
        }
        detail.append(hintClassFound);
        boolean available = !registered.isEmpty();
        return new BenchmarkReport.ProbeResult(
            id,
            available,
            detail.toString(),
            available
                ? "registry candidate found; final tile/capability binding is scenario-local"
                : "no registry candidate found; scenario will be environment-blocked"
        );
    }

    private static boolean containsLoadedClassHint(String modId, String hint) {
        String prefix = modId + ".";
        String[] candidates = new String[]{
            prefix + "machine." + hint,
            prefix + ".machine." + hint,
            "cofh.thermalexpansion.block." + hint,
            "crazypants.enderio." + hint
        };
        for (String candidate : candidates) {
            try {
                Class.forName(candidate, false, BenchmarkEnvironmentProbe.class.getClassLoader());
                return true;
            } catch (Throwable ignored) {
                // Class names vary across 1.12.2 builds; registry probing remains authoritative.
            }
        }
        return false;
    }

    private static BenchmarkReport.ProbeResult probeProfiler() {
        try {
            Class<?> type = Class.forName(
                "com.sci.torcherino.acceleration.AccelerationProfiler",
                false,
                BenchmarkEnvironmentProbe.class.getClassLoader()
            );
            Method getInstance = type.getMethod("getInstance");
            Method start = type.getMethod("start");
            Method snapshot = type.getMethod("snapshot", int.class);
            Method stop = type.getMethod("stop");
            return new BenchmarkReport.ProbeResult(
                "torcherino-profiler",
                getInstance != null && start != null && snapshot != null && stop != null,
                type.getName() + "#start/snapshot(int)/stop",
                "AccelerationProfiler reflection contract detected"
            );
        } catch (Throwable failure) {
            return new BenchmarkReport.ProbeResult(
                "torcherino-profiler",
                false,
                "missing-or-incompatible",
                "AccelerationProfiler is optional for compatibility with Torcherino 7.6: "
                    + failure.getClass().getSimpleName()
            );
        }
    }
}
