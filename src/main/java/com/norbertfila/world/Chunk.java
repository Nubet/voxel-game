package com.norbertfila.world;

public final class Chunk {
    public static final int SIZE = 16;
    private final byte[] blocks = new byte[SIZE * SIZE * SIZE];
    private long revision;

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
