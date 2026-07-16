package com.sci.torcherino.acceleration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoverageMailboxTest {
    @AfterEach
    void stopPlanner() {
        CoverageMailbox.shutdown();
    }

    @Test
    void eventuallyPublishesNewestRevision() throws Exception {
        CoverageMailbox mailbox = new CoverageMailbox();
        mailbox.submit(snapshot(1));
        mailbox.submit(snapshot(2));

        CoveragePlan plan = awaitRevision(mailbox, 2);
        assertEquals(2, plan.getRevision());
        assertEquals(36, plan.getCoverage().get(CoveragePlanner.pack(0, 64, 0)));
    }

    @Test
    void closedMailboxDropsResultsAndFreshMailboxRestartsPlanner() throws Exception {
        CoverageMailbox closed = new CoverageMailbox();
        closed.submit(snapshot(1));
        closed.close();
        Thread.sleep(25L);
        assertNull(closed.poll());

        CoverageMailbox fresh = new CoverageMailbox();
        fresh.submit(snapshot(3));
        assertEquals(3, awaitRevision(fresh, 3).getRevision());
        fresh.close();
    }

    private static CoverageSnapshot snapshot(long revision) {
        return new CoverageSnapshot(
            7,
            0,
            revision,
            new TorchSnapshot[]{
                new TorchSnapshot(CoveragePlanner.pack(0, 64, 0), 1, (int) (revision * 18))
            }
        );
    }

    private static CoveragePlan awaitRevision(CoverageMailbox mailbox, long revision)
        throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        CoveragePlan plan;
        while (System.nanoTime() < deadline) {
            plan = mailbox.poll();
            if (plan != null && plan.getRevision() == revision) {
                return plan;
            }
            Thread.sleep(5L);
        }
        throw new AssertionError("Timed out waiting for coverage revision " + revision);
    }
}
