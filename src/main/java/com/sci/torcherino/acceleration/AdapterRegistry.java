package com.sci.torcherino.acceleration;

import com.sci.torcherino.Torcherino;
import com.sci.torcherino.api.AccelerationContext;
import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdapterExecutionException;
import com.sci.torcherino.api.AdapterProbe;
import com.sci.torcherino.api.AdvanceResult;
import com.sci.torcherino.api.IBatchedAcceleratable;
import com.sci.torcherino.api.IExactAccelerationAdapter;
import com.sci.torcherino.compat.CompatibilityCatalog;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AdapterRegistry {
    private static final AdapterRegistry INSTANCE = new AdapterRegistry();

    private final CopyOnWriteArrayList<Entry<?>> entries =
        new CopyOnWriteArrayList<Entry<?>>();
    private volatile boolean builtInsRegistered;
    private volatile boolean probesCompleted;
    private volatile long generation;

    private AdapterRegistry() {
    }

    public static AdapterRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized void registerBuiltIns() {
        if (builtInsRegistered) {
            return;
        }
        builtInsRegistered = true;
        CompatibilityCatalog.register(this);
    }

    public synchronized <T extends TileEntity> void register(
        IExactAccelerationAdapter<T> adapter
    ) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter");
        }
        for (Entry<?> entry : entries) {
            if (entry.adapter.getId().equals(adapter.getId())) {
                throw new IllegalArgumentException("Duplicate adapter id: " + adapter.getId());
            }
        }
        Entry<T> entry = new Entry<T>(adapter);
        entries.add(entry);
        sortEntries();
        generation++;
        if (probesCompleted) {
            entry.probe();
            generation++;
        }
    }

    public synchronized void probeAll() {
        for (Entry<?> entry : entries) {
            entry.probe();
        }
        probesCompleted = true;
        generation++;
    }

    AdapterDispatch dispatch(TileEntity tile, int ticks, AccelerationContext context) {
        return dispatchPrepared(tile, ticks, context, prepare(tile, null));
    }

    AdapterDispatch dispatchPrepared(
        TileEntity tile,
        int ticks,
        AccelerationContext context,
        PreparedRoute route
    ) {
        if (route.cooperative) {
            AdvanceResult result = validate(
                ((IBatchedAcceleratable) tile).advanceExact(ticks, context),
                ticks
            );
            return route.dispatch(result);
        }

        Entry<?> entry = route.entry;
        if (entry != null) {
            if (entry.adapter.getClassification() == AdapterClassification.BLACKLIST) {
                return route.dispatch(AdvanceResult.invalidated(0));
            }
            try {
                AdvanceResult result = validate(entry.advance(tile, ticks, context), ticks);
                return route.dispatch(result);
            } catch (AdapterExecutionException e) {
                if (e.getConsumedTicks() > 0) {
                    throw new IllegalStateException(
                        "Adapter " + entry.adapter.getId()
                            + " failed after consuming " + e.getConsumedTicks() + " ticks",
                        e
                    );
                }
                disable(
                    entry,
                    "runtime access failure: " + e.getClass().getSimpleName()
                );
                Torcherino.logger.warn("Disabling Torcherino adapter {} after access failure", entry.adapter.getId(), e);
            } catch (ReflectiveOperationException e) {
                disable(
                    entry,
                    "runtime reflection failure: " + e.getClass().getSimpleName()
                );
                Torcherino.logger.warn("Disabling Torcherino adapter {} after reflection failure", entry.adapter.getId(), e);
            } catch (LinkageError e) {
                disable(
                    entry,
                    "runtime linkage failure: " + e.getClass().getSimpleName()
                );
                Torcherino.logger.warn("Disabling Torcherino adapter {} after linkage failure", entry.adapter.getId(), e);
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Torcherino adapter " + entry.adapter.getId() + " failed",
                    e
                );
            }
        }

        return PreparedRoute.legacy(generation, false).dispatch(
            AdvanceResult.fallback(ticks)
        );
    }

    PreparedRoute prepare(TileEntity tile, PreparedRoute previous) {
        long currentGeneration = generation;
        if (previous != null
            && previous.reusable
            && previous.generation == currentGeneration
            && (previous.entry == null || previous.entry.enabled)) {
            return previous;
        }
        if (tile instanceof IBatchedAcceleratable) {
            return PreparedRoute.cooperative(currentGeneration);
        }

        boolean noMatchReusable = true;
        for (Entry<?> entry : entries) {
            if (!entry.enabled) {
                continue;
            }
            boolean stable = entry.adapter.canCacheSupportForInstance();
            if (!stable) {
                noMatchReusable = false;
            }
            if (entry.adapter.supportsInstance(tile)) {
                return PreparedRoute.adapter(
                    currentGeneration,
                    entry,
                    stable
                );
            }
        }
        return PreparedRoute.legacy(currentGeneration, noMatchReusable);
    }

    public List<AdapterReport> getReports() {
        List<AdapterReport> reports = new ArrayList<AdapterReport>();
        for (Entry<?> entry : entries) {
            reports.add(new AdapterReport(
                entry.adapter.getId(),
                entry.adapter.getClassification(),
                entry.enabled,
                entry.signature,
                entry.detail
            ));
        }
        return Collections.unmodifiableList(reports);
    }

    private void sortEntries() {
        List<Entry<?>> sorted = new ArrayList<Entry<?>>(entries);
        Collections.sort(sorted, new Comparator<Entry<?>>() {
            @Override
            public int compare(Entry<?> left, Entry<?> right) {
                return Integer.compare(right.adapter.getPriority(), left.adapter.getPriority());
            }
        });
        entries.clear();
        entries.addAll(sorted);
    }

    private void disable(Entry<?> entry, String reason) {
        entry.disable(reason);
        generation++;
    }

    private static AdvanceResult validate(AdvanceResult result, int requestedTicks) {
        if (result == null) {
            return AdvanceResult.fallback(requestedTicks);
        }
        long represented = (long) result.getConsumedTicks() + result.getFallbackTicks();
        if (!result.isInvalidated() && represented != requestedTicks) {
            throw new IllegalStateException(
                "Adapter represented " + represented + " ticks, expected " + requestedTicks
            );
        }
        if (represented > requestedTicks) {
            throw new IllegalStateException("Adapter returned too many ticks");
        }
        return result;
    }

    private static final class Entry<T extends TileEntity> {
        private final IExactAccelerationAdapter<T> adapter;
        private volatile boolean enabled;
        private volatile String signature = "not-probed";
        private volatile String detail = "not probed";

        private Entry(IExactAccelerationAdapter<T> adapter) {
            this.adapter = adapter;
        }

        @SuppressWarnings("unchecked")
        private AdvanceResult advance(
            TileEntity tile,
            int ticks,
            AccelerationContext context
        ) throws Exception {
            return adapter.advanceExact((T) tile, ticks, context);
        }

        private void probe() {
            try {
                AdapterProbe probe = adapter.probe();
                enabled = probe.isAvailable();
                signature = probe.getVersionSignature();
                detail = probe.getDetail();
            } catch (LinkageError e) {
                disable("probe linkage failure: " + e.getClass().getSimpleName());
            } catch (RuntimeException e) {
                disable("probe failure: " + e.getClass().getSimpleName());
            }
        }

        private void disable(String reason) {
            enabled = false;
            detail = reason;
        }
    }

    static final class PreparedRoute {
        private final long generation;
        private final Entry<?> entry;
        private final boolean cooperative;
        private final boolean reusable;
        private final String adapterId;
        private final AdapterClassification classification;
        private final boolean blacklisted;
        private AdvanceResult cachedResult;
        private AdapterDispatch cachedDispatch;

        private PreparedRoute(
            long generation,
            Entry<?> entry,
            boolean cooperative,
            boolean reusable,
            String adapterId,
            AdapterClassification classification,
            boolean blacklisted
        ) {
            this.generation = generation;
            this.entry = entry;
            this.cooperative = cooperative;
            this.reusable = reusable;
            this.adapterId = adapterId;
            this.classification = classification;
            this.blacklisted = blacklisted;
        }

        private static PreparedRoute cooperative(long generation) {
            return new PreparedRoute(
                generation,
                null,
                true,
                true,
                "cooperative-api",
                AdapterClassification.EXACT_BATCH,
                false
            );
        }

        private static PreparedRoute adapter(
            long generation,
            Entry<?> entry,
            boolean reusable
        ) {
            AdapterClassification classification =
                entry.adapter.getClassification();
            return new PreparedRoute(
                generation,
                entry,
                false,
                reusable,
                entry.adapter.getId(),
                classification,
                classification == AdapterClassification.BLACKLIST
            );
        }

        private static PreparedRoute legacy(long generation, boolean reusable) {
            return new PreparedRoute(
                generation,
                null,
                false,
                reusable,
                "legacy-update",
                AdapterClassification.LEGACY_FALLBACK,
                false
            );
        }

        boolean isReusable() {
            return reusable;
        }

        private AdapterDispatch dispatch(AdvanceResult result) {
            if (cachedResult == result && cachedDispatch != null) {
                return cachedDispatch;
            }
            AdapterDispatch created = new AdapterDispatch(
                adapterId,
                classification,
                result,
                blacklisted
            );
            cachedResult = result;
            cachedDispatch = created;
            return created;
        }
    }
}
