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
    @Test
    void terrainHeightsRemainIndependentFromBiomeResolution() {
        assertEquals(26, new TerrainGenerator(0, TerrainConfig.defaults()).surfaceHeight(-48, -144));
        assertEquals(220, new TerrainGenerator(42, TerrainConfig.defaults()).surfaceHeight(-272, 240));
        assertEquals(26, new TerrainGenerator(-7, TerrainConfig.defaults()).surfaceHeight(-512, 512));
        assertEquals(44, new TerrainGenerator(1234, TerrainConfig.defaults()).surfaceHeight(-48, -144));
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
