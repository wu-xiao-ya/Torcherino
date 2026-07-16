package com.sci.torcherino;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TorcherinoRegistry {
    private static final Set<Block> BLACKLISTED_BLOCKS = new HashSet<Block>();
    private static final Set<Class<? extends TileEntity>> BLACKLISTED_TILES =
        new HashSet<Class<? extends TileEntity>>();
    private static final ConcurrentHashMap<Class<?>, Boolean> TILE_MATCH_CACHE =
        new ConcurrentHashMap<Class<?>, Boolean>();

    private TorcherinoRegistry() {
    }

    public static void blacklistString(String value) {
        if (value == null) {
            return;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return;
        }
        if (value.indexOf(':') == -1) {
            blacklistTileClass(value);
            return;
        }

        String[] parts = value.split(":", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            Torcherino.logger.info("Ignoring malformed Torcherino blacklist entry: {}", value);
            return;
        }

        ResourceLocation id;
        try {
            id = new ResourceLocation(parts[0], parts[1]);
        } catch (RuntimeException e) {
            Torcherino.logger.info("Ignoring malformed Torcherino blacklist entry: {}", value);
            return;
        }
        if (!Block.REGISTRY.containsKey(id)) {
            Torcherino.logger.info("Could not find block {}, ignoring", value);
            return;
        }
        Block block = Block.REGISTRY.getObject(id);
        if (block == null) {
            Torcherino.logger.info("Could not find block {}, ignoring", value);
            return;
        }
        Torcherino.logger.info("Blacklisting block {}", id);
        blacklistBlock(block);
    }

    public static synchronized void blacklistBlock(Block block) {
        if (block != null) {
            BLACKLISTED_BLOCKS.add(block);
        }
    }

    public static synchronized void blacklistTile(Class<? extends TileEntity> tile) {
        if (tile != null) {
            BLACKLISTED_TILES.add(tile);
            TILE_MATCH_CACHE.clear();
        }
    }

    public static synchronized boolean isBlockBlacklisted(Block block) {
        return BLACKLISTED_BLOCKS.contains(block);
    }

    public static boolean isTileBlacklisted(Class<? extends TileEntity> tile) {
        Boolean cached = TILE_MATCH_CACHE.get(tile);
        if (cached != null) {
            return cached.booleanValue();
        }

        boolean blacklisted = false;
        synchronized (TorcherinoRegistry.class) {
            for (Class<? extends TileEntity> blocked : BLACKLISTED_TILES) {
                if (blocked.isAssignableFrom(tile)) {
                    blacklisted = true;
                    break;
                }
            }
        }
        TILE_MATCH_CACHE.put(tile, Boolean.valueOf(blacklisted));
        return blacklisted;
    }

    @SuppressWarnings("unchecked")
    private static void blacklistTileClass(String className) {
        try {
            Class<?> type = Torcherino.class.getClassLoader().loadClass(className);
            if (!TileEntity.class.isAssignableFrom(type)) {
                Torcherino.logger.info("{} is not a TileEntity, ignoring", className);
                return;
            }
            blacklistTile((Class<? extends TileEntity>) type);
        } catch (ClassNotFoundException e) {
            Torcherino.logger.info("Could not find TileEntity class {}, ignoring", className);
        }
    }
}
