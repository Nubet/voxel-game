package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MountainTerrainTest {
    @Test
    void maskHasSmoothClampedTransitions() {
        assertEquals(0, TerrainGenerator.mountainMask(-1, .25, .65));
        assertEquals(0, TerrainGenerator.mountainMask(.25, .25, .65));
        assertEquals(.5, TerrainGenerator.mountainMask(.45, .25, .65), 1e-15);
        assertEquals(1, TerrainGenerator.mountainMask(.65, .25, .65));
        assertEquals(1, TerrainGenerator.mountainMask(2, .25, .65));
        double epsilon = 1e-6;
        assertEquals(0, TerrainGenerator.mountainMask(.25 + epsilon, .25, .65) / epsilon, 1e-4);
        assertEquals(0, (1 - TerrainGenerator.mountainMask(.65 - epsilon, .25, .65)) / epsilon, 1e-4);
    }

    @Test
    void disabledMountainsDoNotSampleRegionOrRidge() {
        TerrainConfig defaults = TerrainConfig.defaults();
        TerrainConfig plains =
                new TerrainConfig(defaults.baseHeight(), defaults.continental(), defaults.hills(), defaults.details());
        int[] calls = {0};
        TerrainGenerator generator = new TerrainGenerator(0, plains, (x, z) -> {
            calls[0]++;
            return .1;
        });
        generator.surfaceHeight(-17, 31);
        assertEquals(9, calls[0]);
    }

    @Test
    void terrainContainsRegionalHighMountainsAndLowerValleys() {
        TerrainConfig config = TerrainConfig.defaults();
        TerrainGenerator mountains = new TerrainGenerator(0, config);
        TerrainGenerator plains = new TerrainGenerator(
                0, new TerrainConfig(config.baseHeight(), config.continental(), config.hills(), config.details()));
        int samples = 0;
        int affected = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int maxStep = 0;
        for (int z = -4096; z <= 4096; z += 32)
            for (int x = -4096; x <= 4096; x += 32) {
                int height = mountains.surfaceHeight(x, z);
                int base = plains.surfaceHeight(x, z);
                assertTrue(height >= base);
                if (height > base) affected++;
                min = Math.min(min, height);
                max = Math.max(max, height);
                maxStep = Math.max(maxStep, Math.abs(height - mountains.surfaceHeight(x + 1, z)));
                maxStep = Math.max(maxStep, Math.abs(height - mountains.surfaceHeight(x, z + 1)));
                samples++;
            }
        assertTrue(affected > samples / 100);
        assertTrue(affected < samples / 3);
        assertTrue(min < config.baseHeight() - 5);
        assertTrue(max > config.baseHeight() + 50);
        assertTrue(maxStep <= 3, "Default mask must not introduce sharp region-boundary jumps");
    }
}
