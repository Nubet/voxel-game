package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class GeographicBiomeTest {
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
        assertEquals(BiomeType.TAIGA, resolver.resolve(cold, 40, 32, 0, false, 28, WaterKind.OCEAN));
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
        var summary = new TerrainSummary(
                80,
                BlockId.TERRACOTTA_RED,
                BlockId.TERRACOTTA_RED,
                BlockId.TERRACOTTA_RED,
                -70,
                TerrainSummary.NO_WATER);
        for (int y = 32; y <= 80; y++) assertEquals(SurfaceMaterialMapper.badlands(y), summary.materialAt(y));
        assertEquals(BlockId.STONE, summary.materialAt(31));
        var summaries = new TerrainSummary[256];
        java.util.Arrays.fill(summaries, summary);
        var column = TerrainColumn.of(summaries, java.util.List.of());
        assertEquals(-1, column.uniformBlock(48));
        assertEquals(BlockId.STONE, column.uniformBlock(0));
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
