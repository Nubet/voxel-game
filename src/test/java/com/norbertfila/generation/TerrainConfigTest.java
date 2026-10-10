package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TerrainConfigTest {
    @Test
    void declaresConservativeHeightBounds() {
        TerrainConfig config = TerrainConfig.defaults();
        assertEquals(-8, config.minHeight());
        assertEquals(147, config.maxHeight());
        TerrainGenerator generator = new TerrainGenerator(42, config);
        for (int z = -2048; z < 2048; z += 17)
            for (int x = -2048; x < 2048; x += 31) {
                int height = generator.surfaceHeight(x, z);
                assertTrue(height >= config.minHeight() && height <= config.maxHeight());
            }
    }

    @Test
    void rejectsInvalidLayersAndUnsafeHeightRanges() {
        TerrainConfig.Layer layer = TerrainConfig.defaults().continental();
        assertThrows(NullPointerException.class, () -> new TerrainConfig(0, null, layer, layer));
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig(Integer.MAX_VALUE, layer, layer, layer));
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig(Integer.MIN_VALUE, layer, layer, layer));
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new TerrainConfig.Layer(.01, 4, .5, 2, invalid));
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig.Layer(0x1p21, 1, .5, 2, 1));
        TerrainConfig.Layer huge = new TerrainConfig.Layer(.01, 4, .5, 2, Double.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> new TerrainConfig(0, huge, huge, huge));
        for (double[] thresholds : new double[][] {
            {-.1, .5}, {.5, .5}, {.7, .4}, {.5, 1.1}, {Double.NaN, .7}, {.5, Double.POSITIVE_INFINITY}
        })
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TerrainConfig.Mountains(layer, layer, thresholds[0], thresholds[1]));
    }
}
