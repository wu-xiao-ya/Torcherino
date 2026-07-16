package com.sci.torcherino.client;

import com.sci.torcherino.Torcherino;
import com.sci.torcherino.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;

public final class ClientBlockRenderer {
    private ClientBlockRenderer() {
    }

    public static void init() {
        register(ModBlocks.torcherino, "blocktorcherino");
        register(ModBlocks.compressedTorcherino, "blockcompressedtorcherino");
        register(ModBlocks.doubleCompressedTorcherino, "blockdoublecompressedtorcherino");
        register(ModBlocks.lanterino, "blocklanterino");
        register(ModBlocks.compressedLanterino, "blockcompressedlanterino");
        register(ModBlocks.doubleCompressedLanterino, "blockdoublecompressedlanterino");
    }

    private static void register(Block block, String path) {
        ModelLoader.setCustomModelResourceLocation(
            Item.getItemFromBlock(block),
            0,
            new ModelResourceLocation(new ResourceLocation(Torcherino.MOD_ID, path), "inventory")
        );
    }
}
