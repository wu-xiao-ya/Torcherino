package com.sci.torcherino.acceleration;

import com.sci.torcherino.Torcherino;
import com.sci.torcherino.TorcherinoRegistry;
import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.blocks.tiles.TileTorcherino;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.BlockFluidBase;

import java.util.ArrayDeque;
import java.util.Random;
import java.util.function.BooleanSupplier;

public final class WorldAccelerationManager {
    private final long managerId;
    private final WorldServer world;
    private final Long2ObjectOpenHashMap<TorchSnapshot> torches =
        new Long2ObjectOpenHashMap<TorchSnapshot>();
    private final LongOpenHashSet activeTargets = new LongOpenHashSet();
    private final Long2LongOpenHashMap targetWarnTimes = new Long2LongOpenHashMap();
    private final Long2ObjectOpenHashMap<TargetExecutionContext> targetContexts =
        new Long2ObjectOpenHashMap<TargetExecutionContext>();
    private final ArrayDeque<Runnable> deferredChanges = new ArrayDeque<Runnable>();
    private final CoverageMailbox mailbox = new CoverageMailbox();
    private final DiscoveryCadence discoveryCadence = new DiscoveryCadence();
    private final Random random = new Random();

    private Long2IntOpenHashMap coverage = new Long2IntOpenHashMap();
    private TargetExecutionContext[] traversalTargets =
        new TargetExecutionContext[0];
    private int[] traversalMultipliers = new int[0];
    private TargetExecutionContext[] discoveredTargets =
        new TargetExecutionContext[0];
    private int[] discoveredMultipliers = new int[0];
    private long revision;
    private long appliedPlanRevision;
    private long discoveryScans;
    private boolean ticking;

    WorldAccelerationManager(long managerId, WorldServer world) {
        this.managerId = managerId;
        this.world = world;
        coverage.defaultReturnValue(0);
        targetWarnTimes.defaultReturnValue(Long.MIN_VALUE);
    }

    public void upsert(TileTorcherino tile) {
        final long pos = tile.getPos().toLong();
        final TorchSnapshot snapshot = tile.createAccelerationSnapshot();
        mutate(new Runnable() {
            @Override
            public void run() {
                TorchSnapshot old = torches.get(pos);
                if (snapshot.equals(old)) {
                    return;
                }
                if (old != null) {
                    CoveragePlanner.apply(coverage, old, -1);
                }
                torches.put(pos, snapshot);
                CoveragePlanner.apply(coverage, snapshot, 1);
                changed();
            }
        });
    }

    public void remove(final long pos) {
        mutate(new Runnable() {
            @Override
            public void run() {
                TorchSnapshot old = torches.remove(pos);
                if (old == null) {
                    return;
                }
                CoveragePlanner.apply(coverage, old, -1);
                changed();
            }
        });
    }

    public void tick() {
        applyCompletedPlan();
        long managerStart = System.nanoTime();
        ticking = true;
        try {
            if (discoveryCadence.beginTick(Torcherino.discoveryIntervalTicks)) {
                discoverTargets();
            }
            executeDiscoveredTargets();
        } finally {
            ticking = false;
            while (!deferredChanges.isEmpty()) {
                deferredChanges.removeFirst().run();
            }
        }

        long elapsed = System.nanoTime() - managerStart;
        if (elapsed >= Torcherino.slowManagerMillis * 1_000_000L) {
            Torcherino.logger.warn(
                "Torcherino dimension {} used {} ms for {} covered positions",
                world.provider.getDimension(),
                elapsed / 1_000_000.0D,
                coverage.size()
            );
        }
    }

    public int getTorchCount() {
        return torches.size();
    }

    public int getCoveredPositionCount() {
        return coverage.size();
    }

    public long getRevision() {
        return revision;
    }

    public long getAppliedPlanRevision() {
        return appliedPlanRevision;
    }

    public int getDiscoveredTargetCount() {
        return discoveredTargets.length;
    }

    public long getDiscoveryScans() {
        return discoveryScans;
    }

    public int getTicksUntilDiscovery() {
        return discoveryCadence.getTicksUntilDiscovery();
    }

    void close() {
        mailbox.close();
        torches.clear();
        coverage.clear();
        deferredChanges.clear();
        activeTargets.clear();
        targetWarnTimes.clear();
        targetContexts.clear();
        traversalTargets = new TargetExecutionContext[0];
        traversalMultipliers = new int[0];
        discoveredTargets = new TargetExecutionContext[0];
        discoveredMultipliers = new int[0];
    }

    private void discoverTargets() {
        TargetExecutionContext[] coverageTargets = traversalTargets;
        int[] coverageMultipliers = traversalMultipliers;
        TargetExecutionContext[] foundTargets =
            new TargetExecutionContext[coverageTargets.length];
        int[] foundMultipliers = new int[coverageTargets.length];
        int found = 0;
        AccelerationProfiler profiler = AccelerationProfiler.getInstance();
        boolean profiling = profiler.isEnabled();
        for (int i = 0; i < coverageTargets.length; i++) {
            TargetExecutionContext context = coverageTargets[i];
            int multiplier = coverageMultipliers[i];
            long scanStart = profiling ? System.nanoTime() : 0L;
            if (isDiscoverableTarget(
                context,
                multiplier,
                profiler,
                profiling,
                scanStart
            )) {
                foundTargets[found] = context;
                foundMultipliers[found] = multiplier;
                found++;
            } else {
                context.clearRoute();
            }
        }
        discoveredTargets = found == foundTargets.length
            ? foundTargets
            : java.util.Arrays.copyOf(foundTargets, found);
        discoveredMultipliers = found == foundMultipliers.length
            ? foundMultipliers
            : java.util.Arrays.copyOf(foundMultipliers, found);
        discoveryScans++;
    }

    private boolean isDiscoverableTarget(
        TargetExecutionContext cachedContext,
        int multiplier,
        AccelerationProfiler profiler,
        boolean profiling,
        long scanStart
    ) {
        BlockPos pos = cachedContext.pos;
        if (!world.isBlockLoaded(pos, false)) {
            recordSkipped(
                profiler,
                profiling,
                "unloaded",
                "unknown",
                multiplier,
                scanStart
            );
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        cachedContext.updateBlockState(state);
        Block block = cachedContext.block;
        if (cachedContext.blockBlacklisted) {
            recordSkipped(
                profiler,
                profiling,
                "block-blacklisted",
                block.getClass().getName(),
                multiplier,
                scanStart
            );
            return false;
        }
        if (cachedContext.randomTick) {
            return true;
        }
        if (!cachedContext.hasTileEntity) {
            recordSkipped(
                profiler,
                profiling,
                "no-tile",
                block.getClass().getName(),
                multiplier,
                scanStart
            );
            return false;
        }
        TileEntity tile = world.getTileEntity(pos);
        return isExecutableTile(
            profiler,
            profiling,
            tile,
            block,
            multiplier,
            scanStart
        );
    }

    private void executeDiscoveredTargets() {
        TargetExecutionContext[] targets = discoveredTargets;
        int[] multipliers = discoveredMultipliers;
        int retained = 0;
        boolean removed = false;
        for (int i = 0; i < targets.length; i++) {
            TargetExecutionContext context = targets[i];
            int multiplier = multipliers[i];
            if (tickTarget(context, multiplier)) {
                if (removed) {
                    targets[retained] = context;
                    multipliers[retained] = multiplier;
                }
                retained++;
            } else {
                removed = true;
                context.clearRoute();
            }
        }
        if (removed) {
            discoveredTargets = java.util.Arrays.copyOf(targets, retained);
            discoveredMultipliers =
                java.util.Arrays.copyOf(multipliers, retained);
        }
    }

    private boolean tickTarget(
        TargetExecutionContext cachedContext,
        int multiplier
    ) {
        long packedPos = cachedContext.packedPos;
        BlockPos pos = cachedContext.pos;
        AccelerationProfiler profiler = AccelerationProfiler.getInstance();
        boolean profiling = profiler.isEnabled();
        long scanStart = profiling ? System.nanoTime() : 0L;
        if (!world.isBlockLoaded(pos, false)) {
            cachedContext.clearRoute();
            recordSkipped(
                profiler,
                profiling,
                "unloaded",
                "unknown",
                multiplier,
                scanStart
            );
            return false;
        }

        IBlockState state = world.getBlockState(pos);
        cachedContext.updateBlockState(state);
        Block block = cachedContext.block;
        if (cachedContext.blockBlacklisted) {
            cachedContext.clearRoute();
            recordSkipped(
                profiler,
                profiling,
                "block-blacklisted",
                block.getClass().getName(),
                multiplier,
                scanStart
            );
            return false;
        }

        boolean randomTick = cachedContext.randomTick;
        TileEntity tile = null;
        if (!randomTick) {
            if (!cachedContext.hasTileEntity) {
                cachedContext.clearRoute();
                recordSkipped(
                    profiler,
                    profiling,
                    "no-tile",
                    block.getClass().getName(),
                    multiplier,
                    scanStart
                );
                return false;
            }
            tile = world.getTileEntity(pos);
            if (!isExecutableTile(
                profiler,
                profiling,
                tile,
                block,
                multiplier,
                scanStart
            )) {
                cachedContext.clearRoute();
                return false;
            }
        }

        if (!activeTargets.add(packedPos)) {
            return true;
        }
        long targetStart = System.nanoTime();
        try {
            if (randomTick) {
                tickRandomBlock(pos, state, block, multiplier);
                state = world.getBlockState(pos);
                cachedContext.updateBlockState(state);
                block = cachedContext.block;
                if (!cachedContext.hasTileEntity) {
                    cachedContext.clearRoute();
                    recordSkipped(
                        profiler,
                        profiling,
                        "no-tile",
                        block.getClass().getName(),
                        multiplier,
                        scanStart
                    );
                    return cachedContext.randomTick;
                }
                tile = world.getTileEntity(pos);
                if (!isExecutableTile(
                    profiler,
                    profiling,
                    tile,
                    block,
                    multiplier,
                    scanStart
                )) {
                    cachedContext.clearRoute();
                    return cachedContext.randomTick;
                }
            }

            long adapterStart = profiling ? System.nanoTime() : 0L;
            AdapterRegistry registry = AdapterRegistry.getInstance();
            AdapterRegistry.PreparedRoute route =
                cachedContext.prepareRoute(registry, tile);
            AdapterDispatch dispatch = registry.dispatchPrepared(
                tile,
                multiplier,
                cachedContext.acceleration,
                route
            );
            if (dispatch.isBlacklisted()) {
                if (profiling) {
                    profiler.recordBlacklisted(
                        tile.getClass().getName(),
                        dispatch.getAdapterId(),
                        multiplier,
                        System.nanoTime() - adapterStart
                    );
                }
                return true;
            }

            AdvanceResult result = dispatch.getResult();
            int fallbackTicks = 0;
            int requestedFallbackTicks = result.getFallbackTicks();
            if (!result.isInvalidated() && requestedFallbackTicks > 0) {
                final TileEntity expectedTile = tile;
                fallbackTicks = runFallbackTicks(
                    tile,
                    (ITickable) tile,
                    requestedFallbackTicks,
                    new BooleanSupplier() {
                        @Override
                        public boolean getAsBoolean() {
                            return world.isBlockLoaded(pos, false)
                                && world.getTileEntity(pos) == expectedTile;
                        }
                    }
                );
            }
            if (result.isSyncRequired() && !tile.isInvalid()) {
                tile.markDirty();
            }
            if (profiling) {
                profiler.record(
                    tile.getClass().getName(),
                    dispatch.getAdapterId(),
                    dispatch.getClassification(),
                    multiplier,
                    fallbackTicks,
                    System.nanoTime() - adapterStart
                );
            }
            return !result.isInvalidated() && !tile.isInvalid();
        } finally {
            activeTargets.remove(packedPos);
            warnSlowTarget(packedPos, pos, System.nanoTime() - targetStart);
        }
    }

    private boolean isExecutableTile(
        AccelerationProfiler profiler,
        boolean profiling,
        TileEntity tile,
        Block block,
        int multiplier,
        long scanStart
    ) {
        if (tile == null) {
            recordSkipped(
                profiler,
                profiling,
                "missing-tile",
                block.getClass().getName(),
                multiplier,
                scanStart
            );
            return false;
        }
        if (tile.isInvalid()) {
            recordSkipped(
                profiler,
                profiling,
                "invalid-tile",
                tile.getClass().getName(),
                multiplier,
                scanStart
            );
            return false;
        }
        if (TorcherinoRegistry.isTileBlacklisted(tile.getClass())) {
            recordSkipped(
                profiler,
                profiling,
                "tile-blacklisted",
                tile.getClass().getName(),
                multiplier,
                scanStart
            );
            return false;
        }
        if (!(tile instanceof ITickable)) {
            recordSkipped(
                profiler,
                profiling,
                "not-tickable",
                tile.getClass().getName(),
                multiplier,
                scanStart
            );
            return false;
        }
        return true;
    }

    private void tickRandomBlock(BlockPos pos, IBlockState state, Block block, int multiplier) {
        for (int i = 0; i < multiplier; i++) {
            if (!world.isBlockLoaded(pos, false) || world.getBlockState(pos) != state) {
                break;
            }
            block.updateTick(world, pos, state, random);
        }
    }

    static int runFallbackTicks(
        TileEntity tile,
        ITickable tickable,
        int ticks,
        BooleanSupplier targetValid
    ) {
        int executed = 0;
        for (; executed < ticks; executed++) {
            if (tile.isInvalid() || !targetValid.getAsBoolean()) {
                break;
            }
            tickable.update();
        }
        return executed;
    }

    private void warnSlowTarget(long packedPos, BlockPos pos, long elapsed) {
        if (elapsed < Torcherino.slowTargetMillis * 1_000_000L) {
            return;
        }
        long now = world.getTotalWorldTime();
        long previous = targetWarnTimes.get(packedPos);
        long cooldownTicks = Torcherino.slowLogCooldownSeconds * 20L;
        if (previous != Long.MIN_VALUE && now - previous < cooldownTicks) {
            return;
        }
        targetWarnTimes.put(packedPos, now);
        Torcherino.logger.warn(
            "Slow Torcherino target in dimension {} at {} used {} ms",
            world.provider.getDimension(),
            pos,
            elapsed / 1_000_000.0D
        );
    }

    private static void recordSkipped(
        AccelerationProfiler profiler,
        boolean profiling,
        String reason,
        String targetClass,
        int skippedTicks,
        long startedAt
    ) {
        if (!profiling) {
            return;
        }
        profiler.recordSkipped(
            targetClass,
            reason,
            skippedTicks,
            System.nanoTime() - startedAt
        );
    }

    private void mutate(Runnable change) {
        if (ticking) {
            deferredChanges.addLast(change);
        } else {
            change.run();
        }
    }

    private void changed() {
        revision++;
        rebuildTraversal();
        discoveryCadence.force();
        CoverageSnapshot snapshot = new CoverageSnapshot(
            managerId,
            world.provider.getDimension(),
            revision,
            torches.values().toArray(new TorchSnapshot[torches.size()])
        );
        if (Torcherino.asyncPlanner) {
            mailbox.submit(snapshot);
        } else {
            applyPlan(CoveragePlanner.compute(snapshot));
        }
    }

    private void applyCompletedPlan() {
        CoveragePlan plan = mailbox.poll();
        if (plan != null) {
            applyPlan(plan);
        }
    }

    private void applyPlan(CoveragePlan plan) {
        if (!acceptsPlan(
            plan,
            managerId,
            world.provider.getDimension(),
            revision
        )) {
            return;
        }
        Long2IntOpenHashMap plannedCoverage = plan.getCoverage();
        plannedCoverage.defaultReturnValue(0);
        coverage = plannedCoverage;
        rebuildTraversal();
        discoveryCadence.force();
        appliedPlanRevision = plan.getRevision();
    }

    private void rebuildTraversal() {
        TargetExecutionContext[] targets =
            new TargetExecutionContext[coverage.size()];
        int[] multipliers = new int[coverage.size()];
        int index = 0;
        java.util.Iterator<Long2IntMap.Entry> iterator =
            Long2IntMaps.fastIterator(coverage);
        while (iterator.hasNext()) {
            Long2IntMap.Entry entry = iterator.next();
            int multiplier = entry.getIntValue();
            if (multiplier <= 0) {
                continue;
            }
            long packedPos = entry.getLongKey();
            TargetExecutionContext context = targetContexts.get(packedPos);
            if (context == null) {
                context = new TargetExecutionContext(world, packedPos);
                targetContexts.put(packedPos, context);
            }
            targets[index] = context;
            multipliers[index] = multiplier;
            index++;
        }
        if (index != targets.length) {
            targets = java.util.Arrays.copyOf(targets, index);
            multipliers = java.util.Arrays.copyOf(multipliers, index);
        }

        LongIterator cachedPositions = targetContexts.keySet().iterator();
        while (cachedPositions.hasNext()) {
            if (!coverage.containsKey(cachedPositions.nextLong())) {
                cachedPositions.remove();
            }
        }
        traversalTargets = targets;
        traversalMultipliers = multipliers;
    }

    static boolean acceptsPlan(
        CoveragePlan plan,
        long expectedManagerId,
        int expectedDimension,
        long expectedRevision
    ) {
        return plan != null
            && plan.getManagerId() == expectedManagerId
            && plan.getDimension() == expectedDimension
            && plan.getRevision() == expectedRevision;
    }

    private static final class TargetExecutionContext {
        private final long packedPos;
        private final BlockPos pos;
        private final AccelerationContext acceleration;
        private IBlockState blockState;
        private Block block;
        private long blockBlacklistRevision = Long.MIN_VALUE;
        private boolean blockBlacklisted;
        private boolean randomTick;
        private boolean hasTileEntity;
        private TileEntity routeTile;
        private AdapterRegistry.PreparedRoute route;

        private TargetExecutionContext(WorldServer world, long packedPos) {
            this.packedPos = packedPos;
            pos = BlockPos.fromLong(packedPos);
            acceleration = new AccelerationContext(world, pos);
        }

        private void updateBlockState(IBlockState state) {
            long blacklistRevision =
                TorcherinoRegistry.getBlockBlacklistRevision();
            if (blockState == state
                && blockBlacklistRevision == blacklistRevision) {
                return;
            }
            blockState = state;
            block = state.getBlock();
            blockBlacklistRevision = blacklistRevision;
            blockBlacklisted = block instanceof BlockFluidBase
                || TorcherinoRegistry.isBlockBlacklisted(block);
            randomTick = block.getTickRandomly();
            hasTileEntity = block.hasTileEntity(state);
        }

        private AdapterRegistry.PreparedRoute prepareRoute(
            AdapterRegistry registry,
            TileEntity tile
        ) {
            AdapterRegistry.PreparedRoute previous =
                routeTile == tile ? route : null;
            AdapterRegistry.PreparedRoute prepared =
                registry.prepare(tile, previous);
            if (prepared.isReusable()) {
                routeTile = tile;
                route = prepared;
            } else {
                routeTile = null;
                route = null;
            }
            return prepared;
        }

        private void clearRoute() {
            routeTile = null;
            route = null;
        }
    }
}
