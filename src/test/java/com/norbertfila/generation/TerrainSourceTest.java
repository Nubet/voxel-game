package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class TerrainSourceTest {
    @Test
    void samplesMatchNearVoxelGenerationAcrossChunkBoundaries() {
        TerrainSource source = new HillsGenerator(42L);
        for (int chunkZ = -2; chunkZ <= 2; chunkZ++)
            for (int chunkX = -2; chunkX <= 2; chunkX++) {
                ChunkPos position = new ChunkPos(chunkX, 0, chunkZ);
                ChunkData data = source.generate(position);
                for (int z = 0; z < Chunk.SIZE; z++)
                    for (int x = 0; x < Chunk.SIZE; x++) {
                        TerrainSummary summary = source.sample(chunkX * Chunk.SIZE + x, chunkZ * Chunk.SIZE + z);
                        for (int y = 0; y < Chunk.SIZE; y++) {
                            assertEquals(summary.materialAt(y), data.get(x, y, z));
                        }
                    }
            }
    }

    @Test
    void sampleIsStableForSeedAndVersion() {
        HillsGenerator first = new HillsGenerator(99L);
        HillsGenerator second = new HillsGenerator(99L);

        assertEquals(first.seed(), second.seed());
        assertEquals(first.generatorVersion(), second.generatorVersion());
        assertEquals(first.sample(-17, 31), second.sample(-17, 31));
    }
}
