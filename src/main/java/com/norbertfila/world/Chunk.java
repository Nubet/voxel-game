package com.norbertfila.world;

public final class Chunk {
    public static final int SIZE = 16;
    private final byte[] blocks = new byte[SIZE * SIZE * SIZE];

    public byte get(int x, int y, int z) {
        return blocks[(y * SIZE + z) * SIZE + x];
    }

    public void set(int x, int y, int z, byte value) {
        blocks[(y * SIZE + z) * SIZE + x] = value;
    }
}
