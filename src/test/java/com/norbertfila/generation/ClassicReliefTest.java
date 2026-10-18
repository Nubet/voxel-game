package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.lod.AreaEdits;
import com.norbertfila.lod.FarTerrainGenerator;
import org.junit.jupiter.api.Test;

class ClassicReliefTest {
    @Test
    void naturalClassicPlainsContainCliffsAndFarTerrainUsesTheSameProfile() {
        for (long seed : new long[] {0, 42, 7340215}) {
            var terrain = new TerrainGenerator(seed, TerrainConfig.defaults());
            for (int z = -2048; z <= 2048; z += 16)
                for (int x = -2048; x <= 2048; x += 16) {
                    if (terrain.landscapeAt(x, z).classicness() < .75) continue;
                    double height = terrain.surfaceElevation(x, z);
                    double slope = Math.max(
                            Math.abs(height - terrain.surfaceElevation(x + 1, z)),
                            Math.abs(height - terrain.surfaceElevation(x, z + 1)));
                    if (slope < 8) continue;
                    var ground = terrain.sample(x, z);
                    if (ground.biome() != BiomeType.CLASSIC_PLAINS) continue;
                    int tx = Math.floorDiv(x, 64), tz = Math.floorDiv(z, 64);
                    var tile =
                            new FarTerrainGenerator(terrain).generate(tx, tz, 1, AreaEdits.none(tx * 64, tz * 64, 64));
                    assertEquals(ground.surfaceHeight(), tile.heightAt(Math.floorMod(x, 64), Math.floorMod(z, 64)));
                    assertEquals(ground.surfaceMaterial(), tile.materialAt(Math.floorMod(x, 64), Math.floorMod(z, 64)));
                    System.out.printf(
                            "Classic Plains cliff: seed=%d x=%d y=%d z=%d slope=%.2f%n",
                            seed, x, ground.surfaceHeight(), z, slope);
                    return;
                }
        }
        fail("Classic Plains must contain local cliffs in the default mixed world");
    }

    @Test
    void classicTerrainMixesCalmGroundWithAbruptLocalCliffsWithoutMountainBiomes() {
        var config = TerrainConfig.defaults().withoutMountains();
        for (long seed : new long[] {0, 42, 7340215}) {
            var classic = new TerrainGenerator(seed, config, LandscapeTestConfig.region(1));
            var modern = new TerrainGenerator(seed, config, LandscapeTestConfig.region(0));
            int cliffs = 0, modernCliffs = 0, calm = 0;
            double steepest = 0;
            for (int z = -1024; z <= 1024; z += 32)
                for (int x = -1024; x <= 1024; x += 32) {
                    double height = classic.surfaceElevation(x, z);
                    if (height <= config.waterLevel() + 2) continue;
                    double slope = Math.max(
                            Math.abs(height - classic.surfaceElevation(x + 1, z)),
                            Math.abs(height - classic.surfaceElevation(x, z + 1)));
                    double modernHeight = modern.surfaceElevation(x, z);
                    double modernSlope = Math.max(
                            Math.abs(modernHeight - modern.surfaceElevation(x + 1, z)),
                            Math.abs(modernHeight - modern.surfaceElevation(x, z + 1)));
                    if (slope >= 5) cliffs++;
                    if (modernSlope >= 5) modernCliffs++;
                    if (slope < .5) calm++;
                    steepest = Math.max(steepest, slope);
                }
            String context = "seed=" + seed + " cliffs=" + cliffs + " modern=" + modernCliffs + " calm=" + calm
                    + " steepest=" + steepest;
            assertTrue(cliffs >= 25 && cliffs > modernCliffs * 3, context);
            assertTrue(calm >= 200, context);
            assertTrue(steepest >= 8, context);
        }
    }
}
