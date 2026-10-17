package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import org.junit.jupiter.api.Test;

class JungleBiomeTest {
    @Test
    void tropicalLandRetainsWaterAndMountainOverrides() {
        var resolver = new BiomeResolver();
        var tropical = new ClimateSample(.6f, .6f);
        assertEquals(BiomeType.JUNGLE, resolver.resolve(tropical));
        assertEquals(BiomeType.JUNGLE, resolver.resolve(tropical, 48, 32, .3, false, 28, WaterKind.OCEAN));
        assertEquals(BiomeType.RIVER, resolver.resolve(tropical, 26, 32, 0, true, 28, WaterKind.RIVER));
        assertEquals(BiomeType.SWAMP, resolver.resolve(tropical, 29, 32, .2, true, 28, WaterKind.RIVER));
        assertNotEquals(BiomeType.JUNGLE, resolver.resolve(new ClimateSample(.6f, -.3f)));
        assertNotEquals(BiomeType.JUNGLE, resolver.resolve(new ClimateSample(-.5f, .6f)));
        assertEquals(
                TreeType.JUNGLE,
                BiomeRegistry.defaults().profile(BiomeType.JUNGLE).trees().type());
    }

    @Test
    void tropicalTerrainWeightHasSmoothEdges() {
        assertEquals(0, JungleRules.climateWeight(new ClimateSample(.22f, .8f)), 1e-12);
        assertEquals(1, JungleRules.climateWeight(new ClimateSample(.6f, .6f)), 1e-12);
        double before = JungleRules.climateWeight(new ClimateSample(.3f - .0001f, .6f));
        double after = JungleRules.climateWeight(new ClimateSample(.3f + .0001f, .6f));
        assertTrue(after > before && after - before < .002);
    }
}
