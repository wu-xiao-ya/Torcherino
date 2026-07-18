package com.sci.torcherino.benchmarkharness;

import com.sci.torcherino.acceleration.AdapterBenchmarkBridge;
import com.sci.torcherino.api.AccelerationContext;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class AdapterDifferentialVerifier {
    private static final BlockPos LEGACY_POS = new BlockPos(160, 80, 160);
    private static final BlockPos ADAPTER_POS = new BlockPos(162, 80, 160);
    private static final int[] TICK_COUNTS = new int[]{1, 4, 36, 324};
    private static final int[] PERFORMANCE_TICK_COUNTS =
        new int[]{4, 36, 324};
    private static final int PERFORMANCE_WARMUP_SAMPLES = 8;
    private static final int PERFORMANCE_MEASURED_SAMPLES = 40;

    private AdapterDifferentialVerifier() {
    }

    static List<String> verify(MinecraftServer server) {
        List<String> lines = new ArrayList<String>();
        WorldServer world = server.getWorld(0);
        if (world == null) {
            lines.add("FAIL no overworld is available");
            return lines;
        }

        SavedBlock legacyOriginal = SavedBlock.capture(world, LEGACY_POS);
        SavedBlock adapterOriginal = SavedBlock.capture(world, ADAPTER_POS);
        int passed = 0;
        int total = 0;
        lines.add("Torcherino adapter differential verification");
        try {
            for (MachineKind kind : MachineKind.values()) {
                for (int ticks : TICK_COUNTS) {
                    total++;
                    VerificationResult result = verifyCase(world, kind, ticks);
                    if (result.passed) {
                        passed++;
                    }
                    lines.add(result.describe());
                }
            }
            total++;
            VerificationResult upgrade = verifyIc2UpgradeFallback(world);
            if (upgrade.passed) {
                passed++;
            }
            lines.add(upgrade.describe());
        } finally {
            legacyOriginal.restore(world, LEGACY_POS);
            adapterOriginal.restore(world, ADAPTER_POS);
        }
        lines.add("SUMMARY " + passed + "/" + total + " passed");
        return lines;
    }

    static List<String> benchmark(MinecraftServer server) {
        List<String> lines = new ArrayList<String>();
        WorldServer world = server.getWorld(0);
        if (world == null) {
            lines.add("FAIL no overworld is available");
            return lines;
        }

        SavedBlock legacyOriginal = SavedBlock.capture(world, LEGACY_POS);
        SavedBlock adapterOriginal = SavedBlock.capture(world, ADAPTER_POS);
        lines.add(
            "Torcherino adapter machine-path benchmark "
                + "(warmup=" + PERFORMANCE_WARMUP_SAMPLES
                + ", samples=" + PERFORMANCE_MEASURED_SAMPLES + ")"
        );
        try {
            for (MachineKind kind : MachineKind.values()) {
                for (int ticks : PERFORMANCE_TICK_COUNTS) {
                    lines.add(benchmarkCase(world, kind, ticks).describe());
                }
            }
        } finally {
            legacyOriginal.restore(world, LEGACY_POS);
            adapterOriginal.restore(world, ADAPTER_POS);
        }
        lines.add(
            "NOTE timings cover extra machine processing only; "
                + "scene reset and state comparison are excluded"
        );
        return lines;
    }

    private static PerformanceResult benchmarkCase(
        WorldServer world,
        MachineKind kind,
        int ticks
    ) {
        try {
            clearTestPositions(world);
            TileEntity legacy = kind.create(world, LEGACY_POS);
            TileEntity accelerated = kind.create(world, ADAPTER_POS);
            PreparedDispatch prepared = preparedDispatch(
                accelerated,
                kind.refreshRouteEachDispatch
            );
            int repetitions = repetitionsFor(ticks);

            for (int sample = 0; sample < PERFORMANCE_WARMUP_SAMPLES; sample++) {
                runPerformanceSample(
                    kind,
                    legacy,
                    accelerated,
                    prepared,
                    ticks,
                    repetitions,
                    sample
                );
            }

            long[] legacySamples =
                new long[PERFORMANCE_MEASURED_SAMPLES];
            long[] adapterSamples =
                new long[PERFORMANCE_MEASURED_SAMPLES];
            for (int sample = 0;
                 sample < PERFORMANCE_MEASURED_SAMPLES;
                 sample++) {
                PerformanceSample result = runPerformanceSample(
                    kind,
                    legacy,
                    accelerated,
                    prepared,
                    ticks,
                    repetitions,
                    sample + PERFORMANCE_WARMUP_SAMPLES
                );
                legacySamples[sample] = result.legacyNanos / repetitions;
                adapterSamples[sample] = result.adapterNanos / repetitions;
            }

            return PerformanceResult.success(
                kind.id,
                ticks,
                repetitions,
                percentile(legacySamples, 0.50D),
                percentile(legacySamples, 0.95D),
                percentile(adapterSamples, 0.50D),
                percentile(adapterSamples, 0.95D)
            );
        } catch (Throwable failure) {
            return PerformanceResult.failure(
                kind.id,
                ticks,
                failure.getClass().getSimpleName() + ": "
                    + String.valueOf(failure.getMessage())
            );
        }
    }

    private static PerformanceSample runPerformanceSample(
        MachineKind kind,
        TileEntity legacy,
        TileEntity accelerated,
        PreparedDispatch prepared,
        int ticks,
        int repetitions,
        int sampleIndex
    ) throws Exception {
        long legacyNanos = 0L;
        long adapterNanos = 0L;
        for (int repetition = 0; repetition < repetitions; repetition++) {
            resetAndPrime(kind, legacy);
            resetAndPrime(kind, accelerated);

            if (((sampleIndex + repetition) & 1) == 0) {
                legacyNanos += timeLegacy(kind, legacy, ticks);
                adapterNanos += timeAdapter(
                    kind,
                    accelerated,
                    prepared,
                    ticks
                );
            } else {
                adapterNanos += timeAdapter(
                    kind,
                    accelerated,
                    prepared,
                    ticks
                );
                legacyNanos += timeLegacy(kind, legacy, ticks);
            }
        }

        String legacyState = kind.snapshot(legacy);
        String acceleratedState = kind.snapshot(accelerated);
        if (!legacyState.equals(acceleratedState)) {
            throw new IllegalStateException(
                "state-mismatch legacy=" + legacyState
                    + " adapter=" + acceleratedState
            );
        }
        return new PerformanceSample(legacyNanos, adapterNanos);
    }

    private static void resetAndPrime(
        MachineKind kind,
        TileEntity tile
    ) throws Exception {
        kind.initialize(tile);
        runTickableLegacy(tile, 1);
    }

    private static long timeLegacy(
        MachineKind kind,
        TileEntity tile,
        int ticks
    ) throws Exception {
        long start = System.nanoTime();
        kind.runLegacy(tile, ticks);
        return System.nanoTime() - start;
    }

    private static long timeAdapter(
        MachineKind kind,
        TileEntity tile,
        PreparedDispatch prepared,
        int ticks
    ) throws Exception {
        try {
            return prepared.timeAdvance(tile, ticks);
        } catch (Exception failure) {
            throw new IllegalStateException(
                String.valueOf(failure.getMessage())
                    + " state=" + kind.snapshot(tile),
                failure
            );
        }
    }

    private static int repetitionsFor(int ticks) {
        if (ticks <= 4) {
            return 64;
        }
        if (ticks <= 36) {
            return 16;
        }
        return 4;
    }

    private static long percentile(long[] values, double percentile) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    private static VerificationResult verifyCase(
        WorldServer world,
        MachineKind kind,
        int ticks
    ) {
        try {
            clearTestPositions(world);
            TileEntity legacy = kind.create(world, LEGACY_POS);
            TileEntity accelerated = kind.create(world, ADAPTER_POS);
            kind.initialize(legacy);
            kind.initialize(accelerated);

            String initialLegacy = kind.snapshot(legacy);
            String initialAccelerated = kind.snapshot(accelerated);
            if (!initialLegacy.equals(initialAccelerated)) {
                return VerificationResult.fail(
                    kind.id,
                    ticks,
                    "initial-state-mismatch"
                );
            }

            kind.runLegacy(legacy, ticks);
            DispatchResult dispatch = dispatch(accelerated, ticks);
            if (!kind.adapterId.equals(dispatch.adapterId)) {
                return VerificationResult.fail(
                    kind.id,
                    ticks,
                    "route=" + dispatch.adapterId
                );
            }
            if (dispatch.fallbackTicks > 0) {
                kind.runLegacy(accelerated, dispatch.fallbackTicks);
            }
            if (dispatch.consumedTicks + dispatch.fallbackTicks != ticks
                || dispatch.invalidated) {
                return VerificationResult.fail(
                    kind.id,
                    ticks,
                    "advance=" + dispatch.consumedTicks
                        + "/" + dispatch.fallbackTicks
                        + " invalidated=" + dispatch.invalidated
                );
            }

            String legacyState = kind.snapshot(legacy);
            String acceleratedState = kind.snapshot(accelerated);
            if (!legacyState.equals(acceleratedState)) {
                return VerificationResult.fail(
                    kind.id,
                    ticks,
                    "state-mismatch legacy=" + legacyState
                        + " adapter=" + acceleratedState
                );
            }
            return VerificationResult.pass(
                kind.id,
                ticks,
                dispatch.adapterId
            );
        } catch (Throwable failure) {
            return VerificationResult.fail(
                kind.id,
                ticks,
                failure.getClass().getSimpleName() + ": "
                    + String.valueOf(failure.getMessage())
            );
        }
    }

    private static VerificationResult verifyIc2UpgradeFallback(WorldServer world) {
        try {
            clearTestPositions(world);
            TileEntity tile = MachineKind.IC2_MACERATOR.create(
                world,
                ADAPTER_POS
            );
            MachineKind.IC2_MACERATOR.initialize(tile);
            Object upgradeSlot = readField(tile, "upgradeSlot");
            ItemStack overclocker = ic2ItemStack("upgrade", "overclocker");
            invoke(upgradeSlot, "put", overclocker);

            DispatchResult dispatch = dispatch(tile, 36);
            boolean fallback = dispatch.consumedTicks == 0
                && dispatch.fallbackTicks == 36
                && !dispatch.invalidated
                && "ic2:macerator-no-upgrade-batch".equals(
                    dispatch.adapterId
                );
            return fallback
                ? VerificationResult.pass(
                    "ic2-upgrade-fallback",
                    36,
                    dispatch.adapterId
                )
                : VerificationResult.fail(
                    "ic2-upgrade-fallback",
                    36,
                    "route=" + dispatch.adapterId
                        + " advance=" + dispatch.consumedTicks
                        + "/" + dispatch.fallbackTicks
                );
        } catch (Throwable failure) {
            return VerificationResult.fail(
                "ic2-upgrade-fallback",
                36,
                failure.getClass().getSimpleName() + ": "
                    + String.valueOf(failure.getMessage())
            );
        }
    }

    private static void runTickableLegacy(TileEntity tile, int ticks)
        throws Exception {
        if (!(tile instanceof ITickable)) {
            throw new IllegalStateException(
                tile.getClass().getName() + " is not ITickable"
            );
        }
        for (int tick = 0; tick < ticks; tick++) {
            ((ITickable) tile).update();
        }
    }

    private static void runIc2Legacy(TileEntity tile, int ticks)
        throws Exception {
        runTickableLegacy(tile, ticks);
    }

    private static DispatchResult dispatch(TileEntity tile, int ticks)
        throws Exception {
        return DispatchBridge.get().dispatch(tile, ticks);
    }

    private static PreparedDispatch preparedDispatch(
        TileEntity tile,
        boolean refreshRouteEachDispatch
    ) throws Exception {
        return DispatchBridge.get().prepare(
            tile,
            refreshRouteEachDispatch
        );
    }

    private static void clearTestPositions(WorldServer world) {
        world.setBlockToAir(LEGACY_POS);
        world.setBlockToAir(ADAPTER_POS);
        world.getChunk(LEGACY_POS.getX() >> 4, LEGACY_POS.getZ() >> 4);
    }

    private static TileEntity placeActuallyAdditions(
        WorldServer world,
        BlockPos pos,
        String field
    ) throws Exception {
        Class<?> initBlocks = Class.forName(
            "de.ellpeck.actuallyadditions.mod.blocks.InitBlocks"
        );
        Block block = (Block) initBlocks.getField(field).get(null);
        world.setBlockState(pos, block.getDefaultState(), 3);
        return requireTile(world, pos, field);
    }

    private static TileEntity placeIc2(
        WorldServer world,
        BlockPos pos,
        String field
    ) throws Exception {
        Class<?> teBlockClass = Class.forName("ic2.core.ref.TeBlock");
        Object teBlock = teBlockClass.getField(field).get(null);
        ResourceLocation identifier = (ResourceLocation) invoke(
            teBlock,
            "getIdentifier"
        );
        Class<?> registry = Class.forName("ic2.core.block.TeBlockRegistry");
        Object block = registry.getMethod("get", ResourceLocation.class)
            .invoke(null, identifier);
        Method getState = block.getClass().getMethod(
            "getState",
            Class.forName("ic2.core.block.ITeBlock")
        );
        IBlockState state = (IBlockState) getState.invoke(block, teBlock);
        world.setBlockState(pos, state, 3);
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null
            || !teBlockClass.getMethod("getTeClass")
                .invoke(teBlock)
                .equals(tile.getClass())) {
            Class<?> tileClass = (Class<?>) teBlockClass
                .getMethod("getTeClass")
                .invoke(teBlock);
            tile = (TileEntity) tileClass.newInstance();
            world.setTileEntity(pos, tile);
        }
        return requireTile(world, pos, "ic2:" + field);
    }

    private static TileEntity requireTile(
        WorldServer world,
        BlockPos pos,
        String label
    ) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) {
            throw new IllegalStateException(
                "missing " + label + " tile at " + pos
            );
        }
        return tile;
    }

    private static void initializeCanolaPress(TileEntity tile) throws Exception {
        Object inventory = readField(tile, "inv");
        Item item = (Item) Class.forName(
            "de.ellpeck.actuallyadditions.mod.items.InitItems"
        ).getField("itemMisc").get(null);
        Object canola = Class.forName(
            "de.ellpeck.actuallyadditions.mod.items.metalists.TheMiscItems"
        ).getField("CANOLA").get(null);
        int metadata = ((Enum<?>) canola).ordinal();
        invoke(inventory, "setStackInSlot", 0, new ItemStack(item, 64, metadata));
        IEnergyStorage energy = tile.getCapability(
            CapabilityEnergy.ENERGY,
            null
        );
        if (energy == null) {
            throw new IllegalStateException("missing Forge Energy capability");
        }
        Object storage = readField(tile, "storage");
        invoke(storage, "setEnergyStored", energy.getMaxEnergyStored());
        ((FluidTank) readField(tile, "tank")).drainInternal(
            Integer.MAX_VALUE,
            true
        );
        writeIntField(tile, "currentProcessTime", 0);
    }

    private static void initializeBarrel(TileEntity tile) throws Exception {
        Fluid input = (Fluid) Class.forName(
            "de.ellpeck.actuallyadditions.mod.fluids.InitFluids"
        ).getField("fluidCanolaOil").get(null);
        FluidTank inputTank = (FluidTank) readField(tile, "canolaTank");
        FluidTank outputTank = (FluidTank) readField(tile, "oilTank");
        inputTank.drainInternal(Integer.MAX_VALUE, true);
        outputTank.drainInternal(Integer.MAX_VALUE, true);
        inputTank.fillInternal(new FluidStack(input, 2000), true);
        writeIntField(tile, "currentProcessTime", 0);
    }

    private static void initializeIc2(
        TileEntity tile,
        ItemStack input
    ) throws Exception {
        ensureIc2Loaded(tile);
        Object inputSlot = readField(tile, "inputSlot");
        Object outputSlot = readField(tile, "outputSlot");
        Object upgradeSlot = readField(tile, "upgradeSlot");
        invoke(inputSlot, "clear");
        invoke(outputSlot, "clear");
        invoke(upgradeSlot, "clear");
        invoke(inputSlot, "put", input);
        Object energy = readField(tile, "energy");
        double stored = ((Number) invoke(energy, "getEnergy")).doubleValue();
        if (stored > 0.0D) {
            invoke(energy, "useEnergy", stored);
        }
        double capacity = ((Number) invoke(energy, "getCapacity")).doubleValue();
        invoke(energy, "forceAddEnergy", capacity);
        writeShortField(tile, "progress", (short) 0);
        findField(tile.getClass(), "guiProgress").setFloat(tile, 0.0F);
        invoke(tile, "setActive", false);
        invoke(tile, "setOverclockRates");
    }

    private static void ensureIc2Loaded(TileEntity tile) throws Exception {
        byte loadState = findField(tile.getClass(), "loadState").getByte(tile);
        if (loadState == 1) {
            invoke(tile, "onLoaded");
        } else if (loadState != 2) {
            throw new IllegalStateException(
                "unexpected IC2 load state " + loadState
            );
        }
    }

    private static ItemStack ic2ItemStack(String itemName, String variant)
        throws Exception {
        Class<?> names = Class.forName("ic2.core.ref.ItemName");
        Object name = names.getField(itemName).get(null);
        return (ItemStack) names.getMethod("getItemStack", String.class)
            .invoke(name, variant);
    }

    private static String snapshotCanola(TileEntity tile) throws Exception {
        Object inventory = readField(tile, "inv");
        ItemStack input = (ItemStack) invoke(inventory, "getStackInSlot", 0);
        IEnergyStorage energy = tile.getCapability(
            CapabilityEnergy.ENERGY,
            null
        );
        FluidTank tank = (FluidTank) readField(tile, "tank");
        return "progress=" + readNumberField(tile, "currentProcessTime")
            + ",energy=" + energy.getEnergyStored()
            + ",input=" + stack(input)
            + ",tank=" + fluid(tank);
    }

    private static String snapshotBarrel(TileEntity tile) throws Exception {
        return "progress=" + readNumberField(tile, "currentProcessTime")
            + ",input=" + fluid((FluidTank) readField(tile, "canolaTank"))
            + ",output=" + fluid((FluidTank) readField(tile, "oilTank"));
    }

    private static String snapshotIc2(TileEntity tile) throws Exception {
        Object energy = readField(tile, "energy");
        Object input = readField(tile, "inputSlot");
        Object output = readField(tile, "outputSlot");
        return "progress=" + readNumberField(tile, "progress")
            + ",energy=" + String.format(
                Locale.ROOT,
                "%.3f",
                ((Number) invoke(energy, "getEnergy")).doubleValue()
            )
            + ",input=" + slot(input)
            + ",output=" + slot(output)
            + ",active=" + invoke(tile, "getActive");
    }

    private static String slot(Object slot) throws Exception {
        int size = ((Number) invoke(slot, "size")).intValue();
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                value.append(';');
            }
            value.append(stack((ItemStack) invoke(slot, "get", index)));
        }
        return value.toString();
    }

    private static String stack(ItemStack stack) {
        return stack == null || stack.isEmpty()
            ? "empty"
            : stack.serializeNBT().toString();
    }

    private static String fluid(FluidTank tank) {
        FluidStack value = tank.getFluid();
        return value == null
            ? "empty"
            : value.getFluid().getName() + "@" + value.amount;
    }

    private static Object readField(Object owner, String name)
        throws Exception {
        Field field = findField(owner.getClass(), name);
        return field.get(owner);
    }

    private static Number readNumberField(Object owner, String name)
        throws Exception {
        return (Number) readField(owner, name);
    }

    private static void writeIntField(Object owner, String name, int value)
        throws Exception {
        findField(owner.getClass(), name).setInt(owner, value);
    }

    private static void writeShortField(Object owner, String name, short value)
        throws Exception {
        findField(owner.getClass(), name).setShort(owner, value);
    }

    private static Field findField(Class<?> type, String name)
        throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }

    private static Object invoke(Object owner, String name, Object... args)
        throws Exception {
        Method method = findMethod(owner.getClass(), name, args);
        return method.invoke(owner, args);
    }

    private static Method findMethod(
        Class<?> type,
        String name,
        Object[] args
    ) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)
                    || method.getParameterTypes().length != args.length) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < parameters.length; index++) {
                    if (args[index] != null
                        && !wrap(parameters[index]).isInstance(args[index])) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private enum MachineKind {
        AA_CANOLA(
            "aa-canola-press",
            "actuallyadditions:processing-batch",
            true
        ) {
            @Override
            TileEntity create(WorldServer world, BlockPos pos) throws Exception {
                return placeActuallyAdditions(
                    world,
                    pos,
                    "blockCanolaPress"
                );
            }

            @Override
            void initialize(TileEntity tile) throws Exception {
                initializeCanolaPress(tile);
            }

            @Override
            String snapshot(TileEntity tile) throws Exception {
                return snapshotCanola(tile);
            }
        },
        AA_BARREL(
            "aa-fermenting-barrel",
            "actuallyadditions:processing-batch",
            true
        ) {
            @Override
            TileEntity create(WorldServer world, BlockPos pos) throws Exception {
                return placeActuallyAdditions(
                    world,
                    pos,
                    "blockFermentingBarrel"
                );
            }

            @Override
            void initialize(TileEntity tile) throws Exception {
                initializeBarrel(tile);
            }

            @Override
            String snapshot(TileEntity tile) throws Exception {
                return snapshotBarrel(tile);
            }
        },
        IC2_MACERATOR(
            "ic2-macerator",
            "ic2:macerator-no-upgrade-batch",
            false
        ) {
            @Override
            TileEntity create(WorldServer world, BlockPos pos) throws Exception {
                return placeIc2(world, pos, "macerator");
            }

            @Override
            void initialize(TileEntity tile) throws Exception {
                initializeIc2(tile, new ItemStack(Blocks.IRON_ORE, 16));
            }

            @Override
            String snapshot(TileEntity tile) throws Exception {
                return snapshotIc2(tile);
            }

            @Override
            void runLegacy(TileEntity tile, int ticks) throws Exception {
                runIc2Legacy(tile, ticks);
            }
        };

        private final String id;
        private final String adapterId;
        private final boolean refreshRouteEachDispatch;

        MachineKind(
            String id,
            String adapterId,
            boolean refreshRouteEachDispatch
        ) {
            this.id = id;
            this.adapterId = adapterId;
            this.refreshRouteEachDispatch = refreshRouteEachDispatch;
        }

        abstract TileEntity create(WorldServer world, BlockPos pos)
            throws Exception;

        abstract void initialize(TileEntity tile) throws Exception;

        abstract String snapshot(TileEntity tile) throws Exception;

        void runLegacy(TileEntity tile, int ticks) throws Exception {
            runTickableLegacy(tile, ticks);
        }
    }

    private static final class DispatchBridge {
        private static volatile DispatchBridge instance;

        private Method adapterId;
        private Method result;
        private Method consumed;
        private Method fallback;
        private Method invalidated;
        private Method syncRequired;

        private DispatchBridge() throws Exception {
        }

        private static DispatchBridge get() throws Exception {
            DispatchBridge current = instance;
            if (current != null) {
                return current;
            }
            synchronized (DispatchBridge.class) {
                if (instance == null) {
                    instance = new DispatchBridge();
                }
                return instance;
            }
        }

        private DispatchResult dispatch(TileEntity tile, int ticks)
            throws Exception {
            Object raw = AdapterBenchmarkBridge.dispatch(
                tile,
                ticks,
                context(tile),
                AdapterBenchmarkBridge.prepare(tile, null)
            );
            return parse(raw);
        }

        private PreparedDispatch prepare(
            TileEntity tile,
            boolean refreshRouteEachDispatch
        ) throws Exception {
            return new PreparedDispatch(
                this,
                context(tile),
                prepareRoute(tile, null),
                refreshRouteEachDispatch
            );
        }

        private AccelerationContext context(TileEntity tile) {
            return new AccelerationContext(
                (WorldServer) tile.getWorld(),
                tile.getPos()
            );
        }

        private Object prepareRoute(TileEntity tile, Object previous) {
            return AdapterBenchmarkBridge.prepare(tile, previous);
        }

        private Object dispatchPrepared(
            TileEntity tile,
            int ticks,
            Object context,
            Object route
        ) {
            return AdapterBenchmarkBridge.dispatch(
                tile,
                ticks,
                (AccelerationContext) context,
                route
            );
        }

        private DispatchResult parse(Object raw) throws Exception {
            bindResultAccess(raw);
            Object advance = result.invoke(raw);
            return new DispatchResult(
                String.valueOf(adapterId.invoke(raw)),
                ((Number) consumed.invoke(advance)).intValue(),
                ((Number) fallback.invoke(advance)).intValue(),
                (Boolean) invalidated.invoke(advance),
                (Boolean) syncRequired.invoke(advance)
            );
        }

        private void bindResultAccess(Object raw) throws Exception {
            if (adapterId != null) {
                return;
            }
            synchronized (this) {
                if (adapterId != null) {
                    return;
                }
                adapterId = raw.getClass().getDeclaredMethod("getAdapterId");
                result = raw.getClass().getDeclaredMethod("getResult");
                adapterId.setAccessible(true);
                result.setAccessible(true);
                Object advance = result.invoke(raw);
                consumed = advance.getClass().getMethod("getConsumedTicks");
                fallback = advance.getClass().getMethod("getFallbackTicks");
                invalidated = advance.getClass().getMethod("isInvalidated");
                syncRequired = advance.getClass().getMethod("isSyncRequired");
            }
        }
    }

    private static final class PreparedDispatch {
        private final DispatchBridge bridge;
        private final Object context;
        private final boolean refreshRouteEachDispatch;
        private Object route;

        private PreparedDispatch(
            DispatchBridge bridge,
            Object context,
            Object route,
            boolean refreshRouteEachDispatch
        ) {
            this.bridge = bridge;
            this.context = context;
            this.route = route;
            this.refreshRouteEachDispatch = refreshRouteEachDispatch;
        }

        private long timeAdvance(TileEntity tile, int ticks)
            throws Exception {
            long start = System.nanoTime();
            if (refreshRouteEachDispatch) {
                route = bridge.prepareRoute(tile, route);
            }
            if (AdapterBenchmarkBridge.usesLegacyBelow(route, ticks)) {
                runTickableLegacy(tile, ticks);
                return System.nanoTime() - start;
            }
            Object raw = bridge.dispatchPrepared(
                tile,
                ticks,
                context,
                route
            );
            long elapsed = System.nanoTime() - start;
            DispatchResult parsed = bridge.parse(raw);
            if (parsed.fallbackTicks > 0) {
                long fallbackStart = System.nanoTime();
                runTickableLegacy(tile, parsed.fallbackTicks);
                elapsed += System.nanoTime() - fallbackStart;
            }
            if (parsed.consumedTicks + parsed.fallbackTicks != ticks
                || parsed.invalidated) {
                throw new IllegalStateException(
                    "route=" + parsed.adapterId
                        + " advance=" + parsed.consumedTicks
                        + "/" + parsed.fallbackTicks
                        + " invalidated=" + parsed.invalidated
                );
            }
            if (parsed.syncRequired) {
                long syncStart = System.nanoTime();
                tile.markDirty();
                elapsed += System.nanoTime() - syncStart;
            }
            return elapsed;
        }
    }

    private static final class DispatchResult {
        private final String adapterId;
        private final int consumedTicks;
        private final int fallbackTicks;
        private final boolean invalidated;
        private final boolean syncRequired;

        private DispatchResult(
            String adapterId,
            int consumedTicks,
            int fallbackTicks,
            boolean invalidated,
            boolean syncRequired
        ) {
            this.adapterId = adapterId;
            this.consumedTicks = consumedTicks;
            this.fallbackTicks = fallbackTicks;
            this.invalidated = invalidated;
            this.syncRequired = syncRequired;
        }
    }

    private static final class PerformanceSample {
        private final long legacyNanos;
        private final long adapterNanos;

        private PerformanceSample(long legacyNanos, long adapterNanos) {
            this.legacyNanos = legacyNanos;
            this.adapterNanos = adapterNanos;
        }
    }

    private static final class PerformanceResult {
        private final boolean success;
        private final String id;
        private final int ticks;
        private final int repetitions;
        private final long legacyP50;
        private final long legacyP95;
        private final long adapterP50;
        private final long adapterP95;
        private final String detail;

        private PerformanceResult(
            boolean success,
            String id,
            int ticks,
            int repetitions,
            long legacyP50,
            long legacyP95,
            long adapterP50,
            long adapterP95,
            String detail
        ) {
            this.success = success;
            this.id = id;
            this.ticks = ticks;
            this.repetitions = repetitions;
            this.legacyP50 = legacyP50;
            this.legacyP95 = legacyP95;
            this.adapterP50 = adapterP50;
            this.adapterP95 = adapterP95;
            this.detail = detail;
        }

        private static PerformanceResult success(
            String id,
            int ticks,
            int repetitions,
            long legacyP50,
            long legacyP95,
            long adapterP50,
            long adapterP95
        ) {
            return new PerformanceResult(
                true,
                id,
                ticks,
                repetitions,
                legacyP50,
                legacyP95,
                adapterP50,
                adapterP95,
                null
            );
        }

        private static PerformanceResult failure(
            String id,
            int ticks,
            String detail
        ) {
            return new PerformanceResult(
                false,
                id,
                ticks,
                0,
                0L,
                0L,
                0L,
                0L,
                detail
            );
        }

        private String describe() {
            if (!success) {
                return "FAIL PERF " + id + " x" + ticks + " " + detail;
            }
            double speedup = adapterP50 <= 0L
                ? 0.0D
                : legacyP50 / (double) adapterP50;
            double reduction = legacyP50 <= 0L
                ? 0.0D
                : (legacyP50 - adapterP50) * 100.0D / legacyP50;
            return String.format(
                Locale.ROOT,
                "PERF %s x%d reps=%d legacy=%.3f/%.3f us "
                    + "adapter=%.3f/%.3f us speedup=%.2fx "
                    + "reduction=%.1f%% perVirtual=%.1f/%.1f ns",
                id,
                ticks,
                repetitions,
                legacyP50 / 1000.0D,
                legacyP95 / 1000.0D,
                adapterP50 / 1000.0D,
                adapterP95 / 1000.0D,
                speedup,
                reduction,
                legacyP50 / (double) ticks,
                adapterP50 / (double) ticks
            );
        }
    }

    private static final class VerificationResult {
        private final boolean passed;
        private final String id;
        private final int ticks;
        private final String detail;

        private VerificationResult(
            boolean passed,
            String id,
            int ticks,
            String detail
        ) {
            this.passed = passed;
            this.id = id;
            this.ticks = ticks;
            this.detail = detail;
        }

        private static VerificationResult pass(
            String id,
            int ticks,
            String detail
        ) {
            return new VerificationResult(true, id, ticks, detail);
        }

        private static VerificationResult fail(
            String id,
            int ticks,
            String detail
        ) {
            return new VerificationResult(false, id, ticks, detail);
        }

        private String describe() {
            return (passed ? "PASS " : "FAIL ") + id + " x" + ticks
                + " " + detail;
        }
    }

    private static final class SavedBlock {
        private final IBlockState state;
        private final NBTTagCompound tileNbt;

        private SavedBlock(IBlockState state, NBTTagCompound tileNbt) {
            this.state = state;
            this.tileNbt = tileNbt;
        }

        private static SavedBlock capture(WorldServer world, BlockPos pos) {
            TileEntity tile = world.getTileEntity(pos);
            return new SavedBlock(
                world.getBlockState(pos),
                tile == null
                    ? null
                    : tile.writeToNBT(new NBTTagCompound())
            );
        }

        private void restore(WorldServer world, BlockPos pos) {
            world.setBlockState(pos, state, 3);
            if (tileNbt != null) {
                TileEntity tile = world.getTileEntity(pos);
                if (tile != null) {
                    tile.readFromNBT(tileNbt.copy());
                }
            }
        }
    }
}
