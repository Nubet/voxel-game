package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;

public final class HillsGenerator implements TerrainSource {
    private static final int GENERATOR_VERSION = 1;
    private final long seed;

    public HillsGenerator() {
        this(0L);
    }

    public HillsGenerator(long seed) {
        this.seed = seed;
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public int generatorVersion() {
        return GENERATOR_VERSION;
    }

    @Override
    public ChunkData generate(ChunkPos position) {
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                int wx = position.x() * Chunk.SIZE + x, wz = position.z() * Chunk.SIZE + z;
                TerrainSummary summary = sample(wx, wz);
                for (int y = 0; y < Chunk.SIZE; y++) {
                    int wy = position.y() * Chunk.SIZE + y;
                    blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = summary.materialAt(wy);
                }
            }
        return new ChunkData(position, blocks);
    }

    @Override
    public TerrainSummary sample(int worldX, int worldZ) {
        return new TerrainSummary(surfaceHeight(worldX, worldZ), BlockId.GRASS, BlockId.DIRT, BlockId.STONE);
    }

    @Override
    public int surfaceHeight(int worldX, int worldZ) {
        int seedOffset = seed == 0
                ? 0
                : (int) Math.floorMod(
                                mix(seed, Math.floorDiv(worldX, Chunk.SIZE), Math.floorDiv(worldZ, Chunk.SIZE)), 3)
                        - 1;
        return 9 + seedOffset + (int) (5 * Math.sin(worldX * 0.065) * Math.cos(worldZ * 0.071));
    }

    private static long mix(long seed, int x, int z) {
        long value = seed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        value ^= GENERATOR_VERSION * 0x165667B19E3779F9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
