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
    void solidIceDoesNotEmitInternalTransparentFaces() {
        Chunk ice = Chunk.uniform(BlockId.ICE);
        assertEquals(36 * 6, meshWith(greedy, ice).vertices().length);
        assertEquals(6 * 16 * 16 * 36, meshWith(baseline, ice).vertices().length);
    }

    @Test
    void mergedSurfacesMatchBaselinePositionsDirectionsAndColorsAcrossAllBoundaries() {
        var generator = new com.norbertfila.generation.HillsGenerator();
        var world = new com.norbertfila.world.World();
        var position = new com.norbertfila.world.ChunkPos(-1, 0, -1);
        for (int y = -1; y <= 1; y++)
            for (int z = -2; z <= 0; z++)
                for (int x = -2; x <= 0; x++) {
                    var neighbor = new com.norbertfila.world.ChunkPos(x, y, z);
                    world.putChunk(
                            neighbor,
                            Chunk.fromBlocks(generator.generate(neighbor).blocks()));
                }
        var neighborhood = world.blockNeighborhood(position);
        assertEquals(
                surfaces(baseline.mesh(neighborhood.center(), neighborhood, -1, 0, -1)),
                surfaces(greedy.mesh(neighborhood.center(), neighborhood, -1, 0, -1)));

        Chunk rectangle = new Chunk();
        for (int y = 2; y < 5; y++)
            for (int z = 4; z < 11; z++) for (int x = 1; x < 6; x++) rectangle.set(x, y, z, BlockId.STONE);
        assertEquals(surfaces(meshWith(baseline, rectangle)), surfaces(meshWith(greedy, rectangle)));
    }

    private record Surface(int axis, int direction, int plane, int u, int v, float r, float g, float b) {}

    private static java.util.Map<Surface, Integer> surfaces(MeshData mesh) {
        var surfaces = new java.util.HashMap<Surface, Integer>();
        float[] vertices = mesh.vertices();
        for (int offset = 0; offset < vertices.length; offset += 36) {
            float ax = vertices[offset + 6] - vertices[offset];
            float ay = vertices[offset + 7] - vertices[offset + 1];
            float az = vertices[offset + 8] - vertices[offset + 2];
            float bx = vertices[offset + 12] - vertices[offset];
            float by = vertices[offset + 13] - vertices[offset + 1];
            float bz = vertices[offset + 14] - vertices[offset + 2];
            float[] normal = {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
            int axis = normal[0] != 0 ? 0 : normal[1] != 0 ? 1 : 2;
            int uAxis = (axis + 1) % 3;
            int vAxis = (axis + 2) % 3;
            int minU = Integer.MAX_VALUE, minV = Integer.MAX_VALUE;
            int maxU = Integer.MIN_VALUE, maxV = Integer.MIN_VALUE;
            for (int vertex = 0; vertex < 6; vertex++) {
                int u = (int) vertices[offset + vertex * 6 + uAxis];
                int v = (int) vertices[offset + vertex * 6 + vAxis];
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
            for (int u = minU; u < maxU; u++)
                for (int v = minV; v < maxV; v++)
                    surfaces.merge(
                            new Surface(
                                    axis,
                                    normal[axis] > 0 ? 1 : -1,
                                    (int) vertices[offset + axis],
                                    u,
                                    v,
                                    vertices[offset + 3],
                                    vertices[offset + 4],
                                    vertices[offset + 5]),
                            1,
                            Integer::sum);
        }
        return surfaces;
    }

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
    void fullWaterChunkHasOnlySixMergedOuterFaces() {
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        java.util.Arrays.fill(blocks, BlockId.WATER);
        Chunk chunk = Chunk.fromBlocks(blocks);
        assertEquals(6, meshWith(greedy, chunk).faceCount());
        assertEquals(6 * Chunk.SIZE * Chunk.SIZE, meshWith(baseline, chunk).faceCount());
        assertEquals(surfaces(meshWith(baseline, chunk)), surfaces(meshWith(greedy, chunk)));
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
    void cullsPresentChunkBoundaryNeighbor() {
        Chunk center = new Chunk();
        Chunk neighbor = new Chunk();
        center.set(Chunk.SIZE - 1, 1, 1, BlockId.STONE);
        neighbor.set(0, 1, 1, BlockId.STONE);

        ChunkSnapshot snapshot = center.snapshot();
        BlockNeighborhood neighborhood =
                new BlockNeighborhood(snapshot, neighbor.snapshot(), null, null, null, null, null);

        assertEquals(5, greedy.mesh(snapshot, neighborhood, 0, 0, 0).faceCount());
    }

    @Test
    void keepsBoundaryFaceWhenNeighborIsMissing() {
        Chunk center = new Chunk();
        center.set(Chunk.SIZE - 1, 1, 1, BlockId.STONE);
        ChunkSnapshot snapshot = center.snapshot();

        assertEquals(
                6,
                greedy.mesh(snapshot, BlockNeighborhood.centerOnly(snapshot), 0, 0, 0)
                        .faceCount());
    }

    @Test
    void mixedWaterAndCutoutSurfacesMatchBaselineAtSignedChunkBoundaries() {
        var world = new com.norbertfila.world.World();
        var position = new com.norbertfila.world.ChunkPos(-1, -1, -1);
        byte[] materials = {BlockId.AIR, BlockId.WATER, BlockId.STONE, BlockId.DIRT, BlockId.LEAVES, BlockId.GLASS};
        Random random = new Random(42);
        for (int face = -1; face < 6; face++) {
            int[] normal = face < 0 ? new int[3] : VisibleFaceMesher.NORMALS[face];
            Chunk chunk = new Chunk();
            for (int y = 0; y < Chunk.SIZE; y++)
                for (int z = 0; z < Chunk.SIZE; z++)
                    for (int x = 0; x < Chunk.SIZE; x++)
                        chunk.set(x, y, z, materials[random.nextInt(materials.length)]);
            world.putChunk(
                    new com.norbertfila.world.ChunkPos(
                            position.x() + normal[0], position.y() + normal[1], position.z() + normal[2]),
                    chunk);
        }
        var neighborhood = world.blockNeighborhood(position);
        assertEquals(
                surfaces(baseline.mesh(neighborhood.center(), neighborhood, -1, -1, -1)),
                surfaces(greedy.mesh(neighborhood.center(), neighborhood, -1, -1, -1)));
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
