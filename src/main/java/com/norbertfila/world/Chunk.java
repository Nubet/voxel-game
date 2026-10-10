package com.norbertfila.world;

import java.util.Objects;

public final class Chunk {
    public static final int SIZE = 16;
    public static final int BLOCK_BYTES = SIZE * SIZE * SIZE;
    private final byte[] blocks = new byte[SIZE * SIZE * SIZE];
    private long revision;

    public static Chunk fromBlocks(byte[] source) {
        Objects.requireNonNull(source);
        if (source.length != SIZE * SIZE * SIZE)
            throw new IllegalArgumentException("Invalid chunk data size: " + source.length);
        for (byte value : source) {
            if (!BlockId.isValid(value))
                throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
        }
        Chunk chunk = new Chunk();
        System.arraycopy(source, 0, chunk.blocks, 0, source.length);
        return chunk;
    }

    public byte get(int x, int y, int z) {
        return blocks[index(x, y, z)];
    }

    public void set(int x, int y, int z, byte value) {
        if (!BlockId.isValid(value)) {
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
        }
        int blockIndex = index(x, y, z);
        if (blocks[blockIndex] != value) {
            blocks[blockIndex] = value;
            revision++;
        }
    }

    public ChunkSnapshot snapshot() {
        return new ChunkSnapshot(blocks, revision);
    }

    private static int index(int x, int y, int z) {
        checkLocalCoordinate(x, "x");
        checkLocalCoordinate(y, "y");
        checkLocalCoordinate(z, "z");
        return (y * SIZE + z) * SIZE + x;
    }

    private static void checkLocalCoordinate(int coordinate, String axis) {
        if (coordinate < 0 || coordinate >= SIZE) {
            throw new IndexOutOfBoundsException(axis + " must be in [0, " + SIZE + "): " + coordinate);
        }
    }
}
