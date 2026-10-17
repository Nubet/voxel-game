package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.generation.biome.BiomeResolver;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import com.norbertfila.generation.biome.SurfaceMaterial;
import com.norbertfila.generation.biome.TreeType;
import org.junit.jupiter.api.Test;

class BiomeModelTest {
    private final BiomeResolver resolver = new BiomeResolver();

    @Test
    void bothTaigasHaveIndependentForestProfilesOnGrass() {
        var registry = BiomeRegistry.defaults();
        var green = registry.profile(BiomeType.TAIGA);
        var snowy = registry.profile(BiomeType.SNOWY_TAIGA);
        for (var profile : java.util.List.of(green, snowy)) {
            assertEquals(SurfaceMaterial.GRASS, profile.surface().surface());
            assertEquals(TreeType.CONIFER, profile.trees().type());
        }
        assertTrue(green.trees().density() > snowy.trees().density());
        assertTrue(green.vegetation().grassDensity() > snowy.vegetation().grassDensity());
        assertTrue(green.vegetation().mushroomDensity() > snowy.vegetation().mushroomDensity());
    }

    @Test
    void resolvesClimateBands() {
        assertEquals(BiomeType.DESERT, resolver.resolve(new ClimateSample(.8f, -.8f)));
        assertEquals(BiomeType.SWAMP, resolver.resolve(new ClimateSample(.2f, .8f)));
        assertEquals(BiomeType.SNOWY_TAIGA, resolver.resolve(new ClimateSample(-.8f, .8f)));
        assertEquals(BiomeType.SNOWY_TAIGA, resolver.resolve(new ClimateSample(-.8f, -.8f)));
        assertEquals(BiomeType.TAIGA, resolver.resolve(new ClimateSample(-.3f, .3f)));
        assertEquals(BiomeType.PLAINS, resolver.resolve(new ClimateSample(0, -.8f)));
    }

    @Test
    void defaultRegistryProfilesEveryBiome() {
        var registry = BiomeRegistry.defaults();
        for (BiomeType type : BiomeType.values()) {
            var profile = registry.profile(type);
            assertNotNull(profile);
            assertEquals(profile, registry.profile(type));
        }
    }

    @Test
    void climateMustBeBounded() {
        assertThrows(IllegalArgumentException.class, () -> new ClimateSample(2, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClimateSample(0, -2));
    }
}
