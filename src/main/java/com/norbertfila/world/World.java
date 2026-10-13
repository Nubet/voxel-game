package com.norbertfila.world;

import com.norbertfila.lod.WorldChangeOverlay;
import com.norbertfila.meshing.BlockNeighborhood;
import java.util.HashMap;
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
        changes.applyTo(position, chunk);
        storage.put(position, chunk);
        requestedChunks.remove(position);
        invalidateNeighbors(position);
    }

    public Chunk getChunk(ChunkPos position) {
        return storage.get(position);
    }

    public boolean hasChunk(ChunkPos position) {
        return storage.contains(position);
    }

    public Chunk removeChunk(ChunkPos position) {
        requestedChunks.remove(Objects.requireNonNull(position));
        Chunk removed = storage.remove(position);
        if (removed != null) invalidateNeighbors(position);
        return removed;
    }

    private void invalidateNeighbors(ChunkPos position) {
        for (ChunkPos neighborPosition : meshNeighbors(position)) {
            Chunk neighbor = storage.get(neighborPosition);
            if (neighbor != null) neighbor.invalidateMesh();
        }
    }

    public static List<ChunkPos> meshNeighbors(ChunkPos position) {
        var neighbors = new java.util.ArrayList<ChunkPos>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    long x = (long) position.x() + dx, y = (long) position.y() + dy, z = (long) position.z() + dz;
                    if (x < Integer.MIN_VALUE
                            || x > Integer.MAX_VALUE
                            || y < Integer.MIN_VALUE
                            || y > Integer.MAX_VALUE
                            || z < Integer.MIN_VALUE
                            || z > Integer.MAX_VALUE) continue;
                    neighbors.add(new ChunkPos((int) x, (int) y, (int) z));
                }
        return List.copyOf(neighbors);
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
        OptionalInt block = getBlock(Objects.requireNonNull(worldPosition));
        return block.isPresent() && BlockRegistry.DEFAULT.isCollidable((byte) block.getAsInt());
    }

    public OptionalInt getWaterState(BlockPos worldPosition) {
        Objects.requireNonNull(worldPosition);
        OptionalInt changed = changes.getWaterState(worldPosition);
        if (changed.isPresent()) return changed;
        Chunk chunk = storage.get(Coordinates.worldToChunk(worldPosition));
        if (chunk == null) return OptionalInt.empty();
        BlockPos local = Coordinates.worldToLocal(worldPosition);
        return OptionalInt.of(chunk.waterState(local.x(), local.y(), local.z()));
    }

    public OptionalInt setBlock(BlockPos worldPosition, byte value, int farTileSize) {
        return setBlock(worldPosition, value, WaterState.defaultFor(value), farTileSize);
    }

    public OptionalInt setBlock(BlockPos worldPosition, byte value, byte waterState, int farTileSize) {
        Objects.requireNonNull(worldPosition);
        WaterState.validate(value, waterState);
        if (farTileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        OptionalInt previous = getBlock(worldPosition);
        if (previous.isPresent()
                && previous.getAsInt() == BlockId.toUnsignedInt(value)
                && getWaterState(worldPosition).orElseThrow() == waterState) return previous;
        Chunk chunk = storage.get(Coordinates.worldToChunk(worldPosition));
        if (chunk != null) {
            BlockPos local = Coordinates.worldToLocal(worldPosition);
            chunk.set(local.x(), local.y(), local.z(), value, waterState);
        }
        changes.set(worldPosition, value, waterState, farTileSize);
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
        var halo = new HashMap<ChunkPos, ChunkSnapshot>();
        for (ChunkPos neighbor : meshNeighbors(position)) {
            var data = snapshot(neighbor);
            if (data != null)
                halo.put(
                        new ChunkPos(
                                neighbor.x() - position.x(), neighbor.y() - position.y(), neighbor.z() - position.z()),
                        data);
        }
        return new BlockNeighborhood(center, halo);
    }

    private ChunkSnapshot snapshot(ChunkPos position) {
        Chunk chunk = storage.get(position);
        return chunk == null ? null : chunk.snapshot();
    }
}
