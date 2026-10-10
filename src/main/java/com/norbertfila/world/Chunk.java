package com.norbertfila.world;

public final class Chunk {
    public static final int SIZE = 16;
    public static final int BLOCK_BYTES = SIZE * SIZE * SIZE;
    private LayerStorage data = new LayerStorage();
    private long revision;
    private long meshDependencyRevision;

    public Chunk() {}

    public static Chunk fromBlocks(byte[] source) {
        return new Chunk(LayerStorage.fromBlocks(source));
    }

    private Chunk(LayerStorage data) {
        this.data = data;
    }

    public byte get(int x, int y, int z) {
        checkLocalCoordinates(x, y, z);
        return data.get(x, y, z);
    }

    public void set(int x, int y, int z, byte value) {
        if (!BlockId.isValid(value)) {
            throw new IllegalArgumentException("Unknown block id: " + BlockId.toUnsignedInt(value));
        }
        checkLocalCoordinates(x, y, z);
        if (data.get(x, y, z) != value) {
            data.set(x, y, z, value);
            revision++;
            invalidateMesh();
        }
    }

    public ChunkSnapshot snapshot() {
        return new ChunkSnapshot(data.toBlocks(), revision);
    }

    public ChunkData.Kind storageKind() {
        return data.kind();
    }

    public long revision() {
        return revision;
    }

    public long meshDependencyRevision() {
        return meshDependencyRevision;
    }

    void invalidateMesh() {
        meshDependencyRevision++;
    }

    public long estimatedBytes() {
        return data.estimatedBytes();
    }

    private static void checkLocalCoordinates(int x, int y, int z) {
        checkLocalCoordinate(x, "x");
        checkLocalCoordinate(y, "y");
        checkLocalCoordinate(z, "z");
    }

    private static void checkLocalCoordinate(int coordinate, String axis) {
        if (coordinate < 0 || coordinate >= SIZE) {
            throw new IndexOutOfBoundsException(axis + " must be in [0, " + SIZE + "): " + coordinate);
        }
    }
}
