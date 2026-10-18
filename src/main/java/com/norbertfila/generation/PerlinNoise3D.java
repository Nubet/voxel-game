package com.norbertfila.generation;

public final class PerlinNoise3D {
    private final long seed;

    public PerlinNoise3D(long seed) {
        this.seed = seed;
    }

    public double sample(double x, double y, double z) {
        if (!(Math.abs(x) < 0x1p52 && Math.abs(y) < 0x1p52 && Math.abs(z) < 0x1p52))
            throw new IllegalArgumentException("Noise coordinates must be finite and retain fractional precision");
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y), iz = (long) Math.floor(z);
        double dx = x - ix, dy = y - iy, dz = z - iz;
        double u = fade(dx), v = fade(dy), w = fade(dz);
        double lower = lerp(
                lerp(gradient(ix, iy, iz, dx, dy, dz), gradient(ix + 1, iy, iz, dx - 1, dy, dz), u),
                lerp(gradient(ix, iy + 1, iz, dx, dy - 1, dz), gradient(ix + 1, iy + 1, iz, dx - 1, dy - 1, dz), u),
                v);
        double upper = lerp(
                lerp(gradient(ix, iy, iz + 1, dx, dy, dz - 1), gradient(ix + 1, iy, iz + 1, dx - 1, dy, dz - 1), u),
                lerp(
                        gradient(ix, iy + 1, iz + 1, dx, dy - 1, dz - 1),
                        gradient(ix + 1, iy + 1, iz + 1, dx - 1, dy - 1, dz - 1),
                        u),
                v);
        return lerp(lower, upper, w) * .5;
    }

    private double gradient(long x, long y, long z, double dx, double dy, double dz) {
        int h = (int) GenerationHash.hash(GenerationHash.hash(seed, x, z), y, 0) & 15;
        double u = h < 8 ? dx : dy;
        double v = h < 4 ? dy : h == 12 || h == 14 ? dx : dz;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
