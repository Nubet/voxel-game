package com.norbertfila.generation.biome;

import com.norbertfila.generation.WaterKind;

public final class BiomeResolver {
    public BiomeType resolve(
            ClimateSample climate,
            int height,
            int baseHeight,
            double slope,
            boolean nearWater,
            int waterLevel,
            WaterKind waterKind) {
        if (height < waterLevel && waterKind != WaterKind.NONE)
            return switch (waterKind) {
                case OCEAN -> BiomeType.OCEAN;
                case LAKE -> BiomeType.LAKE;
                case RIVER -> BiomeType.RIVER;
                case NONE -> throw new IllegalStateException("Flooded column without water");
            };
        BiomeType climateBiome = resolve(climate);
        if (climateBiome == BiomeType.BADLANDS) return climateBiome;
        float temperature = ClimateRules.temperature(climate, height, baseHeight);
        boolean highland = height > (long) baseHeight + 48;
        if (highland && temperature < -.55f) return BiomeType.ALPINE;
        if (highland && (slope > 1.5 || temperature < -.3f)) return BiomeType.STONY_SLOPES;
        if (temperature < -.45f) return BiomeType.SNOWY_TAIGA;
        if (temperature < -.2f) return BiomeType.TAIGA;
        if (climate.humidity() > .15f
                && nearWater
                && slope < .8
                && height >= (long) waterLevel - 1
                && height <= (long) waterLevel + 2) return BiomeType.SWAMP;
        return climateBiome == BiomeType.SWAMP ? BiomeType.FOREST : climateBiome;
    }

    public BiomeType resolve(ClimateSample climate) {
        float temperature = climate.temperature();
        float humidity = climate.humidity();
        if (temperature < -.45f) return BiomeType.SNOWY_TAIGA;
        if (temperature < -.2f) return BiomeType.TAIGA;
        if (temperature > .35f && humidity < -.05f) return BiomeType.DESERT;
        if (temperature > .2f && humidity < .25f) return BiomeType.BADLANDS;
        if (temperature > .3f && humidity > .3f) return BiomeType.JUNGLE;
        if (humidity > .35f) return temperature < .4f ? BiomeType.SWAMP : BiomeType.FOREST;
        if (humidity > -.1f) return BiomeType.FOREST;
        return BiomeType.PLAINS;
    }
}
