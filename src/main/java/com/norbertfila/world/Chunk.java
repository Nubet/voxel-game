package com.norbertfila.world;

public final class Chunk {
    public static final int SIZE = 16;
    public static final int BLOCK_BYTES = SIZE * SIZE * SIZE;
    private LayerStorage data = new LayerStorage();
    // Zero metadata means source for WATER and no fluid for every other block.
    private byte[] waterData;
    private int flowingBlocks;
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
        set(x, y, z, value, WaterState.defaultFor(value));
    }

    public byte waterState(int x, int y, int z) {
        checkLocalCoordinates(x, y, z);
        if (data.get(x, y, z) != BlockId.WATER) return WaterState.EMPTY;
        return waterData == null ? WaterState.SOURCE : waterData[index(x, y, z)];
    }

    public void set(int x, int y, int z, byte value, byte waterState) {
        WaterState.validate(value, waterState);
        checkLocalCoordinates(x, y, z);
        int index = index(x, y, z);
        byte previous = waterData == null ? 0 : waterData[index];
        byte next = value == BlockId.WATER ? waterState : 0;
        if (data.get(x, y, z) == value && previous == next) return;
        if (next != 0 && waterData == null) waterData = new byte[BLOCK_BYTES];
        data.set(x, y, z, value);
        if (waterData != null) {
            flowingBlocks += (next != 0 ? 1 : 0) - (previous != 0 ? 1 : 0);
            waterData[index] = next;
            if (flowingBlocks == 0) waterData = null;
        }
        revision++;
        invalidateMesh();
    }

    public ChunkSnapshot snapshot() {
        return new ChunkSnapshot(data.toBlocks(), waterData, revision, meshDependencyRevision);
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
        return data.estimatedBytes() + (waterData == null ? 0 : waterData.length);
    }

    private static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
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
