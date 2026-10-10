package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HillsGeneratorTest {
    @Test
    void generatesTheSameChunkForTheSameCoordinates() {
        HillsGenerator generator = new HillsGenerator();
        Chunk first = generator.generate(2, 0, -3);
        Chunk second = generator.generate(2, 0, -3);

        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) assertEquals(first.get(x, y, z), second.get(x, y, z));
    }

    @Test
    void generatesGrassDirtAndStoneInTheInitialTerrainLayer() {
        Chunk chunk = new HillsGenerator().generate(0, 0, 0);
        boolean hasGrass = false;
        boolean hasDirt = false;
        boolean hasStone = false;

        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    hasGrass |= chunk.get(x, y, z) == BlockId.GRASS;
                    hasDirt |= chunk.get(x, y, z) == BlockId.DIRT;
                    hasStone |= chunk.get(x, y, z) == BlockId.STONE;
                }

        assertTrue(hasGrass);
        assertTrue(hasDirt);
        assertTrue(hasStone);
    }

    @Test
    void generatesVerticalSectionsUsingWorldHeight() {
        HillsGenerator generator = new HillsGenerator();
        Chunk belowSurface = generator.generate(0, -1, 0);
        Chunk surface = generator.generate(0, 0, 0);
        Chunk aboveSurface = generator.generate(0, 1, 0);

        assertEquals(BlockId.STONE, belowSurface.get(0, Chunk.SIZE - 1, 0));
        assertEquals(BlockId.STONE, surface.get(0, 0, 0));
        assertEquals(BlockId.AIR, surface.get(0, Chunk.SIZE - 1, 0));
        assertEquals(BlockId.AIR, aboveSurface.get(0, 0, 0));
    }
}
