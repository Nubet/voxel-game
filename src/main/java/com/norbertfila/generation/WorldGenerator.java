package com.norbertfila.generation;

import com.norbertfila.world.ChunkPos;

public interface WorldGenerator {
    long seed();

    int generatorVersion();

    ChunkData generate(ChunkPos position);
}
