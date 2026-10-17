package com.norbertfila.generation;

import com.norbertfila.generation.biome.BadlandsSurface;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WorldBounds;

final class BadlandsCliffScene implements TerrainSource {
    private final BadlandsSurface surface = new BadlandsSurface(42);

    @Override
    public int surfaceHeight(int x, int z) {
        return x < 0 ? 80 : 48;
    }

    @Override
    public TerrainSummary sample(int x, int z) {
        int height = surfaceHeight(x, z);
        var layers = surface.column(x, z);
        byte top = layers.surfaceAt(height, 28);
        return new TerrainSummary(
                height,
                top,
                layers.materialAt(height - 1, height, top),
                BlockId.STONE,
                WorldBounds.MIN_Y,
                TerrainSummary.NO_WATER,
                BiomeType.BADLANDS,
                new ClimateSample(.5f, 0),
                WaterKind.NONE,
                false,
                false,
                layers);
    }

    @Override
    public long seed() {
        return 42;
    }

    @Override
    public int generatorVersion() {
        return 0;
    }

    @Override
    public ChunkData generate(ChunkPos position) {
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                var column = sample(position.x() * 16 + x, position.z() * 16 + z);
                for (int y = 0; y < 16; y++) blocks[(y * 16 + z) * 16 + x] = column.materialAt(position.y() * 16 + y);
            }
        return new ChunkData(position, blocks);
    }
}
