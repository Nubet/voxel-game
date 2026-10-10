package com.norbertfila.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

public final class WorldEdit {
    private static final Comparator<ChunkPos> ORDER =
            Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z);

    private final World world;
    private final int farTileSize;

    public WorldEdit(World world, int farTileSize) {
        this.world = Objects.requireNonNull(world);
        if (farTileSize < 1) throw new IllegalArgumentException("Far tile size must be positive");
        this.farTileSize = farTileSize;
    }

    public Result setBlock(BlockPos position, byte value) {
        Objects.requireNonNull(position);
        if (!BlockId.isValid(value))
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
        OptionalInt previous = world.setBlock(position, value, farTileSize);
        boolean changed = previous.isEmpty() || previous.getAsInt() != BlockId.toUnsignedInt(value);
        if (!changed) return new Result(previous, false, List.of(), revision(position));

        ChunkPos chunk = Coordinates.worldToChunk(position);
        Set<ChunkPos> dirty = new LinkedHashSet<>();
        dirty.add(chunk);
        BlockPos local = Coordinates.worldToLocal(position);
        if (local.x() == 0) dirty.add(new ChunkPos(chunk.x() - 1, chunk.y(), chunk.z()));
        if (local.x() == Chunk.SIZE - 1) dirty.add(new ChunkPos(chunk.x() + 1, chunk.y(), chunk.z()));
        if (local.y() == 0) dirty.add(new ChunkPos(chunk.x(), chunk.y() - 1, chunk.z()));
        if (local.y() == Chunk.SIZE - 1) dirty.add(new ChunkPos(chunk.x(), chunk.y() + 1, chunk.z()));
        if (local.z() == 0) dirty.add(new ChunkPos(chunk.x(), chunk.y(), chunk.z() - 1));
        if (local.z() == Chunk.SIZE - 1) dirty.add(new ChunkPos(chunk.x(), chunk.y(), chunk.z() + 1));
        List<ChunkPos> ordered = new ArrayList<>(dirty);
        ordered.sort(ORDER);
        return new Result(previous, true, List.copyOf(ordered), revision(position));
    }

    private long revision(BlockPos position) {
        Chunk chunk = world.getChunk(Coordinates.worldToChunk(position));
        return chunk == null ? -1 : chunk.revision();
    }

    public record Result(OptionalInt previous, boolean changed, List<ChunkPos> dirtyChunks, long chunkRevision) {
        public Result {
            Objects.requireNonNull(previous);
            dirtyChunks = List.copyOf(dirtyChunks);
        }
    }
}
