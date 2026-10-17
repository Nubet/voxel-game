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
    void forestFloorHasMostlyGrassWithLocalSoilAndStonePatches() {
        var noise = new PerlinNoise(42);
        int grass = 0, podzol = 0, dirt = 0, stone = 0;
        for (int z = -128; z < 128; z++)
            for (int x = -128; x < 128; x++) {
                byte material =
                        resolver.forestFloor(noise.sample(x * .035, z * .035)).surface();
                if (material == BlockId.GRASS) grass++;
                else if (material == BlockId.PODZOL) podzol++;
                else if (material == BlockId.DIRT) dirt++;
                else if (material == BlockId.STONE) stone++;
            }
        org.junit.jupiter.api.Assertions.assertTrue(grass > 65536 * .55);
        org.junit.jupiter.api.Assertions.assertTrue(podzol > 65536 * .05 && podzol < 65536 * .3);
        org.junit.jupiter.api.Assertions.assertTrue(dirt > 0 && stone > 0 && stone < 65536 * .15);
        assertEquals(65536, grass + podzol + dirt + stone);
    }

    @Test
    void generatedMountainTransectHasRootableTaigaThenRockThenSnow() {
        var terrain = new TerrainGenerator(0, TerrainConfig.defaults());
        int[] xs = {1088, 1184, 1216};
        BiomeType[] biomes = {BiomeType.SNOWY_TAIGA, BiomeType.STONY_SLOPES, BiomeType.ALPINE};
        byte[] ground = {BlockId.GRASS, BlockId.STONE, BlockId.SNOW};
        for (int i = 0; i < xs.length; i++) {
            var sample = terrain.sample(xs[i], -384);
            assertEquals(biomes[i], sample.biome());
            assertEquals(ground[i], sample.surfaceMaterial());
            var chunk = terrain.generate(new com.norbertfila.world.ChunkPos(
                    Math.floorDiv(xs[i], 16), Math.floorDiv(sample.surfaceHeight(), 16), -24));
            assertEquals(ground[i], chunk.get(Math.floorMod(xs[i], 16), Math.floorMod(sample.surfaceHeight(), 16), 0));
        }
    }

    @Test
    void alpineBoundaryDoesNotCreateSnowFromHumidityAlone() {
        var biomes = new com.norbertfila.generation.biome.BiomeResolver();
        for (float humidity : new float[] {-.01f, .01f}) {
            var climate = new ClimateSample(-.21f, humidity);
            var profile = registry.profile(biomes.resolve(climate, 40, 32, 0, false, 28, WaterKind.OCEAN));
            assertEquals(
                    BlockId.GRASS,
                    resolver.resolve(40, 0, 28, 32, climate, profile).surface());
        }
    }

    @Test
    void coolingCrossesRockBeforeSnowAndUsesGravelAtColdShores() {
        var profile = registry.profile(BiomeType.PLAINS);
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
    void taigaKeepsRootableSoilWhileMountainProfilesUseRockAndSnow() {
        var biomes = new com.norbertfila.generation.biome.BiomeResolver();
        var climate = new ClimateSample(-.21f, 0);
        int[] heights = {40, 100, 120, 160};
        BiomeType[] expectedBiomes = {BiomeType.TAIGA, BiomeType.STONY_SLOPES, BiomeType.ALPINE, BiomeType.ALPINE};
        byte[] expectedSurfaces = {BlockId.GRASS, BlockId.STONE, BlockId.SNOW, BlockId.SNOW};
        for (int i = 0; i < heights.length; i++) {
            var biome = biomes.resolve(climate, heights[i], 32, 0, false, 28, WaterKind.OCEAN);
            assertEquals(expectedBiomes[i], biome);
            var surface = resolver.resolve(heights[i], 0, 28, 32, climate, registry.profile(biome));
            assertEquals(expectedSurfaces[i], surface.surface());
        }
        var cold = new ClimateSample(-.8f, .2f);
        var taiga = resolver.resolve(40, 0, 28, 32, cold, registry.profile(BiomeType.TAIGA));
        assertEquals(BlockId.GRASS, taiga.surface());
        assertEquals(BlockId.DIRT, taiga.subsurface());
        var temperate = new ClimateSample(0, 0);
        var belowSnowline = biomes.resolve(temperate, 130, 32, 0, false, 28, WaterKind.OCEAN);
        assertEquals(BiomeType.STONY_SLOPES, belowSnowline);
        assertEquals(
                BlockId.STONE,
                resolver.resolve(130, 0, 28, 32, temperate, registry.profile(belowSnowline))
                        .surface());
        assertEquals(
                BlockId.SNOW,
                resolver.resolve(130, 0, 28, 32, new ClimateSample(.1f, 0), registry.profile(BiomeType.ALPINE))
                        .surface());
        assertEquals(
                BlockId.STONE,
                resolver.resolve(130, 2, 28, 32, cold, registry.profile(BiomeType.ALPINE))
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
