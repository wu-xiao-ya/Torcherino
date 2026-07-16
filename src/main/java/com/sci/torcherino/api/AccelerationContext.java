package com.sci.torcherino.api;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

public final class AccelerationContext {
    private final WorldServer world;
    private final BlockPos pos;

    public AccelerationContext(WorldServer world, BlockPos pos) {
        this.world = world;
        this.pos = pos.toImmutable();
    }

    public WorldServer getWorld() {
        return world;
    }

    public BlockPos getPos() {
        return pos;
    }
}
