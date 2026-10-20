package com.norbertfila.light;

import com.norbertfila.world.BlockRegistry;
import java.util.Arrays;

/**
 * Minecraft-style block light propagation inside a box: every step into a block that transmits light costs one
 * level. Missing chunks block light, so an edge that is still loading stays dark instead of leaking.
 */
final class LightFlood {
    private static final boolean[] TRANSMITS = new boolean[256];

    static {
        for (int id = 0; id < 256; id++)
            TRANSMITS[id] =
                    BlockRegistry.DEFAULT.isRegistered((byte) id) && BlockRegistry.DEFAULT.transmitsLight((byte) id);
    }

    private final BlockReader reader;
    private final int originX, originY, originZ, sizeX, sizeY, sizeZ;
    private final byte[] levels;
    private final int[][] buckets = new int[LightLevels.MAX_LEVEL + 1][];
    private final int[] bucketSizes = new int[LightLevels.MAX_LEVEL + 1];

    LightFlood(BlockReader reader, int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ) {
        this.reader = reader;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        levels = new byte[sizeX * sizeY * sizeZ];
    }

    /** Seeds a source at world coordinates; sources outside the box are ignored. */
    void seed(int x, int y, int z, int level) {
        int dx = x - originX, dy = y - originY, dz = z - originZ;
        if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) return;
        raise((dz * sizeY + dy) * sizeX + dx, level);
    }

    BlockLightVolume propagate() {
        reader.reset();
        int layer = sizeX * sizeY;
        for (int level = LightLevels.MAX_LEVEL; level > 1; level--) {
            for (int i = 0; i < bucketSizes[level]; i++) {
                int cell = buckets[level][i];
                if (levels[cell] != level) continue;
                int dx = cell % sizeX, dy = cell / sizeX % sizeY, dz = cell / layer;
                if (dx > 0) spread(cell - 1, dx - 1, dy, dz, level);
                if (dx < sizeX - 1) spread(cell + 1, dx + 1, dy, dz, level);
                if (dy > 0) spread(cell - sizeX, dx, dy - 1, dz, level);
                if (dy < sizeY - 1) spread(cell + sizeX, dx, dy + 1, dz, level);
                if (dz > 0) spread(cell - layer, dx, dy, dz - 1, level);
                if (dz < sizeZ - 1) spread(cell + layer, dx, dy, dz + 1, level);
            }
            buckets[level] = null;
        }
        return new BlockLightVolume(originX, originY, originZ, sizeX, sizeY, sizeZ, levels);
    }

    private void spread(int cell, int dx, int dy, int dz, int level) {
        if (levels[cell] >= level - 1) return;
        int block = reader.block(originX + dx, originY + dy, originZ + dz);
        if (block == BlockReader.UNKNOWN || !TRANSMITS[block]) return;
        raise(cell, level - 1);
    }

    private void raise(int cell, int level) {
        if (levels[cell] >= level) return;
        levels[cell] = (byte) level;
        int[] bucket = buckets[level];
        if (bucket == null) bucket = buckets[level] = new int[64];
        else if (bucketSizes[level] == bucket.length)
            bucket = buckets[level] = Arrays.copyOf(bucket, bucket.length * 2);
        bucket[bucketSizes[level]++] = cell;
    }
}
