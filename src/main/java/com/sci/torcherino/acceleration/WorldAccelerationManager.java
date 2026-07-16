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
    private final ArrayDeque<Runnable> deferredChanges = new ArrayDeque<Runnable>();
    private final CoverageMailbox mailbox = new CoverageMailbox();
    private final Random random = new Random();

    private Long2IntOpenHashMap coverage = new Long2IntOpenHashMap();
    private long revision;
    private long appliedPlanRevision;
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
            java.util.Iterator<Long2IntMap.Entry> iterator =
                Long2IntMaps.fastIterator(coverage);
            while (iterator.hasNext()) {
                Long2IntMap.Entry entry = iterator.next();
                int multiplier = entry.getIntValue();
                if (multiplier > 0) {
                    tickTarget(entry.getLongKey(), multiplier);
                }
            }
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

    void close() {
        mailbox.close();
        torches.clear();
        coverage.clear();
        deferredChanges.clear();
        activeTargets.clear();
        targetWarnTimes.clear();
    }

    private void tickTarget(long packedPos, int multiplier) {
        if (!activeTargets.add(packedPos)) {
            return;
        }
        long targetStart = System.nanoTime();
        BlockPos pos = BlockPos.fromLong(packedPos);
        try {
            if (!world.isBlockLoaded(pos, false)) {
                return;
            }

            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof BlockFluidBase || TorcherinoRegistry.isBlockBlacklisted(block)) {
                return;
            }

            if (block.getTickRandomly()) {
                tickRandomBlock(pos, state, block, multiplier);
            }

            IBlockState tileState = world.getBlockState(pos);
            if (!tileState.getBlock().hasTileEntity(tileState)) {
                return;
            }
            TileEntity tile = world.getTileEntity(pos);
            if (tile == null || tile.isInvalid() || TorcherinoRegistry.isTileBlacklisted(tile.getClass())) {
                return;
            }
            if (!(tile instanceof ITickable)) {
                return;
            }

            AccelerationContext context = new AccelerationContext(world, pos);
            long adapterStart = System.nanoTime();
            AdapterDispatch dispatch = AdapterRegistry.getInstance().dispatch(tile, multiplier, context);
            if (dispatch.isBlacklisted()) {
                return;
            }

            AdvanceResult result = dispatch.getResult();
            int fallbackTicks = 0;
            if (!result.isInvalidated()) {
                final TileEntity expectedTile = tile;
                fallbackTicks = runFallbackTicks(
                    tile,
                    (ITickable) tile,
                    result.getFallbackTicks(),
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
            AccelerationProfiler.getInstance().record(
                tile.getClass().getName(),
                dispatch.getAdapterId(),
                dispatch.getClassification(),
                multiplier,
                fallbackTicks,
                System.nanoTime() - adapterStart
            );
        } finally {
            activeTargets.remove(packedPos);
            warnSlowTarget(packedPos, pos, System.nanoTime() - targetStart);
        }
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

    private void mutate(Runnable change) {
        if (ticking) {
            deferredChanges.addLast(change);
        } else {
            change.run();
        }
    }

    private void changed() {
        revision++;
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
        appliedPlanRevision = plan.getRevision();
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
}
