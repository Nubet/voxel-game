package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LandscapeAcceptanceTest {
    @Test
    void multipleSeedsContainEveryBiomeAndInlandWaterWithoutConcentratingAllLandAtSeaLevel() {
        EnumSet<BiomeType> biomes = EnumSet.noneOf(BiomeType.class);
        EnumSet<WaterKind> waters = EnumSet.noneOf(WaterKind.class);
        int low = 0, upland = 0, total = 0;
        for (long seed : new long[] {0, 42, 7340215, -918273}) {
            var terrain = new TerrainGenerator(seed, TerrainConfig.defaults());
            for (int z = -4096; z <= 4096; z += 64)
                for (int x = -4096; x <= 4096; x += 64) {
                    var sample = terrain.sample(x, z);
                    biomes.add(sample.biome());
                    if (sample.flooded()) waters.add(sample.waterKind());
                    if (sample.surfaceHeight() < 35) low++;
                    if (sample.surfaceHeight() > 80) upland++;
                    total++;
                }
        }
        assertEquals(EnumSet.allOf(BiomeType.class), biomes);
        assertTrue(waters.containsAll(EnumSet.of(WaterKind.OCEAN, WaterKind.RIVER, WaterKind.LAKE)));
        assertTrue(low > total / 10 && upland > total / 10);
    }

    @Test
    void climateAmplitudeIsAppliedAndSaturatesWithinTheDomain() {
        var defaults = TerrainConfig.defaults();
        var zero = new TerrainConfig.Layer(.001, 2, .5, 2, 0);
        var disabled = new ClimateNoise(42, defaults.withClimate(zero, zero));
        var huge = new TerrainConfig.Layer(.001, 2, .5, 2, 100);
        var saturated = new ClimateNoise(42, defaults.withClimate(huge, huge));
        for (int x = -1024; x < 1024; x += 31) {
            assertEquals(new ClimateSample(0, 0), disabled.sample(x, 17));
            var climate = saturated.sample(x, 17);
            assertTrue(climate.temperature() >= -1 && climate.temperature() <= 1);
            assertTrue(climate.humidity() >= -1 && climate.humidity() <= 1);
        }
    }
}
