package com.norbertfila.generation.biome;

import java.util.Objects;
import java.util.Set;

public record VegetationProfile(
        float grassDensity, float flowerDensity, Set<PlantType> plants, float fernFraction, float specialDensity) {
    public VegetationProfile(float grassDensity, float flowerDensity, Set<PlantType> plants) {
        this(grassDensity, flowerDensity, plants, .2f, 1);
    }

    public VegetationProfile {
        requireDensity(grassDensity, "grassDensity");
        requireDensity(flowerDensity, "flowerDensity");
        requireDensity(fernFraction, "fernFraction");
        requireDensity(specialDensity, "specialDensity");
        Objects.requireNonNull(plants);
        plants = Set.copyOf(plants);
    }

    private static void requireDensity(float value, String name) {
        if (!Float.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException(name + " must be finite and within [0, 1]");
    }
}
