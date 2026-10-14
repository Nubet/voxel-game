package com.norbertfila.meshing;

import com.norbertfila.world.ChunkSnapshot;

/** Builds the requested parts of a chunk mesh; see {@link ChunkMesh} for what each part contains. */
@FunctionalInterface
public interface LayeredMesher {
    ChunkMesh mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz, int parts);
}
