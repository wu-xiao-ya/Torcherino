package com.sci.torcherino.api;

import net.minecraft.tileentity.TileEntity;

public interface IExactAccelerationAdapter<T extends TileEntity> {
    String getId();

    int getPriority();

    AdapterClassification getClassification();

    AdapterProbe probe();

    boolean supportsInstance(TileEntity tile);

    AdvanceResult advanceExact(T tile, int ticks, AccelerationContext context) throws Exception;
}
