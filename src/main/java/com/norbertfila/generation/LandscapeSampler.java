package com.norbertfila.generation;

import java.util.Objects;

public final class LandscapeSampler {
    private static final int GRID_SIZE = 32;
    private final LandscapeConfig config;
    private final Noise2D regions;
    private final Noise2D woodland;
    private final Noise2D vegetation;
    private final Noise2D flowers;
    private final Noise2D relief;
    private final GenerationCache<LandscapeSample> samples = new GenerationCache<>(4096);

    public LandscapeSampler(long seed, LandscapeConfig config) {
        this.config = Objects.requireNonNull(config);
        regions = new PerlinNoise(seed ^ 0xA0761D6478BD642FL);
        woodland = new PerlinNoise(seed ^ 0xE7037ED1A0B428DBL);
        vegetation = new PerlinNoise(seed ^ 0x8EBC6AF09C88C6E3L);
        flowers = new PerlinNoise(seed ^ 0x589965CC75374CC3L);
        relief = new PerlinNoise(seed ^ 0x1D8E4E27C47D124FL);
    }

    public LandscapeSample sample(double x, double z) {
        if (!Double.isFinite(x)
                || !Double.isFinite(z)
                || x < Integer.MIN_VALUE
                || x > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE
                || z > Integer.MAX_VALUE) throw new IllegalArgumentException("Landscape coordinates outside world");
        int gx = (int) Math.floor(x / GRID_SIZE), gz = (int) Math.floor(z / GRID_SIZE);
        double tx = (x - (double) gx * GRID_SIZE) / GRID_SIZE;
        double tz = (z - (double) gz * GRID_SIZE) / GRID_SIZE;
        return grid(gx, gz).blend(grid(gx + 1, gz), tx).blend(grid(gx, gz + 1).blend(grid(gx + 1, gz + 1), tx), tz);
    }

    private LandscapeSample grid(int x, int z) {
        return samples.get(x, z, () -> resolve((double) x * GRID_SIZE, (double) z * GRID_SIZE));
    }

    private LandscapeSample resolve(double x, double z) {
        double classicness = field(regions, config.regions(), x, z);
        double trees = field(woodland, config.woodland(), x, z);
        double plants = field(vegetation, config.vegetation(), x, z);
        double bloom = field(flowers, config.flowers(), x, z);
        double hills = field(relief, config.relief(), x, z);
        var classic = config.classic();
        var modern = config.modern();
        return new LandscapeSample(
                classicness,
                mix(modern.trees().at(trees), classic.trees().at(trees), classicness),
                mix(modern.vegetation().at(plants), classic.vegetation().at(plants), classicness),
                mix(modern.flowers().at(bloom), classic.flowers().at(bloom), classicness),
                mix(modern.hills().at(hills), classic.hills().at(hills), classicness),
                mix(modern.mountains(), classic.mountains(), classicness),
                mix(modern.warp(), classic.warp(), classicness),
                mix(modern.elevation(), classic.elevation(), classicness));
    }

    private static double field(Noise2D noise, LandscapeConfig.Field field, double x, double z) {
        double value = Math.clamp(
                field.bias()
                        + noise.sample(x / field.wavelength() + .37, z / field.wavelength() - .71) * field.contrast(),
                0,
                1);
        return value * value * (3 - 2 * value);
    }

    private static double mix(double modern, double classic, double weight) {
        return modern * (1 - weight) + classic * weight;
    }
}
