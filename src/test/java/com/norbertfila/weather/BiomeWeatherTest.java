package com.norbertfila.weather;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.biome.*;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class BiomeWeatherTest {
    @Test
    void everyBiomeHasRulesAndTemperatureBlendsRainWithSnow() {
        for (var biome : BiomeType.values()) assertNotNull(BiomeWeather.forBiome(biome, 0));
        assertEquals(0, BiomeWeather.forBiome(BiomeType.DESERT, 1).rain());
        assertTrue(BiomeWeather.forBiome(BiomeType.JUNGLE, 1).rain()
                > BiomeWeather.forBiome(BiomeType.FOREST, 0).rain());
        assertEquals(1, BiomeWeather.forBiome(BiomeType.TUNDRA, 0).snow());
        var mixed = BiomeWeather.forBiome(BiomeType.TAIGA, -.45f);
        assertEquals(mixed.rain(), mixed.snow(), .00001);
        assertEquals(.9f, mixed.rain() + mixed.snow(), .00001);
    }

    @Test
    void bordersAreContinuousAtNegativeCoordinatesAndSamplingIsCached() {
        int[] calls = {0};
        TerrainSource source = new TerrainSource() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public int surfaceHeight(int x, int z) {
                return 0;
            }

            public ChunkData generate(ChunkPos pos) {
                throw new AssertionError("Weather must not generate chunks");
            }

            public TerrainSummary sample(int x, int z) {
                calls[0]++;
                return new TerrainSummary(
                        0,
                        BlockId.GRASS,
                        BlockId.DIRT,
                        BlockId.STONE,
                        TerrainSummary.NO_BEDROCK,
                        TerrainSummary.NO_WATER,
                        x < 0 ? BiomeType.FOREST : BiomeType.DESERT,
                        new ClimateSample(0, 0),
                        WaterKind.NONE,
                        false);
            }
        };
        var weather = new BiomeWeather(source);
        assertEquals(1.1f, weather.sample(-32, -32).rain());
        var middle = weather.sample(-8, -8);
        assertEquals(.55f, middle.rain(), .00001);
        int before = calls[0];
        weather.sample(-8, -8);
        assertEquals(before, calls[0]);
        assertEquals(weather.sample(-.001, 0).rain(), weather.sample(.001, 0).rain(), .00001);
        assertEquals(0, weather.sample(32, 32).rain());
    }
}
