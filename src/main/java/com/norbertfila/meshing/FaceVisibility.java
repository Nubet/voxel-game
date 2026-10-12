package com.norbertfila.meshing;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkSnapshot;

final class FaceVisibility {
    private static final BlockNeighborhood.Side[] SIDES = BlockNeighborhood.Side.values();

    static boolean isVisible(byte block, byte neighbor) {
        return block != BlockId.AIR
                && !BlockRegistry.DEFAULT.occludesFullFace(neighbor)
                && !(block == BlockId.WATER && neighbor == BlockId.WATER);
    }

    static boolean isVisible(
            byte block, ChunkSnapshot snapshot, BlockNeighborhood neighborhood, int face, int x, int y, int z) {
        if (block == BlockId.AIR) return false;
        int nx = x + VisibleFaceMesher.NORMALS[face][0];
        int ny = y + VisibleFaceMesher.NORMALS[face][1];
        int nz = z + VisibleFaceMesher.NORMALS[face][2];
        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < Chunk.SIZE && ny < Chunk.SIZE && nz < Chunk.SIZE)
            return isVisible(block, snapshot.get(nx, ny, nz));
        ChunkSnapshot neighbor = neighborhood.neighbor(SIDES[face]).orElse(null);
        // Unknown neighbors retain temporary faces until streaming supplies a new neighborhood.
        if (neighbor == null) return true;
        return isVisible(
                block,
                neighbor.get(
                        Math.floorMod(nx, Chunk.SIZE), Math.floorMod(ny, Chunk.SIZE), Math.floorMod(nz, Chunk.SIZE)));
    }

    private FaceVisibility() {}
}
