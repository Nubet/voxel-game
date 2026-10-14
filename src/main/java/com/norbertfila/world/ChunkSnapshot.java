package com.norbertfila.world;

import java.util.Objects;

/**
 * Immutable copy of one chunk's blocks and water states for worker threads. Snapshots taken from a {@link Chunk}
 * share their arrays with every other snapshot of the same content revision, so neighboring mesh tasks reuse one
 * copy instead of making their own.
 */
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
        this(revision, meshDependencyRevision, validatedCopy(blocks), validatedWater(blocks, waterData));
    }

    /** Takes ownership of the arrays; callers must not keep or modify them. */
    private ChunkSnapshot(long revision, long meshDependencyRevision, byte[] blocks, byte[] waterData) {
        this.blocks = blocks;
        this.waterData = waterData;
        this.revision = revision;
        this.meshDependencyRevision = meshDependencyRevision;
    }

    static ChunkSnapshot owning(byte[] blocks, byte[] waterData, long revision, long meshDependencyRevision) {
        return new ChunkSnapshot(revision, meshDependencyRevision, blocks, waterData);
    }

    ChunkSnapshot withMeshDependencyRevision(long meshDependencyRevision) {
        return meshDependencyRevision == this.meshDependencyRevision
                ? this
                : new ChunkSnapshot(revision, meshDependencyRevision, blocks, waterData);
    }

    private static byte[] validatedCopy(byte[] blocks) {
        Objects.requireNonNull(blocks);
        if (blocks.length != Chunk.SIZE * Chunk.SIZE * Chunk.SIZE) {
            throw new IllegalArgumentException("Invalid chunk snapshot size: " + blocks.length);
        }
        return blocks.clone();
    }

    private static byte[] validatedWater(byte[] blocks, byte[] waterData) {
        if (waterData == null) return null;
        if (waterData.length != blocks.length)
            throw new IllegalArgumentException("Invalid water metadata size: " + waterData.length);
        byte[] copy = waterData.clone();
        for (int index = 0; index < blocks.length; index++) {
            if (blocks[index] == BlockId.WATER) WaterState.validate(BlockId.WATER, copy[index]);
            else if (copy[index] != 0) throw new IllegalArgumentException("Water metadata requires a water block");
        }
        return copy;
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
