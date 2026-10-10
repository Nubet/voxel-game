package com.norbertfila.lod;

import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class LodManager {
    private static final Comparator<Request> PRIORITY = Comparator.comparingLong(Request::distanceSquared)
            .thenComparingInt(request -> request.level().sampleStep())
            .thenComparingInt(Request::tileZ)
            .thenComparingInt(Request::tileX);

    private final List<LodLevel> levels;
    private final int tileSize;
    private final int maxTiles;

    public LodManager(List<LodLevel> levels, int tileSize, int maxTiles) {
        Objects.requireNonNull(levels);
        if (levels.isEmpty() || tileSize < 1 || maxTiles < 1)
            throw new IllegalArgumentException("Invalid LOD manager configuration");
        for (int i = 0; i < levels.size(); i++) {
            LodLevel level = Objects.requireNonNull(levels.get(i));
            if (tileSize % level.sampleStep() != 0)
                throw new IllegalArgumentException("LOD sample step must divide tile size");
            if (i > 0 && level.minRadiusChunks() != levels.get(i - 1).maxRadiusChunks() + 1) {
                throw new IllegalArgumentException("LOD rings must be contiguous and ordered");
            }
        }
        if (levels.get(0).minRadiusChunks() != 0) throw new IllegalArgumentException("LOD rings must start at zero");
        this.levels = List.copyOf(levels);
        this.tileSize = tileSize;
        this.maxTiles = maxTiles;
    }

    public List<Request> plan(ChunkPos cameraChunk) {
        Objects.requireNonNull(cameraChunk);
        long cameraX = (long) cameraChunk.x() * Chunk.SIZE + Chunk.SIZE / 2L;
        long cameraZ = (long) cameraChunk.z() * Chunk.SIZE + Chunk.SIZE / 2L;
        int maxRadius = levels.get(levels.size() - 1).maxRadiusChunks() * Chunk.SIZE;
        long minTileX = Math.floorDiv(cameraX - maxRadius - tileSize, tileSize);
        long maxTileX = Math.floorDiv(cameraX + maxRadius + tileSize, tileSize);
        long minTileZ = Math.floorDiv(cameraZ - maxRadius - tileSize, tileSize);
        long maxTileZ = Math.floorDiv(cameraZ + maxRadius + tileSize, tileSize);
        List<Request> requests = new ArrayList<>();
        for (long tileZ = minTileZ; tileZ <= maxTileZ; tileZ++)
            for (long tileX = minTileX; tileX <= maxTileX; tileX++) {
                long centerX = tileX * tileSize + tileSize / 2L;
                long centerZ = tileZ * tileSize + tileSize / 2L;
                long dx = centerX - cameraX;
                long dz = centerZ - cameraZ;
                long distanceSquared = (dx * dx + dz * dz) / ((long) Chunk.SIZE * Chunk.SIZE);
                LodLevel level = levelFor(distanceSquared);
                if (level != null)
                    requests.add(new Request(Math.toIntExact(tileX), Math.toIntExact(tileZ), level, distanceSquared));
            }
        requests.sort(PRIORITY);
        return requests.size() <= maxTiles ? List.copyOf(requests) : List.copyOf(requests.subList(0, maxTiles));
    }

    public LodLevel levelFor(long distanceSquaredChunks) {
        for (LodLevel level : levels) if (level.contains(distanceSquaredChunks)) return level;
        return null;
    }

    public int renderDistanceChunks() {
        return levels.get(levels.size() - 1).maxRadiusChunks();
    }

    public int renderDistanceBlocks() {
        return renderDistanceChunks() * Chunk.SIZE;
    }

    public int tileSize() {
        return tileSize;
    }

    public int maxTiles() {
        return maxTiles;
    }

    public record Request(int tileX, int tileZ, LodLevel level, long distanceSquared) {}
}
