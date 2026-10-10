package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PerlinNoiseTest {
    @Test
    void isSeededBoundedAndZeroAtLatticeCorners() {
        Noise2D first = new PerlinNoise(42);
        Noise2D same = new PerlinNoise(42);
        Noise2D other = new PerlinNoise(-7);
        boolean differs = false;
        for (int z = -32; z <= 32; z++)
            for (int x = -32; x <= 32; x++) {
                assertEquals(0, first.sample(x, z), 0.0);
                double value = first.sample(x + .31, z + .73);
                assertTrue(value >= -1 && value <= 1);
                assertEquals(value, same.sample(x + .31, z + .73));
                differs |= value != other.sample(x + .31, z + .73);
            }
        assertTrue(differs);
        assertNotEquals(first.sample(.31, .73), first.sample(256.31, 256.73));
    }

    @Test
    void isContinuousWithMatchingDerivativesAcrossPositiveAndNegativeCells() {
        Noise2D noise = new PerlinNoise(19);
        double epsilon = 1e-5;
        for (int boundary = -5; boundary <= 5; boundary++) {
            double center = noise.sample(boundary, -.37);
            double left = noise.sample(boundary - epsilon, -.37);
            double right = noise.sample(boundary + epsilon, -.37);
            assertEquals(left, right, 2e-5);
            assertEquals((center - left) / epsilon, (right - center) / epsilon, 1e-4);
            center = noise.sample(.61, boundary);
            left = noise.sample(.61, boundary - epsilon);
            right = noise.sample(.61, boundary + epsilon);
            assertEquals(left, right, 2e-5);
            assertEquals((center - left) / epsilon, (right - center) / epsilon, 1e-4);
        }
    }

    @Test
    void rejectsCoordinatesWithoutFiniteFractionalPrecision() {
        Noise2D noise = new PerlinNoise(0);
        for (double invalid :
                new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0x1p52, -0x1p52}) {
            assertThrows(IllegalArgumentException.class, () -> noise.sample(invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> noise.sample(0, invalid));
        }
        assertEquals(noise.sample(-0.0, .5), noise.sample(0.0, .5));
        assertTrue(Double.isFinite(noise.sample(Integer.MIN_VALUE + .5, Integer.MAX_VALUE - .5)));
    }
}
