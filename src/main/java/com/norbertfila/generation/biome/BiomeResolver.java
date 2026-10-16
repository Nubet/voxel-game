package com.norbertfila.generation.biome;

public final class BiomeResolver {
    public BiomeType resolve(ClimateSample climate) {
        float temperature = climate.temperature();
        float humidity = climate.humidity();
        if (temperature < -.2f) return humidity > 0 ? BiomeType.TAIGA : BiomeType.ALPINE;
        if (temperature > .35f && humidity < -.05f) return BiomeType.DESERT;
        if (temperature > .2f && humidity < .25f) return BiomeType.BADLANDS;
        if (humidity > .35f) return temperature < .4f ? BiomeType.SWAMP : BiomeType.FOREST;
        if (humidity > -.1f) return BiomeType.FOREST;
        return BiomeType.PLAINS;
    }
}
