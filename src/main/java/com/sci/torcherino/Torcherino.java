package com.sci.torcherino;

import com.sci.torcherino.acceleration.AccelerationService;
import com.sci.torcherino.acceleration.AdapterRegistry;
import com.sci.torcherino.blocks.ModBlocks;
import com.sci.torcherino.blocks.tiles.TileCompressedTorcherino;
import com.sci.torcherino.blocks.tiles.TileDoubleCompressedTorcherino;
import com.sci.torcherino.blocks.tiles.TileTorcherino;
import com.sci.torcherino.command.CommandTorcherino;
import com.sci.torcherino.datafix.TorcherinoDataFixers;
import net.minecraft.init.Blocks;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(
    modid = Torcherino.MOD_ID,
    name = Torcherino.MOD_NAME,
    version = Torcherino.VERSION,
    acceptedMinecraftVersions = "[1.12,1.12.2]",
    useMetadata = true,
    dependencies = "before:carryon"
)
public class Torcherino {
    public static final String MOD_ID = "torcherino";
    public static final String MOD_NAME = "Torcherino";
    public static final String VERSION = "8.0.0-alpha.11";

    public static boolean logPlacement;
    public static boolean overPoweredRecipe;
    public static boolean compressedTorcherino;
    public static boolean doubleCompressedTorcherino;
    public static boolean strictExecution;
    public static boolean asyncPlanner;
    public static boolean loadChunks;
    public static int discoveryIntervalTicks;
    public static int discoverySlices;
    public static String overlapMode;
    public static String adapterMode;
    public static int slowTargetMillis;
    public static int slowManagerMillis;
    public static int slowLogCooldownSeconds;
    public static Configuration config;
    public static final Map<UUID, Boolean> keyStates =
        new ConcurrentHashMap<UUID, Boolean>();
    public static Logger logger;
    public static SimpleNetworkWrapper network;

    private static String[] blacklistedBlocks;
    private static String[] blacklistedTiles;

    @Mod.Instance(MOD_ID)
    public static Torcherino instance;

    @SidedProxy(
        clientSide = "com.sci.torcherino.ClientProxy",
        serverSide = "com.sci.torcherino.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void construction(FMLConstructionEvent event) {
        maintainCarryOnBlacklist();
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MOD_NAME);

        File folder = new File(event.getModConfigurationDirectory(), "sci4me");
        if (!folder.exists() && !folder.mkdirs()) {
            logger.warn("Could not create Torcherino config directory {}", folder);
        }
        config = new Configuration(new File(folder, MOD_NAME + ".cfg"));
        loadConfig();

        TorcherinoDataFixers.register();
        AdapterRegistry.getInstance().registerBuiltIns();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        TorcherinoRegistry.blacklistBlock(Blocks.AIR);
        TorcherinoRegistry.blacklistBlock(Blocks.WATER);
        TorcherinoRegistry.blacklistBlock(Blocks.FLOWING_WATER);
        TorcherinoRegistry.blacklistBlock(Blocks.LAVA);
        TorcherinoRegistry.blacklistBlock(Blocks.FLOWING_LAVA);
        TorcherinoRegistry.blacklistBlock(ModBlocks.torcherino);
        TorcherinoRegistry.blacklistBlock(ModBlocks.compressedTorcherino);
        TorcherinoRegistry.blacklistBlock(ModBlocks.doubleCompressedTorcherino);
        TorcherinoRegistry.blacklistBlock(ModBlocks.lanterino);
        TorcherinoRegistry.blacklistBlock(ModBlocks.compressedLanterino);
        TorcherinoRegistry.blacklistBlock(ModBlocks.doubleCompressedLanterino);
        TorcherinoRegistry.blacklistTile(TileTorcherino.class);
        TorcherinoRegistry.blacklistTile(TileCompressedTorcherino.class);
        TorcherinoRegistry.blacklistTile(TileDoubleCompressedTorcherino.class);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (Loader.isModLoaded("projecte")) {
            TorcherinoRegistry.blacklistString("projecte:dm_pedestal");
        }
        for (String block : blacklistedBlocks) {
            TorcherinoRegistry.blacklistString(block);
        }
        for (String tile : blacklistedTiles) {
            TorcherinoRegistry.blacklistString(tile);
        }
        AdapterRegistry.getInstance().probeAll();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandTorcherino());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        keyStates.clear();
        AccelerationService.shutdown();
    }

    @Mod.EventHandler
    public void imcMessage(FMLInterModComms.IMCEvent event) {
        for (FMLInterModComms.IMCMessage message : event.getMessages()) {
            if (message.isStringMessage()) {
                TorcherinoRegistry.blacklistString(message.getStringValue());
            } else {
                logger.info("Ignoring non-string Torcherino IMC message from {}", message.getSender());
            }
        }
    }

    private static void loadConfig() {
        try {
            config.load();
            logPlacement = config.getBoolean(
                "logPlacement",
                "general",
                false,
                "(For Server Owners) Is it logged when someone places a Torcherino?"
            );
            overPoweredRecipe = config.getBoolean(
                "overPoweredRecipe",
                "general",
                true,
                "Is the recipe for Torcherino extremely OP?"
            );
            compressedTorcherino = config.getBoolean(
                "compressedTorcherino",
                "general",
                false,
                "Is the recipe for the Compressed Torcherino enabled?"
            );
            doubleCompressedTorcherino = config.getBoolean(
                "doubleCompressedTorcherino",
                "general",
                false,
                "Is the recipe for the Double Compressed Torcherino enabled?"
            );
            strictExecution = config.getBoolean(
                "strictExecution",
                "acceleration",
                true,
                "Execute all requested virtual ticks in the same server tick."
            );
            overlapMode = config.getString(
                "overlapMode",
                "acceleration",
                "LEGACY_SUM",
                "Overlap policy. The refactor currently preserves LEGACY_SUM."
            );
            asyncPlanner = config.getBoolean(
                "asyncPlanner",
                "acceleration",
                true,
                "Build immutable coverage plans on one daemon worker."
            );
            adapterMode = config.getString(
                "adapterMode",
                "acceleration",
                "EXACT_ONLY",
                "Only exact adapters are eligible for batching."
            );
            loadChunks = config.getBoolean(
                "loadChunks",
                "acceleration",
                false,
                "Allow acceleration to load chunks. This implementation always requires loaded chunks."
            );
            discoveryIntervalTicks = config.getInt(
                "discoveryIntervalTicks",
                "acceleration",
                20,
                1,
                1200,
                "Ticks used to complete one incremental covered-position discovery cycle. Cached targets are still validated and accelerated every tick."
            );
            discoverySlices = config.getInt(
                "discoverySlices",
                "acceleration",
                1,
                1,
                1200,
                "Number of evenly spaced scan slices per discovery cycle. Values above the interval are clamped to the interval."
            );
            slowTargetMillis = config.getInt(
                "slowTargetMillis",
                "diagnostics",
                10,
                1,
                60_000,
                "Warn when one accelerated target exceeds this time in one server tick."
            );
            slowManagerMillis = config.getInt(
                "slowManagerMillis",
                "diagnostics",
                50,
                1,
                60_000,
                "Warn when one world's complete acceleration pass exceeds this time."
            );
            slowLogCooldownSeconds = config.getInt(
                "slowLogCooldownSeconds",
                "diagnostics",
                60,
                1,
                86_400,
                "Minimum time between warnings for the same target."
            );
            blacklistedBlocks = config.getStringList(
                "blacklistedBlocks",
                "blacklist",
                new String[0],
                "Block registry names"
            );
            blacklistedTiles = config.getStringList(
                "blacklistedTiles",
                "blacklist",
                new String[0],
                "Fully qualified TileEntity class names"
            );
            normalizeFixedSemantics();
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    private static void normalizeFixedSemantics() {
        if (!strictExecution) {
            logger.warn("strictExecution=false is unsupported by this branch; restoring true");
            strictExecution = true;
            config.get("acceleration", "strictExecution", true).set(true);
        }
        if (!"LEGACY_SUM".equalsIgnoreCase(overlapMode)) {
            logger.warn("Only overlapMode=LEGACY_SUM is implemented; restoring LEGACY_SUM");
            overlapMode = "LEGACY_SUM";
            config.get("acceleration", "overlapMode", "LEGACY_SUM").set("LEGACY_SUM");
        }
        if (!"EXACT_ONLY".equalsIgnoreCase(adapterMode)) {
            logger.warn("Only adapterMode=EXACT_ONLY is implemented; restoring EXACT_ONLY");
            adapterMode = "EXACT_ONLY";
            config.get("acceleration", "adapterMode", "EXACT_ONLY").set("EXACT_ONLY");
        }
        if (loadChunks) {
            logger.warn("Torcherino never loads chunks for acceleration; restoring loadChunks=false");
            loadChunks = false;
            config.get("acceleration", "loadChunks", false).set(false);
        }
    }

    private static void maintainCarryOnBlacklist() {
        if (!Loader.isModLoaded("carryon")) {
            return;
        }

        File file = new File(Loader.instance().getConfigDir(), "carryon.cfg");
        Configuration carryOn = new Configuration(file);
        try {
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch (IOException ignored) {
                    return;
                }
            }
            carryOn.load();
            Property property = carryOn.get(
                "general.blacklist",
                "forbiddenTiles",
                new String[]{"torcherino:*"}
            );
            String[] values = property.getStringList();
            if (!Arrays.asList(values).contains("torcherino:*")) {
                String[] extended = Arrays.copyOf(values, values.length + 1);
                extended[values.length] = "torcherino:*";
                property.set(extended);
            }
        } finally {
            if (carryOn.hasChanged()) {
                carryOn.save();
            }
        }
    }
}
