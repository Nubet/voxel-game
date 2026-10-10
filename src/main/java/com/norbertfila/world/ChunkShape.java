package com.norbertfila.world;

public record ChunkShape(int edge) {
    public static final ChunkShape SIZE_16 = new ChunkShape(16);
    public static final ChunkShape SIZE_32 = new ChunkShape(32);

    public ChunkShape {
        if (edge < 1 || (edge & (edge - 1)) != 0)
            throw new IllegalArgumentException("Chunk edge must be a power of two");
    }

    public long blockCount() {
        return (long) edge * edge * edge;
    }

    public long chunksForVolume(int volumeEdge) {
        if (volumeEdge < 1) throw new IllegalArgumentException("Volume edge must be positive");
        long countPerAxis = (volumeEdge + (long) edge - 1) / edge;
        return countPerAxis * countPerAxis * countPerAxis;
    }
}
