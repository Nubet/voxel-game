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
        for (ChunkPos neighbor : World.meshNeighbors(chunk)) {
            int dx = neighbor.x() - chunk.x(), dy = neighbor.y() - chunk.y(), dz = neighbor.z() - chunk.z();
            if ((dx == 0 || (dx < 0 ? local.x() == 0 : local.x() == Chunk.SIZE - 1))
                    && (dy == 0 || (dy < 0 ? local.y() == 0 : local.y() == Chunk.SIZE - 1))
                    && (dz == 0 || (dz < 0 ? local.z() == 0 : local.z() == Chunk.SIZE - 1))) dirty.add(neighbor);
        }
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
