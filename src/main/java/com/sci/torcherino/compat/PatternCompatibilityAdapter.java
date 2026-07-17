package com.sci.torcherino.compat;

import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdapterProbe;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.api.IExactAccelerationAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

final class PatternCompatibilityAdapter
    implements IExactAccelerationAdapter<TileEntity> {
    private final String id;
    private final String modId;
    private final int priority;
    private final AdapterClassification classification;
    private final String[] packagePrefixes;
    private final String[] classTokens;
    private final String[] probeClasses;

    PatternCompatibilityAdapter(
        String id,
        String modId,
        int priority,
        AdapterClassification classification,
        String[] packagePrefixes,
        String[] classTokens,
        String[] probeClasses
    ) {
        this.id = id;
        this.modId = modId;
        this.priority = priority;
        this.classification = classification;
        this.packagePrefixes = packagePrefixes;
        this.classTokens = classTokens;
        this.probeClasses = probeClasses;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public AdapterClassification getClassification() {
        return classification;
    }

    @Override
    public AdapterProbe probe() {
        ModContainer container = Loader.instance().getIndexedModList().get(modId);
        if (container == null) {
            return AdapterProbe.unavailable(modId + " is not loaded");
        }
        return AdapterProbe.available(
            modId + "@" + container.getVersion() + "/" + StructuralSignature.firstAvailable(probeClasses),
            "structural package audit; exact-only fallback"
        );
    }

    @Override
    public boolean supportsInstance(TileEntity tile) {
        String className = tile.getClass().getName();
        boolean packageMatch = false;
        for (String prefix : packagePrefixes) {
            if (className.startsWith(prefix)) {
                packageMatch = true;
                break;
            }
        }
        if (!packageMatch) {
            return false;
        }
        if (classTokens.length == 0) {
            return true;
        }
        String lowerName = className.toLowerCase();
        for (String token : classTokens) {
            if (lowerName.contains(token)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canCacheSupportForInstance() {
        return true;
    }

    @Override
    public AdvanceResult advanceExact(TileEntity tile, int ticks, AccelerationContext context) {
        if (classification == AdapterClassification.BLACKLIST) {
            return AdvanceResult.invalidated(0);
        }
        return AdvanceResult.fallback(ticks);
    }
}
