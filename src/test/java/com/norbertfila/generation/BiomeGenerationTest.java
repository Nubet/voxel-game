package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.biome.BiomeType;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class BiomeGenerationTest {
    @Test
    void climateAndBiomeSamplesAreDeterministicAcrossChunkSeams() {
        var first = new TerrainGenerator(7340215L, TerrainConfig.defaults());
        var second = new TerrainGenerator(7340215L, TerrainConfig.defaults());
        EnumSet<BiomeType> biomes = EnumSet.noneOf(BiomeType.class);
        for (int z = -1024; z <= 1024; z += 16)
            for (int x = -1024; x <= 1024; x += 16) {
                var a = first.sample(x, z);
                var b = second.sample(x, z);
                assertEquals(a, b);
                biomes.add(a.biome());
            }
        assertTrue(biomes.size() >= 2);
    }

    @Test
    void biomeClimateDoesNotChangeContinuousTerrainHeight() {
        var shape = TerrainConfig.defaults();
        var climate =
                shape.withClimate(new TerrainConfig.Layer(.02, 2, .5, 2, 1), new TerrainConfig.Layer(.03, 2, .5, 2, 1));
        var first = new TerrainGenerator(42L, shape);
        var second = new TerrainGenerator(42L, climate);
        for (int z = -128; z <= 128; z += 16)
            for (int x = -128; x <= 128; x += 16) assertEquals(first.surfaceHeight(x, z), second.surfaceHeight(x, z));
    }
}
