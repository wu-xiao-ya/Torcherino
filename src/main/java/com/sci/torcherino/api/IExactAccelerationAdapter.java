package com.sci.torcherino.api;

import net.minecraft.tileentity.TileEntity;

public interface IExactAccelerationAdapter<T extends TileEntity> {
    String getId();

    int getPriority();

    AdapterClassification getClassification();

    AdapterProbe probe();

    boolean supportsInstance(TileEntity tile);

    default boolean canCacheSupportForInstance() {
        return false;
    }

    default int getMinimumBatchTicks() {
        return 1;
    }

    AdvanceResult advanceExact(T tile, int ticks, AccelerationContext context) throws Exception;
}
