package com.norbertfila.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class World {
    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();
    private final Set<ChunkPos> requestedChunks = new HashSet<>();

    public void putChunk(ChunkPos position, Chunk chunk) {
        chunks.put(Objects.requireNonNull(position), Objects.requireNonNull(chunk));
        requestedChunks.remove(position);
    }

    public Chunk getChunk(ChunkPos position) {
        return chunks.get(position);
    }

    public boolean hasChunk(ChunkPos position) {
        return chunks.containsKey(position);
    }

    public OptionalInt getBlock(BlockPos worldPosition) {
        Objects.requireNonNull(worldPosition);
        Chunk chunk = chunks.get(Coordinates.worldToChunk(worldPosition));
        if (chunk == null) return OptionalInt.empty();
        BlockPos local = Coordinates.worldToLocal(worldPosition);
        return OptionalInt.of(BlockId.toUnsignedInt(chunk.get(local.x(), local.y(), local.z())));
    }

    public void requestChunk(ChunkPos position) {
        if (!chunks.containsKey(Objects.requireNonNull(position))) {
            requestedChunks.add(position);
        }
    }

    public boolean isChunkRequested(ChunkPos position) {
        return requestedChunks.contains(position);
    }
}
