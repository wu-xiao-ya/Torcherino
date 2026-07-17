package com.sci.torcherino.compat;

import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdapterProbe;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.api.IExactAccelerationAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

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

    @Override
    public String getId() {
        return "ic2:standard-machine-shared-upgrade-audit";
    }

    @Override
    public int getPriority() {
        return 700;
    }

    @Override
    public AdapterClassification getClassification() {
        return AdapterClassification.LEGACY_FALLBACK;
    }

    @Override
    public AdapterProbe probe() {
        ModContainer container = Loader.instance().getIndexedModList().get("ic2");
        if (container == null) {
            return AdapterProbe.unavailable("ic2 is not loaded");
        }
        if (!SUPPORTED_VERSION.equals(container.getVersion())) {
            return AdapterProbe.unavailable(
                "signature-mismatch: unsupported IC2 version " + container.getVersion()
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
            standardMachineClass = getClass().getClassLoader().loadClass(
                STANDARD_MACHINE_CLASS
            );
            return AdapterProbe.available(
                "ic2@" + SUPPORTED_VERSION
                    + "/standard#" + STANDARD_MACHINE_SIGNATURE.substring(0, 12)
                    + "/electric#" + ELECTRIC_MACHINE_SIGNATURE.substring(0, 12),
                "unsupported-shared-upgrade-tick: the standard-machine server "
                    + "update also ticks upgrades, including neighboring automatic IO"
            );
        } catch (ClassNotFoundException e) {
            standardMachineClass = null;
            return AdapterProbe.unavailable(
                "signature-mismatch: IC2 standard machine class is absent"
            );
        }
    }

    @Override
    public boolean supportsInstance(TileEntity tile) {
        return standardMachineClass != null
            && inheritsBaseServerUpdate(tile, standardMachineClass);
    }

    @Override
    public AdvanceResult advanceExact(
        TileEntity tile,
        int ticks,
        AccelerationContext context
    ) {
        return AdvanceResult.fallback(ticks);
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
}
