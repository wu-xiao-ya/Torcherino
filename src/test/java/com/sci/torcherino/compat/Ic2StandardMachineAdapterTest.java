package com.sci.torcherino.compat;

import com.sci.torcherino.api.AdapterClassification;
import net.minecraft.tileentity.TileEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Ic2StandardMachineAdapterTest {
    @Test
    void auditedStandardMachinesRemainOnLegacyFallback() {
        Ic2StandardMachineAdapter adapter = new Ic2StandardMachineAdapter();

        assertEquals(
            AdapterClassification.LEGACY_FALLBACK,
            adapter.getClassification()
        );
    }

    @Test
    void recognizesOnlySubclassesThatInheritTheAuditedBaseUpdate() {
        assertTrue(Ic2StandardMachineAdapter.inheritsBaseServerUpdate(
            new FakeStandardSubclass(),
            FakeStandardMachine.class
        ));
        assertFalse(Ic2StandardMachineAdapter.inheritsBaseServerUpdate(
            new FakeCustomMachine(),
            FakeStandardMachine.class
        ));
    }

    static class FakeStandardMachine extends TileEntity {
        protected void updateEntityServer() {
        }
    }

    static final class FakeStandardSubclass extends FakeStandardMachine {
    }

    static final class FakeCustomMachine extends FakeStandardMachine {
        @Override
        protected void updateEntityServer() {
        }
    }
}
