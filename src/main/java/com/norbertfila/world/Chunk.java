package com.norbertfila.world;

public final class Chunk {
    public static final int SIZE = 16;
    public static final int BLOCK_BYTES = SIZE * SIZE * SIZE;
    private LayerStorage data = new LayerStorage();
    // Zero metadata means source for WATER and no fluid for every other block.
    private byte[] waterData;
    private int flowingBlocks;
    private int waterBlocks;
    private ChunkSnapshot snapshot;
    private long revision;
    private long meshDependencyRevision;

    public Chunk() {}

    public static Chunk fromBlocks(byte[] source) {
        Chunk chunk = new Chunk(LayerStorage.fromBlocks(source));
        for (byte block : source) if (block == BlockId.WATER) chunk.waterBlocks++;
        return chunk;
    }

    public static Chunk uniform(byte block) {
        Chunk chunk = new Chunk(LayerStorage.uniform(block));
        if (block == BlockId.WATER) chunk.waterBlocks = BLOCK_BYTES;
        return chunk;
    }

    private Chunk(LayerStorage data) {
        this.data = data;
    }

    /** The block filling the whole chunk as an unsigned ID, or -1 when the chunk holds more than one block type. */
    public int uniformBlock() {
        return data.uniformBlock();
    }

    public boolean containsWater() {
        return waterBlocks > 0;
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
        byte old = data.get(x, y, z);
        waterBlocks += (value == BlockId.WATER ? 1 : 0) - (old == BlockId.WATER ? 1 : 0);
        data.set(x, y, z, value);
        if (waterData != null) {
            flowingBlocks += (next != 0 ? 1 : 0) - (previous != 0 ? 1 : 0);
            waterData[index] = next;
            if (flowingBlocks == 0) waterData = null;
        }
        revision++;
        invalidateMesh();
    }

    /** Returns a snapshot that shares its arrays with every other snapshot taken at the same content revision. */
    public ChunkSnapshot snapshot() {
        if (snapshot == null || snapshot.revision() != revision) {
            // Edits can leave layers holding one block type; compact them now, while the data is copied anyway.
            data.compactLayers();
            byte[] water = waterData == null ? null : waterData.clone();
            int uniform = data.uniformBlock();
            if (uniform >= 0) {
                snapshot = ChunkSnapshot.uniform((byte) uniform, water, revision, meshDependencyRevision);
            } else {
                byte[] blocks = new byte[BLOCK_BYTES];
                data.copyInto(blocks);
                snapshot = ChunkSnapshot.owning(blocks, water, revision, meshDependencyRevision);
            }
        }
        return snapshot.withMeshDependencyRevision(meshDependencyRevision);
    }

    /** True when a cached snapshot holds a block array; the world bounds how many chunks keep one. */
    boolean holdsSnapshotArrays() {
        return snapshot != null && snapshot.arrayBytes() > 0;
    }

    /** Drops the cached snapshot; the next {@link #snapshot()} rebuilds it. Published snapshots stay valid. */
    void releaseSnapshot() {
        snapshot = null;
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

    /** Bytes of block and water arrays held only by the cached meshing snapshot. */
    long snapshotBytes() {
        return snapshot == null ? 0 : snapshot.arrayBytes();
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
