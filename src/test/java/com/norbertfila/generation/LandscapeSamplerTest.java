package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LandscapeSamplerTest {
    @Test
    void independentSeedsProduceBothStylesAndLocalVariation() {
        for (long seed : new long[] {0, 42, 7340215}) {
            var sampler = new LandscapeSampler(seed, LandscapeConfig.defaults());
            double min = 1, max = 0, minTrees = 2, maxTrees = 0;
            for (int z = -4096; z <= 4096; z += 128)
                for (int x = -4096; x <= 4096; x += 128) {
                    var sample = sampler.sample(x, z);
                    min = Math.min(min, sample.classicness());
                    max = Math.max(max, sample.classicness());
                    minTrees = Math.min(minTrees, sample.trees());
                    maxTrees = Math.max(maxTrees, sample.trees());
                }
            assertTrue(min < .05 && max > .95);
            assertTrue(maxTrees - minTrees > 1);
        }
    }

    @Test
    void samplesAreStableAcrossThreadsCacheEvictionAndIntegerLimits() {
        var first = new LandscapeSampler(42, LandscapeConfig.defaults());
        var second = new LandscapeSampler(42, LandscapeConfig.defaults());
        var expected = IntStream.range(-100, 100)
                .mapToObj(i -> first.sample(i * 137, i * -79))
                .toList();
        var parallel = IntStream.range(-100, 100)
                .parallel()
                .mapToObj(i -> second.sample(i * 137, i * -79))
                .toList();
        assertEquals(expected, parallel);
        for (int x : new int[] {Integer.MIN_VALUE, -33, -32, -1, 0, 31, 32, Integer.MAX_VALUE}) {
            var before = first.sample(x, x);
            for (int i = 0; i < 5000; i++) first.sample(i * 32, -i * 32);
            assertEquals(before, first.sample(x, x));
        }
    }

    @Test
    void regionAndPatchBoundariesHaveNoJumps() {
        var sampler = new LandscapeSampler(42, LandscapeConfig.defaults());
        for (int x = -4096; x <= 4096; x += 32) {
            var left = sampler.sample(x - .0001, 17);
            var right = sampler.sample(x + .0001, 17);
            assertEquals(left.classicness(), right.classicness(), .00001);
            assertEquals(left.trees(), right.trees(), .00001);
            assertEquals(left.hills(), right.hills(), .00001);
        }
    }

    @Test
    void rejectsInvalidConfigAndCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new LandscapeConfig.Field(0, 2, .5));
        assertThrows(IllegalArgumentException.class, () -> new LandscapeConfig.Field(32, Double.NaN, .5));
        assertThrows(IllegalArgumentException.class, () -> new LandscapeConfig.Range(1, 0));
        var sampler = new LandscapeSampler(0, LandscapeConfig.defaults());
        assertThrows(IllegalArgumentException.class, () -> sampler.sample(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> sampler.sample(0, Double.POSITIVE_INFINITY));
    }
}
