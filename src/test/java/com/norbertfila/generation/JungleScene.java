package com.norbertfila.generation;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.*;

final class JungleScene implements TerrainSource {
    private final TreeGenerator trees = new TreeGenerator(42, (x, z) -> 1, this);
    private final boolean slope;

    JungleScene(boolean slope) {
        this.slope = slope;
    }

    public int surfaceHeight(int x, int z) {
        return 32 + (slope ? Math.floorDiv(x, 4) : 0);
    }

    public TerrainSummary sample(int x, int z) {
        return new TerrainSummary(
                surfaceHeight(x, z),
                BlockId.GRASS,
                BlockId.DIRT,
                BlockId.STONE,
                WorldBounds.MIN_Y,
                TerrainSummary.NO_WATER,
                BiomeType.JUNGLE,
                new ClimateSample(.7f, .7f));
    }

    public long seed() {
        return 42;
    }

    public int generatorVersion() {
        return 0;
    }

    public ChunkData generate(ChunkPos pos) {
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                var ground = sample(pos.x() * 16 + x, pos.z() * 16 + z);
                for (int y = 0; y < 16; y++) blocks[(y * 16 + z) * 16 + x] = ground.materialAt(pos.y() * 16 + y);
            }
        trees.generate(pos.x() * 16, pos.y() * 16, pos.z() * 16, blocks);
        return new ChunkData(pos, blocks);
    }
}
