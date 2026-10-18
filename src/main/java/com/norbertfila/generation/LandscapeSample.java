package com.norbertfila.generation;

public record LandscapeSample(
        double classicness,
        double trees,
        double vegetation,
        double flowers,
        double hills,
        double mountains,
        double warp,
        double elevation) {
    public LandscapeSample {
        if (!unit(classicness)
                || !density(trees)
                || !density(vegetation)
                || !density(flowers)
                || !unit(hills)
                || !unit(mountains)
                || !unit(warp)
                || !unit(elevation)) throw new IllegalArgumentException("Invalid landscape sample");
    }

    LandscapeSample blend(LandscapeSample other, double weight) {
        return new LandscapeSample(
                mix(classicness, other.classicness, weight), mix(trees, other.trees, weight),
                mix(vegetation, other.vegetation, weight), mix(flowers, other.flowers, weight),
                mix(hills, other.hills, weight), mix(mountains, other.mountains, weight),
                mix(warp, other.warp, weight), mix(elevation, other.elevation, weight));
    }

    private static double mix(double a, double b, double weight) {
        return a * (1 - weight) + b * weight;
    }

    private static boolean unit(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    private static boolean density(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 2;
    }
}
