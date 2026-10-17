package com.norbertfila.meshing;

import com.norbertfila.world.ChunkSnapshot;

/** Concurrent calls consume immutable snapshots. Returned arrays must not be mutated or reused after publication. */
@FunctionalInterface
public interface LayeredMesher {
    ChunkMesh mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz, int parts);
}
