package com.norbertfila.generation.biome;

import java.util.Objects;

public record BiomeProfile(SurfaceProfile surface, VegetationProfile vegetation, TreeProfile trees) {
    public BiomeProfile {
        Objects.requireNonNull(surface);
        Objects.requireNonNull(vegetation);
        Objects.requireNonNull(trees);
    }
}
