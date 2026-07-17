package com.sci.torcherino.benchmarkharness;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = BenchmarkHarnessMod.MOD_ID,
    name = BenchmarkHarnessMod.MOD_NAME,
    version = BenchmarkHarnessMod.VERSION,
    acceptedMinecraftVersions = "[1.12,1.12.2]",
    useMetadata = true,
    dependencies = "required-after:torcherino"
)
public final class BenchmarkHarnessMod {
    public static final String MOD_ID = "torcherino_bench";
    public static final String MOD_NAME = "Torcherino Benchmark Harness";
    public static final String VERSION = "1.0.0";

    @Mod.Instance(MOD_ID)
    public static BenchmarkHarnessMod instance;

    private BenchmarkController controller;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        controller = new BenchmarkController(event.getModLog());
        MinecraftForge.EVENT_BUS.register(controller);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        controller.bindServer(event.getServer());
        event.registerServerCommand(new BenchmarkCommand(controller));
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (controller != null) {
            controller.shutdown();
        }
    }
}
