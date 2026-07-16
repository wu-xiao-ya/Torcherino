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
import java.lang.invoke.MethodType;

final class ThermalAccelerableAdapter
    implements IExactAccelerationAdapter<TileEntity> {
    private static final String INTERFACE_NAME = "cofh.api.core.IAccelerable";

    private Class<?> interfaceClass;
    private MethodHandle updateAccelerable;

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
        return interfaceClass != null && interfaceClass.isInstance(tile);
    }

    @Override
    public AdvanceResult advanceExact(TileEntity tile, int ticks, AccelerationContext context)
        throws Exception {
        int consumed = 0;
        try {
            for (; consumed < ticks; consumed++) {
                if (tile.isInvalid()) {
                    return AdvanceResult.invalidated(consumed);
                }
                updateAccelerable.invoke(tile);
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
}
