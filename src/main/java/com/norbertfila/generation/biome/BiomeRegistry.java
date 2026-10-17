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
                        profile(
                                .65f,
                                .8f,
                                .15f,
                                SurfaceMaterial.GRASS,
                                SurfaceMaterial.DIRT,
                                Set.of(PlantType.SHORT_GRASS, PlantType.FERN)),
                BiomeType.DESERT,
                        profile(
                                0,
                                0,
                                0,
                                SurfaceMaterial.SAND,
                                SurfaceMaterial.SAND,
                                Set.of(PlantType.CACTUS, PlantType.DEAD_BUSH)),
                BiomeType.TAIGA,
                        profile(
                                .55f,
                                .65f,
                                .05f,
                                SurfaceMaterial.PODZOL,
                                SurfaceMaterial.COARSE_DIRT,
                                Set.of(
                                        PlantType.SHORT_GRASS,
                                        PlantType.FERN,
                                        PlantType.BERRY_BUSH,
                                        PlantType.ALPINE_GRASS)),
                BiomeType.SWAMP,
                        profile(
                                .35f,
                                .9f,
                                .2f,
                                SurfaceMaterial.MUD,
                                SurfaceMaterial.DIRT,
                                Set.of(PlantType.SHORT_GRASS, PlantType.FERN, PlantType.REED, PlantType.MUSHROOM)),
                BiomeType.BADLANDS,
                        profile(0, .05f, 0, SurfaceMaterial.GRAVEL, SurfaceMaterial.STONE, Set.of(PlantType.DEAD_BUSH)),
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
                BiomeType.SNOWY_TAIGA,
                profile(
                        .45f,
                        .08f,
                        0,
                        SurfaceMaterial.PODZOL,
                        SurfaceMaterial.DIRT,
                        Set.of(PlantType.FERN, PlantType.SHORT_GRASS)));
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
        return new BiomeRegistry(profiles);
    }

    private static BiomeProfile profile(
            float trees,
            float grass,
            float flowers,
            SurfaceMaterial surface,
            SurfaceMaterial subsurface,
            Set<PlantType> plants) {
        TreeType treeType = switch (surface) {
            case PODZOL -> TreeType.CONIFER;
            case MUD -> TreeType.SWAMP;
            default -> TreeType.BROADLEAF;
        };
        return new BiomeProfile(
                new SurfaceProfile(surface, subsurface),
                new VegetationProfile(grass, flowers, plants),
                new TreeProfile(trees, treeType));
    }
}
