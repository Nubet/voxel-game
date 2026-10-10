package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkSnapshot;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GreedyMesherTest {
    private final VisibleFaceMesher baseline = new VisibleFaceMesher();
    private final GreedyMesher greedy = new GreedyMesher();

    @Test
    void emptyChunkHasNoGeometry() {
        assertEquals(0, mesh(new Chunk()).vertexCount());
    }

    @Test
    void singleBlockMatchesBaselineVertexPositions() {
        Chunk chunk = new Chunk();
        chunk.set(3, 4, 5, BlockId.STONE);

        MeshData expected = meshWith(baseline, chunk);
        MeshData actual = meshWith(greedy, chunk);

        assertEquals(expected.vertexCount(), actual.vertexCount());
        assertEquals(expected.vertices().length, actual.vertices().length);
        for (int i = 0; i < actual.vertices().length; i++) assertEquals(expected.vertices()[i], actual.vertices()[i]);
    }

    @Test
    void fullChunkMergesEachBoundaryIntoOneQuad() {
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        java.util.Arrays.fill(blocks, BlockId.STONE);
        Chunk chunk = Chunk.fromBlocks(blocks);

        assertEquals(6 * MeshData.VERTICES_PER_FACE, meshWith(greedy, chunk).vertexCount());
        assertEquals(
                6 * Chunk.SIZE * Chunk.SIZE * MeshData.VERTICES_PER_FACE,
                meshWith(baseline, chunk).vertexCount());
    }

    @Test
    void checkerboardDoesNotMergeDifferentCells() {
        Chunk chunk = new Chunk();
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) if ((x + z) % 2 == 0) chunk.set(x, 0, z, BlockId.STONE);

        assertEquals(
                meshWith(baseline, chunk).vertexCount(), meshWith(greedy, chunk).vertexCount());
    }

    @Test
    void mergesAContinuousStrip() {
        Chunk chunk = new Chunk();
        for (int x = 0; x < Chunk.SIZE; x++) chunk.set(x, 0, 0, BlockId.STONE);

        assertEquals(6 * MeshData.VERTICES_PER_FACE, meshWith(greedy, chunk).vertexCount());
        assertEquals(
                (Chunk.SIZE * 6 - (Chunk.SIZE - 1) * 2) * MeshData.VERTICES_PER_FACE,
                meshWith(baseline, chunk).vertexCount());
    }

    @Test
    void randomSurfaceAreaMatchesBaseline() {
        Chunk chunk = new Chunk();
        Random random = new Random(7L);
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++)
                    if (random.nextBoolean()) chunk.set(x, y, z, (byte) (1 + random.nextInt(3)));

        assertEquals(surfaceArea(meshWith(baseline, chunk)), surfaceArea(meshWith(greedy, chunk)));
    }

    private static MeshData mesh(Chunk chunk) {
        return meshWith(new GreedyMesher(), chunk);
    }

    private static MeshData meshWith(ChunkMesher mesher, Chunk chunk) {
        ChunkSnapshot snapshot = chunk.snapshot();
        return mesher.mesh(snapshot, BlockNeighborhood.centerOnly(snapshot), 0, 0, 0);
    }

    private static long surfaceArea(MeshData mesh) {
        float[] vertices = mesh.vertices();
        long doubledArea = 0;
        for (int i = 0; i < vertices.length; i += MeshData.VERTEX_STRIDE * 3) {
            float ax = vertices[i + 6] - vertices[i];
            float ay = vertices[i + 7] - vertices[i + 1];
            float az = vertices[i + 8] - vertices[i + 2];
            float bx = vertices[i + 12] - vertices[i];
            float by = vertices[i + 13] - vertices[i + 1];
            float bz = vertices[i + 14] - vertices[i + 2];
            doubledArea += Math.round(Math.sqrt((ay * bz - az * by) * (ay * bz - az * by)
                    + (az * bx - ax * bz) * (az * bx - ax * bz)
                    + (ax * by - ay * bx) * (ax * by - ay * bx)));
        }
        return doubledArea;
    }
}
