package com.norbertfila.generation.biome;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class BiomeRegistry {
    private final Map<BiomeType, BiomeProfile> profiles;

    public BiomeRegistry(Map<BiomeType, BiomeProfile> profiles) {
        EnumMap<BiomeType, BiomeProfile> copy = new EnumMap<>(BiomeType.class);
        copy.putAll(profiles);
        if (!copy.keySet().equals(EnumSet.allOf(BiomeType.class)))
            throw new IllegalArgumentException("Every biome type must have a profile");
        profiles = Map.copyOf(copy);
        this.profiles = profiles;
    }

    public BiomeProfile profile(BiomeType type) {
        return profiles.get(type);
    }

    public static BiomeRegistry defaults() {
        return new BiomeRegistry(Map.of(
                BiomeType.PLAINS, profile(.05f, .65f, .35f, SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                BiomeType.FOREST, profile(.65f, .8f, .15f, SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                BiomeType.DESERT, profile(0, 0, 0, SurfaceMaterial.SAND, SurfaceMaterial.SAND),
                BiomeType.TAIGA, profile(.55f, .65f, .05f, SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                BiomeType.SWAMP, profile(.35f, .9f, .2f, SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                BiomeType.BADLANDS, profile(0, .05f, 0, SurfaceMaterial.GRAVEL, SurfaceMaterial.STONE),
                BiomeType.ALPINE, profile(0, 0, 0, SurfaceMaterial.STONE, SurfaceMaterial.STONE)));
    }

    private static BiomeProfile profile(
            float trees, float grass, float flowers, SurfaceMaterial surface, SurfaceMaterial subsurface) {
        return new BiomeProfile(
                trees,
                grass,
                flowers,
                new SurfaceProfile(surface, subsurface),
                new VegetationProfile(grass, flowers, java.util.Set.of(PlantType.SHORT_GRASS)),
                new TreeProfile(trees, TreeType.GENERIC));
    }
}
