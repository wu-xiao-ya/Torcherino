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
        if (probesCompleted) {
            entry.probe();
        }
    }

    public synchronized void probeAll() {
        for (Entry<?> entry : entries) {
            entry.probe();
        }
        probesCompleted = true;
    }

    AdapterDispatch dispatch(TileEntity tile, int ticks, AccelerationContext context) {
        if (tile instanceof IBatchedAcceleratable) {
            AdvanceResult result = validate(
                ((IBatchedAcceleratable) tile).advanceExact(ticks, context),
                ticks
            );
            return new AdapterDispatch(
                "cooperative-api",
                AdapterClassification.EXACT_BATCH,
                result,
                false
            );
        }

        for (Entry<?> entry : entries) {
            if (!entry.enabled || !entry.adapter.supportsInstance(tile)) {
                continue;
            }
            if (entry.adapter.getClassification() == AdapterClassification.BLACKLIST) {
                return new AdapterDispatch(
                    entry.adapter.getId(),
                    AdapterClassification.BLACKLIST,
                    AdvanceResult.invalidated(0),
                    true
                );
            }
            try {
                AdvanceResult result = validate(entry.advance(tile, ticks, context), ticks);
                return new AdapterDispatch(
                    entry.adapter.getId(),
                    entry.adapter.getClassification(),
                    result,
                    false
                );
            } catch (AdapterExecutionException e) {
                if (e.getConsumedTicks() > 0) {
                    throw new IllegalStateException(
                        "Adapter " + entry.adapter.getId()
                            + " failed after consuming " + e.getConsumedTicks() + " ticks",
                        e
                    );
                }
                entry.disable("runtime access failure: " + e.getClass().getSimpleName());
                Torcherino.logger.warn("Disabling Torcherino adapter {} after access failure", entry.adapter.getId(), e);
                break;
            } catch (ReflectiveOperationException e) {
                entry.disable("runtime reflection failure: " + e.getClass().getSimpleName());
                Torcherino.logger.warn("Disabling Torcherino adapter {} after reflection failure", entry.adapter.getId(), e);
                break;
            } catch (LinkageError e) {
                entry.disable("runtime linkage failure: " + e.getClass().getSimpleName());
                Torcherino.logger.warn("Disabling Torcherino adapter {} after linkage failure", entry.adapter.getId(), e);
                break;
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Torcherino adapter " + entry.adapter.getId() + " failed",
                    e
                );
            }
        }

        return new AdapterDispatch(
            "legacy-update",
            AdapterClassification.LEGACY_FALLBACK,
            AdvanceResult.fallback(ticks),
            false
        );
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
}
