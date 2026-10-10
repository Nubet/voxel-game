package com.norbertfila.world;

import java.util.Objects;

public final class ChunkSnapshot {
    private final byte[] blocks;
    private final long revision;

    public ChunkSnapshot(byte[] blocks, long revision) {
        Objects.requireNonNull(blocks);
        if (blocks.length != Chunk.SIZE * Chunk.SIZE * Chunk.SIZE) {
            throw new IllegalArgumentException("Invalid chunk snapshot size: " + blocks.length);
        }
        this.blocks = blocks.clone();
        this.revision = revision;
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
}
