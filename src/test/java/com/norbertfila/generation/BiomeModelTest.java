package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.norbertfila.generation.biome.BiomeRegistry;
import com.norbertfila.generation.biome.BiomeResolver;
import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.generation.biome.ClimateSample;
import org.junit.jupiter.api.Test;

class BiomeModelTest {
    private final BiomeResolver resolver = new BiomeResolver();

    @Test
    void resolvesClimateBands() {
        assertEquals(BiomeType.DESERT, resolver.resolve(new ClimateSample(.8f, -.8f)));
        assertEquals(BiomeType.SWAMP, resolver.resolve(new ClimateSample(.2f, .8f)));
        assertEquals(BiomeType.TAIGA, resolver.resolve(new ClimateSample(-.8f, .8f)));
        assertEquals(BiomeType.ALPINE, resolver.resolve(new ClimateSample(-.8f, -.8f)));
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
