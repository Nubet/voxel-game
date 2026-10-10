package com.norbertfila.meshing;

import com.norbertfila.world.ChunkSnapshot;

@FunctionalInterface
public interface ChunkMesher {
    MeshData mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz);
}
