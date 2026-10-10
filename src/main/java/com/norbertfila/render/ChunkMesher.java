package com.norbertfila.render;

import com.norbertfila.world.Chunk;

@FunctionalInterface
public interface ChunkMesher {
    MeshData mesh(Chunk chunk, int cx, int cy, int cz);
}
