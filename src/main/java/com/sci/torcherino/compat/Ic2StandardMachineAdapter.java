package com.sci.torcherino.compat;

import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdapterExecutionException;
import com.sci.torcherino.api.AdapterProbe;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.api.IExactAccelerationAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Ic2StandardMachineAdapter
    implements IExactAccelerationAdapter<TileEntity> {
    private static final String SUPPORTED_VERSION = "2.8.188-ex112";
    private static final String STANDARD_MACHINE_CLASS =
        "ic2.core.block.machine.tileentity.TileEntityStandardMachine";
    private static final String STANDARD_MACHINE_SIGNATURE =
        "a73f3eef20b44a150b4265d0a32ec7d38c56b7ba968176eef06028705b8313da";
    private static final String ELECTRIC_MACHINE_CLASS =
        "ic2.core.block.machine.tileentity.TileEntityElectricMachine";
    private static final String ELECTRIC_MACHINE_SIGNATURE =
        "c3c2cd1a3f3b08ded7c3744666c06857f600b2c0f312124e3bc673b02f6b9722";

    private Class<?> standardMachineClass;
    private Access access;

    @Override
    public String getId() {
        return "ic2:standard-machine-no-upgrade-loop";
    }

    @Override
    public int getPriority() {
        return 700;
    }

    @Override
    public AdapterClassification getClassification() {
        return AdapterClassification.EXACT_FAST_LOOP;
    }

    @Override
    public AdapterProbe probe() {
        ModContainer container = Loader.instance().getIndexedModList().get("ic2");
        if (container == null) {
            return AdapterProbe.unavailable("ic2 is not loaded");
        }
        if (!SUPPORTED_VERSION.equals(container.getVersion())) {
            return AdapterProbe.unavailable(
                "signature-mismatch: unsupported IC2 version "
                    + container.getVersion()
            );
        }
        if (!STANDARD_MACHINE_SIGNATURE.equals(
            StructuralSignature.digestForClass(STANDARD_MACHINE_CLASS)
        ) || !ELECTRIC_MACHINE_SIGNATURE.equals(
            StructuralSignature.digestForClass(ELECTRIC_MACHINE_CLASS)
        )) {
            return AdapterProbe.unavailable(
                "signature-mismatch: IC2 standard machine layout changed"
            );
        }
        try {
            ClassLoader loader = getClass().getClassLoader();
            standardMachineClass = loader.loadClass(STANDARD_MACHINE_CLASS);
            access = Access.bind(
                standardMachineClass,
                NetworkEmitter.bind(loader)
            );
            return AdapterProbe.available(
                "ic2@" + SUPPORTED_VERSION
                    + "/standard#" + STANDARD_MACHINE_SIGNATURE.substring(0, 12)
                    + "/electric#" + ELECTRIC_MACHINE_SIGNATURE.substring(0, 12),
                "standard-machine processing loop with no upgrade items; "
                    + "discharge and upgrade slots remain real-tick only"
            );
        } catch (ReflectiveOperationException e) {
            standardMachineClass = null;
            access = null;
            return AdapterProbe.unavailable(
                "signature-mismatch: IC2 standard-machine access "
                    + e.getClass().getSimpleName()
            );
        } catch (SecurityException e) {
            standardMachineClass = null;
            access = null;
            return AdapterProbe.unavailable(
                "IC2 standard-machine access denied"
            );
        }
    }

    @Override
    public boolean supportsInstance(TileEntity tile) {
        if (standardMachineClass == null
            || access == null
            || !inheritsBaseServerUpdate(tile, standardMachineClass)) {
            return false;
        }
        try {
            return access.hasNoUpgrades(tile);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean canCacheSupportForInstance() {
        return false;
    }

    @Override
    public AdvanceResult advanceExact(
        TileEntity tile,
        int ticks,
        AccelerationContext context
    ) throws Exception {
        if (access == null || !supportsInstance(tile)) {
            return AdvanceResult.fallback(ticks);
        }
        return advanceWithAccess(tile, ticks, access);
    }

    static AdvanceResult advanceWithAccess(
        TileEntity tile,
        int ticks,
        Access access
    ) throws AdapterExecutionException {
        return access.advance(tile, ticks);
    }

    static boolean inheritsBaseServerUpdate(Object instance, Class<?> ownerClass) {
        if (!ownerClass.isInstance(instance)) {
            return false;
        }
        Class<?> type = instance.getClass();
        while (type != null && type != ownerClass) {
            try {
                type.getDeclaredMethod("updateEntityServer");
                return false;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        return type == ownerClass;
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

    interface EventEmitter {
        void emit(TileEntity tile, int event) throws Throwable;
    }

    static final class Access {
        private final Class<?> ownerClass;
        private final MethodHandle progressGetter;
        private final MethodHandle progressSetter;
        private final MethodHandle energyConsumeGetter;
        private final MethodHandle operationLengthGetter;
        private final MethodHandle guiProgressSetter;
        private final MethodHandle upgradeSlotGetter;
        private final MethodHandle upgradeSlotEmpty;
        private final MethodHandle getOutput;
        private final MethodHandle operate;
        private final MethodHandle useEnergy;
        private final MethodHandle setActive;
        private final MethodHandle getActive;
        private final EventEmitter eventEmitter;

        private Access(
            Class<?> ownerClass,
            MethodHandle progressGetter,
            MethodHandle progressSetter,
            MethodHandle energyConsumeGetter,
            MethodHandle operationLengthGetter,
            MethodHandle guiProgressSetter,
            MethodHandle upgradeSlotGetter,
            MethodHandle upgradeSlotEmpty,
            MethodHandle getOutput,
            MethodHandle operate,
            MethodHandle useEnergy,
            MethodHandle setActive,
            MethodHandle getActive,
            EventEmitter eventEmitter
        ) {
            this.ownerClass = ownerClass;
            this.progressGetter = progressGetter;
            this.progressSetter = progressSetter;
            this.energyConsumeGetter = energyConsumeGetter;
            this.operationLengthGetter = operationLengthGetter;
            this.guiProgressSetter = guiProgressSetter;
            this.upgradeSlotGetter = upgradeSlotGetter;
            this.upgradeSlotEmpty = upgradeSlotEmpty;
            this.getOutput = getOutput;
            this.operate = operate;
            this.useEnergy = useEnergy;
            this.setActive = setActive;
            this.getActive = getActive;
            this.eventEmitter = eventEmitter;
        }

        static Access bind(Class<?> ownerClass, EventEmitter eventEmitter)
            throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Field progress = findField(ownerClass, "progress");
            Field energyConsume = findField(ownerClass, "energyConsume");
            Field operationLength = findField(ownerClass, "operationLength");
            Field guiProgress = findField(ownerClass, "guiProgress");
            Field upgradeSlot = findField(ownerClass, "upgradeSlot");
            Method empty = findMethod(upgradeSlot.getType(), "isEmpty", 0);
            Method output = findMethod(ownerClass, "getOutput", 0);
            Method operate = findMethod(ownerClass, "operate", 1);
            Method useEnergy = findMethod(ownerClass, "useEnergy", 1);
            Method setActive = findMethod(ownerClass, "setActive", 1);
            Method getActive = findMethod(ownerClass, "getActive", 0);
            return new Access(
                ownerClass,
                lookup.unreflectGetter(progress),
                lookup.unreflectSetter(progress),
                lookup.unreflectGetter(energyConsume),
                lookup.unreflectGetter(operationLength),
                lookup.unreflectSetter(guiProgress),
                lookup.unreflectGetter(upgradeSlot),
                lookup.unreflect(empty),
                lookup.unreflect(output),
                lookup.unreflect(operate),
                lookup.unreflect(useEnergy),
                lookup.unreflect(setActive),
                lookup.unreflect(getActive),
                eventEmitter
            );
        }

        boolean supports(Object instance) {
            return ownerClass.isInstance(instance);
        }

        boolean hasNoUpgrades(Object instance) throws Throwable {
            Object slot = upgradeSlotGetter.invoke(instance);
            return (boolean) upgradeSlotEmpty.invoke(slot);
        }

        AdvanceResult advance(TileEntity tile, int ticks)
            throws AdapterExecutionException {
            int consumed = 0;
            boolean changed = false;
            try {
                if (!supports(tile) || !hasNoUpgrades(tile)) {
                    return AdvanceResult.fallback(ticks);
                }
                int progress = (short) progressGetter.invoke(tile);
                int energyConsume = (int) energyConsumeGetter.invoke(tile);
                int operationLength = (int) operationLengthGetter.invoke(tile);

                for (; consumed < ticks; consumed++) {
                    if (tile.isInvalid()) {
                        progressSetter.invoke(tile, (short) progress);
                        updateGuiProgress(tile, progress, operationLength);
                        return AdvanceResult.invalidated(consumed);
                    }
                    Object output = getOutput.invoke(tile);
                    boolean powered = output != null
                        && (boolean) useEnergy.invoke(
                            tile,
                            (double) energyConsume
                        );
                    if (powered) {
                        setActive.invoke(tile, true);
                        if (progress == 0) {
                            eventEmitter.emit(tile, 0);
                        }
                        progress++;
                        changed = true;
                        progressSetter.invoke(tile, (short) progress);
                        if (progress >= operationLength) {
                            operate.invoke(tile, output);
                            progress = 0;
                            progressSetter.invoke(tile, (short) 0);
                            eventEmitter.emit(tile, 2);
                        }
                    } else {
                        if ((boolean) getActive.invoke(tile)) {
                            eventEmitter.emit(tile, progress != 0 ? 1 : 3);
                        }
                        if (output == null && progress != 0) {
                            progress = 0;
                            progressSetter.invoke(tile, (short) 0);
                            changed = true;
                        }
                        setActive.invoke(tile, false);
                    }
                }

                updateGuiProgress(tile, progress, operationLength);
                return AdvanceResult.consumed(ticks, changed);
            } catch (RuntimeException e) {
                throw e;
            } catch (Error e) {
                throw e;
            } catch (Throwable throwable) {
                throw new AdapterExecutionException(
                    consumed,
                    "IC2 standard-machine invocation failed",
                    throwable
                );
            }
        }

        private void updateGuiProgress(
            Object tile,
            int progress,
            int operationLength
        ) throws Throwable {
            float value = operationLength <= 0
                ? 0.0F
                : progress / (float) operationLength;
            guiProgressSetter.invoke(tile, value);
        }
    }

    static final class NetworkEmitter implements EventEmitter {
        private static final String IC2_CLASS = "ic2.core.IC2";
        private static final String NETWORK_MANAGER_CLASS =
            "ic2.core.network.NetworkManager";

        private final Object gateway;
        private final MethodHandle gatewayGet;
        private final MethodHandle initiateEvent;

        private NetworkEmitter(
            Object gateway,
            MethodHandle gatewayGet,
            MethodHandle initiateEvent
        ) {
            this.gateway = gateway;
            this.gatewayGet = gatewayGet;
            this.initiateEvent = initiateEvent;
        }

        static NetworkEmitter bind(ClassLoader loader)
            throws ReflectiveOperationException {
            Class<?> ic2Class = loader.loadClass(IC2_CLASS);
            Object gateway = findField(ic2Class, "network").get(null);
            Method get = findMethod(gateway.getClass(), "get", 1);
            Class<?> managerClass = loader.loadClass(NETWORK_MANAGER_CLASS);
            Method event = findMethod(
                managerClass,
                "initiateTileEntityEvent",
                3
            );
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return new NetworkEmitter(
                gateway,
                lookup.unreflect(get),
                lookup.unreflect(event)
            );
        }

        @Override
        public void emit(TileEntity tile, int event) throws Throwable {
            Object manager = gatewayGet.invoke(gateway, true);
            initiateEvent.invoke(manager, tile, event, true);
        }
    }
}
