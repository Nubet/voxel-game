package com.norbertfila.player;

import com.norbertfila.generation.TerrainSource;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.Objects;

public final class SpawnResolver {
    private static final int SEARCH_STEP = Chunk.SIZE;
    private static final int SEARCH_RADIUS = 1024;
    private final TerrainSource terrain;

    public SpawnResolver(TerrainSource terrain) {
        this.terrain = Objects.requireNonNull(terrain);
    }

    public PlayerState.Position resolve(int worldX, int worldZ) {
        PlayerState.Position spawn = dryPosition(worldX, worldZ);
        if (spawn != null) return spawn;
        for (int radius = SEARCH_STEP; radius <= SEARCH_RADIUS; radius += SEARCH_STEP)
            for (int dz = -radius; dz <= radius; dz += SEARCH_STEP)
                for (int dx = -radius; dx <= radius; dx += SEARCH_STEP) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    spawn = dryPosition((long) worldX + dx, (long) worldZ + dz);
                    if (spawn != null) return spawn;
                }
        throw new IllegalStateException("No dry, clear spawn found within " + SEARCH_RADIUS + " blocks");
    }

    private PlayerState.Position dryPosition(long x, long z) {
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE)
            return null;
        var sample = terrain.sample((int) x, (int) z);
        if (sample.flooded() || sample.surfaceHeight() > Integer.MAX_VALUE - 2) return null;
        int feetY = sample.surfaceHeight() + 1;
        int chunkX = Math.floorDiv((int) x, Chunk.SIZE), chunkZ = Math.floorDiv((int) z, Chunk.SIZE);
        int localX = Math.floorMod((int) x, Chunk.SIZE), localZ = Math.floorMod((int) z, Chunk.SIZE);
        int chunkY = Math.floorDiv(feetY, Chunk.SIZE);
        var feetChunk = terrain.generate(new ChunkPos(chunkX, chunkY, chunkZ));
        if (feetChunk.get(localX, Math.floorMod(feetY, Chunk.SIZE), localZ) != BlockId.AIR) return null;
        int headY = feetY + 1;
        var headChunk = Math.floorDiv(headY, Chunk.SIZE) == chunkY
                ? feetChunk
                : terrain.generate(new ChunkPos(chunkX, Math.floorDiv(headY, Chunk.SIZE), chunkZ));
        if (headChunk.get(localX, Math.floorMod(headY, Chunk.SIZE), localZ) != BlockId.AIR) return null;
        return new PlayerState.Position(x + .5, feetY, z + .5);
    }
}
