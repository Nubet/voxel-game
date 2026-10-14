package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class CrossPlantMesherTest {
    @Test
    void plantsHaveTwoDoubleSidedPlanesAndUprightUvsInCutoutRange() {
        for (byte id : new byte[] {
            BlockId.SHORT_GRASS, BlockId.FERN, BlockId.DANDELION_YELLOW, BlockId.DANDELION_WHITE, BlockId.POPPY
        }) {
            Chunk chunk = new Chunk();
            chunk.set(2, 3, 4, id);
            var snapshot = chunk.snapshot();
            var neighborhood = BlockNeighborhood.centerOnly(snapshot);
            MeshData mesh = new WaterMesher()
                    .mesh(snapshot, neighborhood, -1, 0, 1, ChunkMesh.ALL)
                    .solid();
            assertEquals(new MeshRanges(0, 24, 0), mesh.ranges());
            float[] vertices = mesh.vertices();
            int stride = MeshFormat.TEXTURED_VERTEX_STRIDE;
            for (int i = 0; i < vertices.length; i += stride) {
                assertTrue(vertices[i] >= -14 && vertices[i] <= -13);
                assertTrue(vertices[i + 2] >= 20 && vertices[i + 2] <= 21);
                assertEquals(1 - (vertices[i + 1] - 3), vertices[i + 4]);
                assertEquals(BlockRegistry.DEFAULT.definition(id).sideTexture(), vertices[i + 5]);
            }
            for (int plane = 0; plane < 2; plane++) {
                int a = plane * 12 * stride;
                for (int attribute = 0; attribute < 3; attribute++) {
                    assertEquals(vertices[a + attribute], vertices[a + 8 * stride + attribute]);
                    assertEquals(vertices[a + 2 * stride + attribute], vertices[a + 6 * stride + attribute]);
                }
            }
            assertThrows(IllegalArgumentException.class, () -> new PackedVertexMesher().mesh(snapshot, neighborhood));
            assertEquals(
                    24,
                    new VisibleFaceMesher()
                            .mesh(snapshot, neighborhood, 0, 0, 0)
                            .vertexCount());
        }
    }

    @Test
    void plantsDoNotHideGroundFacesOrMergeIntoCubesAndWaterKeepsItsRange() {
        Chunk chunk = new Chunk();
        chunk.set(2, 0, 2, BlockId.GRASS);
        chunk.set(2, 1, 2, BlockId.POPPY);
        chunk.set(3, 1, 2, BlockId.DANDELION_WHITE);
        chunk.set(5, 0, 5, BlockId.WATER);
        var snapshot = chunk.snapshot();
        ChunkMesh mesh =
                new WaterMesher().mesh(snapshot, BlockNeighborhood.centerOnly(snapshot), 0, 0, 0, ChunkMesh.ALL);
        assertEquals(new MeshRanges(36, 48, 0), mesh.solid().ranges());
        assertEquals(new MeshRanges(0, 0, 36), mesh.water().ranges());
    }
}
