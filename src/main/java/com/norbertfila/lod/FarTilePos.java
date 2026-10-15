package com.norbertfila.lod;

/** Grid position of a far tile; tile x covers world blocks {@code [x * SIZE, (x + 1) * SIZE)}. */
public record FarTilePos(int x, int z) {
    public int originX() {
        return x * FarTerrainTile.SIZE;
    }

    public int originZ() {
        return z * FarTerrainTile.SIZE;
    }

    // Tiles fill a square around the camera; the default 31 * x + z clusters badly in open-addressing maps.
    @Override
    public int hashCode() {
        long mixed = (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        return (int) (mixed ^ mixed >>> 32);
    }
}
