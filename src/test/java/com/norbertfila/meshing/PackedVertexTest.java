package com.norbertfila.meshing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class PackedVertexTest {
    @Test
    void roundTripsLocalPositionFaceBlockAndCorner() {
        PackedVertex.Decoded decoded = PackedVertex.decode(PackedVertex.pack(15, 14, 13, 5, BlockId.STONE, 3));

        assertEquals(15, decoded.x());
        assertEquals(14, decoded.y());
        assertEquals(13, decoded.z());
        assertEquals(5, decoded.face());
        assertEquals(BlockId.STONE, decoded.blockId());
        assertEquals(3, decoded.corner());
    }

    @Test
    void rejectsValuesOutsidePackedRanges() {
        assertThrows(IllegalArgumentException.class, () -> PackedVertex.pack(16, 0, 0, 0, BlockId.STONE, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedVertex.pack(0, 0, 0, 6, BlockId.STONE, 0));
    }
}
