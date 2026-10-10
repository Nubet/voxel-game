package com.norbertfila.world;

public final class Coordinates {
    private Coordinates() {}

    public static ChunkPos worldToChunk(BlockPos world) {
        return new ChunkPos(
                Math.floorDiv(world.x(), Chunk.SIZE),
                Math.floorDiv(world.y(), Chunk.SIZE),
                Math.floorDiv(world.z(), Chunk.SIZE));
    }

    public static BlockPos worldToLocal(BlockPos world) {
        return new BlockPos(
                Math.floorMod(world.x(), Chunk.SIZE),
                Math.floorMod(world.y(), Chunk.SIZE),
                Math.floorMod(world.z(), Chunk.SIZE));
    }

    public static BlockPos chunkLocalToWorld(ChunkPos chunk, BlockPos local) {
        validateLocal(local);
        return new BlockPos(
                Math.addExact(Math.multiplyExact(chunk.x(), Chunk.SIZE), local.x()),
                Math.addExact(Math.multiplyExact(chunk.y(), Chunk.SIZE), local.y()),
                Math.addExact(Math.multiplyExact(chunk.z(), Chunk.SIZE), local.z()));
    }

    private static void validateLocal(BlockPos local) {
        if (local.x() < 0
                || local.x() >= Chunk.SIZE
                || local.y() < 0
                || local.y() >= Chunk.SIZE
                || local.z() < 0
                || local.z() >= Chunk.SIZE) {
            throw new IllegalArgumentException("Local block position must be within the chunk");
        }
    }
}
