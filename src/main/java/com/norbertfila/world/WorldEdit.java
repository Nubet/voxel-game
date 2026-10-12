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

    public BlockChangeResult setBlock(BlockPos position, byte value) {
        return setBlock(position, value, WaterState.defaultFor(value));
    }

    public BlockChangeResult setBlock(BlockPos position, byte value, byte waterState) {
        Objects.requireNonNull(position);
        WaterState.validate(value, waterState);
        ChunkPos chunk = Coordinates.worldToChunk(position);
        if (!world.hasChunk(chunk))
            return new BlockChangeResult(BlockChangeResult.Status.UNAVAILABLE, OptionalInt.empty(), List.of(), -1);
        OptionalInt previous = world.getBlock(position);
        if (previous.orElseThrow() == BlockId.toUnsignedInt(value)
                && world.getWaterState(position).orElseThrow() == waterState)
            return new BlockChangeResult(BlockChangeResult.Status.UNCHANGED, previous, List.of(), revision(position));
        world.setBlock(position, value, waterState, farTileSize);
        Set<ChunkPos> dirty = new LinkedHashSet<>();
        dirty.add(chunk);
        BlockPos local = Coordinates.worldToLocal(position);
        if (local.x() == 0) dirty.add(new ChunkPos(chunk.x() - 1, chunk.y(), chunk.z()));
        if (local.x() == Chunk.SIZE - 1) dirty.add(new ChunkPos(chunk.x() + 1, chunk.y(), chunk.z()));
        if (local.y() == 0) dirty.add(new ChunkPos(chunk.x(), chunk.y() - 1, chunk.z()));
        if (local.y() == Chunk.SIZE - 1) dirty.add(new ChunkPos(chunk.x(), chunk.y() + 1, chunk.z()));
        if (local.z() == 0) dirty.add(new ChunkPos(chunk.x(), chunk.y(), chunk.z() - 1));
        if (local.z() == Chunk.SIZE - 1) dirty.add(new ChunkPos(chunk.x(), chunk.y(), chunk.z() + 1));
        for (ChunkPos dirtyPosition : dirty) {
            if (dirtyPosition.equals(chunk)) continue;
            Chunk neighbor = world.getChunk(dirtyPosition);
            if (neighbor != null) neighbor.invalidateMesh();
        }
        List<ChunkPos> ordered = new ArrayList<>(dirty);
        ordered.sort(ORDER);
        return new BlockChangeResult(BlockChangeResult.Status.CHANGED, previous, ordered, revision(position));
    }

    private long revision(BlockPos position) {
        Chunk chunk = world.getChunk(Coordinates.worldToChunk(position));
        return chunk == null ? -1 : chunk.revision();
    }
}
