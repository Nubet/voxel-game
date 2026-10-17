package com.norbertfila.generation.biome;

import com.norbertfila.generation.WaterKind;

public final class SnowRules {
    private SnowRules() {}

    public static boolean freezes(WaterKind kind, float temperature) {
        return (kind == WaterKind.LAKE || kind == WaterKind.RIVER) && temperature < -.45f;
    }

    public static boolean covered(BiomeType biome, float temperature, double slope, double variation) {
        if (biome == BiomeType.ALPINE) return true;
        if (biome != BiomeType.SNOWY_TAIGA || slope > 1.5) return false;
        double cold = Math.clamp((-.45 - temperature) / .1, 0, 1);
        return variation > .18 - .5 * cold;
    }
}
