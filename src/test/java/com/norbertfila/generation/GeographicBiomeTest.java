package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class GeographicBiomeTest {
    @Test
    void tinySeaLevelPoolsInPlainsAndDesertAreLakesWithoutChangingTheirWater() {
        var defaults = TerrainConfig.defaults();
        var zero = new TerrainConfig.Layer(1, 1, .5, 2, 0);
        var config = new TerrainConfig(
                32,
                new TerrainConfig.Layer(1, 1, .5, 2, 8),
                zero,
                zero,
                new TerrainConfig.Mountains(zero, zero, .56, .66),
                zero,
                28,
                defaults.temperature(),
                defaults.humidity());
        int[][] centers = {{-2048, -2048}, {-704, -1664}};
        BiomeType[] surroundings = {BiomeType.PLAINS, BiomeType.DESERT};
        for (int i = 0; i < centers.length; i++) {
            int x = centers[i][0], z = centers[i][1];
            var terrain = new TerrainGenerator(
                    0, config, (nx, nz) -> Math.abs(nx - x) <= 2 && Math.abs(nz - z) <= 2 ? -1 : 1);
            var pool = terrain.sample(x, z);
            assertEquals(surroundings[i], terrain.sample(x + 4, z).biome());
            assertEquals(BiomeType.LAKE, pool.biome());
            assertEquals(WaterKind.LAKE, pool.waterKind());
            assertEquals(24, pool.surfaceHeight());
            assertEquals(28, pool.waterLevel());
            assertEquals(BlockId.WATER, pool.materialAt(27));
            assertEquals(
                    BlockId.WATER,
                    terrain.generate(new com.norbertfila.world.ChunkPos(Math.floorDiv(x, 16), 1, Math.floorDiv(z, 16)))
                            .get(Math.floorMod(x, 16), 11, Math.floorMod(z, 16)));
        }
        var ocean = new TerrainGenerator(0, config, (x, z) -> -1).sample(0, 0);
        assertEquals(BiomeType.OCEAN, ocean.biome());
        assertEquals(WaterKind.OCEAN, ocean.waterKind());
    }

    @Test
    void desertClimateDoesNotCarveOrFloodRiverChannelsButOtherRiversRemain() {
        long[] seeds = {0, 42, -1371839073867120140L};
        int[][] deserts = {{2240, 1088, 38}, {2720, -3392, 79}, {-3328, -3808, 113}};
        int[][] rivers = {{-736, -4096, 34}, {3360, -4096, 57}, {-2816, -4096, 36}};
        var resolver = new BiomeResolver();
        for (int i = 0; i < seeds.length; i++) {
            var terrain = new TerrainGenerator(seeds[i], TerrainConfig.defaults(), LandscapeTestConfig.uniform());
            int x = deserts[i][0], z = deserts[i][1], formerWaterY = deserts[i][2];
            var dry = terrain.sample(x, z);
            assertEquals(BiomeType.DESERT, resolver.resolve(dry.climate()));
            assertNotEquals(WaterKind.RIVER, dry.waterKind());
            assertNotEquals(BiomeType.RIVER, dry.biome());
            assertTrue(dry.surfaceHeight() >= formerWaterY, "The original ground must replace the river bed");
            assertTrue(dry.surfaceHeight() >= dry.waterLevel());
            var chunk = terrain.generate(new com.norbertfila.world.ChunkPos(
                    Math.floorDiv(x, 16), Math.floorDiv(formerWaterY, 16), Math.floorDiv(z, 16)));
            assertNotEquals(
                    BlockId.WATER,
                    chunk.get(Math.floorMod(x, 16), Math.floorMod(formerWaterY, 16), Math.floorMod(z, 16)));
            var wet = terrain.sample(rivers[i][0], rivers[i][1]);
            assertEquals(BiomeType.RIVER, wet.biome());
            assertEquals(WaterKind.RIVER, wet.waterKind());
            assertEquals(rivers[i][2], wet.waterLevel());
            assertTrue(wet.surfaceHeight() < wet.waterLevel());
        }
    }

    @Test
    void rockyHighlandsSeparateTaigaFromAlpineWithoutTurningLowCliffsIntoMountains() {
        var resolver = new BiomeResolver();
        var climate = new ClimateSample(-.21f, 0);
        assertEquals(BiomeType.TAIGA, resolver.resolve(climate, 40, 32, 0, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.STONY_SLOPES, resolver.resolve(climate, 100, 32, 0, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.ALPINE, resolver.resolve(climate, 120, 32, 0, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.ALPINE, resolver.resolve(climate, 160, 32, 2, false, 28, WaterKind.OCEAN));
        var warm = new ClimateSample(.4f, .3f);
        assertEquals(BiomeType.FOREST, resolver.resolve(warm, 40, 32, 2, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.FOREST, resolver.resolve(warm, 100, 32, 0, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.STONY_SLOPES, resolver.resolve(warm, 100, 32, 2, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.LAKE, resolver.resolve(climate, 100, 32, 2, true, 110, WaterKind.LAKE));
        var slopes = BiomeRegistry.defaults().profile(BiomeType.STONY_SLOPES);
        assertEquals(0, slopes.trees().density());
        assertTrue(slopes.vegetation().plants().isEmpty());
    }

    @Test
    void swampRequiresWaterAndGentleGroundAndAlpineRequiresAltitude() {
        var resolver = new BiomeResolver();
        var humid = new ClimateSample(.2f, .8f);
        assertEquals(BiomeType.SWAMP, resolver.resolve(humid, 29, 32, .2, true, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.FOREST, resolver.resolve(humid, 29, 32, .2, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.FOREST, resolver.resolve(humid, 29, 32, 2, true, 28, WaterKind.OCEAN));
        var cold = new ClimateSample(-.6f, -.5f);
        assertEquals(BiomeType.SNOWY_TAIGA, resolver.resolve(cold, 40, 32, 0, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.ALPINE, resolver.resolve(cold, 160, 32, 0, false, 28, WaterKind.OCEAN));
    }

    @Test
    void floodedColumnsUseWaterBiomesRegardlessOfClimateButDryShoresKeepLandBiomes() {
        var resolver = new BiomeResolver();
        for (var climate : new ClimateSample[] {
            new ClimateSample(.8f, -.8f), new ClimateSample(-.8f, .8f), new ClimateSample(0, -.8f)
        }) {
            for (var kind : new WaterKind[] {WaterKind.OCEAN, WaterKind.LAKE, WaterKind.RIVER}) {
                var expected = BiomeType.valueOf(kind.name());
                assertEquals(expected, resolver.resolve(climate, 20, 32, 2, true, 28, kind));
                assertEquals(expected, resolver.resolve(climate, 27, 32, 0, true, 28, kind));
                assertNotEquals(expected, resolver.resolve(climate, 28, 32, 0, true, 28, kind));
                assertNotEquals(expected, resolver.resolve(climate, 29, 32, 0, true, 28, kind));
            }
        }
    }

    @Test
    void badlandsExposeVerticalStrataAndReturnToStoneAtDepth() {
        var layers = new BadlandsSurface(42).column(0, 0);
        var summary = new TerrainSummary(
                80,
                layers.bandAt(80),
                layers.bandAt(79),
                BlockId.STONE,
                -70,
                TerrainSummary.NO_WATER,
                BiomeType.BADLANDS,
                new ClimateSample(.5f, 0),
                WaterKind.NONE,
                false,
                false,
                layers);
        for (int y = -16; y <= 80; y++) assertEquals(layers.bandAt(y), summary.materialAt(y));
        assertEquals(BlockId.STONE, summary.materialAt(-17));
        var summaries = new TerrainSummary[256];
        java.util.Arrays.fill(summaries, summary);
        var column = TerrainColumn.of(summaries, java.util.List.of());
        assertEquals(-1, column.uniformBlock(48));
        assertEquals(BlockId.STONE, column.uniformBlock(-48));
    }

    @Test
    void surfaceMaterialsUseIndependentRegisteredBlocks() {
        var mapper = new SurfaceMaterialMapper();
        var ids = new java.util.HashSet<Byte>();
        for (SurfaceMaterial material : SurfaceMaterial.values())
            assertTrue(ids.add(mapper.surface(material)), material.name());
        assertEquals(BlockId.PACKED_ICE, mapper.subsurface(SurfaceMaterial.ICE));
    }
}
