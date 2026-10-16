package com.norbertfila.generation.biome;

public final class BiomeResolver {
    public BiomeType resolve(
            ClimateSample climate, int height, int baseHeight, double slope, boolean nearWater, int waterLevel) {
        float temperature = climate.temperature() - Math.max(0L, (long) height - baseHeight) * .004f;
        if (height > (long) baseHeight + 90 && temperature < -.25f) return BiomeType.ALPINE;
        if (temperature < -.2f) return BiomeType.TAIGA;
        if (climate.humidity() > .15f
                && nearWater
                && slope < .8
                && height >= (long) waterLevel - 1
                && height <= (long) waterLevel + 2) return BiomeType.SWAMP;
        BiomeType climateBiome = resolve(climate);
        return climateBiome == BiomeType.ALPINE
                ? BiomeType.TAIGA
                : climateBiome == BiomeType.SWAMP ? BiomeType.FOREST : climateBiome;
    }

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
