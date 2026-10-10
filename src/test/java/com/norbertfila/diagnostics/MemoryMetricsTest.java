package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.ChunkStorage;
import org.junit.jupiter.api.Test;

class MemoryMetricsTest {
    @Test
    void measuresEstimatedWorldDataOnly() {
        ChunkStorage storage = new ChunkStorage(1, 3);
        storage.put(new ChunkPos(0, 0, 0), new Chunk());
        storage.put(new ChunkPos(1, 0, 0), new Chunk());

        MemoryMetrics metrics = MemoryMetrics.from(storage);

        assertEquals(2, metrics.residentChunks());
        assertEquals(Chunk.BLOCK_BYTES, metrics.bytesPerChunk());
        assertEquals(2L * Chunk.BLOCK_BYTES, metrics.residentBytes());
        assertTrue(metrics.estimated());
    }
}
