package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BiomeResolver;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import org.junit.jupiter.api.Test;

class BadlandsLandformsTest {
    @Test
    void erosionLeavesQuietFloorsButAlsoTallNarrowFormationsAndSteepCliffs() {
        for (long seed : new long[] {0, 42, -918273}) {
            var terrain = new BadlandsLandforms(seed);
            double min = 1, max = -1;
            int quiet = 0, cliffs = 0, pinnacles = 0, total = 0;
            for (int z = -256; z < 256; z += 2)
                for (int x = -256; x < 256; x += 2) {
                    double height = terrain.sample(x, z);
                    min = Math.min(min, height);
                    max = Math.max(max, height);
                    assertTrue(height >= RegionalLandforms.MIN_ELEVATION && height <= RegionalLandforms.MAX_ELEVATION);
                    double slope = Math.max(
                            Math.abs(height - terrain.sample(x + 1, z)), Math.abs(height - terrain.sample(x, z + 1)));
                    if (slope < .015) quiet++;
                    if (slope > .035) cliffs++;
                    if (height > .5
                            && height - terrain.sample(x + 16, z) > .2
                            && height - terrain.sample(x - 16, z) > .2) pinnacles++;
                    total++;
                }
            String metrics = "seed=" + seed + " relief=" + (max - min) + " quiet=" + quiet + " cliffs=" + cliffs
                    + " pinnacles=" + pinnacles;
            assertTrue(max - min > .6, metrics);
            assertTrue(quiet > total / 3, metrics);
            assertTrue(cliffs > total / 50, metrics);
            assertTrue(pinnacles > 10, metrics);
        }
    }

    @Test
    void shapeIsSeededAndContinuousAcrossPositiveAndNegativeChunkBoundaries() {
        var terrain = new BadlandsLandforms(42);
        var same = new BadlandsLandforms(42);
        var different = new BadlandsLandforms(43);
        int changed = 0;
        for (int z = -1024; z <= 1024; z += 16)
            for (int x = -1024; x <= 1024; x += 16) {
                double height = terrain.sample(x, z);
                assertEquals(height, same.sample(x, z));
                if (Math.abs(height - different.sample(x, z)) > .1) changed++;
                assertEquals(height, terrain.sample(x - 1e-6, z), .00001);
                assertEquals(height, terrain.sample(x + 1e-6, z), .00001);
                assertEquals(height, terrain.sample(x, z - 1e-6), .00001);
                assertEquals(height, terrain.sample(x, z + 1e-6), .00001);
            }
        assertTrue(changed > 1000);
    }

    @Test
    void climateBlendStaysInsideBadlandsAndFadesAtItsBorders() {
        assertEquals(1, BadlandsLandforms.weight(new ClimateSample(.3f, 0)), 1e-6);
        for (var climate : new ClimateSample[] {
            new ClimateSample(.19f, 0), new ClimateSample(.3f, .26f), new ClimateSample(.4f, -.1f)
        }) assertEquals(0, BadlandsLandforms.weight(climate));
        for (float temperature : new float[] {.2f, .35f}) {
            double weight = BadlandsLandforms.weight(new ClimateSample(temperature, -.1f));
            assertEquals(weight, BadlandsLandforms.weight(new ClimateSample(temperature - 1e-5f, -.1f)), 1e-5);
            assertEquals(weight, BadlandsLandforms.weight(new ClimateSample(temperature + 1e-5f, -.1f)), 1e-5);
        }
    }

    @Test
    void highBadlandsCliffsKeepTheirBiomeAndFloodedValleysStillUseWaterBiomes() {
        var resolver = new BiomeResolver();
        var climate = new ClimateSample(.3f, 0);
        assertEquals(BiomeType.BADLANDS, resolver.resolve(climate, 120, 32, 12, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.RIVER, resolver.resolve(climate, 40, 32, 12, true, 42, WaterKind.RIVER));
    }
}
