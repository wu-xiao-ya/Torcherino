package com.sci.torcherino.datafix;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TorcherinoTileEntityIdFixTest {
    private final TorcherinoTileEntityIdFix fixer = new TorcherinoTileEntityIdFix();

    @Test
    void migratesLegacyMinecraftNamespaceIds() {
        assertMigrated("minecraft:torcherino_tile", "torcherino:torcherino_tile");
        assertMigrated(
            "minecraft:compressed_torcherino_tile",
            "torcherino:compressed_torcherino_tile"
        );
        assertMigrated(
            "minecraft:double_compressed_torcherino_tile",
            "torcherino:double_compressed_torcherino_tile"
        );
    }

    @Test
    void migratesBareLegacyIds() {
        assertMigrated("torcherino_tile", "torcherino:torcherino_tile");
    }

    @Test
    void leavesCurrentAndForeignIdsUnchanged() {
        assertMigrated("torcherino:torcherino_tile", "torcherino:torcherino_tile");
        assertMigrated("minecraft:furnace", "minecraft:furnace");
    }

    private void assertMigrated(String original, String expected) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", original);
        tag.setByte("Speed", (byte) 4);

        NBTTagCompound fixed = fixer.fixTagCompound(tag);

        assertEquals(expected, fixed.getString("id"));
        assertEquals(4, fixed.getByte("Speed"));
    }
}
