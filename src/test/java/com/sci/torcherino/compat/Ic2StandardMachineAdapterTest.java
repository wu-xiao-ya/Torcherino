package com.sci.torcherino.compat;

import com.sci.torcherino.api.AdapterClassification;
import com.sci.torcherino.api.AdvanceResult;
import net.minecraft.tileentity.TileEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Ic2StandardMachineAdapterTest {
    @Test
    void noUpgradeStandardMachinesUseExactFastLoop() {
        Ic2StandardMachineAdapter adapter = new Ic2StandardMachineAdapter();

        assertEquals(
            AdapterClassification.EXACT_FAST_LOOP,
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

    @Test
    void advancesProcessingWithoutTickingUpgradeOrDischargeSlots()
        throws Exception {
        final List<Integer> events = new ArrayList<Integer>();
        Ic2StandardMachineAdapter.Access access =
            Ic2StandardMachineAdapter.Access.bind(
                FakeStandardMachine.class,
                new Ic2StandardMachineAdapter.EventEmitter() {
                    @Override
                    public void emit(TileEntity tile, int event) {
                        events.add(event);
                    }
                }
            );
        FakeStandardSubclass tile = new FakeStandardSubclass();

        AdvanceResult result = Ic2StandardMachineAdapter.advanceWithAccess(
            tile,
            4,
            access
        );

        assertEquals(4, result.getConsumedTicks());
        assertTrue(result.isSyncRequired());
        assertEquals(60.0D, tile.energy);
        assertEquals(1, tile.input);
        assertEquals(1, tile.output);
        assertEquals(1, tile.progress);
        assertEquals(0, tile.upgradeSlot.tickCalls);
        assertEquals(Arrays.asList(0, 2, 0), events);
    }

    @Test
    void upgradeItemsForceLegacyFallback() throws Exception {
        Ic2StandardMachineAdapter.Access access =
            Ic2StandardMachineAdapter.Access.bind(
                FakeStandardMachine.class,
                new Ic2StandardMachineAdapter.EventEmitter() {
                    @Override
                    public void emit(TileEntity tile, int event) {
                    }
                }
            );
        FakeStandardSubclass tile = new FakeStandardSubclass();
        tile.upgradeSlot.empty = false;

        AdvanceResult result = Ic2StandardMachineAdapter.advanceWithAccess(
            tile,
            36,
            access
        );

        assertEquals(0, result.getConsumedTicks());
        assertEquals(36, result.getFallbackTicks());
        assertEquals(100.0D, tile.energy);
        assertEquals(2, tile.input);
    }

    static class FakeStandardMachine extends TileEntity {
        protected short progress;
        public int energyConsume = 10;
        public int operationLength = 3;
        protected float guiProgress;
        public final FakeUpgradeSlot upgradeSlot = new FakeUpgradeSlot();
        double energy = 100.0D;
        int input = 2;
        int output;
        private boolean active;

        protected void updateEntityServer() {
        }

        protected Object getOutput() {
            return input > 0 ? new Object() : null;
        }

        private void operate(Object recipe) {
            input--;
            output++;
        }

        public boolean useEnergy(double amount) {
            if (energy < amount) {
                return false;
            }
            energy -= amount;
            return true;
        }

        public void setActive(boolean value) {
            active = value;
        }

        public boolean getActive() {
            return active;
        }
    }

    static final class FakeStandardSubclass extends FakeStandardMachine {
    }

    static final class FakeCustomMachine extends FakeStandardMachine {
        @Override
        protected void updateEntityServer() {
        }
    }

    static final class FakeUpgradeSlot {
        private boolean empty = true;
        private int tickCalls;

        public boolean isEmpty() {
            return empty;
        }

        public boolean tickNoMark() {
            tickCalls++;
            return false;
        }
    }
}
