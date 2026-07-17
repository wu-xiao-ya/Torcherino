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
    private static final int FAST_LOOP_MIN_TICKS = 5;

    private Class<?> interfaceClass;
    private MethodHandle updateAccelerable;
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
            return AdapterProbe.available(
                "thermalexpansion@" + container.getVersion()
                    + "/" + StructuralSignature.firstAvailable(new String[]{INTERFACE_NAME}),
                "CoFH IAccelerable contract"
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
