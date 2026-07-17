package com.sci.torcherino.compat;

import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdapterProbe;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.api.IExactAccelerationAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

final class ActuallyAdditionsProcessingAuditAdapter
    implements IExactAccelerationAdapter<TileEntity> {
    private static final String SUPPORTED_VERSION = "1.12.2-r152";
    private static final String[] CLASS_NAMES = {
        "de.ellpeck.actuallyadditions.mod.tile.TileEntityGrinder",
        "de.ellpeck.actuallyadditions.mod.tile.TileEntityFurnaceDouble",
        "de.ellpeck.actuallyadditions.mod.tile.TileEntityCanolaPress",
        "de.ellpeck.actuallyadditions.mod.tile.TileEntityFermentingBarrel"
    };
    private static final String[] SIGNATURES = {
        "6b736e2685b2c5e72356ed766cbe81be6db15860dc23da872c51bba485d4e2ce",
        "c2981f719f8068781bd1479d362c4376a17d2c211b9e260d98c1762b61bdc208",
        "ae68abb99a760ed80fef3f2c0d66f56f08e03e946cf70fa50679901f7f121da7",
        "3a58490f763cb3cf5fe9b9b87156973efca1b61898c046e1e367d9482672fe78"
    };

    private Class<?>[] machineClasses;

    @Override
    public String getId() {
        return "actuallyadditions:processing-shared-base-audit";
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
        Class<?>[] loaded = new Class<?>[CLASS_NAMES.length];
        try {
            ClassLoader loader = getClass().getClassLoader();
            for (int i = 0; i < CLASS_NAMES.length; i++) {
                if (!SIGNATURES[i].equals(
                    StructuralSignature.digestForClass(CLASS_NAMES[i])
                )) {
                    machineClasses = null;
                    return AdapterProbe.unavailable(
                        "signature-mismatch: " + CLASS_NAMES[i]
                    );
                }
                loaded[i] = loader.loadClass(CLASS_NAMES[i]);
            }
            machineClasses = loaded;
            return AdapterProbe.available(
                "actuallyadditions@" + SUPPORTED_VERSION
                    + "/processing#" + SIGNATURES[0].substring(0, 12),
                "unsupported-shared-base-tick: processing updateEntity() also "
                    + "performs neighboring energy or fluid sharing"
            );
        } catch (ClassNotFoundException e) {
            machineClasses = null;
            return AdapterProbe.unavailable(
                "signature-mismatch: processing class is absent"
            );
        }
    }

    @Override
    public boolean supportsInstance(TileEntity tile) {
        if (machineClasses == null) {
            return false;
        }
        for (Class<?> machineClass : machineClasses) {
            if (machineClass.isInstance(tile)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public AdvanceResult advanceExact(
        TileEntity tile,
        int ticks,
        AccelerationContext context
    ) {
        return AdvanceResult.fallback(ticks);
    }
}
