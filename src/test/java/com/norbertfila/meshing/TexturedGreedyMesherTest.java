package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import org.junit.jupiter.api.Test;

class TexturedGreedyMesherTest {
    private final GreedyMesher mesher = new GreedyMesher();

    @Test
    void singleBlockCarriesTheRegistryTextureForEachFaceAndKeepsBaselinePositions() {
        Chunk chunk = new Chunk();
        chunk.set(3, 4, 5, BlockId.GRASS);
        var snapshot = chunk.snapshot();
        var neighborhood = BlockNeighborhood.centerOnly(snapshot);
        MeshData baseline = mesher.mesh(snapshot, neighborhood, 0, 0, 0);
        MeshData textured = mesher.meshTextured(snapshot, neighborhood, 0, 0, 0);

        assertEquals(MeshFormat.TEXTURED_VERSION, textured.format().version());
        assertEquals(7, textured.format().stride());
        assertEquals(36, textured.vertexCount());
        assertEquals(6, textured.faceCount());
        float[] colored = baseline.vertices(), uv = textured.vertices();
        int[][] expectedUv = {{0, 0}, {0, 1}, {1, 1}, {0, 0}, {1, 1}, {1, 0}};
        int[] layers = {
            BlockRegistry.GRASS_SIDE_TEXTURE,
            BlockRegistry.GRASS_SIDE_TEXTURE,
            BlockRegistry.GRASS_TOP_TEXTURE,
            BlockRegistry.DIRT_TEXTURE,
            BlockRegistry.GRASS_SIDE_TEXTURE,
            BlockRegistry.GRASS_SIDE_TEXTURE
        };
        float[] shades = {.77f, .77f, 1, .56f, .86f, .86f};
        for (int face = 0; face < 6; face++) {
            float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE;
            float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            for (int vertex = 0; vertex < 6; vertex++) {
                int texturedOffset = (face * 6 + vertex) * 7;
                int colorOffset = (face * 6 + vertex) * 6;
                for (int axis = 0; axis < 3; axis++)
                    assertEquals(colored[colorOffset + axis], uv[texturedOffset + axis]);
                minU = Math.min(minU, uv[texturedOffset + 3]);
                maxU = Math.max(maxU, uv[texturedOffset + 3]);
                minV = Math.min(minV, uv[texturedOffset + 4]);
                maxV = Math.max(maxV, uv[texturedOffset + 4]);
                assertEquals(layers[face], uv[texturedOffset + 5]);
                assertEquals(shades[face], uv[texturedOffset + 6]);
                assertEquals(expectedUv[vertex][0], uv[texturedOffset + 3]);
                assertEquals(expectedUv[vertex][1], uv[texturedOffset + 4]);
            }
            assertEquals(0, minU);
            assertEquals(1, maxU);
            assertEquals(0, minV);
            assertEquals(1, maxV);
        }
        assertEquals(3, textured.format().bounds().minX());
        assertEquals(4, textured.format().bounds().minY());
        assertEquals(5, textured.format().bounds().minZ());
        assertEquals(4, textured.format().bounds().maxX());
        assertEquals(5, textured.format().bounds().maxY());
        assertEquals(6, textured.format().bounds().maxZ());
    }

    @Test
    void greedyMergedQuadsRepeatUvsByBlockAndKeepOneLayerPerFace() {
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        java.util.Arrays.fill(blocks, BlockId.STONE);
        Chunk chunk = Chunk.fromBlocks(blocks);
        var snapshot = chunk.snapshot();
        MeshData mesh = mesher.meshTextured(snapshot, BlockNeighborhood.centerOnly(snapshot), 0, 0, 0);
        assertEquals(6, mesh.faceCount());
        float[] vertices = mesh.vertices();
        for (int quad = 0; quad < 6; quad++) {
            float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE;
            float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            for (int vertex = 0; vertex < 6; vertex++) {
                int offset = (quad * 6 + vertex) * 7;
                minU = Math.min(minU, vertices[offset + 3]);
                maxU = Math.max(maxU, vertices[offset + 3]);
                minV = Math.min(minV, vertices[offset + 4]);
                maxV = Math.max(maxV, vertices[offset + 4]);
                assertEquals(BlockRegistry.STONE_TEXTURE, vertices[offset + 5]);
            }
            assertEquals(0, minU);
            assertEquals(Chunk.SIZE, maxU);
            assertEquals(0, minV);
            assertEquals(Chunk.SIZE, maxV);
        }
    }

    @Test
    void separateTexturedEntryPointLeavesColorBaselineUnchanged() {
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 3, BlockId.DIRT);
        var snapshot = chunk.snapshot();
        var neighborhood = BlockNeighborhood.centerOnly(snapshot);
        MeshData first = mesher.mesh(snapshot, neighborhood, -2, 3, 4);
        MeshData second = mesher.mesh(snapshot, neighborhood, -2, 3, 4);
        assertArrayEquals(first.vertices(), second.vertices());
        assertEquals(MeshData.VERTEX_STRIDE, first.format().stride());
    }
}
