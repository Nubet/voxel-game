package com.norbertfila.world;

public record RegionPos(int x, int y, int z) {
    public static RegionPos fromChunk(ChunkPos chunk, int regionEdge) {
        if (regionEdge < 1) throw new IllegalArgumentException("Region edge must be positive");
        return new RegionPos(
                Math.floorDiv(chunk.x(), regionEdge),
                Math.floorDiv(chunk.y(), regionEdge),
                Math.floorDiv(chunk.z(), regionEdge));
    }

    public ChunkPos localChunk(ChunkPos chunk, int regionEdge) {
        if (!equals(fromChunk(chunk, regionEdge)))
            throw new IllegalArgumentException("Chunk does not belong to region");
        return new ChunkPos(
                Math.floorMod(chunk.x(), regionEdge),
                Math.floorMod(chunk.y(), regionEdge),
                Math.floorMod(chunk.z(), regionEdge));
    }

    public ChunkPos chunkAt(ChunkPos local, int regionEdge) {
        if (regionEdge < 1
                || local.x() < 0
                || local.x() >= regionEdge
                || local.y() < 0
                || local.y() >= regionEdge
                || local.z() < 0
                || local.z() >= regionEdge) {
            throw new IllegalArgumentException("Local chunk position must be within the region");
        }
        return new ChunkPos(
                Math.addExact(Math.multiplyExact(x, regionEdge), local.x()),
                Math.addExact(Math.multiplyExact(y, regionEdge), local.y()),
                Math.addExact(Math.multiplyExact(z, regionEdge), local.z()));
    }

    public Bounds bounds(int regionEdge) {
        if (regionEdge < 1) throw new IllegalArgumentException("Region edge must be positive");
        long minX = (long) x * regionEdge;
        long minY = (long) y * regionEdge;
        long minZ = (long) z * regionEdge;
        return new Bounds(minX, minY, minZ, minX + regionEdge, minY + regionEdge, minZ + regionEdge);
    }

    public record Bounds(long minX, long minY, long minZ, long maxExclusiveX, long maxExclusiveY, long maxExclusiveZ) {
        public boolean contains(ChunkPos chunk) {
            return chunk.x() >= minX
                    && chunk.x() < maxExclusiveX
                    && chunk.y() >= minY
                    && chunk.y() < maxExclusiveY
                    && chunk.z() >= minZ
                    && chunk.z() < maxExclusiveZ;
        }
    }
}
