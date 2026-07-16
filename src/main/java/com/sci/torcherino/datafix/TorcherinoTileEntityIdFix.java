package com.sci.torcherino.datafix;

import com.sci.torcherino.Torcherino;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.IFixableData;

public final class TorcherinoTileEntityIdFix implements IFixableData {
    private static final int FIX_VERSION = 1;
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final String TORCHERINO_PREFIX = Torcherino.MOD_ID + ":";

    @Override
    public int getFixVersion() {
        return FIX_VERSION;
    }

    @Override
    public NBTTagCompound fixTagCompound(NBTTagCompound tag) {
        String id = tag.getString("id");
        String path = legacyPath(id);
        if (path != null) {
            tag.setString("id", TORCHERINO_PREFIX + path);
        }
        return tag;
    }

    private static String legacyPath(String id) {
        String path = id.startsWith(MINECRAFT_PREFIX)
            ? id.substring(MINECRAFT_PREFIX.length())
            : id;
        if ("torcherino_tile".equals(path)
            || "compressed_torcherino_tile".equals(path)
            || "double_compressed_torcherino_tile".equals(path)) {
            return path;
        }
        return null;
    }
}
