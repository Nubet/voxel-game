package com.norbertfila.world;

import com.norbertfila.meshing.BlockNeighborhood;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    public Chunk removeChunk(ChunkPos position) {
        requestedChunks.remove(Objects.requireNonNull(position));
        return chunks.remove(position);
    }

    public int residentCount() {
        return chunks.size();
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

    public List<ChunkPos> residentPositions() {
        return chunks.keySet().stream()
                .sorted(Comparator.comparingInt(ChunkPos::y)
                        .thenComparingInt(ChunkPos::z)
                        .thenComparingInt(ChunkPos::x))
                .toList();
    }

    public BlockNeighborhood blockNeighborhood(ChunkPos position) {
        Objects.requireNonNull(position);
        ChunkSnapshot center = snapshot(position);
        if (center == null) {
            throw new IllegalArgumentException("Center chunk is not resident: " + position);
        }
        return new BlockNeighborhood(
                center,
                snapshot(new ChunkPos(position.x() + 1, position.y(), position.z())),
                snapshot(new ChunkPos(position.x() - 1, position.y(), position.z())),
                snapshot(new ChunkPos(position.x(), position.y() + 1, position.z())),
                snapshot(new ChunkPos(position.x(), position.y() - 1, position.z())),
                snapshot(new ChunkPos(position.x(), position.y(), position.z() + 1)),
                snapshot(new ChunkPos(position.x(), position.y(), position.z() - 1)));
    }

    private ChunkSnapshot snapshot(ChunkPos position) {
        Chunk chunk = chunks.get(position);
        return chunk == null ? null : chunk.snapshot();
    }
}
