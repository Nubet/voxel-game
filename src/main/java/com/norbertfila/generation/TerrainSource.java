package com.norbertfila.generation;

public interface TerrainSource extends WorldGenerator {
    int surfaceHeight(int worldX, int worldZ);

    TerrainSummary sample(int worldX, int worldZ);
}
