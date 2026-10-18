package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.*;
import java.util.concurrent.CancellationException;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

class BiomeLocatorTest {
    static TerrainSource terrain(BiomeType target, BiPredicate<Integer, Integer> matches) {
        return new TerrainSource() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public int surfaceHeight(int x, int z) {
                return 70;
            }

            public TerrainSummary sample(int x, int z) {
                return new TerrainSummary(
                        70,
                        BlockId.GRASS,
                        BlockId.DIRT,
                        BlockId.STONE,
                        TerrainSummary.NO_BEDROCK,
                        TerrainSummary.NO_WATER,
                        matches.test(x, z) ? target : BiomeType.PLAINS,
                        new ClimateSample(0, 0));
            }

            public ChunkData generate(ChunkPos pos) {
                throw new AssertionError("Search must not generate chunks");
            }
        };
    }

    @Test
    void findsNearestColumnRatherThanFirstRingHitForEveryBiome() {
        for (var biome : BiomeType.values()) {
            if (biome == BiomeType.PLAINS) continue;
            var locator = new BiomeLocator(terrain(biome, (x, z) -> x == -4 && z == -4 || x == 5 && z == 0));
            var result = locator.locate(biome, .5, .5, 8, Long.MAX_VALUE);
            assertEquals(BiomeLocator.Status.FOUND, result.status());
            assertEquals(5, result.match().orElseThrow().x());
            assertEquals(0, result.match().orElseThrow().z());
            assertEquals(5, result.match().orElseThrow().distance());
        }
    }

    @Test
    void handlesMissingBiomesLimitsWorldEdgesAndCancellation() {
        var locator = new BiomeLocator(terrain(BiomeType.FOREST, (x, z) -> false));
        assertEquals(
                BiomeLocator.Status.NOT_FOUND,
                locator.locate(BiomeType.FOREST, -2.5, -2.5, 4, Long.MAX_VALUE).status());
        assertEquals(
                BiomeLocator.Status.NOT_FOUND,
                locator.locate(BiomeType.FOREST, Integer.MAX_VALUE, Integer.MIN_VALUE, 2, Long.MAX_VALUE)
                        .status());
        assertEquals(
                BiomeLocator.Status.LIMIT_REACHED,
                locator.locate(BiomeType.FOREST, 0, 0, 4, Long.MIN_VALUE).status());
        Thread.currentThread().interrupt();
        try {
            assertThrows(CancellationException.class, () -> locator.locate(BiomeType.FOREST, 0, 0, 4, Long.MAX_VALUE));
        } finally {
            Thread.interrupted();
        }
    }
}
