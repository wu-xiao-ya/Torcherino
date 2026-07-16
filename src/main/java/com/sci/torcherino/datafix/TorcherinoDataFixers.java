package com.sci.torcherino.datafix;

import com.sci.torcherino.Torcherino;
import net.minecraft.util.datafix.FixTypes;
import net.minecraftforge.common.util.ModFixs;
import net.minecraftforge.fml.common.FMLCommonHandler;

public final class TorcherinoDataFixers {
    private static final int DATA_VERSION = 1;

    private TorcherinoDataFixers() {
    }

    public static void register() {
        ModFixs fixes = FMLCommonHandler.instance()
            .getDataFixer()
            .init(Torcherino.MOD_ID, DATA_VERSION);
        fixes.registerFix(FixTypes.BLOCK_ENTITY, new TorcherinoTileEntityIdFix());
    }
}
