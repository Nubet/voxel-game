package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class QuadCodecTest {
    @Test
    void solidFieldsUseTheirFullRanges() {
        int position = QuadCodec.solidPosition(16, 0, 16, QuadCodec.CROSS_PLANE_B, 16, 16, true);
        assertEquals(16, position & 31);
        assertEquals(16, position >>> 10 & 31);
        assertEquals(QuadCodec.CROSS_PLANE_B, position >>> 15 & 7);
        assertEquals(15, position >>> 18 & 15);
        assertEquals(15, position >>> 22 & 15);
        assertEquals(1, position >>> 26 & 1);
        int material = QuadCodec.solidMaterial(0xFFFF, 255);
        assertEquals(0xFFFF, material & 0xFFFF);
        assertEquals(255, material >>> 16);
    }

    @Test
    void rejectsValuesOutsideTheirBits() {
        assertThrows(IllegalArgumentException.class, () -> QuadCodec.solidPosition(17, 0, 0, 0, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> QuadCodec.solidPosition(0, -1, 0, 0, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> QuadCodec.solidPosition(0, 0, 0, 0, 17, 1, false));
        assertThrows(IllegalArgumentException.class, () -> QuadCodec.solidPosition(0, 0, 0, 0, 1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> QuadCodec.solidMaterial(0x10000, 0));
        assertThrows(IllegalArgumentException.class, () -> QuadCodec.waterCell(16, 0, 0, 0, false, 0));
        assertThrows(IllegalArgumentException.class, () -> QuadCodec.heights(1.01f, 0));
        assertThrows(IllegalArgumentException.class, () -> QuadCodec.flow(0, -1.01f));
    }

    @Test
    void waterExtremesSurviveQuantizationExactly() {
        int heights = QuadCodec.heights(0, 1);
        assertEquals(0, QuadCodec.height(heights, false));
        assertEquals(1, QuadCodec.height(heights, true));
        int flow = QuadCodec.flow(-1, 1);
        assertEquals(-1, QuadCodec.flow(flow, false));
        assertEquals(1, QuadCodec.flow(flow, true));
        assertEquals(8 / 9f, QuadCodec.height(QuadCodec.heights(8 / 9f, 0), false), 1e-5f);
    }

    @Test
    void decodedGeometryMatchesTheReferenceFarFromTheOriginAndAtNegativeChunks() {
        for (ChunkPos position : new ChunkPos[] {new ChunkPos(-1, -2, -3), new ChunkPos(-50_000, 3, 70_000)}) {
            World world = new World();
            Chunk chunk = new Chunk();
            chunk.set(0, 0, 0, BlockId.STONE);
            chunk.set(15, 15, 15, BlockId.GRASS);
            chunk.set(4, 5, 6, BlockId.POPPY);
            world.putChunk(position, chunk);
            var neighborhood = world.blockNeighborhood(position);
            int x = position.x(), y = position.y(), z = position.z();
            assertArrayEquals(
                    new ReferenceGreedyMesher()
                            .meshTextured(neighborhood.center(), neighborhood, x, y, z)
                            .vertices(),
                    new GreedyMesher()
                            .meshTextured(neighborhood.center(), neighborhood, x, y, z)
                            .vertices());
        }
    }
}
