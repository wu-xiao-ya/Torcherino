package com.sci.torcherino.acceleration;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FallbackTickRunnerTest {
    @Test
    void stopsWhenTileInvalidatesItself() {
        CountingTile tile = new CountingTile(3);

        int executed = WorldAccelerationManager.runFallbackTicks(
            tile,
            tile,
            324,
            alwaysValid()
        );

        assertEquals(3, executed);
        assertEquals(3, tile.updates);
    }

    @Test
    void stopsWhenTargetIdentityChanges() {
        final CountingTile tile = new CountingTile(Integer.MAX_VALUE);

        int executed = WorldAccelerationManager.runFallbackTicks(
            tile,
            tile,
            36,
            new BooleanSupplier() {
                @Override
                public boolean getAsBoolean() {
                    return tile.updates < 4;
                }
            }
        );

        assertEquals(4, executed);
        assertEquals(4, tile.updates);
    }

    private static BooleanSupplier alwaysValid() {
        return new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return true;
            }
        };
    }

    private static final class CountingTile extends TileEntity implements ITickable {
        private final int invalidateAfter;
        private int updates;

        private CountingTile(int invalidateAfter) {
            this.invalidateAfter = invalidateAfter;
        }

        @Override
        public void update() {
            updates++;
            if (updates >= invalidateAfter) {
                invalidate();
            }
        }
    }
}
