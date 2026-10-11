package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class PackedVertexTest {
    @Test
    void roundTripsLocalPositionFaceBlockAndCorner() {
        int packed = PackedVertex.pack(15, 14, 13, 5, BlockId.STONE, 3, BlockRegistry.STONE_TEXTURE);
        PackedVertex.Decoded decoded = PackedVertex.decode(packed);

        assertEquals(15, decoded.x());
        assertEquals(14, decoded.y());
        assertEquals(13, decoded.z());
        assertEquals(5, decoded.face());
        assertEquals(BlockId.STONE, decoded.blockId());
        assertEquals(3, decoded.corner());
        assertEquals(BlockRegistry.STONE_TEXTURE, PackedVertex.decodeTextureLayer(packed));
    }

    @Test
    void rejectsValuesOutsidePackedRanges() {
        assertThrows(IllegalArgumentException.class, () -> PackedVertex.pack(16, 0, 0, 0, BlockId.STONE, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedVertex.pack(0, 0, 0, 6, BlockId.STONE, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedVertex.pack(0, 0, 0, 0, BlockId.STONE, 0, 128));
    }
}
