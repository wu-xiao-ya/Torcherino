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
import java.lang.reflect.Method;

final class EnderIoPoweredTaskAdapter
    implements IExactAccelerationAdapter<TileEntity> {
    private static final String SUPPORTED_VERSION = "5.4.2";
    private static final String LEGACY_CLASS =
        "crazypants.enderio.base.machine.baselegacy.AbstractPoweredTaskEntity";
    private static final String CAPABILITY_CLASS =
        "crazypants.enderio.base.machine.base.te.AbstractCapabilityPoweredTaskEntity";
    private static final String LEGACY_SIGNATURE =
        "f28e84e0766a46a56a900432540aa70fa0d54c067fbb6dddb05c7fc718900dce";
    private static final String CAPABILITY_SIGNATURE =
        "3fc4347455b410768d62689cd0a7712ec3fed58623ac252a95052612c1c432ec";

    private Access legacyAccess;
    private Access capabilityAccess;

    @Override
    public String getId() {
        return "enderio:powered-task-process";
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
        ModContainer container = Loader.instance().getIndexedModList().get("enderio");
        if (container == null) {
            return AdapterProbe.unavailable("enderio is not loaded");
        }
        if (!SUPPORTED_VERSION.equals(container.getVersion())) {
            return AdapterProbe.unavailable(
                "unsupported Ender IO version " + container.getVersion()
            );
        }
        if (!LEGACY_SIGNATURE.equals(StructuralSignature.digestForClass(LEGACY_CLASS))
            || !CAPABILITY_SIGNATURE.equals(
                StructuralSignature.digestForClass(CAPABILITY_CLASS)
            )) {
            return AdapterProbe.unavailable("Ender IO powered-task signature mismatch");
        }
        try {
            ClassLoader loader = getClass().getClassLoader();
            legacyAccess = Access.bind(loader.loadClass(LEGACY_CLASS));
            capabilityAccess = Access.bind(loader.loadClass(CAPABILITY_CLASS));
            return AdapterProbe.available(
                "enderio@" + SUPPORTED_VERSION
                    + "/legacy#" + LEGACY_SIGNATURE.substring(0, 12)
                    + "/capability#" + CAPABILITY_SIGNATURE.substring(0, 12),
                "powered-task fast loop without repeated machine energy loss"
            );
        } catch (ReflectiveOperationException e) {
            legacyAccess = null;
            capabilityAccess = null;
            return AdapterProbe.unavailable(
                "Ender IO powered-task access mismatch: " + e.getClass().getSimpleName()
            );
        } catch (SecurityException e) {
            legacyAccess = null;
            capabilityAccess = null;
            return AdapterProbe.unavailable("Ender IO powered-task access denied");
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
        Access access = accessFor(tile);
        if (access == null) {
            return AdvanceResult.fallback(ticks);
        }
        return advanceWithAccess(tile, ticks, access);
    }

    static AdvanceResult advanceWithAccess(TileEntity tile, int ticks, Access access)
        throws AdapterExecutionException {
        int consumed = 0;
        try {
            for (; consumed < ticks; consumed++) {
                if (tile.isInvalid()) {
                    return AdvanceResult.invalidated(consumed);
                }
                boolean redstonePassed = access.getRedstoneChecksPassed(tile);
                access.processTasks(tile, redstonePassed);
            }
            return AdvanceResult.consumed(consumed, true);
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new AdapterExecutionException(
                consumed,
                "Ender IO powered-task invocation failed",
                throwable
            );
        }
    }

    private Access accessFor(TileEntity tile) {
        if (legacyAccess != null && legacyAccess.supports(tile)) {
            return legacyAccess;
        }
        if (capabilityAccess != null && capabilityAccess.supports(tile)) {
            return capabilityAccess;
        }
        return null;
    }

    static final class Access {
        private final Class<?> ownerClass;
        private final MethodHandle processTasks;
        private final MethodHandle redstoneChecks;

        private Access(
            Class<?> ownerClass,
            MethodHandle processTasks,
            MethodHandle redstoneChecks
        ) {
            this.ownerClass = ownerClass;
            this.processTasks = processTasks;
            this.redstoneChecks = redstoneChecks;
        }

        static Access bind(Class<?> ownerClass) throws ReflectiveOperationException {
            Method process = ownerClass.getDeclaredMethod("processTasks", boolean.class);
            Method redstone = ownerClass.getMethod("getRedstoneChecksPassed");
            process.setAccessible(true);
            redstone.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            return new Access(
                ownerClass,
                lookup.unreflect(process),
                lookup.unreflect(redstone)
            );
        }

        boolean supports(Object instance) {
            return ownerClass.isInstance(instance);
        }

        void processTasks(Object instance, boolean redstonePassed) throws Throwable {
            processTasks.invoke(instance, redstonePassed);
        }

        boolean getRedstoneChecksPassed(Object instance) throws Throwable {
            return (boolean) redstoneChecks.invoke(instance);
        }
    }
}
