package com.sci.torcherino.acceleration;

import com.sci.torcherino.Torcherino;
import com.sci.torcherino.TorcherinoRegistry;
import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.blocks.tiles.TileTorcherino;
import com.sci.torcherino.diagnostics.TorcherinoDiagnostics;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final DiscoveryWheel discoveryWheel = new DiscoveryWheel();
    private final Random random = new Random();

    private Long2IntOpenHashMap coverage = new Long2IntOpenHashMap();
    private TargetExecutionContext[] traversalTargets =
        new TargetExecutionContext[0];
    private int[] traversalMultipliers = new int[0];
    private TargetExecutionContext[] discoveredTargets =
        new TargetExecutionContext[0];
    private int[] discoveredMultipliers = new int[0];
    private int discoveredTargetCount;
    private long revision;
    private long appliedPlanRevision;
    private long discoveryBatches;
    private long discoveryScans;
    private long diagnosticManagerTicks;
    private long diagnosticManagerNanos;
    private long diagnosticManagerMaxNanos;
    private long diagnosticTargetVisits;
    private long diagnosticRequestedVirtualTicks;
    private long diagnosticConsumedVirtualTicks;
    private long diagnosticFallbackTicks;
    private long diagnosticSkippedTicks;
    private long diagnosticRandomBlockTicks;
    private long diagnosticExactBatchCalls;
    private long diagnosticExactFastLoopCalls;
    private long diagnosticLegacyCalls;
    private long diagnosticBlacklistedCalls;
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
        if (traversalTargets.length == 0 && discoveredTargetCount == 0) {
            return;
        }
        long managerStart = System.nanoTime();
        ticking = true;
        try {
            AccelerationProfiler profiler = AccelerationProfiler.getInstance();
            boolean profiling = profiler.isEnabled();
            discoveryWheel.advance(
                traversalTargets.length,
                Torcherino.discoveryIntervalTicks,
                Torcherino.discoverySlices
            );
            if (discoveryWheel.hasScheduledBatch()) {
                discoverTargets(profiler, profiling);
                discoveryBatches++;
            }
            if (discoveryWheel.completedCycle()) {
                discoveryScans++;
            }
            executeDiscoveredTargets(profiler, profiling);
        } finally {
            ticking = false;
            while (!deferredChanges.isEmpty()) {
                deferredChanges.removeFirst().run();
            }
        }

        long elapsed = System.nanoTime() - managerStart;
        diagnosticManagerTicks++;
        diagnosticManagerNanos += elapsed;
        diagnosticManagerMaxNanos = Math.max(
            diagnosticManagerMaxNanos,
            elapsed
        );
        if (elapsed >= Torcherino.slowManagerMillis * 1_000_000L) {
            Torcherino.logger.warn(
                "Torcherino dimension {} used {} ms for {} covered positions",
                world.provider.getDimension(),
                elapsed / 1_000_000.0D,
                coverage.size()
            );
            TorcherinoDiagnostics.recordSlowManager(
                world.provider.getDimension(),
                elapsed,
                coverage.size(),
                discoveredTargetCount
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
        return discoveredTargetCount;
    }

    public long getDiscoveryBatches() {
        return discoveryBatches;
    }

    public long getDiscoveryScans() {
        return discoveryScans;
    }

    public int getTicksUntilDiscovery() {
        return discoveryWheel.getTicksUntilCycleComplete();
    }

    public int getDiscoveryCursor() {
        return discoveryWheel.getCursor();
    }

    public int getDiscoverySlices() {
        return discoveryWheel.getSlices();
    }

    AccelerationMetricsSnapshot drainDiagnosticSnapshot() {
        List<AccelerationRouteSnapshot> activeRoutes =
            buildDiagnosticRoutes();
        AccelerationMetricsSnapshot snapshot =
            new AccelerationMetricsSnapshot(
                world.provider.getDimension(),
                torches.size(),
                coverage.size(),
                discoveredTargetCount,
                revision,
                appliedPlanRevision,
                discoveryBatches,
                discoveryScans,
                diagnosticManagerTicks,
                diagnosticManagerNanos,
                diagnosticManagerMaxNanos,
                diagnosticTargetVisits,
                diagnosticRequestedVirtualTicks,
                diagnosticConsumedVirtualTicks,
                diagnosticFallbackTicks,
                diagnosticSkippedTicks,
                diagnosticRandomBlockTicks,
                diagnosticExactBatchCalls,
                diagnosticExactFastLoopCalls,
                diagnosticLegacyCalls,
                diagnosticBlacklistedCalls,
                activeRoutes
            );
        diagnosticManagerTicks = 0L;
        diagnosticManagerNanos = 0L;
        diagnosticManagerMaxNanos = 0L;
        diagnosticTargetVisits = 0L;
        diagnosticRequestedVirtualTicks = 0L;
        diagnosticConsumedVirtualTicks = 0L;
        diagnosticFallbackTicks = 0L;
        diagnosticSkippedTicks = 0L;
        diagnosticRandomBlockTicks = 0L;
        diagnosticExactBatchCalls = 0L;
        diagnosticExactFastLoopCalls = 0L;
        diagnosticLegacyCalls = 0L;
        diagnosticBlacklistedCalls = 0L;
        return snapshot;
    }

    private List<AccelerationRouteSnapshot> buildDiagnosticRoutes() {
        Map<String, MutableRouteSnapshot> grouped =
            new HashMap<String, MutableRouteSnapshot>();
        for (int index = 0; index < discoveredTargetCount; index++) {
            TargetExecutionContext context = discoveredTargets[index];
            int multiplier = discoveredMultipliers[index];
            boolean randomOnly = context.randomTick
                && context.diagnosticTileClass == null;
            String tileClass = randomOnly
                ? context.block.getClass().getName()
                : context.diagnosticTileClass == null
                    ? "unknown"
                    : context.diagnosticTileClass;
            String adapterId = randomOnly
                ? "random-block"
                : context.diagnosticAdapterId == null
                    ? "unresolved"
                    : context.diagnosticAdapterId;
            AdapterClassification classification =
                context.diagnosticClassification == null
                    ? AdapterClassification.LEGACY_FALLBACK
                    : context.diagnosticClassification;
            if (classification != AdapterClassification.BLACKLIST
                && multiplier < context.diagnosticMinimumBatchTicks) {
                classification = AdapterClassification.LEGACY_FALLBACK;
            }
            String key = tileClass
                + '\u0000'
                + adapterId
                + '\u0000'
                + classification.name();
            MutableRouteSnapshot value = grouped.get(key);
            if (value == null) {
                value = new MutableRouteSnapshot(
                    tileClass,
                    adapterId,
                    classification
                );
                grouped.put(key, value);
            }
            value.add(multiplier);
        }

        List<AccelerationRouteSnapshot> result =
            new ArrayList<AccelerationRouteSnapshot>(grouped.size());
        for (MutableRouteSnapshot value : grouped.values()) {
            result.add(value.snapshot());
        }
        Collections.sort(
            result,
            new Comparator<AccelerationRouteSnapshot>() {
                @Override
                public int compare(
                    AccelerationRouteSnapshot left,
                    AccelerationRouteSnapshot right
                ) {
                    int byClass = left.getTileClass().compareTo(
                        right.getTileClass()
                    );
                    if (byClass != 0) {
                        return byClass;
                    }
                    return left.getAdapterId().compareTo(
                        right.getAdapterId()
                    );
                }
            }
        );
        return result;
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
        discoveredTargetCount = 0;
    }

    private void discoverTargets(
        AccelerationProfiler profiler,
        boolean profiling
    ) {
        TargetExecutionContext[] coverageTargets = traversalTargets;
        int[] coverageMultipliers = traversalMultipliers;
        int end = discoveryWheel.getBatchEnd();
        for (int i = discoveryWheel.getBatchStart(); i < end; i++) {
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
                addOrUpdateDiscoveredTarget(context, multiplier);
            } else {
                removeDiscoveredTarget(context);
            }
        }
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

    private void executeDiscoveredTargets(
        AccelerationProfiler profiler,
        boolean profiling
    ) {
        TargetExecutionContext[] targets = discoveredTargets;
        int[] multipliers = discoveredMultipliers;
        int count = discoveredTargetCount;
        int index = 0;
        while (index < count) {
            TargetExecutionContext context = targets[index];
            int multiplier = multipliers[index];
            if (tickTarget(context, multiplier, profiler, profiling)) {
                index++;
            } else {
                removeDiscoveredTargetAt(index);
                count = discoveredTargetCount;
            }
        }
    }

    private void addOrUpdateDiscoveredTarget(
        TargetExecutionContext context,
        int multiplier
    ) {
        int index = context.discoveredIndex;
        if (index >= 0
            && index < discoveredTargetCount
            && discoveredTargets[index] == context) {
            discoveredMultipliers[index] = multiplier;
            return;
        }
        ensureDiscoveredCapacity(discoveredTargetCount + 1);
        context.discoveredIndex = discoveredTargetCount;
        discoveredTargets[discoveredTargetCount] = context;
        discoveredMultipliers[discoveredTargetCount] = multiplier;
        discoveredTargetCount++;
    }

    private void removeDiscoveredTarget(TargetExecutionContext context) {
        int index = context.discoveredIndex;
        if (index >= 0
            && index < discoveredTargetCount
            && discoveredTargets[index] == context) {
            removeDiscoveredTargetAt(index);
        } else {
            context.discoveredIndex = -1;
            context.clearRoute();
        }
    }

    private void removeDiscoveredTargetAt(int index) {
        int lastIndex = --discoveredTargetCount;
        TargetExecutionContext removed = discoveredTargets[index];
        if (index != lastIndex) {
            TargetExecutionContext moved = discoveredTargets[lastIndex];
            discoveredTargets[index] = moved;
            discoveredMultipliers[index] = discoveredMultipliers[lastIndex];
            moved.discoveredIndex = index;
        }
        discoveredTargets[lastIndex] = null;
        discoveredMultipliers[lastIndex] = 0;
        removed.discoveredIndex = -1;
        removed.clearRoute();
    }

    private void ensureDiscoveredCapacity(int required) {
        if (required <= discoveredTargets.length) {
            return;
        }
        int current = discoveredTargets.length;
        int capacity = Math.max(required, Math.max(4, current + (current >> 1) + 1));
        discoveredTargets = java.util.Arrays.copyOf(discoveredTargets, capacity);
        discoveredMultipliers =
            java.util.Arrays.copyOf(discoveredMultipliers, capacity);
    }

    private void reconcileDiscoveredTargets() {
        int index = 0;
        while (index < discoveredTargetCount) {
            TargetExecutionContext context = discoveredTargets[index];
            int multiplier = coverage.get(context.packedPos);
            if (multiplier <= 0) {
                removeDiscoveredTargetAt(index);
            } else {
                discoveredMultipliers[index] = multiplier;
                index++;
            }
        }
    }

    private boolean tickTarget(
        TargetExecutionContext cachedContext,
        int multiplier,
        AccelerationProfiler profiler,
        boolean profiling
    ) {
        diagnosticTargetVisits++;
        long packedPos = cachedContext.packedPos;
        BlockPos pos = cachedContext.pos;
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
                diagnosticRandomBlockTicks +=
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

            diagnosticRequestedVirtualTicks += multiplier;
            long adapterStart = profiling ? System.nanoTime() : 0L;
            AdapterRegistry registry = AdapterRegistry.getInstance();
            AdapterRegistry.PreparedRoute route =
                cachedContext.prepareRoute(registry, tile);
            if (route.usesLegacyBelow(multiplier)) {
                diagnosticLegacyCalls++;
                final TileEntity expectedTile = tile;
                int fallbackTicks = runFallbackTicks(
                    tile,
                    (ITickable) tile,
                    multiplier,
                    new BooleanSupplier() {
                        @Override
                        public boolean getAsBoolean() {
                            return world.isBlockLoaded(pos, false)
                                && world.getTileEntity(pos) == expectedTile;
                        }
                    }
                );
                if (profiling) {
                    profiler.record(
                        tile.getClass().getName(),
                        route.getAdapterId(),
                        AdapterClassification.LEGACY_FALLBACK,
                        multiplier,
                        fallbackTicks,
                        System.nanoTime() - adapterStart
                    );
                }
                diagnosticConsumedVirtualTicks += fallbackTicks;
                diagnosticFallbackTicks += fallbackTicks;
                diagnosticSkippedTicks +=
                    Math.max(0, multiplier - fallbackTicks);
                return !tile.isInvalid()
                    && world.isBlockLoaded(pos, false)
                    && world.getTileEntity(pos) == expectedTile;
            }
            AdapterDispatch dispatch = registry.dispatchPrepared(
                tile,
                multiplier,
                cachedContext.acceleration,
                route
            );
            if (dispatch.isBlacklisted()) {
                diagnosticBlacklistedCalls++;
                diagnosticSkippedTicks += multiplier;
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
            recordDiagnosticClassification(
                dispatch.getClassification()
            );
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
            int executedTicks =
                result.getConsumedTicks() + fallbackTicks;
            diagnosticConsumedVirtualTicks += executedTicks;
            diagnosticFallbackTicks += fallbackTicks;
            diagnosticSkippedTicks +=
                Math.max(0, multiplier - executedTicks);
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

    private int tickRandomBlock(
        BlockPos pos,
        IBlockState state,
        Block block,
        int multiplier
    ) {
        int executed = 0;
        for (; executed < multiplier; executed++) {
            if (!world.isBlockLoaded(pos, false) || world.getBlockState(pos) != state) {
                break;
            }
            block.updateTick(world, pos, state, random);
        }
        return executed;
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
        TorcherinoDiagnostics.recordSlowTarget(
            world.provider.getDimension(),
            packedPos,
            elapsed
        );
    }

    private void recordDiagnosticClassification(
        AdapterClassification classification
    ) {
        if (classification == AdapterClassification.EXACT_BATCH) {
            diagnosticExactBatchCalls++;
        } else if (
            classification == AdapterClassification.EXACT_FAST_LOOP
        ) {
            diagnosticExactFastLoopCalls++;
        } else if (
            classification == AdapterClassification.LEGACY_FALLBACK
        ) {
            diagnosticLegacyCalls++;
        } else if (
            classification == AdapterClassification.BLACKLIST
        ) {
            diagnosticBlacklistedCalls++;
        }
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
        discoveryWheel.force();
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
        discoveryWheel.force();
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

        reconcileDiscoveredTargets();
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
        private int discoveredIndex = -1;
        private TileEntity routeTile;
        private AdapterRegistry.PreparedRoute route;
        private String diagnosticTileClass;
        private String diagnosticAdapterId;
        private AdapterClassification diagnosticClassification;
        private int diagnosticMinimumBatchTicks = 1;

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
            if (previous != null
                && previous.isCurrent(registry.getGeneration())) {
                return previous;
            }
            AdapterRegistry.PreparedRoute prepared =
                registry.prepare(tile, previous);
            diagnosticTileClass = tile.getClass().getName();
            diagnosticAdapterId = prepared.getAdapterId();
            diagnosticClassification = prepared.getClassification();
            diagnosticMinimumBatchTicks =
                prepared.getMinimumBatchTicks();
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
            diagnosticTileClass = null;
            diagnosticAdapterId = null;
            diagnosticClassification = null;
            diagnosticMinimumBatchTicks = 1;
        }
    }

    private static final class MutableRouteSnapshot {
        private final String tileClass;
        private final String adapterId;
        private final AdapterClassification classification;
        private int targets;
        private long summedMultiplier;
        private int minimumMultiplier = Integer.MAX_VALUE;
        private int maximumMultiplier = Integer.MIN_VALUE;

        private MutableRouteSnapshot(
            String tileClass,
            String adapterId,
            AdapterClassification classification
        ) {
            this.tileClass = tileClass;
            this.adapterId = adapterId;
            this.classification = classification;
        }

        private void add(int multiplier) {
            targets++;
            summedMultiplier += multiplier;
            minimumMultiplier = Math.min(minimumMultiplier, multiplier);
            maximumMultiplier = Math.max(maximumMultiplier, multiplier);
        }

        private AccelerationRouteSnapshot snapshot() {
            return new AccelerationRouteSnapshot(
                tileClass,
                adapterId,
                classification,
                targets,
                summedMultiplier,
                minimumMultiplier,
                maximumMultiplier
            );
        }
    }
}
