package com.norbertfila.generation.biome;

public final class JungleRules {
    private JungleRules() {}

    public static double climateWeight(ClimateSample climate) {
        return smooth(climate.temperature(), .22, .4) * smooth(climate.humidity(), .22, .4);
    }

    public static double smooth(double value, double start, double end) {
        double t = Math.clamp((value - start) / (end - start), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
