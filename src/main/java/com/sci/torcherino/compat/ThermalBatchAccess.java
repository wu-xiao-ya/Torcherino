package com.sci.torcherino.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ThermalBatchAccess {
    private static final String MACHINE_BASE =
        "cofh.thermalexpansion.block.machine.TileMachineBase";
    private static final String TILE_POWERED = "cofh.core.block.TilePowered";
    private static final String ENERGY_CONFIG = "cofh.core.util.core.EnergyConfig";
    private static final String ENERGY_STORAGE =
        "cofh.redstoneflux.impl.EnergyStorage";

    private final Class<?> machineBaseClass;
    private final MethodHandle processRemainingGetter;
    private final MethodHandle processRemainingSetter;
    private final MethodHandle energyConfigGetter;
    private final MethodHandle energyStorageGetter;
    private final MethodHandle energyStoredGetter;
    private final MethodHandle energyStoredModifier;
    private final MethodHandle minPowerGetter;
    private final MethodHandle maxPowerGetter;
    private final MethodHandle minPowerLevelGetter;
    private final MethodHandle maxPowerLevelGetter;
    private final MethodHandle energyRampGetter;

    private ThermalBatchAccess(
        Class<?> machineBaseClass,
        MethodHandle processRemainingGetter,
        MethodHandle processRemainingSetter,
        MethodHandle energyConfigGetter,
        MethodHandle energyStorageGetter,
        MethodHandle energyStoredGetter,
        MethodHandle energyStoredModifier,
        MethodHandle minPowerGetter,
        MethodHandle maxPowerGetter,
        MethodHandle minPowerLevelGetter,
        MethodHandle maxPowerLevelGetter,
        MethodHandle energyRampGetter
    ) {
        this.machineBaseClass = machineBaseClass;
        this.processRemainingGetter = processRemainingGetter;
        this.processRemainingSetter = processRemainingSetter;
        this.energyConfigGetter = energyConfigGetter;
        this.energyStorageGetter = energyStorageGetter;
        this.energyStoredGetter = energyStoredGetter;
        this.energyStoredModifier = energyStoredModifier;
        this.minPowerGetter = minPowerGetter;
        this.maxPowerGetter = maxPowerGetter;
        this.minPowerLevelGetter = minPowerLevelGetter;
        this.maxPowerLevelGetter = maxPowerLevelGetter;
        this.energyRampGetter = energyRampGetter;
    }

    static ThermalBatchAccess bind(ClassLoader loader)
        throws ReflectiveOperationException {
        Class<?> machineBase = loader.loadClass(MACHINE_BASE);
        Class<?> tilePowered = loader.loadClass(TILE_POWERED);
        Class<?> energyConfig = loader.loadClass(ENERGY_CONFIG);
        Class<?> energyStorage = loader.loadClass(ENERGY_STORAGE);

        Field processRemaining = requireField(
            machineBase,
            "processRem",
            int.class
        );
        Field config = requireField(machineBase, "energyConfig", energyConfig);
        Field storage = requireField(tilePowered, "energyStorage", energyStorage);
        Method getEnergyStored = energyStorage.getMethod("getEnergyStored");
        Method modifyEnergyStored =
            energyStorage.getMethod("modifyEnergyStored", int.class);

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        return new ThermalBatchAccess(
            machineBase,
            lookup.unreflectGetter(processRemaining),
            lookup.unreflectSetter(processRemaining),
            lookup.unreflectGetter(config),
            lookup.unreflectGetter(storage),
            lookup.unreflect(getEnergyStored),
            lookup.unreflect(modifyEnergyStored),
            lookup.unreflectGetter(requireField(energyConfig, "minPower", int.class)),
            lookup.unreflectGetter(requireField(energyConfig, "maxPower", int.class)),
            lookup.unreflectGetter(
                requireField(energyConfig, "minPowerLevel", int.class)
            ),
            lookup.unreflectGetter(
                requireField(energyConfig, "maxPowerLevel", int.class)
            ),
            lookup.unreflectGetter(
                requireField(energyConfig, "energyRamp", int.class)
            )
        );
    }

    boolean supports(Object instance) {
        return machineBaseClass.isInstance(instance);
    }

    State read(Object instance) throws Throwable {
        Object config = energyConfigGetter.invoke(instance);
        Object storage = energyStorageGetter.invoke(instance);
        if (config == null || storage == null) {
            return null;
        }
        return new State(
            (int) processRemainingGetter.invoke(instance),
            (int) energyStoredGetter.invoke(storage),
            storage,
            new ThermalBatchAdvancer.EnergyParameters(
                (int) minPowerGetter.invoke(config),
                (int) maxPowerGetter.invoke(config),
                (int) minPowerLevelGetter.invoke(config),
                (int) maxPowerLevelGetter.invoke(config),
                (int) energyRampGetter.invoke(config)
            )
        );
    }

    void write(
        Object instance,
        State previous,
        ThermalBatchAdvancer.Segment segment
    ) throws Throwable {
        processRemainingSetter.invoke(instance, segment.processRemaining);
        int energyDelta = segment.energyStored - previous.energyStored;
        if (energyDelta != 0) {
            energyStoredModifier.invoke(previous.energyStorage, energyDelta);
        }
    }

    private static Field requireField(
        Class<?> owner,
        String name,
        Class<?> expectedType
    ) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != expectedType) {
            throw new NoSuchFieldException(
                owner.getName() + "." + name + " type mismatch"
            );
        }
        field.setAccessible(true);
        return field;
    }

    static final class State {
        final int processRemaining;
        final int energyStored;
        final Object energyStorage;
        final ThermalBatchAdvancer.EnergyParameters parameters;

        State(
            int processRemaining,
            int energyStored,
            Object energyStorage,
            ThermalBatchAdvancer.EnergyParameters parameters
        ) {
            this.processRemaining = processRemaining;
            this.energyStored = energyStored;
            this.energyStorage = energyStorage;
            this.parameters = parameters;
        }
    }
}
