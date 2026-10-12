package com.norbertfila.world;

import java.util.Objects;

public final class ChunkSnapshot {
    private final byte[] blocks;
    private final byte[] waterData;
    private final long revision;
    private final long meshDependencyRevision;

    public ChunkSnapshot(byte[] blocks, long revision) {
        this(blocks, revision, 0);
    }

    public ChunkSnapshot(byte[] blocks, long revision, long meshDependencyRevision) {
        this(blocks, null, revision, meshDependencyRevision);
    }

    ChunkSnapshot(byte[] blocks, byte[] waterData, long revision, long meshDependencyRevision) {
        Objects.requireNonNull(blocks);
        if (blocks.length != Chunk.SIZE * Chunk.SIZE * Chunk.SIZE) {
            throw new IllegalArgumentException("Invalid chunk snapshot size: " + blocks.length);
        }
        this.blocks = blocks.clone();
        if (waterData != null && waterData.length != blocks.length)
            throw new IllegalArgumentException("Invalid water metadata size: " + waterData.length);
        this.waterData = waterData == null ? null : waterData.clone();
        if (this.waterData != null) {
            for (int index = 0; index < this.blocks.length; index++) {
                if (this.blocks[index] == BlockId.WATER) WaterState.validate(BlockId.WATER, this.waterData[index]);
                else if (this.waterData[index] != 0)
                    throw new IllegalArgumentException("Water metadata requires a water block");
            }
        }
        this.revision = revision;
        this.meshDependencyRevision = meshDependencyRevision;
    }

    public byte get(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    public byte waterState(int x, int y, int z) {
        int index = index(x, y, z);
        if (blocks[index] != BlockId.WATER) return WaterState.EMPTY;
        return waterData == null ? WaterState.SOURCE : waterData[index];
    }

    private static int index(int x, int y, int z) {
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) {
            throw new IndexOutOfBoundsException("Snapshot coordinates must be local to the chunk");
        }
        return (y * Chunk.SIZE + z) * Chunk.SIZE + x;
    }

    public long revision() {
        return revision;
    }

    public long meshDependencyRevision() {
        return meshDependencyRevision;
    }
}
