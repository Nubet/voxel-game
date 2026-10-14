package com.norbertfila.meshing;

import com.norbertfila.world.ChunkSnapshot;

/** Builds an explicit-vertex mesh; used by tools and tests that inspect triangles directly. */
@FunctionalInterface
public interface ChunkMesher {
    MeshData mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz);
}
