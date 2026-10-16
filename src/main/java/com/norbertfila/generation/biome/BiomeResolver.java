package com.norbertfila.generation.biome;

public final class BiomeResolver {
    public BiomeType resolve(ClimateSample climate) {
        float temperature = climate.temperature();
        float humidity = climate.humidity();
        if (temperature < -.35f) return humidity > .1f ? BiomeType.TAIGA : BiomeType.ALPINE;
        if (temperature > .55f && humidity < -.2f) return BiomeType.DESERT;
        if (temperature > .35f && humidity < .1f) return BiomeType.BADLANDS;
        if (humidity > .55f) return temperature < .35f ? BiomeType.SWAMP : BiomeType.FOREST;
        if (humidity > .05f) return BiomeType.FOREST;
        return BiomeType.PLAINS;
    }
}
