package com.sci.torcherino.api;

public interface IBatchedAcceleratable {
    AdvanceResult advanceExact(int ticks, AccelerationContext context);
}
