package com.norbertfila.world;

import com.norbertfila.lod.WorldChangeOverlay;
import com.norbertfila.meshing.BlockNeighborhood;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/** Mutable state confined to its creating thread; workers consume snapshots instead. */
public final class World {
    private final Thread owner = Thread.currentThread();

    public void assertOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("World accessed outside its owner thread");
    }

    private final ChunkStorage storage;
    private final WorldChangeOverlay changes;
    private final Set<ChunkPos> requestedChunks = new HashSet<>();
    private Consumer<BlockMutation> blockChangeListener;

    public void setBlockChangeListener(Consumer<BlockMutation> listener) {
        assertOwner();
        blockChangeListener = Objects.requireNonNull(listener);
    }
    // Chunks whose cached meshing snapshot holds block arrays, least recently used first. Snapshots are a cache:
    // dropping one only costs a copy the next time a mesh task needs that chunk.
    private static final int DEFAULT_SNAPSHOT_LIMIT = 4096;
    private final int snapshotLimit = DEFAULT_SNAPSHOT_LIMIT;
    private final java.util.LinkedHashMap<ChunkPos, Chunk> snapshotHolders =
            new java.util.LinkedHashMap<>(64, .75f, true);

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
        assertOwner();
        changes.applyTo(position, chunk);
        snapshotHolders.remove(position);
        storage.put(position, chunk);
        requestedChunks.remove(position);
        invalidateNeighbors(position);
    }

    public Chunk getChunk(ChunkPos position) {
        assertOwner();
        return storage.get(position);
    }

    public boolean hasChunk(ChunkPos position) {
        assertOwner();
        return storage.contains(position);
    }

    public Chunk removeChunk(ChunkPos position) {
        assertOwner();
        requestedChunks.remove(Objects.requireNonNull(position));
        snapshotHolders.remove(position);
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
        assertOwner();
        return storage.size();
    }

    public OptionalInt getBlock(BlockPos worldPosition) {
        assertOwner();
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
        assertOwner();
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
        assertOwner();
        Objects.requireNonNull(worldPosition);
        WaterState.validate(value, waterState);
        if (farTileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        if (!WorldBounds.containsY(worldPosition.y()))
            throw new IllegalArgumentException("Block Y is outside world bounds");
        OptionalInt previous = getBlock(worldPosition);
        OptionalInt previousWater = getWaterState(worldPosition);
        if (previous.isPresent()
                && previous.getAsInt() == BlockId.toUnsignedInt(value)
                && previousWater.orElseThrow() == waterState) return previous;
        Chunk chunk = storage.get(Coordinates.worldToChunk(worldPosition));
        if (chunk != null) {
            BlockPos local = Coordinates.worldToLocal(worldPosition);
            chunk.set(local.x(), local.y(), local.z(), value, waterState);
        }
        changes.set(worldPosition, value, waterState, farTileSize);
        if (blockChangeListener != null)
            blockChangeListener.accept(new BlockMutation(worldPosition, previous, previousWater, value, waterState));
        return previous;
    }

    public OptionalInt removeBlockOverride(BlockPos worldPosition, int farTileSize) {
        assertOwner();
        return changes.remove(Objects.requireNonNull(worldPosition), farTileSize);
    }

    public WorldChangeOverlay changes() {
        assertOwner();
        return changes;
    }

    public void requestChunk(ChunkPos position) {
        assertOwner();
        if (!storage.contains(Objects.requireNonNull(position))) {
            requestedChunks.add(position);
        }
    }

    public boolean isChunkRequested(ChunkPos position) {
        assertOwner();
        return requestedChunks.contains(position);
    }

    public List<ChunkPos> residentPositions() {
        assertOwner();
        return storage.positions();
    }

    /** Snapshots of the chunk and its resident neighbors; snapshots are shared until a chunk's content changes. */
    public BlockNeighborhood blockNeighborhood(ChunkPos position) {
        assertOwner();
        Objects.requireNonNull(position);
        if (!storage.contains(position))
            throw new IllegalArgumentException("Center chunk is not resident: " + position);
        ChunkSnapshot[] chunks = new ChunkSnapshot[27];
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    long x = (long) position.x() + dx, y = (long) position.y() + dy, z = (long) position.z() + dz;
                    if (x < Integer.MIN_VALUE
                            || x > Integer.MAX_VALUE
                            || y < Integer.MIN_VALUE
                            || y > Integer.MAX_VALUE
                            || z < Integer.MIN_VALUE
                            || z > Integer.MAX_VALUE) continue;
                    chunks[BlockNeighborhood.slot(dx, dy, dz)] = snapshot(new ChunkPos((int) x, (int) y, (int) z));
                }
        return new BlockNeighborhood(chunks);
    }

    private ChunkSnapshot snapshot(ChunkPos position) {
        Chunk chunk = storage.get(position);
        if (chunk == null) return null;
        ChunkSnapshot snapshot = chunk.snapshot();
        if (!chunk.holdsSnapshotArrays()) snapshotHolders.remove(position);
        else {
            snapshotHolders.put(position, chunk);
            if (snapshotHolders.size() > snapshotLimit) {
                var eldest = snapshotHolders.entrySet().iterator();
                eldest.next().getValue().releaseSnapshot();
                eldest.remove();
            }
        }
        return snapshot;
    }

    /** Estimated bytes of resident block data, water states and cached snapshots. */
    public long estimatedBytes() {
        assertOwner();
        long snapshots = 0;
        for (Chunk chunk : snapshotHolders.values()) snapshots += chunk.snapshotBytes();
        return storage.estimatedBytes() + snapshots;
    }

    public int cachedSnapshots() {
        assertOwner();
        return snapshotHolders.size();
    }
}
