package com.norbertfila.world;

import java.util.Objects;

/**
 * Immutable copy of one chunk's blocks and water states for worker threads. Snapshots taken from a {@link Chunk}
 * share their arrays with every other snapshot of the same content revision, so neighboring mesh tasks reuse one
 * copy instead of making their own.
 */
public final class ChunkSnapshot {
    // Null for a chunk of one block type, which then reads as uniformBlock and costs no array.
    private final byte[] blocks;
    private final byte uniformBlock;
    private final byte[] waterData;
    private final long revision;
    private final long meshDependencyRevision;
    private final byte[] tints;

    public ChunkSnapshot(byte[] blocks, long revision) {
        this(blocks, revision, 0);
    }

    public ChunkSnapshot(byte[] blocks, long revision, long meshDependencyRevision) {
        this(blocks, null, revision, meshDependencyRevision);
    }

    ChunkSnapshot(byte[] blocks, byte[] waterData, long revision, long meshDependencyRevision) {
        this(revision, meshDependencyRevision, validatedCopy(blocks), BlockId.AIR, validatedWater(blocks, waterData));
    }

    /** Takes ownership of the arrays; callers must not keep or modify them. */
    private ChunkSnapshot(
            long revision, long meshDependencyRevision, byte[] blocks, byte uniformBlock, byte[] waterData) {
        this(revision, meshDependencyRevision, blocks, uniformBlock, waterData, null);
    }

    private ChunkSnapshot(
            long revision,
            long meshDependencyRevision,
            byte[] blocks,
            byte uniformBlock,
            byte[] waterData,
            byte[] tints) {
        this.blocks = blocks;
        this.uniformBlock = uniformBlock;
        this.waterData = waterData;
        this.revision = revision;
        this.meshDependencyRevision = meshDependencyRevision;
        this.tints = tints;
    }

    ChunkSnapshot withTints(byte[] tints) {
        return this.tints == tints
                ? this
                : new ChunkSnapshot(revision, meshDependencyRevision, blocks, uniformBlock, waterData, tints);
    }

    public int tintIndex(int x, int z) {
        index(x, 0, z);
        return tints == null ? 0 : tints[z * Chunk.SIZE + x];
    }

    static ChunkSnapshot owning(byte[] blocks, byte[] waterData, long revision, long meshDependencyRevision) {
        return new ChunkSnapshot(revision, meshDependencyRevision, blocks, BlockId.AIR, waterData);
    }

    static ChunkSnapshot uniform(byte block, byte[] waterData, long revision, long meshDependencyRevision) {
        return new ChunkSnapshot(revision, meshDependencyRevision, null, block, waterData);
    }

    ChunkSnapshot withMeshDependencyRevision(long meshDependencyRevision) {
        return meshDependencyRevision == this.meshDependencyRevision
                ? this
                : new ChunkSnapshot(revision, meshDependencyRevision, blocks, uniformBlock, waterData, tints);
    }

    /** Bytes of block and water arrays held by this snapshot, shared with snapshots of the same revision. */
    long arrayBytes() {
        return (blocks == null ? 0 : blocks.length) + (waterData == null ? 0 : waterData.length);
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
            if (BlockId.isFluid(Byte.toUnsignedInt(blocks[index]))) WaterState.validate(blocks[index], copy[index]);
            else if (copy[index] != 0) throw new IllegalArgumentException("Water metadata requires a water block");
        }
        return copy;
    }

    public byte get(int x, int y, int z) {
        int index = index(x, y, z);
        return blocks == null ? uniformBlock : blocks[index];
    }

    public byte waterState(int x, int y, int z) {
        int index = index(x, y, z);
        if (!BlockId.isFluid(Byte.toUnsignedInt(blocks == null ? uniformBlock : blocks[index])))
            return WaterState.EMPTY;
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
