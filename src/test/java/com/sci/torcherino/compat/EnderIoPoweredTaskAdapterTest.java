package com.sci.torcherino.compat;

import com.sci.torcherino.api.AdvanceResult;
import net.minecraft.tileentity.TileEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnderIoPoweredTaskAdapterTest {
    @Test
    void advancesTaskWithoutCallingWholeMachineUpdate() throws Exception {
        FakePoweredTaskTile tile = new FakePoweredTaskTile();
        EnderIoPoweredTaskAdapter.Access access =
            EnderIoPoweredTaskAdapter.Access.bind(FakePoweredTaskTile.class);

        AdvanceResult result =
            EnderIoPoweredTaskAdapter.advanceWithAccess(tile, 4, access);

        assertEquals(4, result.getConsumedTicks());
        assertEquals(0, result.getFallbackTicks());
        assertTrue(result.isSyncRequired());
        assertEquals(4, tile.taskProgress);
        assertEquals(4, tile.processingEnergyUsed);
        assertEquals(0, tile.passiveEnergyLoss);
    }

    @Test
    void respectsMachineRedstoneState() throws Exception {
        FakePoweredTaskTile tile = new FakePoweredTaskTile();
        tile.redstonePassed = false;
        EnderIoPoweredTaskAdapter.Access access =
            EnderIoPoweredTaskAdapter.Access.bind(FakePoweredTaskTile.class);

        EnderIoPoweredTaskAdapter.advanceWithAccess(tile, 4, access);

        assertEquals(0, tile.taskProgress);
        assertEquals(0, tile.processingEnergyUsed);
        assertEquals(0, tile.passiveEnergyLoss);
    }

    static final class FakePoweredTaskTile extends TileEntity {
        private boolean redstonePassed = true;
        private int taskProgress;
        private int processingEnergyUsed;
        private int passiveEnergyLoss;

        public boolean getRedstoneChecksPassed() {
            return redstonePassed;
        }

        protected void processTasks(boolean enabled) {
            if (enabled) {
                taskProgress++;
                processingEnergyUsed++;
            }
        }

        public void updateWholeMachine() {
            passiveEnergyLoss++;
            processTasks(redstonePassed);
        }
    }
}
