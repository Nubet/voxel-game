package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FractalNoiseTest {
    @Test
    void scalesSingleOctaveAndNormalizesMultipleOctaves() {
        Noise2D linear = (x, z) -> x + 2 * z;
        assertEquals(.3, new FractalNoise(linear, .1, 1, .5, 2).sample(1, 1), 1e-15);
        assertEquals(1.2 / 1.875, new FractalNoise(linear, .1, 4, .5, 2).sample(1, 1), 1e-15);
        assertEquals(1, new FractalNoise((x, z) -> 1, .1, 4, .5, 2).sample(-17, 31), 1e-15);
    }

    @Test
    void zeroPersistenceSamplesOnlyTheFirstOctave() {
        int[] calls = {0};
        Noise2D counting = (x, z) -> {
            calls[0]++;
            return x;
        };
        assertEquals(.5, new FractalNoise(counting, .25, 8, 0, 2).sample(2, -3));
        assertEquals(1, calls[0]);
    }

    @Test
    void isDeterministicAndBoundedAcrossWorldCoordinates() {
        Noise2D first = new FractalNoise(new PerlinNoise(42), .005, 4, .5, 2);
        Noise2D second = new FractalNoise(new PerlinNoise(42), .005, 4, .5, 2);
        for (int z = -256; z <= 256; z += 7)
            for (int x = -256; x <= 256; x += 11) {
                double value = first.sample(x, z);
                assertEquals(value, second.sample(x, z));
                assertTrue(value >= -1 && value <= 1);
            }
    }

    @Test
    void rejectsInvalidParametersBeforeSampling() {
        Noise2D noise = new PerlinNoise(0);
        assertThrows(NullPointerException.class, () -> new FractalNoise(null, .1, 4, .5, 2));
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new FractalNoise(noise, invalid, 4, .5, 2));
            assertThrows(IllegalArgumentException.class, () -> new FractalNoise(noise, .1, 4, .5, invalid));
        }
        for (int invalid : new int[] {0, -1, 33, Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class, () -> new FractalNoise(noise, .1, invalid, .5, 2));
        for (double invalid : new double[] {-1, 1.01, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new FractalNoise(noise, .1, 4, invalid, 2));
        assertThrows(IllegalArgumentException.class, () -> new FractalNoise(noise, Double.MAX_VALUE, 2, .5, 2));
        assertThrows(IllegalArgumentException.class, () -> new FractalNoise(noise, Double.MIN_VALUE, 2, .5, .5));
    }
}
