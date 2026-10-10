package com.norbertfila.lod;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
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
    private final Map<BlockPos, Byte> changes = new HashMap<>();
    private final Set<FarTilePos> dirtyTiles = new HashSet<>();

    public OptionalInt get(BlockPos position) {
        Byte value = changes.get(Objects.requireNonNull(position));
        return value == null ? OptionalInt.empty() : OptionalInt.of(BlockId.toUnsignedInt(value));
    }

    public OptionalInt set(BlockPos position, byte value, int tileSize) {
        Objects.requireNonNull(position);
        validate(value);
        Byte previous = changes.put(position, value);
        dirtyTiles.add(tile(position, tileSize));
        return previous == null ? OptionalInt.empty() : OptionalInt.of(BlockId.toUnsignedInt(previous));
    }

    public OptionalInt remove(BlockPos position, int tileSize) {
        Objects.requireNonNull(position);
        Byte previous = changes.remove(position);
        if (previous != null) dirtyTiles.add(tile(position, tileSize));
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
        return changes.size();
    }

    public List<Change> changes() {
        return changes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(BlockPos::x)
                        .thenComparingInt(BlockPos::y)
                        .thenComparingInt(BlockPos::z)))
                .map(entry -> new Change(entry.getKey(), entry.getValue()))
                .toList();
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
