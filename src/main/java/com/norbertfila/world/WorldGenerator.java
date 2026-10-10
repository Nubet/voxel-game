package com.norbertfila.world;

@FunctionalInterface
public interface WorldGenerator {
    Chunk generate(int chunkX, int chunkY, int chunkZ);
}
