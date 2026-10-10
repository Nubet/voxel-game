package com.norbertfila.world;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChunkStorage {
    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();
    private final int softLimit;
    private final int hardLimit;

    public ChunkStorage() {
        this(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public ChunkStorage(int softLimit, int hardLimit) {
        if (softLimit < 0 || hardLimit < softLimit) throw new IllegalArgumentException("Invalid chunk limits");
        this.softLimit = softLimit;
        this.hardLimit = hardLimit;
    }

    public void put(ChunkPos position, Chunk chunk) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(chunk);
        if (!chunks.containsKey(position) && chunks.size() >= hardLimit) {
            throw new IllegalStateException("Chunk storage hard limit reached: " + hardLimit);
        }
        chunks.put(position, chunk);
    }

    public Chunk get(ChunkPos position) {
        return chunks.get(position);
    }

    public Chunk remove(ChunkPos position) {
        return chunks.remove(Objects.requireNonNull(position));
    }

    public boolean contains(ChunkPos position) {
        return chunks.containsKey(position);
    }

    public int size() {
        return chunks.size();
    }

    public long estimatedBytes() {
        return (long) size() * Chunk.BLOCK_BYTES;
    }

    public int softLimit() {
        return softLimit;
    }

    public int hardLimit() {
        return hardLimit;
    }

    public boolean softLimitExceeded() {
        return size() > softLimit;
    }

    public List<ChunkPos> positions() {
        return chunks.keySet().stream()
                .sorted(Comparator.comparingInt(ChunkPos::y)
                        .thenComparingInt(ChunkPos::z)
                        .thenComparingInt(ChunkPos::x))
                .toList();
    }
}
