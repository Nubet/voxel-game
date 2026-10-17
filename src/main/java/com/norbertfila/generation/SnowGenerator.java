package com.norbertfila.generation;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.PlantRules;

final class SnowGenerator {
    void generate(int ox, int oy, int oz, byte[] blocks, TerrainColumn columns) {
        for (int z = 0; z < Chunk.SIZE; z++)
            for (int x = 0; x < Chunk.SIZE; x++) {
                int column = z * Chunk.SIZE + x;
                if (!columns.snowCover(column) || !columns.surfaceSupported(column)) continue;
                long canopy = TreeGenerator.canopyHeight(columns.trees(), ox + x, oz + z);
                long ground = columns.surfaceHeight(column);
                boolean forest = columns.biome(column) == BiomeType.SNOWY_TAIGA;
                if (forest && canopy > ground) place(x, canopy + 1 - oy, z, blocks);
                if (ground < columns.waterLevel(column)) continue;
                if (!PlantRules.supports(BlockId.SNOW_LAYER, columns.surfaceMaterial(column), false)) continue;
                place(x, ground + 1 - oy, z, blocks);
            }
    }

    private static void place(int x, long y, int z, byte[] blocks) {
        if (y < 0 || y >= Chunk.SIZE) return;
        int index = ((int) y * Chunk.SIZE + z) * Chunk.SIZE + x;
        if (blocks[index] == BlockId.AIR) blocks[index] = BlockId.SNOW_LAYER;
    }
}
