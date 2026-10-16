package com.norbertfila.generation.biome;

import java.util.Objects;

public record BiomeProfile(
        float treeDensity,
        float grassDensity,
        float flowerDensity,
        SurfaceProfile surface,
        VegetationProfile vegetation,
        TreeProfile trees) {
    public BiomeProfile {
        requireDensity(treeDensity, "treeDensity");
        requireDensity(grassDensity, "grassDensity");
        requireDensity(flowerDensity, "flowerDensity");
        Objects.requireNonNull(surface);
        Objects.requireNonNull(vegetation);
        Objects.requireNonNull(trees);
    }

    private static void requireDensity(float value, String name) {
        if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException(name + " must be finite and within [0, 1]");
    }
}
