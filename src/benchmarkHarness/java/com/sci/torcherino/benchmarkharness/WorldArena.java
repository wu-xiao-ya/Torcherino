package com.sci.torcherino.benchmarkharness;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WorldArena {
    private static final ResourceLocation NORMAL_TORCHERINO =
        new ResourceLocation("torcherino", "blocktorcherino");
    private static final BlockPos ORIGIN = new BlockPos(32, 80, 32);
    private static final int WIDTH = 9;
    private static final int DEPTH = 9;
    private static final int FLOOR_Y_OFFSET = 0;
    private static final int MACHINE_Y_OFFSET = 1;
    private static final int TORCH_Y_OFFSET = 2;
    private static final int BOUNDARY_MARGIN = 1;

    private final WorldServer world;
    private final BlockPos origin;
    private final BlockPos min;
    private final BlockPos max;
    private final Map<Long, BlockSnapshot> original =
        new LinkedHashMap<Long, BlockSnapshot>();
    private final Map<Long, BlockSnapshot> baseline =
        new LinkedHashMap<Long, BlockSnapshot>();
    private final List<BlockPos> torcherinoPositions = new ArrayList<BlockPos>();
    private final List<MachineHandle> machines = new ArrayList<MachineHandle>();
    private boolean centerSingleMachine;
    private boolean originalCaptured;
    private boolean sceneBuilt;
    private MachineObservation baselineObservation;
    private String machineBindingDescription = "";

    public WorldArena(WorldServer world) {
        this.world = world;
        this.origin = ORIGIN;
        this.min = new BlockPos(
            origin.getX() - BOUNDARY_MARGIN,
            origin.getY() + FLOOR_Y_OFFSET - BOUNDARY_MARGIN,
            origin.getZ() - BOUNDARY_MARGIN
        );
        this.max = new BlockPos(
            origin.getX() + WIDTH + BOUNDARY_MARGIN,
            origin.getY() + TORCH_Y_OFFSET + BOUNDARY_MARGIN,
            origin.getZ() + DEPTH + BOUNDARY_MARGIN
        );
    }

    public String describeOrigin() {
        return origin.toString() + " binding=" + machineBindingDescription;
    }

    public void buildMachineArray(
        BenchmarkSuite.ScenarioKind kind,
        int machineCount,
        String torchVariant,
        int torchCount,
        int expectedMultiplier
    ) throws BenchmarkEnvironmentException {
        if (machineCount <= 0 || machineCount > WIDTH * DEPTH) {
            throw new BenchmarkEnvironmentException("Unsupported machine count: " + machineCount);
        }
        ensureChunksLoaded();
        captureOriginalOnce();
        restoreSnapshot(original);
        clearArena();
        machines.clear();
        torcherinoPositions.clear();
        centerSingleMachine = machineCount == 1;
        baselineObservation = null;
        machineBindingDescription = "";

        buildFloor();
        if (kind == BenchmarkSuite.ScenarioKind.VANILLA_FURNACE) {
            buildVanillaFurnaces(machineCount);
        } else {
            buildOptionalMachines(kind, machineCount);
        }
        buildTorcherinos(torchVariant, torchCount, expectedMultiplier);
        initializeMachineInputs(kind);
        primeMachines(kind);
        captureMachineBaseline();
        activateTorcherinos();
        restoreMachineBaseline();
        baselineObservation = captureMachineObservation();
        sceneBuilt = true;
    }

    public void resetToBaseline() throws BenchmarkEnvironmentException {
        if (!sceneBuilt || baselineObservation == null) {
            throw new BenchmarkEnvironmentException("Scene baseline has not been built");
        }
        restoreMachineBaseline();
    }

    public MachineObservation captureMachineObservation()
        throws BenchmarkEnvironmentException {
        if (machines.isEmpty()) {
            throw new BenchmarkEnvironmentException("No benchmark machines are present");
        }
        List<MachineState> states = new ArrayList<MachineState>();
        for (MachineHandle machine : machines) {
            states.add(readMachineState(machine));
        }
        return new MachineObservation(states);
    }

    public MachineMetrics measure(
        MachineObservation before,
        MachineObservation after
    ) {
        return MachineMetrics.between(
            baselineObservation,
            before,
            after
        );
    }

    public long restoreOriginal() {
        long start = System.nanoTime();
        if (originalCaptured) {
            restoreSnapshot(original);
        }
        machines.clear();
        torcherinoPositions.clear();
        sceneBuilt = false;
        baselineObservation = null;
        return System.nanoTime() - start;
    }

    private void buildFloor() {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                setBlock(
                    origin.add(x, FLOOR_Y_OFFSET, z),
                    Blocks.STONE.getDefaultState(),
                    null
                );
            }
        }
    }

    private void buildVanillaFurnaces(int machineCount)
        throws BenchmarkEnvironmentException {
        Block furnace = ForgeRegistries.BLOCKS.getValue(
            new ResourceLocation("minecraft", "furnace")
        );
        if (furnace == null) {
            throw new BenchmarkEnvironmentException("minecraft:furnace is not registered");
        }
        for (int index = 0; index < machineCount; index++) {
            BlockPos pos = machinePosition(index);
            setBlock(pos, furnace.getDefaultState(), null);
            TileEntity tile = requireTile(pos, "vanilla furnace");
            if (!(tile instanceof IInventory)) {
                throw new BenchmarkEnvironmentException(
                    "Vanilla furnace is not an IInventory at " + pos
                );
            }
            machines.add(new MachineHandle(
                "vanilla-furnace-" + index,
                pos,
                "vanilla"
            ));
        }
        machineBindingDescription = "minecraft:furnace";
    }

    private void buildOptionalMachines(
        BenchmarkSuite.ScenarioKind kind,
        int machineCount
    ) throws BenchmarkEnvironmentException {
        OptionalMachineBinding binding = findOptionalBinding(kind, machinePosition(0));
        for (int index = 0; index < machineCount; index++) {
            BlockPos pos = machinePosition(index);
            setBlock(pos, binding.state, null);
            TileEntity tile = requireTile(pos, kind.name());
            if (!binding.accepts(tile)) {
                throw new BenchmarkEnvironmentException(
                    "Optional machine tile changed type at " + pos + ": "
                        + tile.getClass().getName()
                );
            }
            machines.add(new MachineHandle(
                kind.name().toLowerCase(Locale.ROOT) + "-" + index,
                pos,
                kind.name().toLowerCase(Locale.ROOT)
            ));
        }
        machineBindingDescription = binding.description;
    }

    private OptionalMachineBinding findOptionalBinding(
        BenchmarkSuite.ScenarioKind kind,
        BlockPos testPos
    ) throws BenchmarkEnvironmentException {
        String modId = kind == BenchmarkSuite.ScenarioKind.THERMAL_REDSTONE_FURNACE
            ? "thermalexpansion"
            : "enderio";
        String[] exactNames = kind == BenchmarkSuite.ScenarioKind.THERMAL_REDSTONE_FURNACE
            ? new String[]{
                "thermalexpansion:machine",
                "thermalexpansion:machine2"
            }
            : new String[]{
                "enderio:block_alloy_smelter",
                "enderio:block_machine",
                "enderio:block_machine1",
                "enderio:block_machine2"
            };

        List<Block> candidates = new ArrayList<Block>();
        for (String name : exactNames) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name));
            if (block != null && !candidates.contains(block)) {
                candidates.add(block);
            }
        }
        for (ResourceLocation id : ForgeRegistries.BLOCKS.getKeys()) {
            if (!modId.equals(id.getNamespace())) {
                continue;
            }
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("machine")
                || path.contains("furnace")
                || path.contains("alloy")) {
                Block block = ForgeRegistries.BLOCKS.getValue(id);
                if (block != null && !candidates.contains(block)) {
                    candidates.add(block);
                }
            }
        }

        List<String> failures = new ArrayList<String>();
        for (Block block : candidates) {
            for (int meta = 0; meta < 16; meta++) {
                IBlockState state;
                try {
                    state = block.getStateFromMeta(meta);
                } catch (Throwable failure) {
                    continue;
                }
                setBlock(testPos, state, null);
                TileEntity tile = world.getTileEntity(testPos);
                if (tile != null && matchesOptionalMachine(kind, tile)) {
                    return new OptionalMachineBinding(
                        block,
                        state,
                        block.getRegistryName() + "@meta" + meta
                            + " tile=" + tile.getClass().getName()
                    );
                }
                failures.add(
                    String.valueOf(block.getRegistryName()) + "@meta" + meta
                );
                setBlock(testPos, Blocks.AIR.getDefaultState(), null);
            }
        }
        throw new BenchmarkEnvironmentException(
            "Unable to bind " + kind + " through registry/reflection/capability; tried "
                + failures
        );
    }

    private boolean matchesOptionalMachine(
        BenchmarkSuite.ScenarioKind kind,
        TileEntity tile
    ) {
        String name = tile.getClass().getName().toLowerCase(Locale.ROOT);
        if (kind == BenchmarkSuite.ScenarioKind.THERMAL_REDSTONE_FURNACE) {
            return (name.contains("redstonefurnace")
                || name.contains("redstone_furnace")
                || name.endsWith(".tilefurnace"))
                && hasItemOrEnergyCapability(tile);
        }
        return (name.contains("alloysmelter") || name.contains("alloy_smelter"))
            && hasItemOrEnergyCapability(tile);
    }

    private boolean hasItemOrEnergyCapability(TileEntity tile) {
        try {
            return tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
                || tile.hasCapability(CapabilityEnergy.ENERGY, null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void initializeMachineInputs(BenchmarkSuite.ScenarioKind kind)
        throws BenchmarkEnvironmentException {
        for (MachineHandle machine : machines) {
            TileEntity tile = requireTile(machine.pos, machine.id);
            if (tile instanceof IInventory) {
                IInventory inventory = (IInventory) tile;
                if (inventory.getSizeInventory() > 0) {
                    inventory.setInventorySlotContents(
                        0,
                        inputStack(kind)
                    );
                }
                if (kind == BenchmarkSuite.ScenarioKind.VANILLA_FURNACE
                    && inventory.getSizeInventory() > 1) {
                    inventory.setInventorySlotContents(
                        1,
                        new ItemStack(Items.COAL, 64)
                    );
                }
                continue;
            }

            IItemHandler handler = getItemHandler(tile);
            if (handler != null) {
                if (handler instanceof IItemHandlerModifiable) {
                    IItemHandlerModifiable modifiable = (IItemHandlerModifiable) handler;
                    if (handler.getSlots() > 0) {
                        modifiable.setStackInSlot(0, inputStack(kind));
                    }
                    if (kind == BenchmarkSuite.ScenarioKind.VANILLA_FURNACE
                        && handler.getSlots() > 1) {
                        modifiable.setStackInSlot(1, new ItemStack(Items.COAL, 64));
                    }
                } else {
                    if (handler.getSlots() > 0) {
                        handler.insertItem(0, inputStack(kind), false);
                    }
                    if (kind == BenchmarkSuite.ScenarioKind.VANILLA_FURNACE
                        && handler.getSlots() > 1) {
                        handler.insertItem(1, new ItemStack(Items.COAL, 64), false);
                    }
                }
            }

            if (kind != BenchmarkSuite.ScenarioKind.VANILLA_FURNACE) {
                fillEnergy(machine, tile);
            }

            MachineState initialized = readMachineState(machine);
            if (initialized.inputItems <= 0L) {
                throw new BenchmarkEnvironmentException(
                    "Unable to insert deterministic input into " + machine.id
                );
            }
            if (kind != BenchmarkSuite.ScenarioKind.VANILLA_FURNACE
                && initialized.energyStored <= 0L) {
                throw new BenchmarkEnvironmentException(
                    "Unable to precharge " + machine.id
                );
            }
        }
    }

    private ItemStack inputStack(BenchmarkSuite.ScenarioKind kind) {
        return new ItemStack(Blocks.IRON_ORE, 64);
    }

    private void primeMachines(BenchmarkSuite.ScenarioKind kind)
        throws BenchmarkEnvironmentException {
        if (kind == BenchmarkSuite.ScenarioKind.THERMAL_REDSTONE_FURNACE) {
            for (MachineHandle machine : machines) {
                primeThermalMachine(machine);
            }
            for (MachineHandle machine : machines) {
                fillEnergy(machine, requireTile(machine.pos, machine.id));
            }
            return;
        }
        for (int tick = 0; tick < 5; tick++) {
            for (MachineHandle machine : machines) {
                TileEntity tile = requireTile(machine.pos, machine.id);
                if (!(tile instanceof ITickable)) {
                    throw new BenchmarkEnvironmentException(
                        "Benchmark machine is not tickable: " + machine.id
                    );
                }
                ((ITickable) tile).update();
            }
        }
        if (kind != BenchmarkSuite.ScenarioKind.VANILLA_FURNACE) {
            for (MachineHandle machine : machines) {
                fillEnergy(machine, requireTile(machine.pos, machine.id));
            }
        }
    }

    private void primeThermalMachine(MachineHandle machine)
        throws BenchmarkEnvironmentException {
        TileEntity tile = requireTile(machine.pos, machine.id);
        try {
            Method getRecipe = findMethod(tile.getClass(), "getRecipe");
            Method processStart = findMethod(tile.getClass(), "processStart");
            Field isActive = findField(tile.getClass(), "isActive");
            getRecipe.invoke(tile);
            processStart.invoke(tile);
            isActive.setBoolean(tile, true);

            NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
            if (tag.getInteger("ProcMax") <= 0 || tag.getInteger("ProcRem") <= 0) {
                throw new BenchmarkEnvironmentException(
                    "Thermal machine did not enter a deterministic processing state: "
                        + machine.id
                );
            }
        } catch (BenchmarkEnvironmentException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new BenchmarkEnvironmentException(
                "Unable to prime Thermal processing state for " + machine.id
                    + ": " + failure.getClass().getSimpleName()
                    + ": " + String.valueOf(failure.getMessage())
            );
        }
    }

    private static Method findMethod(Class<?> type, String name)
        throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "()");
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
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private void fillEnergy(MachineHandle machine, TileEntity tile)
        throws BenchmarkEnvironmentException {
        IEnergyStorage energy = getEnergyStorage(tile);
        if (energy == null || !energy.canReceive()) {
            throw new BenchmarkEnvironmentException(
                "No writable Forge Energy capability for " + machine.id
            );
        }
        int previous = energy.getEnergyStored();
        int iterations = 0;
        while (previous < energy.getMaxEnergyStored() && iterations++ < 10000) {
            int accepted = energy.receiveEnergy(Integer.MAX_VALUE, false);
            int current = energy.getEnergyStored();
            if (accepted <= 0 || current <= previous) {
                break;
            }
            previous = current;
        }
        if (energy.getEnergyStored() <= 0) {
            throw new BenchmarkEnvironmentException(
                "Energy capability rejected precharge for " + machine.id
            );
        }
    }

    private void buildTorcherinos(
        String variant,
        int torchCount,
        int expectedMultiplier
    )
        throws BenchmarkEnvironmentException {
        if (torchCount <= 0) {
            return;
        }
        ResourceLocation registryName = new ResourceLocation(
            "torcherino",
            variant == null ? NORMAL_TORCHERINO.getPath() : variant
        );
        Block torch = ForgeRegistries.BLOCKS.getValue(registryName);
        if (torch == null) {
            throw new BenchmarkEnvironmentException(
                "Required Torcherino block is not registered: " + registryName
            );
        }
        int compressionFactor = variant != null && variant.contains("doublecompressed")
            ? 81
            : variant != null && variant.contains("compressed") ? 9 : 1;
        int divisor = Math.max(1, torchCount * compressionFactor);
        int speed = expectedMultiplier / divisor;
        if (speed < 1 || speed > 4 || speed * divisor != expectedMultiplier) {
            throw new BenchmarkEnvironmentException(
                "Cannot represent multiplier " + expectedMultiplier
                    + " with " + torchCount + " " + registryName
            );
        }
        for (BlockPos pos : torcherinoPlacements(torchCount)) {
            BlockPos support = pos.down();
            if (world.isAirBlock(support)) {
                setBlock(support, Blocks.STONE.getDefaultState(), null);
            }
            setBlock(pos, torch.getDefaultState(), null);
            TileEntity tile = world.getTileEntity(pos);
            if (tile == null) {
                throw new BenchmarkEnvironmentException(
                    "Torcherino tile entity did not initialize at " + pos
                );
            }
            NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
            tag.setByte("Speed", (byte) speed);
            tag.setByte("Mode", (byte) 4);
            tag.setBoolean("PoweredByRedstone", false);
            tile.readFromNBT(tag);
            torcherinoPositions.add(pos);
        }
    }

    private MachineState readMachineState(MachineHandle machine)
        throws BenchmarkEnvironmentException {
        TileEntity tile = requireTile(machine.pos, machine.id);
        NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
        List<ItemStack> stacks = readInventory(tile);
        long totalItems = 0L;
        long inputItems = 0L;
        long fuelItems = 0L;
        long outputItems = 0L;
        StringBuilder inventory = new StringBuilder();
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            int count = stack.isEmpty() ? 0 : stack.getCount();
            totalItems += count;
            if (isInputSlot(machine.kind, index)) {
                inputItems += count;
            } else if (isFuelSlot(machine.kind, index)) {
                fuelItems += count;
            } else if (isOutputSlot(machine.kind, index)) {
                outputItems += count;
            }
            if (index > 0) {
                inventory.append(';');
            }
            inventory.append(index).append('=').append(stackFingerprint(stack));
        }

        IEnergyStorage energy = getEnergyStorage(tile);
        long energyStored = energy == null ? readLong(tag, "Energy", "energy", "EnergyStored") :
            energy.getEnergyStored();
        long energyCapacity = energy == null
            ? readLong(tag, "MaxEnergy", "capacity", "EnergyCapacity")
            : energy.getMaxEnergyStored();
        long processMax = readLong(tag, "ProcMax");
        long processRem = readLong(tag, "ProcRem");
        long progress = processMax > 0L
            ? processMax - processRem
            : readLong(
                tag,
                "CookTime",
                "cookTime",
                "Progress",
                "progress",
                "ProcessTime",
                "processTime",
                "smeltingProgress"
            );
        long burnTime = readLong(
            tag,
            "BurnTime",
            "burnTime",
            "Fuel",
            "fuel"
        );
        if (tile instanceof TileEntityFurnace) {
            TileEntityFurnace furnace = (TileEntityFurnace) tile;
            burnTime = furnace.getField(0);
            progress = furnace.getField(2);
        }
        if (burnTime == 0L && fuelItems > 0L) {
            burnTime = fuelItems;
        }
        return new MachineState(
            machine.id,
            tag.toString(),
            inventory.toString(),
            totalItems,
            inputItems,
            fuelItems,
            outputItems,
            energyStored,
            energyCapacity,
            progress,
            burnTime
        );
    }

    private static boolean isInputSlot(String kind, int index) {
        if ("enderio_alloy_smelter".equals(kind)) {
            return index >= 0 && index <= 2;
        }
        return index == 0;
    }

    private static boolean isFuelSlot(String kind, int index) {
        return "vanilla".equals(kind) && index == 1;
    }

    private static boolean isOutputSlot(String kind, int index) {
        if ("vanilla".equals(kind)) {
            return index >= 2;
        }
        if ("thermal_redstone_furnace".equals(kind)) {
            return index >= 1;
        }
        if ("enderio_alloy_smelter".equals(kind)) {
            return index >= 3;
        }
        return index > 0;
    }

    private List<ItemStack> readInventory(TileEntity tile) {
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        if (tile instanceof IInventory) {
            IInventory inventory = (IInventory) tile;
            for (int index = 0; index < inventory.getSizeInventory(); index++) {
                stacks.add(inventory.getStackInSlot(index).copy());
            }
            return stacks;
        }
        IItemHandler handler = getItemHandler(tile);
        if (handler != null) {
            for (int index = 0; index < handler.getSlots(); index++) {
                stacks.add(handler.getStackInSlot(index).copy());
            }
        }
        return stacks;
    }

    private IItemHandler getItemHandler(TileEntity tile) {
        try {
            if (tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)) {
                return tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            }
        } catch (Throwable ignored) {
            // Capability shape is optional and may differ between mod builds.
        }
        return null;
    }

    private IEnergyStorage getEnergyStorage(TileEntity tile) {
        try {
            if (tile.hasCapability(CapabilityEnergy.ENERGY, null)) {
                return tile.getCapability(CapabilityEnergy.ENERGY, null);
            }
        } catch (Throwable ignored) {
            // Capability shape is optional and may differ between mod builds.
        }
        return null;
    }

    private static String stackFingerprint(ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.serializeNBT().toString();
    }

    private static long readLong(NBTTagCompound tag, String... keys) {
        for (String key : keys) {
            if (!tag.hasKey(key)) {
                continue;
            }
            try {
                return tag.getLong(key);
            } catch (Throwable ignored) {
                try {
                    return tag.getInteger(key);
                } catch (Throwable ignoredAgain) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private void activateTorcherinos() {
        for (BlockPos pos : torcherinoPositions) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof ITickable) {
                ((ITickable) tile).update();
            }
        }
    }

    private List<BlockPos> torcherinoPlacements(int torchCount) {
        List<BlockPos> placements = new ArrayList<BlockPos>();
        if (torchCount == 1) {
            placements.add(origin.add(WIDTH / 2, TORCH_Y_OFFSET, DEPTH / 2));
            return placements;
        }
        for (int x = 0; x <= WIDTH && placements.size() < torchCount; x++) {
            for (int z = 0; z <= DEPTH && placements.size() < torchCount; z++) {
                placements.add(origin.add(x, TORCH_Y_OFFSET, z));
            }
        }
        return placements;
    }

    private BlockPos machinePosition(int index) {
        if (centerSingleMachine) {
            return origin.add(WIDTH / 2, MACHINE_Y_OFFSET, DEPTH / 2);
        }
        return origin.add(index / WIDTH, MACHINE_Y_OFFSET, index % WIDTH);
    }

    private TileEntity requireTile(BlockPos pos, String label)
        throws BenchmarkEnvironmentException {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) {
            throw new BenchmarkEnvironmentException(
                "Missing " + label + " tile entity at " + pos
            );
        }
        return tile;
    }

    private void ensureChunksLoaded() {
        world.getChunk(min.getX() >> 4, min.getZ() >> 4);
        world.getChunk(max.getX() >> 4, max.getZ() >> 4);
    }

    private void captureOriginalOnce() {
        if (originalCaptured) {
            return;
        }
        captureSnapshot(original);
        originalCaptured = true;
    }

    private void captureMachineBaseline() {
        baseline.clear();
        for (MachineHandle machine : machines) {
            TileEntity tile = world.getTileEntity(machine.pos);
            NBTTagCompound tag = tile == null
                ? null
                : tile.writeToNBT(new NBTTagCompound());
            baseline.put(
                machine.pos.toLong(),
                new BlockSnapshot(world.getBlockState(machine.pos), tag)
            );
        }
    }

    private void restoreMachineBaseline() throws BenchmarkEnvironmentException {
        for (MachineHandle machine : machines) {
            BlockSnapshot snapshot = baseline.get(machine.pos.toLong());
            if (snapshot == null) {
                throw new BenchmarkEnvironmentException(
                    "Missing machine baseline for " + machine.id
                );
            }
            setBlock(machine.pos, snapshot.state, snapshot.nbt);
            TileEntity tile = requireTile(machine.pos, machine.id);
            if (!"vanilla".equals(machine.kind)) {
                fillEnergy(machine, tile);
            }
        }
    }

    private void captureSnapshot(Map<Long, BlockSnapshot> target) {
        target.clear();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    TileEntity tile = world.getTileEntity(pos);
                    NBTTagCompound tag = tile == null
                        ? null
                        : tile.writeToNBT(new NBTTagCompound());
                    target.put(
                        pos.toLong(),
                        new BlockSnapshot(world.getBlockState(pos), tag)
                    );
                }
            }
        }
    }

    private void restoreSnapshot(Map<Long, BlockSnapshot> snapshot) {
        for (Map.Entry<Long, BlockSnapshot> entry : snapshot.entrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getKey().longValue());
            BlockSnapshot blockSnapshot = entry.getValue();
            setBlock(pos, blockSnapshot.state, blockSnapshot.nbt);
        }
    }

    private void clearArena() {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    setBlock(
                        new BlockPos(x, y, z),
                        Blocks.AIR.getDefaultState(),
                        null
                    );
                }
            }
        }
    }

    private void setBlock(
        BlockPos pos,
        IBlockState state,
        NBTTagCompound tag
    ) {
        world.setBlockState(pos, state, 2);
        if (tag != null) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) {
                tile.readFromNBT(tag.copy());
            }
        }
    }

    private static final class BlockSnapshot {
        private final IBlockState state;
        private final NBTTagCompound nbt;

        private BlockSnapshot(IBlockState state, NBTTagCompound nbt) {
            this.state = state;
            this.nbt = nbt == null ? null : nbt.copy();
        }
    }

    private static final class MachineHandle {
        private final String id;
        private final BlockPos pos;
        private final String kind;

        private MachineHandle(String id, BlockPos pos, String kind) {
            this.id = id;
            this.pos = pos;
            this.kind = kind;
        }
    }

    private static final class OptionalMachineBinding {
        private final Block block;
        private final IBlockState state;
        private final String description;

        private OptionalMachineBinding(
            Block block,
            IBlockState state,
            String description
        ) {
            this.block = block;
            this.state = state;
            this.description = description;
        }

        private boolean accepts(TileEntity tile) {
            return tile != null;
        }
    }

    public static final class MachineObservation {
        private final List<MachineState> states;
        private final String stateHash;
        private final long inventoryItems;
        private final long inputItems;
        private final long fuelItems;
        private final long outputItems;
        private final long energyStored;
        private final long energyCapacity;
        private final long progress;
        private final long burnTime;

        private MachineObservation(List<MachineState> states) {
            this.states = Collections.unmodifiableList(
                new ArrayList<MachineState>(states)
            );
            StringBuilder hash = new StringBuilder();
            long inventory = 0L;
            long input = 0L;
            long fuel = 0L;
            long output = 0L;
            long energy = 0L;
            long capacity = 0L;
            long progressValue = 0L;
            long burn = 0L;
            for (MachineState state : states) {
                hash.append(state.id).append('|')
                    .append(state.nbtFingerprint).append('|')
                    .append(state.inventoryFingerprint).append('\n');
                inventory += state.inventoryItems;
                input += state.inputItems;
                fuel += state.fuelItems;
                output += state.outputItems;
                energy += state.energyStored;
                capacity += state.energyCapacity;
                progressValue += state.progress;
                burn += state.burnTime;
            }
            this.stateHash = hash.toString();
            this.inventoryItems = inventory;
            this.inputItems = input;
            this.fuelItems = fuel;
            this.outputItems = output;
            this.energyStored = energy;
            this.energyCapacity = capacity;
            this.progress = progressValue;
            this.burnTime = burn;
        }

        public String getStateHash() {
            return stateHash;
        }

        public long getInventoryItems() {
            return inventoryItems;
        }

        public long getInputItems() {
            return inputItems;
        }

        public long getFuelItems() {
            return fuelItems;
        }

        public long getOutputItems() {
            return outputItems;
        }

        public long getEnergyStored() {
            return energyStored;
        }

        public long getEnergyCapacity() {
            return energyCapacity;
        }

        public long getProgress() {
            return progress;
        }

        public long getBurnTime() {
            return burnTime;
        }
    }

    public static final class MachineMetrics {
        private final boolean baselineRestored;
        private final long inventoryBefore;
        private final long inventoryAfter;
        private final long fuelBefore;
        private final long fuelAfter;
        private final long energyBefore;
        private final long energyAfter;
        private final long progressBefore;
        private final long progressAfter;
        private final long outputBefore;
        private final long outputAfter;
        private final long inputConsumed;
        private final long fuelConsumed;
        private final long energyConsumed;
        private final long progressDelta;
        private final long outputDelta;
        private final double throughput;
        private final double unitFuelCost;
        private final double unitEnergyCost;

        private MachineMetrics(
            boolean baselineRestored,
            long inventoryBefore,
            long inventoryAfter,
            long fuelBefore,
            long fuelAfter,
            long energyBefore,
            long energyAfter,
            long progressBefore,
            long progressAfter,
            long outputBefore,
            long outputAfter,
            long inputConsumed,
            long fuelConsumed,
            long energyConsumed,
            long progressDelta,
            long outputDelta
        ) {
            this.baselineRestored = baselineRestored;
            this.inventoryBefore = inventoryBefore;
            this.inventoryAfter = inventoryAfter;
            this.fuelBefore = fuelBefore;
            this.fuelAfter = fuelAfter;
            this.energyBefore = energyBefore;
            this.energyAfter = energyAfter;
            this.progressBefore = progressBefore;
            this.progressAfter = progressAfter;
            this.outputBefore = outputBefore;
            this.outputAfter = outputAfter;
            this.inputConsumed = inputConsumed;
            this.fuelConsumed = fuelConsumed;
            this.energyConsumed = energyConsumed;
            this.progressDelta = progressDelta;
            this.outputDelta = outputDelta;
            this.throughput = outputDelta;
            this.unitFuelCost = outputDelta > 0L
                ? fuelConsumed / (double) outputDelta
                : -1.0D;
            this.unitEnergyCost = outputDelta > 0L && energyConsumed >= 0L
                ? energyConsumed / (double) outputDelta
                : -1.0D;
        }

        private static MachineMetrics between(
            MachineObservation baseline,
            MachineObservation before,
            MachineObservation after
        ) {
            long inputConsumed = Math.max(
                0L,
                before.inputItems - after.inputItems
            );
            long fuelConsumed = Math.max(
                0L,
                before.fuelItems - after.fuelItems
            );
            long energyConsumed = before.energyStored >= 0L && after.energyStored >= 0L
                ? Math.max(0L, before.energyStored - after.energyStored)
                : -1L;
            long outputDelta = Math.max(
                0L,
                after.outputItems - before.outputItems
            );
            return new MachineMetrics(
                baseline != null && baseline.stateHash.equals(before.stateHash),
                before.inventoryItems,
                after.inventoryItems,
                before.fuelItems,
                after.fuelItems,
                before.energyStored,
                after.energyStored,
                before.progress,
                after.progress,
                before.outputItems,
                after.outputItems,
                inputConsumed,
                fuelConsumed,
                energyConsumed,
                after.progress - before.progress,
                outputDelta
            );
        }

        public boolean isBaselineRestored() {
            return baselineRestored;
        }

        public long getInventoryBefore() {
            return inventoryBefore;
        }

        public long getInventoryAfter() {
            return inventoryAfter;
        }

        public long getFuelBefore() {
            return fuelBefore;
        }

        public long getFuelAfter() {
            return fuelAfter;
        }

        public long getEnergyBefore() {
            return energyBefore;
        }

        public long getEnergyAfter() {
            return energyAfter;
        }

        public long getProgressBefore() {
            return progressBefore;
        }

        public long getProgressAfter() {
            return progressAfter;
        }

        public long getOutputBefore() {
            return outputBefore;
        }

        public long getOutputAfter() {
            return outputAfter;
        }

        public long getInputConsumed() {
            return inputConsumed;
        }

        public long getFuelConsumed() {
            return fuelConsumed;
        }

        public long getEnergyConsumed() {
            return energyConsumed;
        }

        public long getProgressDelta() {
            return progressDelta;
        }

        public long getOutputDelta() {
            return outputDelta;
        }

        public double getThroughput() {
            return throughput;
        }

        public double getUnitFuelCost() {
            return unitFuelCost;
        }

        public double getUnitEnergyCost() {
            return unitEnergyCost;
        }
    }

    private static final class MachineState {
        private final String id;
        private final String nbtFingerprint;
        private final String inventoryFingerprint;
        private final long inventoryItems;
        private final long inputItems;
        private final long fuelItems;
        private final long outputItems;
        private final long energyStored;
        private final long energyCapacity;
        private final long progress;
        private final long burnTime;

        private MachineState(
            String id,
            String nbtFingerprint,
            String inventoryFingerprint,
            long inventoryItems,
            long inputItems,
            long fuelItems,
            long outputItems,
            long energyStored,
            long energyCapacity,
            long progress,
            long burnTime
        ) {
            this.id = id;
            this.nbtFingerprint = nbtFingerprint;
            this.inventoryFingerprint = inventoryFingerprint;
            this.inventoryItems = inventoryItems;
            this.inputItems = inputItems;
            this.fuelItems = fuelItems;
            this.outputItems = outputItems;
            this.energyStored = energyStored;
            this.energyCapacity = energyCapacity;
            this.progress = progress;
            this.burnTime = burnTime;
        }
    }
}
