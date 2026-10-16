package com.norbertfila.generation;

final class RegionalLandforms {
    static final double MIN_ELEVATION = -.2;
    static final double MAX_ELEVATION = .75;
    private final TerrainConfig.Layer config;
    private final Noise2D elevation;
    private final Noise2D character;

    RegionalLandforms(TerrainConfig.Layer config, Noise2D noise) {
        this.config = config;
        elevation = config.fractal((x, z) -> noise.sample(x + 311.7, z - 147.3));
        character = config.fractal((x, z) -> noise.sample(x - 591.1, z + 239.7));
    }

    Sample sample(double x, double z) {
        if (config.amplitude() == 0) return new Sample(0, 1, 1);
        double region = checked(elevation.sample(x, z));
        double shape = checked(character.sample(x, z));
        double upland = smooth((region + .2) / .55);
        double plateau = smooth((shape - .05) / .3);
        double rolling = smooth((shape + .25) / .5);
        double height = config.amplitude() * (MIN_ELEVATION + (MAX_ELEVATION - MIN_ELEVATION) * upland);
        double hillScale = (.15 + .85 * rolling) * (1 - .85 * plateau * upland);
        return new Sample(height, hillScale, .35 + .65 * rolling);
    }

    private static double checked(double value) {
        if (!Double.isFinite(value) || value < -1 || value > 1)
            throw new IllegalStateException("Noise must produce finite values within [-1, 1]");
        return value;
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    record Sample(double elevation, double hills, double details) {}
}
