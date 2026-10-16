package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RegionalLandformsTest {
    @Test
    void regionalUpliftCreatesBroadElevationBandsWithoutRemovingQuietGround() {
        var config = TerrainConfig.defaults().withoutMountains();
        for (long seed : new long[] {0, 42, -918273}) {
            var terrain = new TerrainGenerator(seed, config);
            int low = 0, high = 0, quiet = 0, total = 0;
            for (int z = -8192; z <= 8192; z += 128)
                for (int x = -8192; x <= 8192; x += 128) {
                    int height = terrain.surfaceHeight(x, z);
                    if (height < 28) low++;
                    if (height > 64) high++;
                    if (Math.abs(height - terrain.surfaceHeight(x + 16, z)) <= 2) quiet++;
                    total++;
                }
            assertTrue(low > total / 20, "Lowlands must occupy a regional area");
            assertTrue(high > total / 20, "Uplands must exist without mountains");
            assertTrue(quiet > total / 3, "Calm ground must survive regional shaping");
        }
    }

    @Test
    void plateauCharacterSuppressesHillsWithoutCreatingHeightDiscontinuities() {
        var layer = new TerrainConfig.Layer(1, 1, .5, 2, 96);
        var plateau = new RegionalLandforms(layer, (x, z) -> .5).sample(0, 0);
        var lowland = new RegionalLandforms(layer, (x, z) -> -.5).sample(0, 0);
        assertTrue(plateau.elevation() - lowland.elevation() > 80);
        assertTrue(plateau.hills() < .2);
        assertTrue(lowland.hills() < .2);
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        for (int x = -4096; x < 4096; x += 17)
            assertEquals(terrain.surfaceElevation(x, -1024), terrain.surfaceElevation(x + 1e-6, -1024), .001);
    }
}
