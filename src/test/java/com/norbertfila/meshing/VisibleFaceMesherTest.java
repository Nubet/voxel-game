package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import org.junit.jupiter.api.Test;

class VisibleFaceMesherTest {
    private static final int[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final VisibleFaceMesher mesher = new VisibleFaceMesher();

    @Test
    void emptyChunkHasNoFaces() {
        assertCounts(new Chunk(), 0);
    }

    @Test
    void isolatedBlockHasSixFaces() {
        Chunk chunk = new Chunk();
        chunk.set(1, 1, 1, BlockId.STONE);

        assertCounts(chunk, 6);
    }

    @Test
    void adjacentBlocksHideSharedFace() {
        Chunk chunk = new Chunk();
        chunk.set(1, 1, 1, BlockId.STONE);
        chunk.set(2, 1, 1, BlockId.STONE);

        assertCounts(chunk, 10);
    }

    @Test
    void fullChunkEmitsOnlyOuterShell() {
        Chunk chunk = new Chunk();
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++) for (int x = 0; x < Chunk.SIZE; x++) chunk.set(x, y, z, BlockId.STONE);

        assertCounts(chunk, 6 * Chunk.SIZE * Chunk.SIZE);
    }

    @Test
    void eachFaceHasOutwardWinding() {
        Chunk chunk = new Chunk();
        chunk.set(1, 1, 1, BlockId.STONE);
        float[] vertices = mesher.mesh(chunk, 0, 0, 0).vertices();

        for (int face = 0; face < NORMALS.length; face++) {
            int offset = face * MeshData.VERTICES_PER_FACE * MeshData.VERTEX_STRIDE;
            float ax = vertices[offset + 6] - vertices[offset];
            float ay = vertices[offset + 7] - vertices[offset + 1];
            float az = vertices[offset + 8] - vertices[offset + 2];
            float bx = vertices[offset + 12] - vertices[offset];
            float by = vertices[offset + 13] - vertices[offset + 1];
            float bz = vertices[offset + 14] - vertices[offset + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;

            assertEquals(NORMALS[face][0], (int) nx);
            assertEquals(NORMALS[face][1], (int) ny);
            assertEquals(NORMALS[face][2], (int) nz);
        }
    }

    private void assertCounts(Chunk chunk, int faces) {
        MeshData mesh = mesher.mesh(chunk, 0, 0, 0);

        assertEquals(faces, mesh.faceCount());
        assertEquals(faces * MeshData.VERTICES_PER_FACE, mesh.vertexCount());
    }
}
