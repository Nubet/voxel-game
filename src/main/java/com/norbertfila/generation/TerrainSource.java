package com.norbertfila.generation;

/** Sampling runs concurrently with chunk generation and Far LOD; implementations must not access live world state. */
public interface TerrainSource extends WorldGenerator {
    int surfaceHeight(int worldX, int worldZ);

    TerrainSummary sample(int worldX, int worldZ);

    default java.util.List<Canopy> canopies(int originX, int originZ, int size) {
        return java.util.List.of();
    }

    default int tintIndex(int worldX, int worldZ) {
        return 0;
    }
}
