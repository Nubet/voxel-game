package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkStorageTest {
    @Test
    void reportsEstimatedResidentBytesAndSoftLimit() {
        ChunkStorage storage = new ChunkStorage(1, 2);
        storage.put(new ChunkPos(0, 0, 0), new Chunk());
        storage.put(new ChunkPos(1, 0, 0), new Chunk());

        assertEquals(2, storage.size());
        assertEquals(2L * Chunk.BLOCK_BYTES, storage.estimatedBytes());
        assertTrue(storage.softLimitExceeded());
    }

    @Test
    void hardLimitRejectsNewResidentChunk() {
        ChunkStorage storage = new ChunkStorage(1, 1);
        storage.put(new ChunkPos(0, 0, 0), new Chunk());

        assertThrows(IllegalStateException.class, () -> storage.put(new ChunkPos(1, 0, 0), new Chunk()));
    }

    @Test
    void removalReturnsStorageBelowLimit() {
        ChunkStorage storage = new ChunkStorage(1, 2);
        ChunkPos first = new ChunkPos(0, 0, 0);
        storage.put(first, new Chunk());
        storage.put(new ChunkPos(1, 0, 0), new Chunk());

        assertTrue(storage.remove(first) != null);
        assertEquals(Chunk.BLOCK_BYTES, storage.estimatedBytes());
        assertEquals(1, storage.size());
        assertTrue(!storage.softLimitExceeded());
    }
}
