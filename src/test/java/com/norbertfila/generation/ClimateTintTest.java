package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BiomeResolver;
import com.norbertfila.generation.biome.BiomeTint;
import com.norbertfila.generation.biome.ClimateSample;
import org.junit.jupiter.api.Test;

class ClimateTintTest {
    @Test
    void colorChangesInsideTheSameBiome() {
        var dry = new ClimateSample(0, -.05f);
        var wet = new ClimateSample(0, .3f);
        var resolver = new BiomeResolver();
        assertEquals(resolver.resolve(dry), resolver.resolve(wet));
        assertNotEquals(BiomeTint.climateIndex(dry), BiomeTint.climateIndex(wet));
    }

    @Test
    void climateBlendingMatchesOnBothSidesOfGridBoundaries() {
        var a = new ClimateSample(-.8f, .1f);
        var b = new ClimateSample(.1f, -.3f);
        var c = new ClimateSample(.8f, -.8f);
        assertEquals(BiomeTint.climateIndex(b), BiomeTint.blend(a, b, a, b, 1, .5));
        assertEquals(BiomeTint.climateIndex(b), BiomeTint.blend(b, c, b, c, 0, .5));
        for (int i = 0; i <= 100; i++) {
            int index = BiomeTint.blend(a, c, b, c, i / 100d, .5);
            assertTrue(index >= 0 && index < 32);
            for (float channel : BiomeTint.color(index, false)) assertTrue(channel >= 0 && channel <= 1);
        }
    }
}
