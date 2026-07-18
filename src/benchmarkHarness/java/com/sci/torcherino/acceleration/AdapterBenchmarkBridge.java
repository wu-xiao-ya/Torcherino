package com.sci.torcherino.acceleration;

import com.sci.torcherino.api.AccelerationContext;
import net.minecraft.tileentity.TileEntity;

public final class AdapterBenchmarkBridge {
    private AdapterBenchmarkBridge() {
    }

    public static Object prepare(TileEntity tile, Object previous) {
        AdapterRegistry registry = AdapterRegistry.getInstance();
        AdapterRegistry.PreparedRoute route =
            (AdapterRegistry.PreparedRoute) previous;
        return route != null && route.isCurrent(registry.getGeneration())
            ? route
            : registry.prepare(tile, route);
    }

    public static boolean usesLegacyBelow(Object route, int ticks) {
        return ((AdapterRegistry.PreparedRoute) route)
            .usesLegacyBelow(ticks);
    }

    public static Object dispatch(
        TileEntity tile,
        int ticks,
        AccelerationContext context,
        Object route
    ) {
        return AdapterRegistry.getInstance().dispatchPrepared(
            tile,
            ticks,
            context,
            (AdapterRegistry.PreparedRoute) route
        );
    }
}
