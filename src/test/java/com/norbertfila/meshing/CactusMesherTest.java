package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CactusMesherTest {
    @Test
    void sidePlanesMeetBehindTransparentBordersWithoutShrinkingTextureUvs() {
        var chunk = new Chunk();
        chunk.set(3, 4, 5, BlockId.CACTUS);
        var snapshot = chunk.snapshot();
        var neighbors = BlockNeighborhood.centerOnly(snapshot);
        var mesh = GreedyMesher.meshSolids(snapshot, neighbors);
        assertEquals(new QuadRanges(0, 6, 0), mesh.ranges());
        float[] vertices = QuadCodec.decodeTextured(mesh, 0, 0, 0);
        int[] axes = {0, 0, 1, 1, 2, 2};
        float[] planes = {3 + 15 / 16f, 3 + 1 / 16f, 5, 4, 5 + 15 / 16f, 5 + 1 / 16f};
        for (int face = 0; face < 6; face++) {
            for (int vertex = 0; vertex < 6; vertex++) {
                int offset = (face * 6 + vertex) * 7;
                assertEquals(planes[face], vertices[offset + axes[face]]);
                assertEquals(BlockRegistry.DEFAULT.definition(BlockId.CACTUS).textureLayer(face), vertices[offset + 5]);
                assertTrue(vertices[offset + 3] == 0 || vertices[offset + 3] == 1);
                assertTrue(vertices[offset + 4] == 0 || vertices[offset + 4] == 1);
            }
            int o = face * 6 * 7;
            int a = (axes[face] + 1) % 3, b = (axes[face] + 2) % 3;
            float cross = (vertices[o + 7 + a] - vertices[o + a]) * (vertices[o + 14 + b] - vertices[o + b])
                    - (vertices[o + 7 + b] - vertices[o + b]) * (vertices[o + 14 + a] - vertices[o + a]);
            assertTrue(cross * VisibleFaceMesher.NORMALS[face][axes[face]] > 0, "Inverted face " + face);
        }
        assertArrayEquals(
                new VisibleFaceMesher().mesh(snapshot, neighbors, 0, 0, 0).vertices(),
                new GreedyMesher().mesh(snapshot, neighbors, 0, 0, 0).vertices());
    }

    @Test
    void stackedSegmentsStayContinuousAcrossChunkBoundaryWithoutInternalCaps() {
        var world = new World();
        for (int cy = 0; cy <= 1; cy++) world.putChunk(new ChunkPos(0, cy, 0), new Chunk());
        for (int y = 14; y <= 16; y++) world.setBlock(new BlockPos(3, y, 5), BlockId.CACTUS, 64);
        for (int cy = 0; cy <= 1; cy++) {
            var n = world.blockNeighborhood(new ChunkPos(0, cy, 0));
            var mesh = GreedyMesher.meshSolids(n.center(), n);
            assertEquals(new QuadRanges(0, 5, 0), mesh.ranges());
            int top = 0, bottom = 0;
            for (int i = 0; i < mesh.records().length; i += 2) {
                int face = mesh.records()[i] >>> 15 & 7;
                if (face == 2) top++;
                if (face == 3) bottom++;
            }
            assertEquals(cy == 0 ? 0 : 1, top);
            assertEquals(cy == 0 ? 1 : 0, bottom);
            float[] vertices = QuadCodec.decodeTextured(mesh, 0, cy, 0);
            for (int i = 0; i < vertices.length; i += 7) {
                assertTrue(vertices[i + 1] >= (cy == 0 ? 14 : 16));
                assertTrue(vertices[i + 1] <= (cy == 0 ? 16 : 17));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void cactusDoesNotHideNeighborsAndInsetSidesSurviveSolidNeighbors(int face) {
        var chunk = new Chunk();
        chunk.set(3, 4, 5, BlockId.CACTUS);
        int[] normal = VisibleFaceMesher.NORMALS[face];
        chunk.set(3 + normal[0], 4 + normal[1], 5 + normal[2], BlockId.STONE);
        var snapshot = chunk.snapshot();
        var mesh = GreedyMesher.meshSolids(snapshot, BlockNeighborhood.centerOnly(snapshot));
        assertEquals(6, mesh.ranges().opaque());
        assertEquals(face == 2 || face == 3 ? 5 : 6, mesh.ranges().cutout());
    }
}
