package com.norbertfila.generation;

import com.norbertfila.world.ChunkPos;

/** Implementations must support concurrent calls and return independently owned, deterministic results. */
public interface WorldGenerator {
    long seed();

    int generatorVersion();

    ChunkData generate(ChunkPos position);
}
