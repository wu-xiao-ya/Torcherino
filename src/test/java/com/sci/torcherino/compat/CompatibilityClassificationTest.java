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
    void actuallyAdditionsAuditRemainsExactLegacyFallback() {
        ActuallyAdditionsProcessingAuditAdapter adapter =
            new ActuallyAdditionsProcessingAuditAdapter();

        assertEquals(
            AdapterClassification.LEGACY_FALLBACK,
            adapter.getClassification()
        );
    }

    @Test
    void ic2AuditRemainsLegacyFallbackUntilUpgradeTicksCanBeIsolated() {
        Ic2StandardMachineAdapter adapter = new Ic2StandardMachineAdapter();

        assertEquals(
            AdapterClassification.LEGACY_FALLBACK,
            adapter.getClassification()
        );
    }
}
