package com.sci.torcherino.compat;

import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdapterExecutionException;
import com.sci.torcherino.api.AdapterProbe;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.api.IExactAccelerationAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ThermalAccelerableAdapter
    implements IExactAccelerationAdapter<TileEntity> {
    private static final String INTERFACE_NAME = "cofh.api.core.IAccelerable";
    private static final String SUPPORTED_BATCH_VERSION = "5.5.7";
    private static final String SUPPORTED_BATCH_FILE_VERSION = "5.5.7.1";
    private static final String MACHINE_BASE =
        "cofh.thermalexpansion.block.machine.TileMachineBase";
    private static final String TILE_POWERED = "cofh.core.block.TilePowered";
    private static final String ENERGY_CONFIG = "cofh.core.util.core.EnergyConfig";
    private static final String ENERGY_STORAGE =
        "cofh.redstoneflux.impl.EnergyStorage";
    private static final String MACHINE_BASE_SIGNATURE =
        "fb4510656f726d135bea5ee935b94a28aabc3830f5f769a69ad7922f1d90d95e";
    private static final String TILE_POWERED_SIGNATURE =
        "a0ef3a45c934db4c7c8781c8fbf2741b8eb7e1df1a580460e2abe47ccfcf47f9";
    private static final String ENERGY_CONFIG_SIGNATURE =
        "b9e4b8129f1feb781e80516be0c1b6b4580888c36d1033b4210b53cfee70f7ac";
    private static final String ENERGY_STORAGE_SIGNATURE =
        "63bd372954cb717a7690ea9e85e8161b0c3573504f7a1f046d63c9aa0b035017";
    private static final int FAST_LOOP_MIN_TICKS = 5;

    private Class<?> interfaceClass;
    private MethodHandle updateAccelerable;
    private ThermalBatchAccess batchAccess;
    private final Map<Class<?>, MethodHandle> canFinishHandles =
        new ConcurrentHashMap<Class<?>, MethodHandle>();

    @Override
    public String getId() {
        return "thermalexpansion:official-accelerable";
    }

    @Override
    public int getPriority() {
        return 500;
    }

    @Override
    public AdapterClassification getClassification() {
        return AdapterClassification.EXACT_FAST_LOOP;
    }

    @Override
    public AdapterProbe probe() {
        ModContainer container = Loader.instance().getIndexedModList().get("thermalexpansion");
        if (container == null) {
            return AdapterProbe.unavailable("thermalexpansion is not loaded");
        }
        try {
            interfaceClass = getClass().getClassLoader().loadClass(INTERFACE_NAME);
            updateAccelerable = MethodHandles.publicLookup().findVirtual(
                interfaceClass,
                "updateAccelerable",
                MethodType.methodType(int.class)
            );
            String signature =
                "thermalexpansion@" + container.getVersion()
                    + "/" + StructuralSignature.firstAvailable(
                        new String[]{INTERFACE_NAME}
                    );
            if (supportsBatchStructure(container.getVersion())) {
                try {
                    batchAccess =
                        ThermalBatchAccess.bind(getClass().getClassLoader());
                    return AdapterProbe.available(
                        signature
                            + "/batch#"
                            + MACHINE_BASE_SIGNATURE.substring(0, 12),
                        "verified Thermal continuous-process batch advancement"
                    );
                } catch (ReflectiveOperationException e) {
                    batchAccess = null;
                    return AdapterProbe.available(
                        signature,
                        "CoFH IAccelerable exact loop; batch access mismatch"
                    );
                } catch (SecurityException e) {
                    batchAccess = null;
                    return AdapterProbe.available(
                        signature,
                        "CoFH IAccelerable exact loop; batch access denied"
                    );
                }
            }
            batchAccess = null;
            return AdapterProbe.available(
                signature,
                "CoFH IAccelerable exact loop; batch structure not verified"
            );
        } catch (ClassNotFoundException e) {
            return AdapterProbe.unavailable("CoFH IAccelerable is absent");
        } catch (NoSuchMethodException e) {
            return AdapterProbe.unavailable("updateAccelerable() signature mismatch");
        } catch (IllegalAccessException e) {
            return AdapterProbe.unavailable("updateAccelerable() is not publicly accessible");
        }
    }

    @Override
    public boolean supportsInstance(TileEntity tile) {
        return interfaceClass != null
            && interfaceClass.isInstance(tile)
            && tile instanceof ITickable;
    }

    @Override
    public boolean canCacheSupportForInstance() {
        return true;
    }

    @Override
    public AdvanceResult advanceExact(TileEntity tile, int ticks, AccelerationContext context)
        throws Exception {
        if (ticks < FAST_LOOP_MIN_TICKS) {
            int consumed = 0;
            for (; consumed < ticks; consumed++) {
                if (tile.isInvalid()) {
                    return AdvanceResult.invalidated(consumed);
                }
                ((ITickable) tile).update();
            }
            return AdvanceResult.consumed(consumed, false);
        }
        int consumed = 0;
        try {
            MethodHandle canFinish = canFinishHandle(tile.getClass());
            if (batchAccess != null && batchAccess.supports(tile)) {
                return advanceBatched(tile, ticks, canFinish);
            }
            for (; consumed < ticks; consumed++) {
                if (tile.isInvalid()) {
                    return AdvanceResult.invalidated(consumed);
                }
                int energyUsed = (int) updateAccelerable.invoke(tile);
                if ((boolean) canFinish.invoke(tile) || energyUsed <= 0) {
                    ((ITickable) tile).update();
                }
            }
            return AdvanceResult.consumed(consumed, false);
        } catch (AdapterExecutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new AdapterExecutionException(
                consumed,
                "Thermal updateAccelerable invocation failed",
                throwable
            );
        }
    }

    private AdvanceResult advanceBatched(
        TileEntity tile,
        int ticks,
        MethodHandle canFinish
    ) throws AdapterExecutionException {
        int consumed = 0;
        try {
            while (consumed < ticks) {
                if (tile.isInvalid()) {
                    return AdvanceResult.invalidated(consumed);
                }

                ThermalBatchAccess.State state = batchAccess.read(tile);
                ThermalBatchAdvancer.Segment segment = state == null
                    ? null
                    : ThermalBatchAdvancer.advance(
                        state.processRemaining,
                        state.energyStored,
                        state.parameters,
                        ticks - consumed
                    );
                if (segment == null || segment.ticks <= 0) {
                    int energyUsed = (int) updateAccelerable.invoke(tile);
                    consumed++;
                    if ((boolean) canFinish.invoke(tile) || energyUsed <= 0) {
                        ((ITickable) tile).update();
                    }
                    continue;
                }

                batchAccess.write(tile, state, segment);
                consumed += segment.ticks;
                if (segment.processRemaining <= 0
                    && (boolean) canFinish.invoke(tile)) {
                    ((ITickable) tile).update();
                }
            }
            return AdvanceResult.consumed(consumed, false);
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new AdapterExecutionException(
                consumed,
                "Thermal batch invocation failed",
                throwable
            );
        }
    }

    private static boolean supportsBatchStructure(String thermalVersion) {
        return (SUPPORTED_BATCH_VERSION.equals(thermalVersion)
            || SUPPORTED_BATCH_FILE_VERSION.equals(thermalVersion))
            && MACHINE_BASE_SIGNATURE.equals(
                StructuralSignature.digestForClass(MACHINE_BASE)
            )
            && TILE_POWERED_SIGNATURE.equals(
                StructuralSignature.digestForClass(TILE_POWERED)
            )
            && ENERGY_CONFIG_SIGNATURE.equals(
                StructuralSignature.digestForClass(ENERGY_CONFIG)
            )
            && ENERGY_STORAGE_SIGNATURE.equals(
                StructuralSignature.digestForClass(ENERGY_STORAGE)
            );
    }

    private MethodHandle canFinishHandle(Class<?> tileClass)
        throws IllegalAccessException, NoSuchMethodException {
        MethodHandle cached = canFinishHandles.get(tileClass);
        if (cached != null) {
            return cached;
        }
        Class<?> current = tileClass;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod("canFinish");
                method.setAccessible(true);
                MethodHandle handle = MethodHandles.lookup().unreflect(method);
                MethodHandle previous = canFinishHandles.putIfAbsent(tileClass, handle);
                return previous == null ? handle : previous;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(tileClass.getName() + ".canFinish()");
    }
}
