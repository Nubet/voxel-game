package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.ChunkPos;
import org.junit.jupiter.api.Test;

class LandscapeTerrainTest {
    @Test
    void classicRegionsKeepLocalDetailOutsideDeserts() {
        var off = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        var config = new TerrainConfig(
                32,
                off,
                off,
                new TerrainConfig.Layer(.06, 1, .5, 2, 3),
                new TerrainConfig.Mountains(off, off, .5, .6),
                off,
                28,
                off,
                off,
                new TerrainConfig.Layer(.001, 1, .5, 2, 1));
        var classic = new TerrainGenerator(42, config, (x, z) -> .5, (x, z) -> -1, LandscapeTestConfig.region(1));
        var modern = new TerrainGenerator(42, config, (x, z) -> .5, (x, z) -> -1, LandscapeTestConfig.region(0));
        assertTrue(classic.surfaceElevation(37, 51) > modern.surfaceElevation(37, 51) + 1);
    }

    @Test
    void regionalStyleChangesReliefWithoutChangingClimateOrSeaLevel() {
        var classic = new TerrainGenerator(
                42, TerrainConfig.classic(), (x, z) -> .5, (x, z) -> -1, LandscapeTestConfig.region(1));
        var modern = new TerrainGenerator(
                42, TerrainConfig.classic(), (x, z) -> .5, (x, z) -> -1, LandscapeTestConfig.region(0));
        var a = classic.sample(37, -51);
        var b = modern.sample(37, -51);
        assertTrue(a.surfaceHeight() < b.surfaceHeight());
        assertEquals(a.climate(), b.climate());
        assertEquals(a.waterLevel(), b.waterLevel());
        assertEquals(1, classic.landscapeAt(37, -51).classicness());
        assertEquals(0, modern.landscapeAt(37, -51).classicness());
    }

    @Test
    void sharedHydrologyAndSamplingStayStableInBothStyles() {
        for (double weight : new double[] {0, .5, 1}) {
            var terrain = new TerrainGenerator(42, TerrainConfig.defaults(), LandscapeTestConfig.region(weight));
            for (int x : new int[] {-513, -512, -33, -32, -1, 0, 31, 32, 511, 512}) {
                var summary = terrain.sample(x, -17);
                assertEquals(terrain.surfaceHeight(x, -17), summary.surfaceHeight());
                assertTrue(Math.abs(terrain.surfaceElevation(x - .0001, -17) - terrain.surfaceElevation(x + .0001, -17))
                        < .01);
                var position = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(summary.surfaceHeight(), 16), -2);
                assertArrayEquals(
                        terrain.generate(position).blocks(),
                        terrain.generate(position).blocks());
            }
        }
    }
}
