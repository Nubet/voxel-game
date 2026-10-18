package com.norbertfila.generation.biome;

import com.norbertfila.generation.WaterKind;

public final class BiomeResolver {
    private static final float FOREST_HUMIDITY = -.1f;
    private static final double FOREST_EDGE_WIDTH = .2;

    public static double forestWeight(ClimateSample climate) {
        double t = Math.clamp((climate.humidity() - FOREST_HUMIDITY) / FOREST_EDGE_WIDTH + .5, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public BiomeType resolve(
            ClimateSample climate,
            int height,
            int baseHeight,
            double slope,
            boolean nearWater,
            int waterLevel,
            WaterKind waterKind) {
        return resolve(climate, height, baseHeight, slope, nearWater, waterLevel, waterKind, 0);
    }

    public BiomeType resolve(
            ClimateSample climate,
            int height,
            int baseHeight,
            double slope,
            boolean nearWater,
            int waterLevel,
            WaterKind waterKind,
            double classicness) {
        if (height < waterLevel && waterKind != WaterKind.NONE)
            return switch (waterKind) {
                case OCEAN -> BiomeType.OCEAN;
                case LAKE -> BiomeType.LAKE;
                case RIVER -> BiomeType.RIVER;
                case NONE -> throw new IllegalStateException("Flooded column without water");
            };
        BiomeType climateBiome = resolve(climate, classicness);
        if (climateBiome.classic()) return climateBiome;
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
        if (temperature > .2f && humidity > .3f) return BiomeType.JUNGLE;
        if (humidity > .35f) return temperature < .4f ? BiomeType.SWAMP : BiomeType.FOREST;
        if (humidity > FOREST_HUMIDITY) return BiomeType.FOREST;
        return BiomeType.PLAINS;
    }

    public BiomeType resolve(ClimateSample climate, double classicness) {
        if (classicness <= .5) return resolve(climate);
        float temperature = climate.temperature();
        float humidity = climate.humidity();
        if (temperature < -.45f) return BiomeType.TUNDRA;
        if (temperature < -.2f) return BiomeType.TAIGA;
        if (humidity < -.35f) {
            if (temperature > .3f) return BiomeType.CLASSIC_DESERT;
            return temperature > .05f ? BiomeType.SAVANNA : BiomeType.CLASSIC_PLAINS;
        }
        if (humidity < -.1f) return temperature > .1f ? BiomeType.SAVANNA : BiomeType.SHRUBLAND;
        if (humidity < .1f) return BiomeType.CLASSIC_PLAINS;
        if (humidity > .35f && temperature < .1f) return BiomeType.SWAMPLAND;
        if (temperature > .25f && humidity > .35f) return BiomeType.RAINFOREST;
        if (temperature > .1f) return BiomeType.SEASONAL_FOREST;
        return BiomeType.CLASSIC_FOREST;
    }
}
