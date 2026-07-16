package com.sci.torcherino.blocks;

import com.sci.torcherino.Torcherino;
import com.sci.torcherino.blocks.blocks.BlockCompressedLanterino;
import com.sci.torcherino.blocks.blocks.BlockCompressedTorcherino;
import com.sci.torcherino.blocks.blocks.BlockDoubleCompressedLanterino;
import com.sci.torcherino.blocks.blocks.BlockDoubleCompressedTorcherino;
import com.sci.torcherino.blocks.blocks.BlockLanterino;
import com.sci.torcherino.blocks.blocks.BlockTorcherino;
import com.sci.torcherino.blocks.tiles.TileCompressedTorcherino;
import com.sci.torcherino.blocks.tiles.TileDoubleCompressedTorcherino;
import com.sci.torcherino.blocks.tiles.TileTorcherino;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;

@GameRegistry.ObjectHolder(Torcherino.MOD_ID)
public final class ModBlocks{
    public static BlockTorcherino torcherino;
    public static BlockTorcherino compressedTorcherino;
    public static BlockTorcherino doubleCompressedTorcherino;
    public static BlockLanterino lanterino;
    public static BlockLanterino compressedLanterino;
    public static BlockLanterino doubleCompressedLanterino;
    public static void preInit()
    {
        torcherino = new BlockTorcherino();
        compressedTorcherino = new BlockCompressedTorcherino();
        doubleCompressedTorcherino = new BlockDoubleCompressedTorcherino();
        lanterino = new BlockLanterino();
        compressedLanterino = new BlockCompressedLanterino();
        doubleCompressedLanterino = new BlockDoubleCompressedLanterino();
        ForgeRegistries.BLOCKS.register(torcherino.setRegistryName("blocktorcherino"));
        ForgeRegistries.BLOCKS.register(compressedTorcherino.setRegistryName("blockcompressedtorcherino"));
        ForgeRegistries.BLOCKS.register(doubleCompressedTorcherino.setRegistryName("blockdoublecompressedtorcherino"));
        ForgeRegistries.BLOCKS.register(lanterino.setRegistryName("blocklanterino"));
        ForgeRegistries.BLOCKS.register(compressedLanterino.setRegistryName("blockcompressedlanterino"));
        ForgeRegistries.BLOCKS.register(doubleCompressedLanterino.setRegistryName("blockdoublecompressedlanterino"));
        ForgeRegistries.ITEMS.register(new ItemBlock(torcherino).setRegistryName(torcherino.getRegistryName()));
        ForgeRegistries.ITEMS.register(new ItemBlock(compressedTorcherino).setRegistryName(compressedTorcherino.getRegistryName()));
        ForgeRegistries.ITEMS.register(new ItemBlock(doubleCompressedTorcherino).setRegistryName(doubleCompressedTorcherino.getRegistryName()));
        ForgeRegistries.ITEMS.register(new ItemBlock(lanterino).setRegistryName(lanterino.getRegistryName()));
        ForgeRegistries.ITEMS.register(new ItemBlock(compressedLanterino).setRegistryName(compressedLanterino.getRegistryName()));
        ForgeRegistries.ITEMS.register(new ItemBlock(doubleCompressedLanterino).setRegistryName(doubleCompressedLanterino.getRegistryName()));
        // For 1.12 - 1.12.2 compat
        GameRegistry.registerTileEntity(TileTorcherino.class, "torcherino_tile");
        GameRegistry.registerTileEntity(TileCompressedTorcherino.class, "compressed_torcherino_tile");
        GameRegistry.registerTileEntity(TileDoubleCompressedTorcherino.class, "double_compressed_torcherino_tile");
        // Todo: enable in 1.13.x
        //GameRegistry.registerTileEntity(TileTorcherino.class, new ResourceLocation(Torcherino.MOD_ID, "torcherino_tile"));
        //GameRegistry.registerTileEntity(TileCompressedTorcherino.class, new ResourceLocation(Torcherino.MOD_ID, "compressed_torcherino_tile"));
        //GameRegistry.registerTileEntity(TileDoubleCompressedTorcherino.class, new ResourceLocation(Torcherino.MOD_ID, "double_compressed_torcherino_tile"));
    }
}
