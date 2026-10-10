package com.norbertfila.diagnostics;

import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkStorage;

public record MemoryMetrics(
        int residentChunks,
        long bytesPerChunk,
        long residentBytes,
        int softLimit,
        int hardLimit,
        boolean softLimitExceeded,
        boolean estimated) {
    public static MemoryMetrics from(ChunkStorage storage) {
        return new MemoryMetrics(
                storage.size(),
                Chunk.BLOCK_BYTES,
                storage.estimatedBytes(),
                storage.softLimit(),
                storage.hardLimit(),
                storage.softLimitExceeded(),
                true);
    }
}
