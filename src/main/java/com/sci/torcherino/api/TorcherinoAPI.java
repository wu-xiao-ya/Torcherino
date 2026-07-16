package com.sci.torcherino.api;

import com.sci.torcherino.acceleration.AdapterRegistry;
import net.minecraft.tileentity.TileEntity;

public final class TorcherinoAPI {
    private TorcherinoAPI() {
    }

    public static <T extends TileEntity> void registerAdapter(
        IExactAccelerationAdapter<T> adapter
    ) {
        AdapterRegistry.getInstance().register(adapter);
    }
}
