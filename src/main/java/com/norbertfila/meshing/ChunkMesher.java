package com.norbertfila.meshing;

import com.norbertfila.world.ChunkSnapshot;

/** A mesher that writes every render layer into one buffer, so it always rebuilds all parts. */
@FunctionalInterface
public interface ChunkMesher extends LayeredMesher {
    MeshData mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz);

    @Override
    default ChunkMesh mesh(ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int cx, int cy, int cz, int parts) {
        ChunkMesh.checkParts(parts);
        return ChunkMesh.single(mesh(snapshot, neighborhood, cx, cy, cz));
    }
}
