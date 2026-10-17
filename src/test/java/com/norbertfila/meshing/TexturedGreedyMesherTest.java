package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.RenderLayer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TexturedGreedyMesherTest {
    private final GreedyMesher mesher = new GreedyMesher();

    @Test
    void grassBelowThinSnowUsesSnowySidesWithoutChangingItsBlockIdentity() {
        var chunk = new Chunk();
        chunk.set(3, 4, 5, BlockId.GRASS);
        chunk.set(3, 5, 5, BlockId.SNOW_LAYER);
        var snapshot = chunk.snapshot();
        var mesh = GreedyMesher.meshSolids(snapshot, BlockNeighborhood.centerOnly(snapshot));
        int snowySides = 0;
        for (int i = 0; i < mesh.records().length; i += 2) {
            int material = mesh.records()[i + 1];
            if ((material & 255) != BlockRegistry.SNOWY_GRASS_SIDE_TEXTURE) continue;
            assertEquals(BlockId.GRASS, (material >>> 16) & 255);
            assertEquals(0, (material >>> 8) & 31);
            snowySides++;
        }
        assertEquals(4, snowySides);
    }

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
    void mixedGeometryIsGroupedWithoutChangingVerticesUvsOrShade() {
        Chunk opaque = new Chunk();
        opaque.set(1, 5, 5, BlockId.STONE);
        Chunk cutout = new Chunk();
        cutout.set(4, 5, 5, BlockId.LEAVES);
        cutout.set(7, 5, 5, BlockId.GLASS);
        Chunk translucent = new Chunk();
        translucent.set(10, 5, 5, BlockId.WATER);
        translucent.set(11, 5, 5, BlockId.WATER);
        Chunk mixed = new Chunk();
        mixed.set(1, 5, 5, BlockId.STONE);
        mixed.set(4, 5, 5, BlockId.LEAVES);
        mixed.set(7, 5, 5, BlockId.GLASS);
        mixed.set(10, 5, 5, BlockId.WATER);
        mixed.set(11, 5, 5, BlockId.WATER);

        MeshData result = textured(mixed);
        assertEquals(new MeshRanges(36, 72, 36), result.ranges());
        assertEquals(7, result.format().stride());
        Chunk[] layers = {opaque, cutout, translucent};
        for (RenderLayer layer : RenderLayer.values()) {
            int start = result.ranges().firstVertex(layer) * result.format().stride();
            int end =
                    start + result.ranges().vertexCount(layer) * result.format().stride();
            assertArrayEquals(
                    textured(layers[layer.ordinal()]).vertices(), Arrays.copyOfRange(result.vertices(), start, end));
        }
        assertArrayEquals(result.vertices(), textured(mixed).vertices());
        assertEquals(
                new MeshRanges(result.vertexCount(), 0, 0),
                mesher.mesh(mixed.snapshot(), BlockNeighborhood.centerOnly(mixed.snapshot()), -1, -1, -1)
                        .ranges());
    }

    @ParameterizedTest
    @EnumSource(RenderLayer.class)
    void singleLayerMeshesHaveEmptyOtherRanges(RenderLayer layer) {
        byte block = switch (layer) {
            case OPAQUE -> BlockId.STONE;
            case CUTOUT -> BlockId.LEAVES;
            case TRANSLUCENT -> BlockId.WATER;
        };
        Chunk chunk = new Chunk();
        chunk.set(3, 4, 5, block);
        MeshData mesh = textured(chunk);
        for (RenderLayer current : RenderLayer.values())
            assertEquals(current == layer ? 36 : 0, mesh.ranges().vertexCount(current));
    }

    @Test
    void adjacentMaterialsKeepIndependentRangesAndWaterCulling() {
        Chunk chunk = new Chunk();
        chunk.set(3, 4, 5, BlockId.STONE);
        chunk.set(4, 4, 5, BlockId.WATER);
        MeshData mesh = textured(chunk);
        assertEquals(new MeshRanges(36, 0, 30), mesh.ranges());
        float[] vertices = mesh.vertices();
        for (int vertex = 0; vertex < mesh.vertexCount(); vertex++) {
            assertEquals(
                    vertex < 36 ? BlockRegistry.STONE_TEXTURE : BlockRegistry.WATER_TEXTURE,
                    vertices[vertex * mesh.format().stride() + 5]);
        }
        assertEquals(new MeshRanges(0, 0, 0), textured(new Chunk()).ranges());
    }

    private MeshData textured(Chunk chunk) {
        var snapshot = chunk.snapshot();
        return mesher.meshTextured(snapshot, BlockNeighborhood.centerOnly(snapshot), -1, -1, -1);
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
