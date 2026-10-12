package com.norbertfila.lod;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.WaterState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class WorldChangeOverlay {
    private static final Comparator<FarTilePos> ORDER =
            Comparator.comparingInt(FarTilePos::z).thenComparingInt(FarTilePos::x);
    private final Map<ChunkPos, Map<BlockPos, Short>> changes = new HashMap<>();
    private final Set<FarTilePos> dirtyTiles = new HashSet<>();

    public OptionalInt get(BlockPos position) {
        Objects.requireNonNull(position);
        Map<BlockPos, Short> chunkChanges = changes.get(Coordinates.worldToChunk(position));
        Short value = chunkChanges == null ? null : chunkChanges.get(position);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value & 0xff);
    }

    public OptionalInt getWaterState(BlockPos position) {
        Objects.requireNonNull(position);
        Map<BlockPos, Short> chunkChanges = changes.get(Coordinates.worldToChunk(position));
        Short value = chunkChanges == null ? null : chunkChanges.get(position);
        return value == null ? OptionalInt.empty() : OptionalInt.of(waterState(value));
    }

    public OptionalInt set(BlockPos position, byte value, int tileSize) {
        return set(position, value, WaterState.defaultFor(value), tileSize);
    }

    public OptionalInt set(BlockPos position, byte value, byte waterState, int tileSize) {
        Objects.requireNonNull(position);
        WaterState.validate(value, waterState);
        FarTilePos dirtyTile = tile(position, tileSize);
        short packed = (short) (BlockId.toUnsignedInt(value) | (value == BlockId.WATER ? waterState << 8 : 0));
        Short previous = changes.computeIfAbsent(Coordinates.worldToChunk(position), ignored -> new HashMap<>())
                .put(position, packed);
        dirtyTiles.add(dirtyTile);
        return previous == null ? OptionalInt.empty() : OptionalInt.of(previous & 0xff);
    }

    public OptionalInt remove(BlockPos position, int tileSize) {
        Objects.requireNonNull(position);
        FarTilePos dirtyTile = tile(position, tileSize);
        ChunkPos chunkPosition = Coordinates.worldToChunk(position);
        Map<BlockPos, Short> chunkChanges = changes.get(chunkPosition);
        if (chunkChanges == null) return OptionalInt.empty();
        Short previous = chunkChanges.remove(position);
        if (chunkChanges.isEmpty()) changes.remove(chunkPosition);
        if (previous != null) dirtyTiles.add(dirtyTile);
        return previous == null ? OptionalInt.empty() : OptionalInt.of(previous & 0xff);
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
        return changes.values().stream().mapToInt(Map::size).sum();
    }

    public List<Change> changes() {
        return changes.values().stream()
                .flatMap(chunkChanges -> chunkChanges.entrySet().stream())
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(BlockPos::x)
                        .thenComparingInt(BlockPos::y)
                        .thenComparingInt(BlockPos::z)))
                .map(entry ->
                        new Change(entry.getKey(), (byte) entry.getValue().shortValue(), waterState(entry.getValue())))
                .toList();
    }

    public void applyTo(ChunkPos position, Chunk chunk) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(chunk);
        Map<BlockPos, Short> chunkChanges = changes.get(position);
        if (chunkChanges == null) return;
        for (Map.Entry<BlockPos, Short> change : chunkChanges.entrySet()) {
            BlockPos local = Coordinates.worldToLocal(change.getKey());
            short value = change.getValue();
            chunk.set(local.x(), local.y(), local.z(), (byte) value, waterState(value));
        }
    }

    private static FarTilePos tile(BlockPos position, int tileSize) {
        if (tileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        return new FarTilePos(Math.floorDiv(position.x(), tileSize), Math.floorDiv(position.z(), tileSize));
    }

    private static byte waterState(short packed) {
        return (byte) packed == BlockId.WATER ? (byte) (packed >>> 8) : WaterState.EMPTY;
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
