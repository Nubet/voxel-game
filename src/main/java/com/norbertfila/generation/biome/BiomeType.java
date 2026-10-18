package com.norbertfila.generation.biome;

public enum BiomeType {
    PLAINS,
    FOREST,
    DESERT,
    TAIGA,
    SWAMP,
    BADLANDS,
    ALPINE,
    OCEAN,
    LAKE,
    RIVER,
    STONY_SLOPES,
    SNOWY_TAIGA,
    JUNGLE,
    RAINFOREST,
    SEASONAL_FOREST,
    CLASSIC_FOREST,
    SWAMPLAND,
    SAVANNA,
    SHRUBLAND,
    CLASSIC_PLAINS,
    CLASSIC_DESERT,
    TUNDRA,
    ARCHLANDS_CLASSIC,
    FLOATING_ISLES_CLASSIC,
    OVERHANG_HIGHLANDS_CLASSIC,
    HIDDEN_VALLEYS_CLASSIC,
    COASTAL_CLIFFS_CLASSIC;

    public boolean rareClassic() {
        return switch (this) {
            case ARCHLANDS_CLASSIC,
                    FLOATING_ISLES_CLASSIC,
                    OVERHANG_HIGHLANDS_CLASSIC,
                    HIDDEN_VALLEYS_CLASSIC,
                    COASTAL_CLIFFS_CLASSIC -> true;
            default -> false;
        };
    }

    public boolean classic() {
        return rareClassic()
                || switch (this) {
                    case RAINFOREST,
                            SEASONAL_FOREST,
                            CLASSIC_FOREST,
                            SWAMPLAND,
                            SAVANNA,
                            SHRUBLAND,
                            CLASSIC_PLAINS,
                            CLASSIC_DESERT,
                            TUNDRA -> true;
                    default -> false;
                };
    }
}
