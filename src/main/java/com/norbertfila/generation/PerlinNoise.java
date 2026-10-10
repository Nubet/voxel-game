package com.norbertfila.generation;

public final class PerlinNoise implements Noise2D {
    private static final double AXIS_GRADIENT = Math.sqrt(2);
    private final long seed;

    public PerlinNoise(long seed) {
        this.seed = seed;
    }

    @Override
    public double sample(double x, double z) {
        if (!(x > -0x1p52 && x < 0x1p52 && z > -0x1p52 && z < 0x1p52))
            throw new IllegalArgumentException("Noise coordinates must be finite and retain fractional precision");
        long cellX = (long) Math.floor(x);
        long cellZ = (long) Math.floor(z);
        double dx = x - cellX;
        double dz = z - cellZ;
        double u = fade(dx);
        double v = fade(dz);
        double lower = lerp(gradient(cellX, cellZ, dx, dz), gradient(cellX + 1, cellZ, dx - 1, dz), u);
        double upper = lerp(gradient(cellX, cellZ + 1, dx, dz - 1), gradient(cellX + 1, cellZ + 1, dx - 1, dz - 1), u);
        // Corner dot products lie in [-2, 2]; halving preserves TerrainConfig's height bounds.
        return lerp(lower, upper, v) * 0.5;
    }

    private double gradient(long x, long z, double dx, double dz) {
        long hash = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        hash = (hash ^ (hash >>> 30)) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ (hash >>> 27)) * 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return switch ((int) hash & 7) {
            case 0 -> dx + dz;
            case 1 -> dx - dz;
            case 2 -> -dx + dz;
            case 3 -> -dx - dz;
            case 4 -> AXIS_GRADIENT * dx;
            case 5 -> -AXIS_GRADIENT * dx;
            case 6 -> AXIS_GRADIENT * dz;
            default -> -AXIS_GRADIENT * dz;
        };
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
