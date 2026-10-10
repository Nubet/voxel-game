package com.norbertfila.world;

import java.util.Objects;

public final class ChunkSnapshot {
    private final byte[] blocks;
    private final long revision;
    private final long meshDependencyRevision;

    public ChunkSnapshot(byte[] blocks, long revision) {
        this(blocks, revision, 0);
    }

    public ChunkSnapshot(byte[] blocks, long revision, long meshDependencyRevision) {
        Objects.requireNonNull(blocks);
        if (blocks.length != Chunk.SIZE * Chunk.SIZE * Chunk.SIZE) {
            throw new IllegalArgumentException("Invalid chunk snapshot size: " + blocks.length);
        }
        this.blocks = blocks.clone();
        this.revision = revision;
        this.meshDependencyRevision = meshDependencyRevision;
    }

    public byte get(int x, int y, int z) {
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) {
            throw new IndexOutOfBoundsException("Snapshot coordinates must be local to the chunk");
        }
        return blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x];
    }

    public long revision() {
        return revision;
    }

    public long meshDependencyRevision() {
        return meshDependencyRevision;
    }
}
