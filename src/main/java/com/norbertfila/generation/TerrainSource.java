package com.norbertfila.generation;

public interface TerrainSource extends WorldGenerator {
    TerrainSummary sample(int worldX, int worldZ);
}
