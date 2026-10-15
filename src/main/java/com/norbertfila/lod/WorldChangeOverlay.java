package com.norbertfila.lod;

import com.norbertfila.persistence.ChunkDelta;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.WaterState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Authoritative block changes on top of generated terrain, kept per chunk in local-index arrays. Each chunk caches
 * its immutable {@link ChunkDelta} until it changes again, so a save only re-encodes chunks edited since the last one.
 */
public final class WorldChangeOverlay {
    private static final Comparator<FarTilePos> ORDER =
            Comparator.comparingInt(FarTilePos::z).thenComparingInt(FarTilePos::x);
    private static final Comparator<ChunkPos> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z);
    private final Map<ChunkPos, ChunkChanges> changes = new HashMap<>();
    private final Set<FarTilePos> dirtyTiles = new HashSet<>();
    // Per chunk column (x, z): the edit stamp of its latest change and the chunk heights holding changes.
    private final Map<Long, EditedColumn> columns = new HashMap<>();
    private long editStamp;
    private int size;

    public OptionalInt get(BlockPos position) {
        int packed = packed(position);
        return packed < 0 ? OptionalInt.empty() : OptionalInt.of(packed & 0xff);
    }

    public OptionalInt getWaterState(BlockPos position) {
        int packed = packed(position);
        return packed < 0 ? OptionalInt.empty() : OptionalInt.of(waterState(packed));
    }

    private int packed(BlockPos position) {
        Objects.requireNonNull(position);
        ChunkChanges chunkChanges = changes.get(Coordinates.worldToChunk(position));
        return chunkChanges == null ? -1 : chunkChanges.values[localIndex(position)];
    }

    public OptionalInt set(BlockPos position, byte value, int tileSize) {
        return set(position, value, WaterState.defaultFor(value), tileSize);
    }

    public OptionalInt set(BlockPos position, byte value, byte waterState, int tileSize) {
        Objects.requireNonNull(position);
        WaterState.validate(value, waterState);
        FarTilePos dirtyTile = tile(position, tileSize);
        short packed = (short) (BlockId.toUnsignedInt(value) | (value == BlockId.WATER ? waterState << 8 : 0));
        ChunkChanges chunkChanges = changes.computeIfAbsent(Coordinates.worldToChunk(position), ChunkChanges::new);
        int previous = chunkChanges.set(localIndex(position), packed);
        if (previous < 0) size++;
        if (previous != packed) stamp(chunkChanges.position, true);
        dirtyTiles.add(dirtyTile);
        return previous < 0 ? OptionalInt.empty() : OptionalInt.of(previous & 0xff);
    }

    public OptionalInt remove(BlockPos position, int tileSize) {
        Objects.requireNonNull(position);
        FarTilePos dirtyTile = tile(position, tileSize);
        ChunkPos chunkPosition = Coordinates.worldToChunk(position);
        ChunkChanges chunkChanges = changes.get(chunkPosition);
        if (chunkChanges == null) return OptionalInt.empty();
        int previous = chunkChanges.set(localIndex(position), (short) -1);
        if (chunkChanges.count == 0) changes.remove(chunkPosition);
        if (previous < 0) return OptionalInt.empty();
        stamp(chunkPosition, chunkChanges.count > 0);
        size--;
        dirtyTiles.add(dirtyTile);
        return OptionalInt.of(previous & 0xff);
    }

    private void stamp(ChunkPos chunk, boolean holdsChanges) {
        EditedColumn column = columns.computeIfAbsent(columnKey(chunk.x(), chunk.z()), ignored -> new EditedColumn());
        column.stamp = ++editStamp;
        if (holdsChanges) column.chunkHeights.add(chunk.y());
        else column.chunkHeights.remove(chunk.y());
    }

    /**
     * Stamp of the latest change in the square of block columns {@code [minX, minX + size) x [minZ, minZ + size)},
     * or 0 when it was never edited. Stamps only grow, also when a change is removed, so a changed stamp means
     * terrain built from an earlier {@link #edits} snapshot of the area is stale.
     */
    public long areaRevision(int minX, int minZ, int size) {
        long revision = 0;
        int minChunkX = Math.floorDiv(minX, Chunk.SIZE), maxChunkX = Math.floorDiv(minX + size - 1, Chunk.SIZE);
        int minChunkZ = Math.floorDiv(minZ, Chunk.SIZE), maxChunkZ = Math.floorDiv(minZ + size - 1, Chunk.SIZE);
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                EditedColumn column = columns.get(columnKey(chunkX, chunkZ));
                if (column != null) revision = Math.max(revision, column.stamp);
            }
        return revision;
    }

    /** Immutable copy of the changes inside a square of block columns, for building far terrain off this thread. */
    public AreaEdits edits(int minX, int minZ, int size) {
        if (size < 1 || size > AreaEdits.MAX_SIZE) throw new IllegalArgumentException("Invalid edit area size");
        Map<Integer, List<Long>> byColumn = new HashMap<>();
        int minChunkX = Math.floorDiv(minX, Chunk.SIZE), maxChunkX = Math.floorDiv(minX + size - 1, Chunk.SIZE);
        int minChunkZ = Math.floorDiv(minZ, Chunk.SIZE), maxChunkZ = Math.floorDiv(minZ + size - 1, Chunk.SIZE);
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                EditedColumn column = columns.get(columnKey(chunkX, chunkZ));
                if (column == null) continue;
                for (int chunkY : column.chunkHeights) {
                    short[] values = changes.get(new ChunkPos(chunkX, chunkY, chunkZ)).values;
                    for (int local = 0; local < values.length; local++) {
                        if (values[local] < 0) continue;
                        int x = chunkX * Chunk.SIZE + (local & 15) - minX;
                        int z = chunkZ * Chunk.SIZE + (local >>> 4 & 15) - minZ;
                        if (x < 0 || z < 0 || x >= size || z >= size) continue;
                        int y = chunkY * Chunk.SIZE + (local >>> 8);
                        byColumn.computeIfAbsent(z * size + x, ignored -> new ArrayList<>())
                                .add(AreaEdits.entry(y, values[local]));
                    }
                }
            }
        return AreaEdits.of(minX, minZ, size, areaRevision(minX, minZ, size), byColumn);
    }

    private static long columnKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | Integer.toUnsignedLong(chunkZ);
    }

    public List<FarTilePos> dirtyTiles() {
        return dirtyTiles.stream().sorted(ORDER).toList();
    }

    public List<FarTilePos> drainDirtyTiles() {
        List<FarTilePos> result = dirtyTiles();
        dirtyTiles.clear();
        return result;
    }

    public int size() {
        return size;
    }

    /** Every change sorted by world x, y, z; allocates, so the save path uses {@link #deltas()} instead. */
    public List<Change> changes() {
        List<Change> result = new ArrayList<>(size);
        for (ChunkDelta delta : deltas())
            for (int index = 0; index < delta.size(); index++)
                result.add(new Change(delta.blockPosition(index), delta.value(index), delta.waterState(index)));
        result.sort(Comparator.comparing(
                Change::position,
                Comparator.comparingInt(BlockPos::x)
                        .thenComparingInt(BlockPos::y)
                        .thenComparingInt(BlockPos::z)));
        return List.copyOf(result);
    }

    /** Immutable per-chunk deltas ordered by chunk; unchanged chunks reuse the delta built for an earlier save. */
    public List<ChunkDelta> deltas() {
        List<ChunkPos> positions = new ArrayList<>(changes.keySet());
        positions.sort(CHUNK_ORDER);
        List<ChunkDelta> deltas = new ArrayList<>(positions.size());
        for (ChunkPos position : positions) deltas.add(changes.get(position).delta());
        return deltas;
    }

    public void applyTo(ChunkPos position, Chunk chunk) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(chunk);
        ChunkChanges chunkChanges = changes.get(position);
        if (chunkChanges == null) return;
        short[] values = chunkChanges.values;
        for (int local = 0; local < values.length; local++) {
            short value = values[local];
            if (value < 0) continue;
            chunk.set(local & 15, local >>> 8, local >>> 4 & 15, (byte) value, waterState(value));
        }
    }

    private static int localIndex(BlockPos position) {
        int x = Math.floorMod(position.x(), Chunk.SIZE), y = Math.floorMod(position.y(), Chunk.SIZE);
        int z = Math.floorMod(position.z(), Chunk.SIZE);
        return (y * Chunk.SIZE + z) * Chunk.SIZE + x;
    }

    private static FarTilePos tile(BlockPos position, int tileSize) {
        if (tileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        return new FarTilePos(Math.floorDiv(position.x(), tileSize), Math.floorDiv(position.z(), tileSize));
    }

    private static byte waterState(int packed) {
        return (byte) packed == BlockId.WATER ? (byte) (packed >>> 8) : WaterState.EMPTY;
    }

    private static final class EditedColumn {
        private final java.util.TreeSet<Integer> chunkHeights = new java.util.TreeSet<>();
        private long stamp;
    }

    /** Changes of one chunk; -1 marks a block without an override. */
    private static final class ChunkChanges {
        private final ChunkPos position;
        private final short[] values = new short[Chunk.BLOCK_BYTES];
        private int count;
        private ChunkDelta delta;

        ChunkChanges(ChunkPos position) {
            this.position = position;
            Arrays.fill(values, (short) -1);
        }

        int set(int local, short value) {
            short previous = values[local];
            if (previous == value) return previous;
            values[local] = value;
            if (previous < 0) count++;
            if (value < 0) count--;
            delta = null;
            return previous;
        }

        ChunkDelta delta() {
            if (delta == null) {
                int[] entries = new int[count];
                int next = 0;
                for (int local = 0; local < values.length; local++) {
                    short value = values[local];
                    if (value >= 0) entries[next++] = ChunkDelta.entry(local, (byte) value, waterState(value));
                }
                delta = ChunkDelta.packed(position, entries);
            }
            return delta;
        }
    }

    public record FarTilePos(int x, int z) {}

    public record Change(BlockPos position, byte value, byte waterState) {
        public Change(BlockPos position, byte value) {
            this(position, value, WaterState.defaultFor(value));
        }

        public Change {
            Objects.requireNonNull(position);
            WaterState.validate(value, waterState);
        }
    }
}
