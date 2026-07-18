package com.sci.torcherino.compat;

import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdapterExecutionException;
import com.sci.torcherino.api.AdapterProbe;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.api.IExactAccelerationAdapter;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class ActuallyAdditionsProcessingAuditAdapter
    implements IExactAccelerationAdapter<TileEntity> {
    private static final String SUPPORTED_VERSION = "1.12.2-r152";
    private static final String CANOLA_CLASS =
        "de.ellpeck.actuallyadditions.mod.tile.TileEntityCanolaPress";
    private static final String CANOLA_SIGNATURE =
        "ae68abb99a760ed80fef3f2c0d66f56f08e03e946cf70fa50679901f7f121da7";
    private static final String BARREL_CLASS =
        "de.ellpeck.actuallyadditions.mod.tile.TileEntityFermentingBarrel";
    private static final String BARREL_SIGNATURE =
        "3a58490f763cb3cf5fe9b9b87156973efca1b61898c046e1e367d9482672fe78";
    private static final String FLUIDS_CLASS =
        "de.ellpeck.actuallyadditions.mod.fluids.InitFluids";

    private CanolaAccess canolaAccess;
    private BarrelAccess barrelAccess;

    @Override
    public String getId() {
        return "actuallyadditions:processing-batch";
    }

    @Override
    public int getPriority() {
        return 700;
    }

    @Override
    public AdapterClassification getClassification() {
        return AdapterClassification.EXACT_BATCH;
    }

    @Override
    public AdapterProbe probe() {
        ModContainer container = Loader.instance().getIndexedModList().get(
            "actuallyadditions"
        );
        if (container == null) {
            return AdapterProbe.unavailable("actuallyadditions is not loaded");
        }
        if (!SUPPORTED_VERSION.equals(container.getVersion())) {
            return AdapterProbe.unavailable(
                "signature-mismatch: unsupported Actually Additions version "
                    + container.getVersion()
            );
        }
        if (!CANOLA_SIGNATURE.equals(
            StructuralSignature.digestForClass(CANOLA_CLASS)
        ) || !BARREL_SIGNATURE.equals(
            StructuralSignature.digestForClass(BARREL_CLASS)
        )) {
            return AdapterProbe.unavailable(
                "signature-mismatch: Actually Additions processing layout changed"
            );
        }
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> fluidsClass = loader.loadClass(FLUIDS_CLASS);
            Fluid canolaOil = (Fluid) readStaticField(
                fluidsClass,
                "fluidCanolaOil"
            );
            Fluid refinedOil = (Fluid) readStaticField(
                fluidsClass,
                "fluidRefinedCanolaOil"
            );
            canolaAccess = CanolaAccess.bind(
                loader.loadClass(CANOLA_CLASS),
                canolaOil
            );
            barrelAccess = BarrelAccess.bind(
                loader.loadClass(BARREL_CLASS),
                refinedOil
            );
            return AdapterProbe.available(
                "actuallyadditions@" + SUPPORTED_VERSION
                    + "/canola#" + CANOLA_SIGNATURE.substring(0, 12)
                    + "/barrel#" + BARREL_SIGNATURE.substring(0, 12),
                "boundary batch for Canola Press and Fermenting Barrel; "
                    + "neighbor fluid and energy sharing remains real-tick only"
            );
        } catch (ReflectiveOperationException e) {
            clearAccess();
            return AdapterProbe.unavailable(
                "signature-mismatch: Actually Additions access "
                    + e.getClass().getSimpleName()
            );
        } catch (SecurityException e) {
            clearAccess();
            return AdapterProbe.unavailable(
                "Actually Additions processing access denied"
            );
        }
    }

    @Override
    public boolean supportsInstance(TileEntity tile) {
        return accessFor(tile) != null;
    }

    @Override
    public boolean canCacheSupportForInstance() {
        return true;
    }

    @Override
    public AdvanceResult advanceExact(
        TileEntity tile,
        int ticks,
        AccelerationContext context
    ) throws Exception {
        ProcessingAccess access = accessFor(tile);
        if (access == null) {
            return AdvanceResult.fallback(ticks);
        }
        return advanceWithAccess(tile, ticks, access);
    }

    static AdvanceResult advanceWithAccess(
        TileEntity tile,
        int ticks,
        ProcessingAccess access
    ) throws AdapterExecutionException {
        try {
            return access.advance(tile, ticks);
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new AdapterExecutionException(
                0,
                "Actually Additions processing invocation failed",
                throwable
            );
        }
    }

    private ProcessingAccess accessFor(TileEntity tile) {
        if (canolaAccess != null && canolaAccess.supports(tile)) {
            return canolaAccess;
        }
        if (barrelAccess != null && barrelAccess.supports(tile)) {
            return barrelAccess;
        }
        return null;
    }

    private void clearAccess() {
        canolaAccess = null;
        barrelAccess = null;
    }

    private static Object readStaticField(Class<?> owner, String name)
        throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Field findField(Class<?> owner, String name)
        throws NoSuchFieldException {
        Class<?> type = owner;
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
    }

    private static Method findMethod(Class<?> owner, String name, int arguments)
        throws NoSuchMethodException {
        Class<?> type = owner;
        while (type != null) {
            Method[] methods = type.getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().equals(name)
                    && method.getParameterTypes().length == arguments) {
                    method.setAccessible(true);
                    return method;
                }
            }
            type = type.getSuperclass();
        }
        throw new NoSuchMethodException(owner.getName() + "#" + name);
    }

    interface ProcessingAccess {
        boolean supports(Object instance);

        AdvanceResult advance(TileEntity tile, int ticks) throws Throwable;
    }

    static final class CanolaAccess implements ProcessingAccess {
        private static final int ENERGY_PER_TICK = 35;
        private static final int PROCESS_TICKS = 30;
        private static final int FLUID_PER_OPERATION = 80;

        private final Class<?> ownerClass;
        private final MethodHandle storageGetter;
        private final MethodHandle tankGetter;
        private final MethodHandle inventoryGetter;
        private final MethodHandle progressGetter;
        private final MethodHandle progressSetter;
        private final MethodHandle getEnergy;
        private final MethodHandle extractEnergy;
        private final MethodHandle getStack;
        private final MethodHandle setStack;
        private final MethodHandle isCanola;
        private final Fluid outputFluid;

        private CanolaAccess(
            Class<?> ownerClass,
            MethodHandle storageGetter,
            MethodHandle tankGetter,
            MethodHandle inventoryGetter,
            MethodHandle progressGetter,
            MethodHandle progressSetter,
            MethodHandle getEnergy,
            MethodHandle extractEnergy,
            MethodHandle getStack,
            MethodHandle setStack,
            MethodHandle isCanola,
            Fluid outputFluid
        ) {
            this.ownerClass = ownerClass;
            this.storageGetter = storageGetter;
            this.tankGetter = tankGetter;
            this.inventoryGetter = inventoryGetter;
            this.progressGetter = progressGetter;
            this.progressSetter = progressSetter;
            this.getEnergy = getEnergy;
            this.extractEnergy = extractEnergy;
            this.getStack = getStack;
            this.setStack = setStack;
            this.isCanola = isCanola;
            this.outputFluid = outputFluid;
        }

        static CanolaAccess bind(Class<?> ownerClass, Fluid outputFluid)
            throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Field storage = findField(ownerClass, "storage");
            Field tank = findField(ownerClass, "tank");
            Field inventory = findField(ownerClass, "inv");
            Field progress = findField(ownerClass, "currentProcessTime");
            Method getEnergyMethod = findMethod(
                storage.getType(),
                "getEnergyStored",
                0
            );
            Method extractMethod = findMethod(
                storage.getType(),
                "extractEnergyInternal",
                2
            );
            Method getStackMethod = findMethod(
                inventory.getType(),
                "getStackInSlot",
                1
            );
            Method setStackMethod = findMethod(
                inventory.getType(),
                "setStackInSlot",
                2
            );
            Method isCanolaMethod = findMethod(ownerClass, "isCanola", 1);
            if (!Modifier.isStatic(isCanolaMethod.getModifiers())) {
                throw new NoSuchMethodException("isCanola is not static");
            }
            return new CanolaAccess(
                ownerClass,
                lookup.unreflectGetter(storage),
                lookup.unreflectGetter(tank),
                lookup.unreflectGetter(inventory),
                lookup.unreflectGetter(progress),
                lookup.unreflectSetter(progress),
                lookup.unreflect(getEnergyMethod),
                lookup.unreflect(extractMethod),
                lookup.unreflect(getStackMethod),
                lookup.unreflect(setStackMethod),
                lookup.unreflect(isCanolaMethod),
                outputFluid
            );
        }

        @Override
        public boolean supports(Object instance) {
            return ownerClass.isInstance(instance);
        }

        @Override
        public AdvanceResult advance(TileEntity tile, int ticks)
            throws Throwable {
            Object storage = storageGetter.invoke(tile);
            FluidTank tank = (FluidTank) tankGetter.invoke(tile);
            Object inventory = inventoryGetter.invoke(tile);
            int progress = (int) progressGetter.invoke(tile);
            int consumed = 0;
            boolean changed = false;

            while (consumed < ticks) {
                if (tile.isInvalid()) {
                    progressSetter.invoke(tile, progress);
                    return AdvanceResult.invalidated(consumed);
                }
                ItemStack input = (ItemStack) getStack.invoke(inventory, 0);
                boolean canProcess = !input.isEmpty()
                    && (boolean) isCanola.invoke(input)
                    && tank.getCapacity() - tank.getFluidAmount()
                        >= FLUID_PER_OPERATION;
                if (!canProcess) {
                    if (progress != 0) {
                        progress = 0;
                        changed = true;
                    }
                    consumed = ticks;
                    break;
                }

                int affordable = ((int) getEnergy.invoke(storage))
                    / ENERGY_PER_TICK;
                if (affordable <= 0) {
                    consumed = ticks;
                    break;
                }
                int step = Math.min(
                    ticks - consumed,
                    Math.min(affordable, PROCESS_TICKS - progress)
                );
                extractEnergy.invoke(
                    storage,
                    step * ENERGY_PER_TICK,
                    false
                );
                progress += step;
                consumed += step;
                changed = true;

                if (progress >= PROCESS_TICKS) {
                    progress = 0;
                    ItemStack reduced = input.copy();
                    reduced.shrink(1);
                    setStack.invoke(
                        inventory,
                        0,
                        reduced.isEmpty() ? ItemStack.EMPTY : reduced
                    );
                    tank.fillInternal(
                        new FluidStack(outputFluid, FLUID_PER_OPERATION),
                        true
                    );
                }
            }

            progressSetter.invoke(tile, progress);
            return AdvanceResult.consumed(ticks, changed);
        }
    }

    static final class BarrelAccess implements ProcessingAccess {
        private static final int PROCESS_TICKS = 100;
        private static final int FLUID_PER_OPERATION = 80;

        private final Class<?> ownerClass;
        private final MethodHandle inputTankGetter;
        private final MethodHandle outputTankGetter;
        private final MethodHandle progressGetter;
        private final MethodHandle progressSetter;
        private final Fluid outputFluid;

        private BarrelAccess(
            Class<?> ownerClass,
            MethodHandle inputTankGetter,
            MethodHandle outputTankGetter,
            MethodHandle progressGetter,
            MethodHandle progressSetter,
            Fluid outputFluid
        ) {
            this.ownerClass = ownerClass;
            this.inputTankGetter = inputTankGetter;
            this.outputTankGetter = outputTankGetter;
            this.progressGetter = progressGetter;
            this.progressSetter = progressSetter;
            this.outputFluid = outputFluid;
        }

        static BarrelAccess bind(Class<?> ownerClass, Fluid outputFluid)
            throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Field inputTank = findField(ownerClass, "canolaTank");
            Field outputTank = findField(ownerClass, "oilTank");
            Field progress = findField(ownerClass, "currentProcessTime");
            return new BarrelAccess(
                ownerClass,
                lookup.unreflectGetter(inputTank),
                lookup.unreflectGetter(outputTank),
                lookup.unreflectGetter(progress),
                lookup.unreflectSetter(progress),
                outputFluid
            );
        }

        @Override
        public boolean supports(Object instance) {
            return ownerClass.isInstance(instance);
        }

        @Override
        public AdvanceResult advance(TileEntity tile, int ticks)
            throws Throwable {
            FluidTank inputTank = (FluidTank) inputTankGetter.invoke(tile);
            FluidTank outputTank = (FluidTank) outputTankGetter.invoke(tile);
            int progress = (int) progressGetter.invoke(tile);
            int consumed = 0;
            boolean changed = false;

            while (consumed < ticks) {
                if (tile.isInvalid()) {
                    progressSetter.invoke(tile, progress);
                    return AdvanceResult.invalidated(consumed);
                }
                boolean canProcess =
                    inputTank.getFluidAmount() >= FLUID_PER_OPERATION
                        && outputTank.getCapacity()
                            - outputTank.getFluidAmount()
                            >= FLUID_PER_OPERATION;
                if (!canProcess) {
                    if (progress != 0) {
                        progress = 0;
                        changed = true;
                    }
                    consumed = ticks;
                    break;
                }

                int step = Math.min(
                    ticks - consumed,
                    PROCESS_TICKS - progress
                );
                progress += step;
                consumed += step;
                changed = true;

                if (progress >= PROCESS_TICKS) {
                    progress = 0;
                    outputTank.fillInternal(
                        new FluidStack(outputFluid, FLUID_PER_OPERATION),
                        true
                    );
                    inputTank.drainInternal(FLUID_PER_OPERATION, true);
                }
            }

            progressSetter.invoke(tile, progress);
            return AdvanceResult.consumed(ticks, changed);
        }
    }
}
