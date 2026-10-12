package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TerrainConfigTest {
    @Test
    void declaresConservativeHeightBounds() {
        TerrainConfig config = TerrainConfig.defaults();
        assertEquals(-18, config.minHeight());
        assertEquals(300, config.maxHeight());
        TerrainGenerator generator = new TerrainGenerator(42, config);
        for (int z = -2048; z < 2048; z += 17)
            for (int x = -2048; x < 2048; x += 31) {
                int height = generator.surfaceHeight(x, z);
                assertTrue(height >= config.minHeight() && height <= config.maxHeight());
            }
    }

    @Test
    void waterLevelIsExplicitValidatedAndPreservedByTerrainVariants() {
        TerrainConfig c = TerrainConfig.defaults();
        assertEquals(28, c.waterLevel());
        TerrainConfig.Layer flat = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        assertEquals(Integer.MIN_VALUE + 28, new TerrainConfig(Integer.MIN_VALUE + 32, flat, flat, flat).waterLevel());
        TerrainConfig configured =
                new TerrainConfig(c.baseHeight(), c.continental(), c.hills(), c.details(), c.mountains(), c.warp(), 45);
        assertEquals(45, configured.withoutWarp().waterLevel());
        assertEquals(45, configured.withoutMountains().waterLevel());
        for (int unsafe : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TerrainConfig(
                            c.baseHeight(), c.continental(), c.hills(), c.details(), c.mountains(), c.warp(), unsafe));
    }

    @Test
    void validatesShapingAndWarpedCoordinatePrecision() {
        TerrainConfig config = TerrainConfig.defaults();
        TerrainConfig.Mountains mountains = config.mountains();
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TerrainConfig.Mountains(
                            mountains.region(), mountains.ridge(), .54, .62, mountains.ranges(), invalid, 1.25, true));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TerrainConfig.Mountains(
                            mountains.region(), mountains.ridge(), .54, .62, mountains.ranges(), 3, invalid, true));
        }
        TerrainConfig.Layer hugeWarp = new TerrainConfig.Layer(.003, 3, .5, 2, Double.MAX_VALUE);
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainConfig(
                        config.baseHeight(),
                        config.continental(),
                        config.hills(),
                        config.details(),
                        mountains,
                        hugeWarp));
        assertEquals(0, config.withoutWarp().warp().amplitude());
        assertEquals(config.mountains(), config.withoutWarp().mountains());
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
