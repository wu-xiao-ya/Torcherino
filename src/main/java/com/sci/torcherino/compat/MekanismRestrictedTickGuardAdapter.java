package com.sci.torcherino.compat;

import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdapterProbe;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.api.IExactAccelerationAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

final class MekanismRestrictedTickGuardAdapter
    implements IExactAccelerationAdapter<TileEntity> {
    private static final String SUPPORTED_VERSION = "10.0.1.455";
    private static final String RESTRICTED_TICK_CLASS =
        "mekanism.common.tile.base.TileEntityRestrictedTick";
    private static final String RESTRICTED_TICK_SIGNATURE =
        "ae4d78064478144c2841ed6b539cc61c962ee5501889bc97a96a6f2abcb717a2";
    private static final String BASIC_MACHINE_CLASS =
        "mekanism.common.tile.prefab.TileEntityBasicMachine";
    private static final String BASIC_MACHINE_SIGNATURE =
        "1d03090f6e3abd0bb21cb0e1d80ec2c4e1bda30c268a930a774070c52aa6e830";
    private static final String FACTORY_CLASS =
        "mekanism.common.tile.factory.TileEntityFactory";
    private static final String FACTORY_SIGNATURE =
        "e2168b25b61d8b2bbbc710d268ad6483452f2004bf2b31d5fe0a027da534c870";

    private Class<?> restrictedTickClass;

    @Override
    public String getId() {
        return "mekanism:restricted-world-tick-guard";
    }

    @Override
    public int getPriority() {
        return 1200;
    }

    @Override
    public AdapterClassification getClassification() {
        return AdapterClassification.BLACKLIST;
    }

    @Override
    public AdapterProbe probe() {
        ModContainer container = Loader.instance().getIndexedModList().get("mekanism");
        if (container == null) {
            return AdapterProbe.unavailable("mekanism is not loaded");
        }
        if (!SUPPORTED_VERSION.equals(container.getVersion())) {
            return AdapterProbe.unavailable(
                "signature-mismatch: unsupported Mekanism version "
                    + container.getVersion()
            );
        }
        if (!matches(RESTRICTED_TICK_CLASS, RESTRICTED_TICK_SIGNATURE)
            || !matches(BASIC_MACHINE_CLASS, BASIC_MACHINE_SIGNATURE)
            || !matches(FACTORY_CLASS, FACTORY_SIGNATURE)) {
            return AdapterProbe.unavailable(
                "signature-mismatch: Mekanism restricted tick layout changed"
            );
        }
        try {
            restrictedTickClass = getClass().getClassLoader().loadClass(
                RESTRICTED_TICK_CLASS
            );
            return AdapterProbe.available(
                "mekanism@" + SUPPORTED_VERSION
                    + "/restricted#" + RESTRICTED_TICK_SIGNATURE.substring(0, 12)
                    + "/basic#" + BASIC_MACHINE_SIGNATURE.substring(0, 12)
                    + "/factory#" + FACTORY_SIGNATURE.substring(0, 12),
                "unsupported-same-tick-guard: final update() rejects repeated "
                    + "world ticks and processing uses an asynchronous task executor"
            );
        } catch (ClassNotFoundException e) {
            restrictedTickClass = null;
            return AdapterProbe.unavailable(
                "signature-mismatch: restricted tick base is absent"
            );
        }
    }

    @Override
    public boolean supportsInstance(TileEntity tile) {
        return restrictedTickClass != null && restrictedTickClass.isInstance(tile);
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
    ) {
        return AdvanceResult.invalidated(0);
    }

    private static boolean matches(String className, String signature) {
        return signature.equals(StructuralSignature.digestForClass(className));
    }
}
