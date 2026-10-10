package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.Objects;

public final class ChunkData {
    public static final int BLOCK_COUNT = Chunk.SIZE * Chunk.SIZE * Chunk.SIZE;

    private final ChunkPos position;
    private final byte[] blocks;

    public ChunkData(ChunkPos position, byte[] blocks) {
        this.position = Objects.requireNonNull(position);
        Objects.requireNonNull(blocks);
        if (blocks.length != BLOCK_COUNT)
            throw new IllegalArgumentException("Invalid chunk data size: " + blocks.length);
        for (byte block : blocks) {
            if (!BlockId.isValid(block))
                throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(block));
        }
        this.blocks = blocks.clone();
    }

    public ChunkPos position() {
        return position;
    }

    public byte get(int x, int y, int z) {
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) {
            throw new IndexOutOfBoundsException("Chunk data coordinates must be local to the chunk");
        }
        return blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x];
    }

    public byte[] blocks() {
        return blocks.clone();
    }
}
