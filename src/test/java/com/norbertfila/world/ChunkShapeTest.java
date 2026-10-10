package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChunkShapeTest {
    @Test
    void comparesShapesOverTheSameWorldVolume() {
        assertEquals(64L, ChunkShape.SIZE_16.chunksForVolume(64));
        assertEquals(8L, ChunkShape.SIZE_32.chunksForVolume(64));
        assertEquals(64L * 64 * 64, ChunkShape.SIZE_16.blockCount() * 64);
    }

    @Test
    void rejectsNonPowerOfTwoEdges() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkShape(24));
    }
}
