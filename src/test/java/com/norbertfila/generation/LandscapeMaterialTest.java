package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.generation.biome.SurfaceMaterialMapper;
import com.norbertfila.generation.biome.SurfaceMaterialResolver;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class LandscapeMaterialTest {
    private final SurfaceMaterialResolver resolver = new SurfaceMaterialResolver(new SurfaceMaterialMapper());
    private final BiomeRegistry registry = BiomeRegistry.defaults();

    @Test
    void alpineBoundaryDoesNotCreateSnowFromHumidityAlone() {
        var biomes = new com.norbertfila.generation.biome.BiomeResolver();
        for (float humidity : new float[] {-.01f, .01f}) {
            var climate = new ClimateSample(-.21f, humidity);
            var profile = registry.profile(biomes.resolve(climate));
            assertEquals(
                    BlockId.GRASS,
                    resolver.resolve(40, 0, 28, 32, climate, profile).surface());
        }
    }

    @Test
    void coolingCrossesRockBeforeSnowAndUsesGravelAtColdShores() {
        var profile = registry.profile(BiomeType.ALPINE);
        byte[] expected = {BlockId.GRASS, BlockId.STONE, BlockId.SNOW};
        float[] temperatures = {-.21f, -.4f, -.7f};
        for (int i = 0; i < temperatures.length; i++) {
            var climate = new ClimateSample(temperatures[i], -.5f);
            assertEquals(
                    expected[i],
                    resolver.resolve(32, 0, 28, 32, climate, profile).surface());
        }
        assertEquals(
                BlockId.SAND,
                resolver.resolve(28, 0, 28, 32, new ClimateSample(.2f, 0), profile)
                        .surface());
        assertEquals(
                BlockId.GRAVEL,
                resolver.resolve(28, 0, 28, 32, new ClimateSample(-.7f, 0), profile)
                        .surface());
    }

    @Test
    void altitudeCoolsContinuouslyInsteadOfSwitchingAtFixedHeight() {
        var climate = new ClimateSample(0, 0);
        var profile = registry.profile(BiomeType.PLAINS);
        assertEquals(
                BlockId.GRASS,
                resolver.resolve(101, 0, 28, 32, climate, profile).surface());
        assertEquals(
                BlockId.GRASS,
                resolver.resolve(102, 0, 28, 32, climate, profile).surface());
        assertEquals(
                BlockId.STONE,
                resolver.resolve(132, 0, 28, 32, climate, profile).surface());
        assertEquals(
                BlockId.SNOW, resolver.resolve(182, 0, 28, 32, climate, profile).surface());
        assertEquals(
                BlockId.STONE,
                resolver.resolve(182, 2, 28, 32, climate, profile).surface());
    }

    @Test
    void terrainHeightsRemainIndependentFromBiomeResolution() {
        var config = TerrainConfig.defaults();
        var alteredClimate = config.withClimate(
                new TerrainConfig.Layer(.03, 2, .5, 2, 1), new TerrainConfig.Layer(.01, 1, .5, 2, 1));
        for (long seed : new long[] {0, 42, -7, 1234}) {
            var terrain = new TerrainGenerator(seed, config);
            var altered = new TerrainGenerator(seed, alteredClimate);
            for (int z = -512; z <= 512; z += 64)
                for (int x = -512; x <= 512; x += 64)
                    assertEquals(terrain.surfaceHeight(x, z), altered.surfaceHeight(x, z));
        }
    }

    @Test
    void profilesChooseSurfaceMaterialsWithoutChangingTerrainShape() {
        var resolver = new SurfaceMaterialResolver(new SurfaceMaterialMapper());
        var registry = BiomeRegistry.defaults();
        var plains = resolver.resolve(32, 0, 28, 32, new ClimateSample(0, 0), registry.profile(BiomeType.PLAINS));
        var desert = resolver.resolve(32, 0, 0, 32, new ClimateSample(.8f, -.8f), registry.profile(BiomeType.DESERT));
        var alpine = resolver.resolve(110, 0, 0, 32, new ClimateSample(-.8f, -.8f), registry.profile(BiomeType.ALPINE));
        assertEquals(BlockId.GRASS, plains.surface());
        assertEquals(BlockId.SAND, desert.surface());
        assertEquals(BlockId.SNOW, alpine.surface());
    }
}
