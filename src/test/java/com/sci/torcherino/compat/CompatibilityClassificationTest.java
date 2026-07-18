package com.sci.torcherino.compat;

import com.sci.torcherino.api.AdapterClassification;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompatibilityClassificationTest {
    @Test
    void mekanismGuardPreemptsGenericFallback() {
        MekanismRestrictedTickGuardAdapter adapter =
            new MekanismRestrictedTickGuardAdapter();

        assertEquals(AdapterClassification.BLACKLIST, adapter.getClassification());
        assertTrue(adapter.getPriority() > 1000);
    }

    @Test
    void actuallyAdditionsProcessingUsesExactBatch() {
        ActuallyAdditionsProcessingAuditAdapter adapter =
            new ActuallyAdditionsProcessingAuditAdapter();

        assertEquals(
            AdapterClassification.EXACT_BATCH,
            adapter.getClassification()
        );
        assertEquals(5, adapter.getMinimumBatchTicks());
    }

    @Test
    void ic2AuditedProcessingUsesExactBatch() {
        Ic2StandardMachineAdapter adapter = new Ic2StandardMachineAdapter();

        assertEquals(
            AdapterClassification.EXACT_BATCH,
            adapter.getClassification()
        );
    }
}
