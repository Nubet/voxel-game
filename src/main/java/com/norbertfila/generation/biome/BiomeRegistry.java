package com.norbertfila.generation.biome;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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
        var profiles = new EnumMap<>(Map.of(
                BiomeType.PLAINS,
                        profile(
                                .05f,
                                .65f,
                                .35f,
                                SurfaceMaterial.GRASS,
                                SurfaceMaterial.DIRT,
                                Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.FLOWER)),
                BiomeType.FOREST,
                        new BiomeProfile(
                                new SurfaceProfile(SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                                new VegetationProfile(
                                        .8f,
                                        .15f,
                                        Set.of(
                                                PlantType.SHORT_GRASS,
                                                PlantType.FERN,
                                                PlantType.FLOWER,
                                                PlantType.MUSHROOM),
                                        .2f,
                                        .06f),
                                new TreeProfile(.65f, TreeType.BROADLEAF)),
                BiomeType.DESERT,
                        profile(
                                0,
                                0,
                                0,
                                SurfaceMaterial.SAND,
                                SurfaceMaterial.SAND,
                                Set.of(PlantType.CACTUS, PlantType.DEAD_BUSH)),
                BiomeType.SWAMP,
                        profile(
                                .35f,
                                .9f,
                                .2f,
                                SurfaceMaterial.MUD,
                                SurfaceMaterial.DIRT,
                                Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.REED, PlantType.MUSHROOM),
                                TreeType.SWAMP),
                BiomeType.BADLANDS,
                        profile(
                                0,
                                .05f,
                                0,
                                SurfaceMaterial.TERRACOTTA_ORANGE,
                                SurfaceMaterial.TERRACOTTA_ORANGE,
                                Set.of(PlantType.DEAD_BUSH)),
                BiomeType.ALPINE,
                        profile(
                                0,
                                0,
                                0,
                                SurfaceMaterial.SNOW,
                                SurfaceMaterial.STONE,
                                Set.of(PlantType.ALPINE_GRASS, PlantType.FLOWER)),
                BiomeType.OCEAN, profile(0, 0, 0, SurfaceMaterial.SAND, SurfaceMaterial.SAND, Set.of()),
                BiomeType.LAKE, profile(0, 0, 0, SurfaceMaterial.MUD, SurfaceMaterial.DIRT, Set.of()),
                BiomeType.RIVER, profile(0, 0, 0, SurfaceMaterial.SAND, SurfaceMaterial.SAND, Set.of())));
        profiles.put(BiomeType.STONY_SLOPES, profile(0, 0, 0, SurfaceMaterial.STONE, SurfaceMaterial.STONE, Set.of()));
        profiles.put(
                BiomeType.TAIGA,
                new BiomeProfile(
                        new SurfaceProfile(SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                        new VegetationProfile(
                                .65f,
                                0,
                                Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.BERRY_BUSH, PlantType.MUSHROOM),
                                .65f,
                                .08f),
                        new TreeProfile(.55f, TreeType.CONIFER)));
        profiles.put(
                BiomeType.SNOWY_TAIGA,
                new BiomeProfile(
                        new SurfaceProfile(SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                        new VegetationProfile(
                                .08f, 0, Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.MUSHROOM), .7f, .01f),
                        new TreeProfile(.45f, TreeType.CONIFER)));
        profiles.put(
                BiomeType.JUNGLE,
                new BiomeProfile(
                        new SurfaceProfile(SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                        new VegetationProfile(1f, 0, Set.of(PlantType.SHORT_GRASS, PlantType.FERN), .35f, 0),
                        new TreeProfile(.9f, TreeType.JUNGLE)));
        profiles.put(
                BiomeType.RAINFOREST,
                new BiomeProfile(
                        new SurfaceProfile(SurfaceMaterial.GRASS, SurfaceMaterial.DIRT),
                        new VegetationProfile(
                                .9f,
                                .08f,
                                Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.FLOWER, PlantType.MUSHROOM),
                                .55f,
                                .12f),
                        new TreeProfile(.95f, TreeType.BROADLEAF)));
        profiles.put(
                BiomeType.SEASONAL_FOREST,
                profile(
                        .55f,
                        .65f,
                        .22f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.FLOWER)));
        profiles.put(
                BiomeType.CLASSIC_FOREST,
                profile(
                        .7f,
                        .5f,
                        .12f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.FLOWER, PlantType.MUSHROOM)));
        profiles.put(
                BiomeType.SWAMPLAND,
                profile(
                        .25f,
                        .7f,
                        .05f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.REED, PlantType.MUSHROOM)));
        profiles.put(
                BiomeType.SAVANNA,
                profile(
                        .04f,
                        .45f,
                        .08f,
                        SurfaceMaterial.DRY_GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FLOWER)));
        profiles.put(
                BiomeType.SHRUBLAND,
                profile(
                        .015f,
                        .25f,
                        .05f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FLOWER)));
        profiles.put(
                BiomeType.CLASSIC_PLAINS,
                profile(
                        .005f,
                        .8f,
                        .4f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FLOWER)));
        profiles.put(
                BiomeType.CLASSIC_DESERT,
                profile(
                        0,
                        0,
                        0,
                        SurfaceMaterial.SAND,
                        SurfaceMaterial.SAND,
                        Set.of(PlantType.CACTUS, PlantType.DEAD_BUSH)));
        profiles.put(
                BiomeType.TUNDRA,
                profile(.005f, .04f, 0, SurfaceMaterial.GRASS, SurfaceMaterial.DIRT, Set.of(PlantType.SHORT_GRASS)));
        profiles.put(
                BiomeType.ARCHLANDS_CLASSIC,
                profile(
                        .25f,
                        .5f,
                        .12f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.FLOWER)));
        profiles.put(
                BiomeType.FLOATING_ISLES_CLASSIC,
                profile(
                        .18f,
                        .4f,
                        .15f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FLOWER)));
        profiles.put(
                BiomeType.OVERHANG_HIGHLANDS_CLASSIC,
                profile(
                        .35f,
                        .45f,
                        .08f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FERN)));
        profiles.put(
                BiomeType.HIDDEN_VALLEYS_CLASSIC,
                profile(
                        .7f,
                        .85f,
                        .3f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.FLOWER, PlantType.REED)));
        profiles.put(
                BiomeType.COASTAL_CLIFFS_CLASSIC,
                profile(
                        .12f,
                        .35f,
                        .1f,
                        SurfaceMaterial.GRASS,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.SHORT_GRASS, PlantType.FLOWER, PlantType.REED)));
        return new BiomeRegistry(profiles);
    }

    private static BiomeProfile profile(
            float trees,
            float grass,
            float flowers,
            SurfaceMaterial surface,
            SurfaceMaterial subsurface,
            Set<PlantType> plants) {
        return profile(trees, grass, flowers, surface, subsurface, plants, TreeType.BROADLEAF);
    }

    private static BiomeProfile profile(
            float trees,
            float grass,
            float flowers,
            SurfaceMaterial surface,
            SurfaceMaterial subsurface,
            Set<PlantType> plants,
            TreeType treeType) {
        return new BiomeProfile(
                new SurfaceProfile(surface, subsurface),
                new VegetationProfile(grass, flowers, plants),
                new TreeProfile(trees, treeType));
    }
}
