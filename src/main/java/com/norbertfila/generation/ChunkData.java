package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.Arrays;
import java.util.Objects;

/** Generated blocks of one chunk section. A section filled with one block stores no block array. */
public final class ChunkData {
    public static final int BLOCK_COUNT = Chunk.SIZE * Chunk.SIZE * Chunk.SIZE;

    private final ChunkPos position;
    private final byte[] blocks;
    private final byte uniformBlock;

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
        uniformBlock = BlockId.AIR;
    }

    private ChunkData(ChunkPos position, byte uniformBlock) {
        this.position = Objects.requireNonNull(position);
        if (!BlockId.isValid(uniformBlock))
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(uniformBlock));
        blocks = null;
        this.uniformBlock = uniformBlock;
    }

    public static ChunkData uniform(ChunkPos position, byte block) {
        return new ChunkData(position, block);
    }

    public ChunkPos position() {
        return position;
    }

    public boolean isUniform() {
        return blocks == null;
    }

    /** The block filling a uniform section; only meaningful when {@link #isUniform()}. */
    public byte uniformBlock() {
        return uniformBlock;
    }

    public byte get(int x, int y, int z) {
        if (x < 0 || x >= Chunk.SIZE || y < 0 || y >= Chunk.SIZE || z < 0 || z >= Chunk.SIZE) {
            throw new IndexOutOfBoundsException("Chunk data coordinates must be local to the chunk");
        }
        return blocks == null ? uniformBlock : blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x];
    }

    public byte[] blocks() {
        if (blocks != null) return blocks.clone();
        byte[] filled = new byte[BLOCK_COUNT];
        Arrays.fill(filled, uniformBlock);
        return filled;
    }

    public Chunk toChunk() {
        return blocks == null ? Chunk.uniform(uniformBlock) : Chunk.fromBlocks(blocks);
    }
}
