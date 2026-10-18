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

    @Test
    void doesNotReturnTheCoarseHitWhenAThinCloserBiomeExists() {
        var locator = new BiomeLocator(terrain(BiomeType.FOREST, (x, z) -> z == 0 && (x == 128 || x == 5)));
        var result = locator.locate(BiomeType.FOREST, .5, .5, 256, Long.MAX_VALUE);
        assertEquals(BiomeLocator.Status.FOUND, result.status());
        assertEquals(5, result.match().orElseThrow().x());
    }

    @Test
    void matchesExhaustiveSearchWithFractionalOriginsAndTies() {
        var random = new java.util.Random(42);
        for (int run = 0; run < 30; run++) {
            var points = new java.util.HashSet<String>();
            for (int n = 0; n < 20; n++) points.add((random.nextInt(25) - 12) + "," + (random.nextInt(25) - 12));
            double ox = random.nextDouble(-4, 4), oz = random.nextDouble(-4, 4);
            double nearest = Double.POSITIVE_INFINITY;
            for (int z = -25; z <= 25; z++)
                for (int x = -25; x <= 25; x++)
                    if (points.contains(x + "," + z)) nearest = Math.min(nearest, Math.hypot(x + .5 - ox, z + .5 - oz));
            var locator = new BiomeLocator(terrain(BiomeType.FOREST, (x, z) -> points.contains(x + "," + z)));
            var result = locator.locate(BiomeType.FOREST, ox, oz, 20, Long.MAX_VALUE);
            assertEquals(BiomeLocator.Status.FOUND, result.status());
            assertEquals(nearest, result.match().orElseThrow().distance(), 1e-9);
        }
    }

    @Test
    void measuresRareBiomeSearchesAgainstTheRealGenerator() {
        for (long seed : new long[] {0, 7340215}) {
            var terrain = new TerrainGenerator(seed, TerrainConfig.defaults());
            var locator = new BiomeLocator(terrain);
            for (var biome : new BiomeType[] {BiomeType.ARCHLANDS_CLASSIC, BiomeType.FLOATING_ISLES_CLASSIC}) {
                long start = System.nanoTime();
                var result = locator.locate(biome, .5, .5, 32768, start + 2_000_000_000L);
                assertTrue(result.match().isPresent(), "Expected a verified rare biome candidate");
                result.match()
                        .ifPresent(match -> assertEquals(
                                biome, terrain.sample(match.x(), match.z()).biome()));
                System.out.printf(
                        "seed=%d biome=%s status=%s samples=%d match=%s ms=%.1f%n",
                        seed,
                        biome,
                        result.status(),
                        result.samples(),
                        result.match(),
                        (System.nanoTime() - start) / 1e6);
                assertTrue(result.samples() <= 250_000);
                assertNotEquals(BiomeLocator.Status.NOT_FOUND, result.status());
            }
        }
    }
}
