package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChunkTest {
    @Test
    void readsAndWritesAllCornerCoordinates() {
        Chunk chunk = new Chunk();

        chunk.set(0, 0, 0, BlockId.GRASS);
        chunk.set(Chunk.SIZE - 1, Chunk.SIZE - 1, Chunk.SIZE - 1, BlockId.STONE);

        assertEquals(BlockId.GRASS, chunk.get(0, 0, 0));
        assertEquals(BlockId.STONE, chunk.get(Chunk.SIZE - 1, Chunk.SIZE - 1, Chunk.SIZE - 1));
    }

    @Test
    void cellsAreIndependent() {
        Chunk chunk = new Chunk();

        chunk.set(1, 2, 3, BlockId.DIRT);
        chunk.set(1, 2, 4, BlockId.GRASS);

        assertEquals(BlockId.DIRT, chunk.get(1, 2, 3));
        assertEquals(BlockId.GRASS, chunk.get(1, 2, 4));
        assertEquals(BlockId.AIR, chunk.get(1, 2, 2));
    }

    @Test
    void rejectsCoordinatesOutsideLocalRange() {
        Chunk chunk = new Chunk();

        assertThrows(IndexOutOfBoundsException.class, () -> chunk.get(-1, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> chunk.get(Chunk.SIZE, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> chunk.set(0, -1, 0, BlockId.AIR));
        assertThrows(IndexOutOfBoundsException.class, () -> chunk.set(0, 0, Chunk.SIZE, BlockId.AIR));
    }

    @Test
    void usesUnsignedBlockIds() {
        assertEquals(255, BlockId.toUnsignedInt((byte) 0xff));
        assertFalse(BlockId.isValid((byte) 0xff));
        assertThrows(IllegalArgumentException.class, () -> new Chunk().set(0, 0, 0, (byte) 0xff));
    }
}
