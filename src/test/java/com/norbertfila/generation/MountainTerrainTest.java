package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MountainTerrainTest {
    @Test
    void highMassifsAreRareWhileOrdinaryHillsAndPeakHeightArePreserved() {
        TerrainConfig current = baseShape();
        var m = current.mountains();
        TerrainConfig previous = new TerrainConfig(
                current.baseHeight(),
                current.continental(),
                current.hills(),
                current.details(),
                new TerrainConfig.Mountains(
                        m.region(),
                        m.ridge(),
                        .49,
                        .63,
                        m.ranges(),
                        m.ridgeExponent(),
                        m.rangeExponent(),
                        m.perOctaveRidges()),
                current.warp(),
                current.waterLevel(),
                current.temperature(),
                current.humidity());
        int oldMassifs = 0, newMassifs = 0, ordinary = 0, total = 0;
        double highest = Double.NEGATIVE_INFINITY;
        for (long seed : new long[] {0, 1, 42, 7340215, -1, -918273, 987654321, Long.MAX_VALUE}) {
            var before = new TerrainGenerator(seed, previous);
            var after = new TerrainGenerator(seed, current);
            var hills = new TerrainGenerator(seed, current.withoutMountains());
            for (int z = -4096; z < 4096; z += 64)
                for (int x = -4096; x < 4096; x += 64) {
                    double base = hills.surfaceElevation(x, z);
                    double height = after.surfaceElevation(x, z);
                    double oldRise = before.surfaceElevation(x, z) - base;
                    double newRise = height - base;
                    assertTrue(newRise >= -1e-9 && newRise <= oldRise + 1e-9);
                    if (oldRise >= 80) oldMassifs++;
                    if (newRise >= 80) newMassifs++;
                    if (newRise < 1) ordinary++;
                    highest = Math.max(highest, height);
                    total++;
                }
        }
        System.out.printf(
                "Massifs rising >=80 blocks: before=%d after=%d total=%d ordinary=%d highest=%.1f%n",
                oldMassifs, newMassifs, total, ordinary, highest);
        assertTrue(newMassifs > oldMassifs * .25 && newMassifs < oldMassifs * .4);
        assertTrue(ordinary > total * 2 / 3);
        assertTrue(highest >= 200);
    }

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
        TerrainConfig defaults = baseShape();
        TerrainConfig plains = defaults.withoutMountains().withoutWarp();
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
        config = new TerrainConfig(
                config.baseHeight(),
                config.continental(),
                config.hills(),
                config.details(),
                config.mountains(),
                config.warp(),
                config.waterLevel(),
                config.temperature(),
                config.humidity());
        TerrainGenerator mountains = new TerrainGenerator(0, config, LandscapeTestConfig.region(0));
        TerrainGenerator plains = new TerrainGenerator(0, config.withoutMountains(), LandscapeTestConfig.region(0));
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
        assertTrue(affected < samples * .6);
        assertTrue(min < config.baseHeight() - 5);
        assertTrue(max >= 200 && max <= config.maxHeight());
        assertTrue(maxStep > 3 && maxStep <= 16, "Multiscale peaks should have steep, bounded slopes");
    }

    @Test
    void ridgeIsAppliedBeforeOctavesAreCombined() {
        assertEquals(1, TerrainGenerator.ridged(0));
        assertEquals(.5, TerrainGenerator.ridged(.25));
        assertEquals(.5, TerrainGenerator.ridged(-.25));
        assertEquals(0, TerrainGenerator.ridged(1));
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        TerrainConfig.Layer region = new TerrainConfig.Layer(1, 1, .5, 2, 1);
        TerrainConfig.Layer ridge = new TerrainConfig.Layer(1, 2, 1, 2, 100);
        TerrainConfig config = new TerrainConfig(
                0,
                disabled,
                disabled,
                disabled,
                new TerrainConfig.Mountains(region, ridge, 0, .1, disabled, 1, 1, true));
        Noise2D alternating = (x, z) -> x < 39.2 ? -.25 : .25;
        assertEquals(
                62,
                new TerrainGenerator(0, config, alternating, (x, z) -> -1, LandscapeTestConfig.uniform())
                        .surfaceHeight(1, 0));
    }

    @Test
    void warpedElevationIsContinuousAcrossChunkBoundaries() {
        TerrainGenerator generator = new TerrainGenerator(42, TerrainConfig.defaults());
        double epsilon = 1e-5;
        for (int z = 192; z <= 288; z += 16)
            for (int x = -320; x <= -224; x += 16) {
                double height = generator.surfaceElevation(x, z);
                assertEquals(height, generator.surfaceElevation(x - epsilon, z), .01);
                assertEquals(height, generator.surfaceElevation(x + epsilon, z), .01);
                assertEquals(height, generator.surfaceElevation(x, z - epsilon), .01);
                assertEquals(height, generator.surfaceElevation(x, z + epsilon), .01);
            }
    }

    @Test
    void disablingWarpSkipsItsSamplesAndChangesOnlyCoordinates() {
        TerrainConfig defaults = baseShape();
        TerrainConfig flat = defaults.withoutMountains();
        int[] calls = {0};
        Noise2D counting = (x, z) -> {
            calls[0]++;
            return .1;
        };
        int warped = new TerrainGenerator(0, flat, counting).surfaceHeight(-17, 31);
        assertEquals(15, calls[0]);
        calls[0] = 0;
        int unwarped = new TerrainGenerator(0, flat.withoutWarp(), counting).surfaceHeight(-17, 31);
        assertEquals(9, calls[0]);
        assertEquals(warped, unwarped);
        TerrainGenerator first = new TerrainGenerator(42, defaults);
        TerrainGenerator second = new TerrainGenerator(42, defaults.withoutWarp());
        boolean differs = false;
        for (int z = -512; z <= 512; z += 31)
            for (int x = -512; x <= 512; x += 17) differs |= first.surfaceHeight(x, z) != second.surfaceHeight(x, z);
        assertTrue(differs);
    }

    private static TerrainConfig baseShape() {
        var c = TerrainConfig.defaults();
        return new TerrainConfig(
                c.baseHeight(),
                c.continental(),
                c.hills(),
                c.details(),
                c.mountains(),
                c.warp(),
                c.waterLevel(),
                c.temperature(),
                c.humidity());
    }
}
