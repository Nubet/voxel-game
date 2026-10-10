package com.norbertfila.lod;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
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
    private final Map<ChunkPos, Map<BlockPos, Byte>> changes = new HashMap<>();
    private final Set<FarTilePos> dirtyTiles = new HashSet<>();

    public OptionalInt get(BlockPos position) {
        Objects.requireNonNull(position);
        Map<BlockPos, Byte> chunkChanges = changes.get(Coordinates.worldToChunk(position));
        Byte value = chunkChanges == null ? null : chunkChanges.get(position);
        return value == null ? OptionalInt.empty() : OptionalInt.of(BlockId.toUnsignedInt(value));
    }

    public OptionalInt set(BlockPos position, byte value, int tileSize) {
        Objects.requireNonNull(position);
        validate(value);
        FarTilePos dirtyTile = tile(position, tileSize);
        Byte previous = changes.computeIfAbsent(Coordinates.worldToChunk(position), ignored -> new HashMap<>())
                .put(position, value);
        dirtyTiles.add(dirtyTile);
        return previous == null ? OptionalInt.empty() : OptionalInt.of(BlockId.toUnsignedInt(previous));
    }

    public OptionalInt remove(BlockPos position, int tileSize) {
        Objects.requireNonNull(position);
        FarTilePos dirtyTile = tile(position, tileSize);
        ChunkPos chunkPosition = Coordinates.worldToChunk(position);
        Map<BlockPos, Byte> chunkChanges = changes.get(chunkPosition);
        if (chunkChanges == null) return OptionalInt.empty();
        Byte previous = chunkChanges.remove(position);
        if (chunkChanges.isEmpty()) changes.remove(chunkPosition);
        if (previous != null) dirtyTiles.add(dirtyTile);
        return previous == null ? OptionalInt.empty() : OptionalInt.of(BlockId.toUnsignedInt(previous));
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
                .map(entry -> new Change(entry.getKey(), entry.getValue()))
                .toList();
    }

    public void applyTo(ChunkPos position, Chunk chunk) {
        Objects.requireNonNull(position);
        Objects.requireNonNull(chunk);
        Map<BlockPos, Byte> chunkChanges = changes.get(position);
        if (chunkChanges == null) return;
        for (Map.Entry<BlockPos, Byte> change : chunkChanges.entrySet()) {
            BlockPos local = Coordinates.worldToLocal(change.getKey());
            chunk.set(local.x(), local.y(), local.z(), change.getValue());
        }
    }

    private static FarTilePos tile(BlockPos position, int tileSize) {
        if (tileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        return new FarTilePos(Math.floorDiv(position.x(), tileSize), Math.floorDiv(position.z(), tileSize));
    }

    private static void validate(byte value) {
        if (!BlockId.isValid(value))
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
    }

    public record FarTilePos(int x, int z) {}

    public record Change(BlockPos position, byte value) {}
}
