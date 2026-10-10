package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HillsGeneratorTest {
    @Test
    void generatesTheSameChunkForTheSameCoordinates() {
        HillsGenerator generator = new HillsGenerator(42L);
        ChunkData first = generator.generate(new ChunkPos(2, 0, -3));
        ChunkData second = generator.generate(new ChunkPos(2, 0, -3));

        assertEquals(first.position(), second.position());
        assertEquals(java.util.Arrays.hashCode(first.blocks()), java.util.Arrays.hashCode(second.blocks()));
    }

    @Test
    void generationIsIndependentOfRequestOrder() {
        HillsGenerator generator = new HillsGenerator(42L);
        List<ChunkPos> positions = new ArrayList<>();
        for (int z = -5; z < 5; z++) for (int x = -5; x < 5; x++) positions.add(new ChunkPos(x, 0, z));
        Map<ChunkPos, Integer> first = hashes(generator, positions);
        Collections.reverse(positions);
        Map<ChunkPos, Integer> second = hashes(generator, positions);

        assertEquals(first, second);
    }

    @Test
    void generatesGrassDirtAndStoneInTheInitialTerrainLayer() {
        ChunkData data = new HillsGenerator().generate(new ChunkPos(0, 0, 0));
        boolean hasGrass = false;
        boolean hasDirt = false;
        boolean hasStone = false;

        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    hasGrass |= data.get(x, y, z) == BlockId.GRASS;
                    hasDirt |= data.get(x, y, z) == BlockId.DIRT;
                    hasStone |= data.get(x, y, z) == BlockId.STONE;
                }

        assertTrue(hasGrass);
        assertTrue(hasDirt);
        assertTrue(hasStone);
    }

    @Test
    void generatesVerticalSectionsUsingWorldHeight() {
        HillsGenerator generator = new HillsGenerator();
        ChunkData belowSurface = generator.generate(new ChunkPos(0, -1, 0));
        ChunkData surface = generator.generate(new ChunkPos(0, 0, 0));
        ChunkData aboveSurface = generator.generate(new ChunkPos(0, 1, 0));

        assertEquals(BlockId.STONE, belowSurface.get(0, Chunk.SIZE - 1, 0));
        assertEquals(BlockId.STONE, surface.get(0, 0, 0));
        assertEquals(BlockId.AIR, surface.get(0, Chunk.SIZE - 1, 0));
        assertEquals(BlockId.AIR, aboveSurface.get(0, 0, 0));
    }

    private static Map<ChunkPos, Integer> hashes(HillsGenerator generator, List<ChunkPos> positions) {
        Map<ChunkPos, Integer> hashes = new HashMap<>();
        for (ChunkPos position : positions)
            hashes.put(
                    position,
                    java.util.Arrays.hashCode(generator.generate(position).blocks()));
        return hashes;
    }
}
