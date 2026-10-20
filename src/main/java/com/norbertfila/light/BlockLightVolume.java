package com.norbertfila.light;

import java.util.Objects;

/**
 * Block light levels for a box of cells starting at the origin, laid out X fastest, then Y, then Z, which matches an
 * OpenGL 3D texture. The array is never modified after construction, so volumes can be shared across threads.
 */
public record BlockLightVolume(int originX, int originY, int originZ, int sizeX, int sizeY, int sizeZ, byte[] levels) {
    public static final BlockLightVolume EMPTY = new BlockLightVolume(0, 0, 0, 0, 0, 0, new byte[0]);

    public BlockLightVolume {
        Objects.requireNonNull(levels);
        if (sizeX < 0 || sizeY < 0 || sizeZ < 0 || levels.length != (long) sizeX * sizeY * sizeZ)
            throw new IllegalArgumentException("Light volume size does not match its levels");
    }

    public boolean isEmpty() {
        return levels.length == 0;
    }

    public int level(int x, int y, int z) {
        int dx = x - originX, dy = y - originY, dz = z - originZ;
        if (dx < 0 || dy < 0 || dz < 0 || dx >= sizeX || dy >= sizeY || dz >= sizeZ) return 0;
        return levels[index(dx, dy, dz)];
    }

    int index(int dx, int dy, int dz) {
        return (dz * sizeY + dy) * sizeX + dx;
    }

    /** Identity equality: two separately computed volumes are different uploads even with equal content. */
    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
