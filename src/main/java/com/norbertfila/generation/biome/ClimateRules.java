package com.norbertfila.generation.biome;

public final class ClimateRules {
    private ClimateRules() {}

    public static float temperature(ClimateSample climate, int height, int baseHeight) {
        return climate.temperature() - Math.max(0L, (long) height - baseHeight) * .004f;
    }
}
