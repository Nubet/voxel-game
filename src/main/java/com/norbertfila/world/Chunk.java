package com.norbertfila.world;

public final class Chunk {
    public static final int SIZE = 16;
    public static final int BLOCK_BYTES = SIZE * SIZE * SIZE;
    private ChunkData data = ChunkData.empty();
    private long revision;

    public Chunk() {}

    public static Chunk fromBlocks(byte[] source) {
        return new Chunk(ChunkData.fromBlocks(source));
    }

    private Chunk(ChunkData data) {
        this.data = data;
    }

    public byte get(int x, int y, int z) {
        return data.get(index(x, y, z));
    }

    public void set(int x, int y, int z, byte value) {
        if (!BlockId.isValid(value)) {
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
        }
        int blockIndex = index(x, y, z);
        if (data.get(blockIndex) != value) {
            data = data.set(blockIndex, value);
            revision++;
        }
    }

    public ChunkSnapshot snapshot() {
        return new ChunkSnapshot(data.toBlocks(), revision);
    }

    public ChunkData.Kind storageKind() {
        return data.kind();
    }

    public long estimatedBytes() {
        return data.estimatedBytes();
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
