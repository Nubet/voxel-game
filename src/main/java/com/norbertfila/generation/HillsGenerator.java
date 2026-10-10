package com.norbertfila.generation;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;

public final class HillsGenerator implements WorldGenerator {
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
        int seedOffset = seed == 0 ? 0 : (int) Math.floorMod(mix(seed, position.x(), position.z()), 3) - 1;
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                int wx = position.x() * Chunk.SIZE + x, wz = position.z() * Chunk.SIZE + z;
                int surface = 9 + seedOffset + (int) (5 * Math.sin(wx * 0.065) * Math.cos(wz * 0.071));
                for (int y = 0; y < Chunk.SIZE; y++) {
                    int wy = position.y() * Chunk.SIZE + y;
                    byte id = wy > surface
                            ? BlockId.AIR
                            : wy == surface ? BlockId.GRASS : wy >= surface - 3 ? BlockId.DIRT : BlockId.STONE;
                    blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = id;
                }
            }
        return new ChunkData(position, blocks);
    }

    private static long mix(long seed, int x, int z) {
        long value = seed ^ ((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        value ^= GENERATOR_VERSION * 0x165667B19E3779F9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
