package com.sci.torcherino.acceleration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class CoverageMailbox {
    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Torcherino-Coverage-Planner");
            thread.setDaemon(true);
            return thread;
        }
    };
    private static ExecutorService executor;

    private final AtomicReference<CoverageSnapshot> pending = new AtomicReference<CoverageSnapshot>();
    private final AtomicReference<CoveragePlan> completed = new AtomicReference<CoveragePlan>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    void submit(CoverageSnapshot snapshot) {
        if (closed.get()) {
            return;
        }
        pending.set(snapshot);
        schedule();
    }

    CoveragePlan poll() {
        if (closed.get()) {
            return null;
        }
        return completed.getAndSet(null);
    }

    void close() {
        closed.set(true);
        pending.set(null);
        completed.set(null);
    }

    private void schedule() {
        if (!closed.get() && scheduled.compareAndSet(false, true)) {
            executor().execute(new Runnable() {
                @Override
                public void run() {
                    drain();
                }
            });
        }
    }

    private void drain() {
        try {
            CoverageSnapshot snapshot;
            while (!closed.get() && (snapshot = pending.getAndSet(null)) != null) {
                CoveragePlan plan = CoveragePlanner.compute(snapshot);
                if (!closed.get()) {
                    completed.set(plan);
                }
            }
        } finally {
            scheduled.set(false);
            if (!closed.get() && pending.get() != null) {
                schedule();
            }
        }
    }

    static synchronized void shutdown() {
        if (executor != null) {
            ExecutorService stopping = executor;
            executor = null;
            stopping.shutdownNow();
            try {
                stopping.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static synchronized ExecutorService executor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(THREAD_FACTORY);
        }
        return executor;
    }
}
