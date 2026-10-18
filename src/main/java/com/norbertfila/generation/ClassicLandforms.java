package com.norbertfila.generation;

final class ClassicLandforms {
    private final Noise2D rolling;
    private final Noise2D cliffs;
    private final Noise2D roughness;
    private final Noise2D warp;

    ClassicLandforms(long seed) {
        rolling = new FractalNoise(new PerlinNoise(seed ^ 0x6A09E667F3BCC909L), .004, 3, .5, 2);
        cliffs = new FractalNoise(new PerlinNoise(seed ^ 0xBB67AE8584CAA73BL), .012, 3, .5, 2);
        roughness = new FractalNoise(new PerlinNoise(seed ^ 0x3C6EF372FE94F82BL), .035, 2, .5, 2);
        warp = new PerlinNoise(seed ^ 0xA54FF53A5F1D36F1L);
    }

    double sample(double x, double z) {
        double broad = rolling.sample(x, z);
        double wx = x + 28 * warp.sample(x * .018, z * .018);
        double wz = z + 28 * warp.sample(x * .018 + 53.7, z * .018 - 17.1);
        double massif = smooth((broad + .1) / .35) * smooth((cliffs.sample(wx, wz) - .03) / .035);
        return .15 + .1 * broad + .05 * roughness.sample(x, z) + .45 * massif;
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
