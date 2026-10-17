package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import org.junit.jupiter.api.Test;

class SnowRulesTest {
    @Test
    void climateProducesGreenTaigaAndNearlyContinuousColdSnowyTaiga() {
        var noise = new PerlinNoise(42);
        int covered = 0, total = 0;
        for (int z = -128; z < 128; z++)
            for (int x = -128; x < 128; x++) {
                double variation = noise.sample(x * .018, z * .018);
                assertFalse(SnowRules.covered(BiomeType.TAIGA, -.3f, 0, variation));
                if (SnowRules.covered(BiomeType.SNOWY_TAIGA, -.6f, 0, variation)) covered++;
                assertFalse(SnowRules.covered(BiomeType.SNOWY_TAIGA, -.6f, 2, variation));
                total++;
            }
        assertTrue(covered > total * .85 && covered < total);
    }
}
