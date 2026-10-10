package com.norbertfila.lod;

import com.norbertfila.generation.TerrainSource;
import com.norbertfila.generation.TerrainSummary;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.Objects;
import java.util.Optional;

public final class FarTerrainGenerator {
    private final TerrainSource source;
    private final int tileSize;
    private final int sampleStep;
    private final int minRadiusChunks;
    private final int maxRadiusChunks;

    public FarTerrainGenerator(
            TerrainSource source, int tileSize, int sampleStep, int minRadiusChunks, int maxRadiusChunks) {
        if (tileSize < 1 || sampleStep < 1 || tileSize % sampleStep != 0)
            throw new IllegalArgumentException("Invalid far terrain sampling");
        if (minRadiusChunks < 0 || maxRadiusChunks < minRadiusChunks)
            throw new IllegalArgumentException("Invalid far terrain radius");
        this.source = Objects.requireNonNull(source);
        this.tileSize = tileSize;
        this.sampleStep = sampleStep;
        this.minRadiusChunks = minRadiusChunks;
        this.maxRadiusChunks = maxRadiusChunks;
    }

    public Optional<FarTerrainTile> generate(ChunkPos cameraChunk, int tileX, int tileZ) {
        Objects.requireNonNull(cameraChunk);
        return inRange(cameraChunk, tileX, tileZ) ? Optional.of(generate(tileX, tileZ)) : Optional.empty();
    }

    public FarTerrainTile generate(int tileX, int tileZ) {
        int originX = Math.multiplyExact(tileX, tileSize);
        int originZ = Math.multiplyExact(tileZ, tileSize);
        int sampleCount = tileSize / sampleStep + 1;
        int[] heights = new int[sampleCount * sampleCount];
        byte[] materials = new byte[heights.length];
        for (int z = 0; z < sampleCount; z++)
            for (int x = 0; x < sampleCount; x++) {
                TerrainSummary sample = source.sample(originX + x * sampleStep, originZ + z * sampleStep);
                int index = z * sampleCount + x;
                heights[index] = sample.surfaceHeight();
                materials[index] = sample.surfaceMaterial();
            }
        return new FarTerrainTile(originX, originZ, tileSize, sampleStep, heights, materials);
    }

    public boolean inRange(ChunkPos cameraChunk, int tileX, int tileZ) {
        Objects.requireNonNull(cameraChunk);
        long centerX = (long) tileX * tileSize + tileSize / 2L;
        long centerZ = (long) tileZ * tileSize + tileSize / 2L;
        long cameraX = (long) cameraChunk.x() * Chunk.SIZE + Chunk.SIZE / 2L;
        long cameraZ = (long) cameraChunk.z() * Chunk.SIZE + Chunk.SIZE / 2L;
        long dx = centerX - cameraX;
        long dz = centerZ - cameraZ;
        long distanceSquared = dx * dx + dz * dz;
        long min = (long) minRadiusChunks * Chunk.SIZE;
        long max = (long) maxRadiusChunks * Chunk.SIZE;
        return distanceSquared >= min * min && distanceSquared <= max * max;
    }

    public int tileSize() {
        return tileSize;
    }

    public int sampleStep() {
        return sampleStep;
    }

    public int minRadiusChunks() {
        return minRadiusChunks;
    }

    public int maxRadiusChunks() {
        return maxRadiusChunks;
    }
}
