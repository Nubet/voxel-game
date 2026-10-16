package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BiomeDecorationsTest {
    @Test
    void everySpecialPlantIsReachableAndUsesItsOwnBlock() {
        var registry = BiomeRegistry.defaults();
        Map<PlantType, Byte> expected = Map.of(
                PlantType.CACTUS,
                BlockId.CACTUS,
                PlantType.DEAD_BUSH,
                BlockId.DEAD_BUSH,
                PlantType.REED,
                BlockId.REED,
                PlantType.MUSHROOM,
                BlockId.MUSHROOM,
                PlantType.BERRY_BUSH,
                BlockId.BERRY_BUSH,
                PlantType.ALPINE_GRASS,
                BlockId.ALPINE_GRASS);
        var found = new HashSet<Byte>();
        int berryBushes = 0;
        for (BiomeType biome : BiomeType.values()) {
            var summaries = new TerrainSummary[256];
            byte ground = biome == BiomeType.DESERT || biome == BiomeType.BADLANDS ? BlockId.SAND : BlockId.GRASS;
            Arrays.fill(
                    summaries,
                    new TerrainSummary(
                            0,
                            ground,
                            BlockId.DIRT,
                            BlockId.STONE,
                            TerrainSummary.NO_BEDROCK,
                            0,
                            biome,
                            new ClimateSample(0, 0)));
            var column = TerrainColumn.of(summaries, List.of());
            var generator = new VegetationGenerator(42, (x, z) -> 1, (x, z) -> 1, registry);
            for (int chunk = 0; chunk < 64; chunk++) {
                byte[] blocks = new byte[4096];
                generator.generate(chunk * Chunk.SIZE, 0, 0, blocks, column);
                for (byte block : blocks)
                    if (block != BlockId.AIR) {
                        assertTrue(PlantRules.supports(block, ground, true));
                        found.add(block);
                        if (block == BlockId.BERRY_BUSH) berryBushes++;
                    }
            }
        }
        assertTrue(found.containsAll(expected.values()), "Generated species: " + found);
        double berryDensity = berryBushes / (64d * Chunk.SIZE * Chunk.SIZE);
        assertTrue(berryDensity > .002 && berryDensity < .008, "Berry bush density: " + berryDensity);
    }

    @Test
    void reedsGenerateOnSandInEveryBiomeOnlyNearWater() {
        var generator = new VegetationGenerator(42, (x, z) -> 1, (x, z) -> 1);
        for (BiomeType biome : BiomeType.values()) {
            for (int waterLevel : new int[] {0, TerrainSummary.NO_WATER}) {
                var summaries = new TerrainSummary[Chunk.SIZE * Chunk.SIZE];
                Arrays.fill(
                        summaries,
                        new TerrainSummary(
                                0,
                                BlockId.SAND,
                                BlockId.SAND,
                                BlockId.STONE,
                                TerrainSummary.NO_BEDROCK,
                                waterLevel,
                                biome,
                                new ClimateSample(0, 0)));
                byte[] blocks = new byte[4096];
                generator.generate(0, 0, 0, blocks, TerrainColumn.of(summaries, List.of()));
                boolean foundReed = false;
                for (byte block : blocks) foundReed |= block == BlockId.REED;
                assertEquals(waterLevel == 0, foundReed, "Reeds in " + biome + " with water level " + waterLevel);
            }
        }
    }

    @Test
    void conifersTaperAndUsePineTextures() {
        var tree = new TreeGenerator.Tree(8, 8, 1, 7, 2, 42, TreeType.CONIFER);
        byte[] blocks = new byte[4096];
        TreeGenerator.place(List.of(tree), 0, 0, 0, blocks);
        assertEquals(BlockId.PINE_LOG, blocks[(1 * 16 + 8) * 16 + 8]);
        int lower = 0, upper = 0;
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                if (blocks[(6 * 16 + z) * 16 + x] == BlockId.PINE_NEEDLES) lower++;
                if (blocks[(10 * 16 + z) * 16 + x] == BlockId.PINE_NEEDLES) upper++;
            }
        assertTrue(lower > upper && upper > 0);
    }
}
