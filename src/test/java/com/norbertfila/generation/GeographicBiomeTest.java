package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class GeographicBiomeTest {
    @Test
    void swampRequiresWaterAndGentleGroundAndAlpineRequiresAltitude() {
        var resolver = new BiomeResolver();
        var humid = new ClimateSample(.2f, .8f);
        assertEquals(BiomeType.SWAMP, resolver.resolve(humid, 29, 32, .2, true, 28));
        assertEquals(BiomeType.FOREST, resolver.resolve(humid, 29, 32, .2, false, 28));
        assertEquals(BiomeType.FOREST, resolver.resolve(humid, 29, 32, 2, true, 28));
        var cold = new ClimateSample(-.6f, -.5f);
        assertEquals(BiomeType.TAIGA, resolver.resolve(cold, 40, 32, 0, false, 28));
        assertEquals(BiomeType.ALPINE, resolver.resolve(cold, 160, 32, 0, false, 28));
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
