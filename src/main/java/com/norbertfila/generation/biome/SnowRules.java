package com.norbertfila.generation.biome;

public final class SnowRules {
    private SnowRules() {}

    public static boolean shouldSnow(int height, int baseHeight, ClimateSample climate, SurfaceMaterial surface) {
        return surface == SurfaceMaterial.SNOW
                || height >= baseHeight + 70 && climate.temperature() < .5f
                || surface == SurfaceMaterial.PODZOL && height >= baseHeight + 20 && climate.temperature() < -.15f;
    }
}
