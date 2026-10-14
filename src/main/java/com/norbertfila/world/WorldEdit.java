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
        if (position.y() < Integer.MAX_VALUE) {
            BlockPos above = new BlockPos(position.x(), position.y() + 1, position.z());
            if (world.hasChunk(Coordinates.worldToChunk(above))
                    && PlantRules.isPlant((byte) world.getBlock(above).orElseThrow())
                    && !PlantRules.supports(value))
                dirty.addAll(setBlock(above, BlockId.AIR).dirtyChunks());
        }
        List<ChunkPos> ordered = new ArrayList<>(dirty);
        ordered.sort(ORDER);
        return new BlockChangeResult(BlockChangeResult.Status.CHANGED, previous, ordered, revision(position));
    }

    public List<ChunkPos> validatePlants(ChunkPos loaded) {
        if (!world.hasChunk(loaded)) return List.of();
        Set<ChunkPos> dirty = new LinkedHashSet<>();
        validatePlantsIn(loaded, false, dirty);
        if (loaded.y() < Integer.MAX_VALUE) {
            ChunkPos above = new ChunkPos(loaded.x(), loaded.y() + 1, loaded.z());
            if (world.hasChunk(above)) validatePlantsIn(above, true, dirty);
        }
        return List.copyOf(dirty);
    }

    private void validatePlantsIn(ChunkPos chunkPos, boolean bottomOnly, Set<ChunkPos> dirty) {
        Chunk chunk = world.getChunk(chunkPos);
        int uniform = chunk.uniformBlock();
        if (uniform >= 0 && !PlantRules.isPlant((byte) uniform)) return;
        for (int y = 0; y < (bottomOnly ? 1 : Chunk.SIZE); y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    if (!PlantRules.isPlant(chunk.get(x, y, z))) continue;
                    BlockPos position = new BlockPos(
                            Math.toIntExact((long) chunkPos.x() * Chunk.SIZE + x),
                            Math.toIntExact((long) chunkPos.y() * Chunk.SIZE + y),
                            Math.toIntExact((long) chunkPos.z() * Chunk.SIZE + z));
                    if (PlantRules.unsupported(world, position))
                        dirty.addAll(setBlock(position, BlockId.AIR).dirtyChunks());
                }
    }

    private long revision(BlockPos position) {
        Chunk chunk = world.getChunk(Coordinates.worldToChunk(position));
        return chunk == null ? -1 : chunk.revision();
    }
}
