package com.sci.torcherino.blocks.tiles;

import com.sci.torcherino.acceleration.TorchSnapshot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TileTorcherinoNbtTest {
    @BeforeAll
    static void registerLegacyTileIds() {
        GameRegistry.registerTileEntity(TileTorcherino.class, "torcherino_tile");
        GameRegistry.registerTileEntity(
            TileCompressedTorcherino.class,
            "compressed_torcherino_tile"
        );
        GameRegistry.registerTileEntity(
            TileDoubleCompressedTorcherino.class,
            "double_compressed_torcherino_tile"
        );
    }

    @Test
    void readsLegacyBaseTorchState() {
        assertLegacyState(new TileTorcherino(), 4);
    }

    @Test
    void readsLegacyCompressedTorchState() {
        assertLegacyState(new TileCompressedTorcherino(), 36);
    }

    @Test
    void readsLegacyDoubleCompressedTorchState() {
        assertLegacyState(new TileDoubleCompressedTorcherino(), 324);
    }

    @Test
    void poweredLegacyTorchPublishesInactiveSnapshot() {
        TileTorcherino tile = new TileTorcherino();
        NBTTagCompound tag = legacyTag(4, 4, true);

        tile.readFromNBT(tag);

        assertEquals(0, tile.createAccelerationSnapshot().getMultiplier());
    }

    private static void assertLegacyState(TileTorcherino tile, int multiplier) {
        tile.readFromNBT(legacyTag(4, 4, false));
        TorchSnapshot snapshot = tile.createAccelerationSnapshot();

        assertEquals(4, snapshot.getRange());
        assertEquals(multiplier, snapshot.getMultiplier());

        NBTTagCompound written = tile.writeToNBT(new NBTTagCompound());
        assertEquals(4, written.getByte("Speed"));
        assertEquals(4, written.getByte("Mode"));
        assertFalse(written.getBoolean("PoweredByRedstone"));
    }

    private static NBTTagCompound legacyTag(int speed, int mode, boolean powered) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Speed", (byte) speed);
        tag.setByte("Mode", (byte) mode);
        tag.setBoolean("PoweredByRedstone", powered);
        return tag;
    }
}
