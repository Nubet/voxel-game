package com.norbertfila.world;

import com.norbertfila.lod.WorldChangeOverlay;
import com.norbertfila.meshing.BlockNeighborhood;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class World {
    private final ChunkStorage storage;
    private final WorldChangeOverlay changes;
    private final Set<ChunkPos> requestedChunks = new HashSet<>();

    public World() {
        this(new ChunkStorage(), new WorldChangeOverlay());
    }

    public World(ChunkStorage storage) {
        this(storage, new WorldChangeOverlay());
    }

    public World(ChunkStorage storage, WorldChangeOverlay changes) {
        this.storage = Objects.requireNonNull(storage);
        this.changes = Objects.requireNonNull(changes);
    }

    public void putChunk(ChunkPos position, Chunk chunk) {
        storage.put(Objects.requireNonNull(position), Objects.requireNonNull(chunk));
        requestedChunks.remove(position);
    }

    public Chunk getChunk(ChunkPos position) {
        return storage.get(position);
    }

    public boolean hasChunk(ChunkPos position) {
        return storage.contains(position);
    }

    public Chunk removeChunk(ChunkPos position) {
        requestedChunks.remove(Objects.requireNonNull(position));
        return storage.remove(position);
    }

    public int residentCount() {
        return storage.size();
    }

    public OptionalInt getBlock(BlockPos worldPosition) {
        Objects.requireNonNull(worldPosition);
        OptionalInt changed = changes.get(worldPosition);
        if (changed.isPresent()) return changed;
        Chunk chunk = storage.get(Coordinates.worldToChunk(worldPosition));
        if (chunk == null) return OptionalInt.empty();
        BlockPos local = Coordinates.worldToLocal(worldPosition);
        return OptionalInt.of(BlockId.toUnsignedInt(chunk.get(local.x(), local.y(), local.z())));
    }

    public boolean isSolid(BlockPos worldPosition) {
        return getBlock(Objects.requireNonNull(worldPosition)).orElse(BlockId.AIR) != BlockId.AIR;
    }

    public OptionalInt setBlock(BlockPos worldPosition, byte value, int farTileSize) {
        Objects.requireNonNull(worldPosition);
        OptionalInt previous = getBlock(worldPosition);
        Chunk chunk = storage.get(Coordinates.worldToChunk(worldPosition));
        if (chunk != null) {
            BlockPos local = Coordinates.worldToLocal(worldPosition);
            chunk.set(local.x(), local.y(), local.z(), value);
        }
        changes.set(worldPosition, value, farTileSize);
        return previous;
    }

    public OptionalInt removeBlockOverride(BlockPos worldPosition, int farTileSize) {
        return changes.remove(Objects.requireNonNull(worldPosition), farTileSize);
    }

    public WorldChangeOverlay changes() {
        return changes;
    }

    public void requestChunk(ChunkPos position) {
        if (!storage.contains(Objects.requireNonNull(position))) {
            requestedChunks.add(position);
        }
    }

    public boolean isChunkRequested(ChunkPos position) {
        return requestedChunks.contains(position);
    }

    public List<ChunkPos> residentPositions() {
        return storage.positions();
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
        Chunk chunk = storage.get(position);
        return chunk == null ? null : chunk.snapshot();
    }
}
