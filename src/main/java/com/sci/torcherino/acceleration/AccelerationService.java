package com.sci.torcherino.acceleration;

import com.sci.torcherino.blocks.tiles.TileTorcherino;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AccelerationService {
    private static final Map<WorldServer, WorldAccelerationManager> MANAGERS =
        new WeakHashMap<WorldServer, WorldAccelerationManager>();
    private static final AtomicLong NEXT_MANAGER_ID = new AtomicLong();

    private AccelerationService() {
    }

    public static void upsert(TileTorcherino tile) {
        World world = tile.getWorld();
        if (!(world instanceof WorldServer)) {
            return;
        }
        get((WorldServer) world).upsert(tile);
    }

    public static void remove(TileTorcherino tile) {
        World world = tile.getWorld();
        if (!(world instanceof WorldServer)) {
            return;
        }
        WorldAccelerationManager manager = MANAGERS.get((WorldServer) world);
        if (manager != null) {
            manager.remove(tile.getPos().toLong());
        }
    }

    public static void tick(WorldServer world) {
        get(world).tick();
    }

    public static void unload(WorldServer world) {
        WorldAccelerationManager manager = MANAGERS.remove(world);
        if (manager != null) {
            manager.close();
        }
    }

    public static List<String> describePlans() {
        List<String> lines = new ArrayList<String>();
        for (Map.Entry<WorldServer, WorldAccelerationManager> entry : MANAGERS.entrySet()) {
            WorldAccelerationManager manager = entry.getValue();
            lines.add(
                "dim=" + entry.getKey().provider.getDimension()
                    + " torches=" + manager.getTorchCount()
                    + " positions=" + manager.getCoveredPositionCount()
                    + " targets=" + manager.getDiscoveredTargetCount()
                    + " discoveryIn=" + manager.getTicksUntilDiscovery()
                    + " discoveryCursor=" + manager.getDiscoveryCursor()
                    + "/" + manager.getCoveredPositionCount()
                    + " discoverySlices=" + manager.getDiscoverySlices()
                    + " discoveryBatches=" + manager.getDiscoveryBatches()
                    + " discoveryScans=" + manager.getDiscoveryScans()
                    + " revision=" + manager.getRevision()
                    + " applied=" + manager.getAppliedPlanRevision()
            );
        }
        Collections.sort(lines);
        return lines;
    }

    public static void shutdown() {
        for (WorldAccelerationManager manager : MANAGERS.values()) {
            manager.close();
        }
        MANAGERS.clear();
        CoverageMailbox.shutdown();
    }

    private static WorldAccelerationManager get(WorldServer world) {
        WorldAccelerationManager manager = MANAGERS.get(world);
        if (manager == null) {
            manager = new WorldAccelerationManager(NEXT_MANAGER_ID.incrementAndGet(), world);
            MANAGERS.put(world, manager);
        }
        return manager;
    }
}
