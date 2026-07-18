package com.sci.torcherino.benchmarkharness;

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
import java.util.List;
import java.util.Locale;

final class AdapterDifferentialVerifier {
    private static final BlockPos LEGACY_POS = new BlockPos(160, 80, 160);
    private static final BlockPos ADAPTER_POS = new BlockPos(162, 80, 160);
    private static final int[] TICK_COUNTS = new int[]{1, 4, 36, 324};

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
            if (dispatch.consumedTicks != ticks
                || dispatch.fallbackTicks != 0
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
                && !"ic2:standard-machine-no-upgrade-loop".equals(
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
        for (int tick = 0; tick < ticks; tick++) {
            invoke(tile, "updateEntityServer");
        }
    }

    private static DispatchResult dispatch(TileEntity tile, int ticks)
        throws Exception {
        ClassLoader loader = AdapterDifferentialVerifier.class.getClassLoader();
        Class<?> registryClass = loader.loadClass(
            "com.sci.torcherino.acceleration.AdapterRegistry"
        );
        Class<?> contextClass = loader.loadClass(
            "com.sci.torcherino.api.AccelerationContext"
        );
        Object context = contextClass
            .getConstructor(WorldServer.class, BlockPos.class)
            .newInstance(tile.getWorld(), tile.getPos());
        Method dispatch = registryClass.getDeclaredMethod(
            "dispatch",
            TileEntity.class,
            int.class,
            contextClass
        );
        dispatch.setAccessible(true);
        Object registry = registryClass.getMethod("getInstance").invoke(null);
        Object value = dispatch.invoke(
            registry,
            tile,
            ticks,
            context
        );
        Method adapterId = value.getClass().getDeclaredMethod("getAdapterId");
        Method result = value.getClass().getDeclaredMethod("getResult");
        adapterId.setAccessible(true);
        result.setAccessible(true);
        Object advance = result.invoke(value);
        Method consumed = advance.getClass().getMethod("getConsumedTicks");
        Method fallback = advance.getClass().getMethod("getFallbackTicks");
        Method invalidated = advance.getClass().getMethod("isInvalidated");
        return new DispatchResult(
            String.valueOf(adapterId.invoke(value)),
            ((Number) consumed.invoke(advance)).intValue(),
            ((Number) fallback.invoke(advance)).intValue(),
            (Boolean) invalidated.invoke(advance)
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
        invoke(inventory, "setStackInSlot", 0, new ItemStack(item, 16, metadata));
        fillForgeEnergy(tile);
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
        Object inputSlot = readField(tile, "inputSlot");
        Object outputSlot = readField(tile, "outputSlot");
        Object upgradeSlot = readField(tile, "upgradeSlot");
        invoke(inputSlot, "clear");
        invoke(outputSlot, "clear");
        invoke(upgradeSlot, "clear");
        invoke(inputSlot, "put", input);
        Object energy = readField(tile, "energy");
        double capacity = ((Number) invoke(energy, "getCapacity")).doubleValue();
        invoke(energy, "forceAddEnergy", capacity);
        writeShortField(tile, "progress", (short) 0);
        invoke(tile, "setOverclockRates");
    }

    private static void fillForgeEnergy(TileEntity tile) {
        IEnergyStorage energy = tile.getCapability(
            CapabilityEnergy.ENERGY,
            null
        );
        if (energy == null) {
            throw new IllegalStateException("missing Forge Energy capability");
        }
        int guard = 0;
        while (energy.getEnergyStored() < energy.getMaxEnergyStored()
            && guard++ < 1000) {
            if (energy.receiveEnergy(Integer.MAX_VALUE, false) <= 0) {
                break;
            }
        }
        if (energy.getEnergyStored() <= 0) {
            throw new IllegalStateException("unable to precharge machine");
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
            "actuallyadditions:processing-batch"
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
            "actuallyadditions:processing-batch"
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
            "ic2:standard-machine-no-upgrade-loop"
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
        },
        IC2_COMPRESSOR(
            "ic2-compressor",
            "ic2:standard-machine-no-upgrade-loop"
        ) {
            @Override
            TileEntity create(WorldServer world, BlockPos pos) throws Exception {
                return placeIc2(world, pos, "compressor");
            }

            @Override
            void initialize(TileEntity tile) throws Exception {
                initializeIc2(tile, new ItemStack(Items.SNOWBALL, 64));
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

        MachineKind(String id, String adapterId) {
            this.id = id;
            this.adapterId = adapterId;
        }

        abstract TileEntity create(WorldServer world, BlockPos pos)
            throws Exception;

        abstract void initialize(TileEntity tile) throws Exception;

        abstract String snapshot(TileEntity tile) throws Exception;

        void runLegacy(TileEntity tile, int ticks) throws Exception {
            runTickableLegacy(tile, ticks);
        }
    }

    private static final class DispatchResult {
        private final String adapterId;
        private final int consumedTicks;
        private final int fallbackTicks;
        private final boolean invalidated;

        private DispatchResult(
            String adapterId,
            int consumedTicks,
            int fallbackTicks,
            boolean invalidated
        ) {
            this.adapterId = adapterId;
            this.consumedTicks = consumedTicks;
            this.fallbackTicks = fallbackTicks;
            this.invalidated = invalidated;
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
