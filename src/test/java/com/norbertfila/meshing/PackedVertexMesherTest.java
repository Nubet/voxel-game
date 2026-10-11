package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkSnapshot;
import org.junit.jupiter.api.Test;

class PackedVertexMesherTest {
    @Test
    void packedGoldenSceneMatchesFloatVertexPositions() {
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 3, BlockId.STONE);
        ChunkSnapshot snapshot = chunk.snapshot();
        MeshData floatMesh = new VisibleFaceMesher().mesh(snapshot, BlockNeighborhood.centerOnly(snapshot), 0, 0, 0);
        PackedMesh packedMesh = new PackedVertexMesher().mesh(snapshot, BlockNeighborhood.centerOnly(snapshot));

        assertEquals(floatMesh.vertexCount(), packedMesh.vertexCount());
        assertEquals(floatMesh.vertexCount() * PackedVertex.BYTES, packedMesh.bytes());
        float[] vertices = floatMesh.vertices();
        int[] packed = packedMesh.vertices();
        for (int i = 0; i < packed.length; i++) {
            PackedVertex.Decoded decoded = PackedVertex.decode(packed[i]);
            int[] corner = PackedVertex.cornerPosition(decoded.face(), decoded.corner());
            int offset = i * MeshData.VERTEX_STRIDE;
            assertEquals(vertices[offset], decoded.x() + corner[0]);
            assertEquals(vertices[offset + 1], decoded.y() + corner[1]);
            assertEquals(vertices[offset + 2], decoded.z() + corner[2]);
            assertEquals(BlockId.STONE, decoded.blockId());
            assertEquals(BlockRegistry.STONE_TEXTURE, PackedVertex.decodeTextureLayer(packed[i]));
        }
    }

    @Test
    void packedGrassUsesTheFaceSpecificRegistryLayers() {
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 3, BlockId.GRASS);
        ChunkSnapshot snapshot = chunk.snapshot();
        int[] vertices = new PackedVertexMesher()
                .mesh(snapshot, BlockNeighborhood.centerOnly(snapshot))
                .vertices();
        int[] expectedLayers = {
            BlockRegistry.GRASS_SIDE_TEXTURE,
            BlockRegistry.GRASS_SIDE_TEXTURE,
            BlockRegistry.GRASS_TOP_TEXTURE,
            BlockRegistry.DIRT_TEXTURE,
            BlockRegistry.GRASS_SIDE_TEXTURE,
            BlockRegistry.GRASS_SIDE_TEXTURE
        };
        for (int face = 0; face < expectedLayers.length; face++)
            assertEquals(expectedLayers[face], PackedVertex.decodeTextureLayer(vertices[face * 6]));
    }
}
