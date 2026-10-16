package com.norbertfila.generation.biome;

public final class BiomeTint {
    private BiomeTint() {}

    public static float[] grass(BiomeType biome) {
        return switch (biome) {
            case DESERT, BADLANDS -> new float[] {.82f, .72f, .38f};
            case SWAMP -> new float[] {.24f, .48f, .18f};
            case TAIGA, ALPINE -> new float[] {.38f, .62f, .32f};
            default -> new float[] {.32f, .69f, .22f};
        };
    }

    public static float[] foliage(BiomeType biome) {
        return switch (biome) {
            case SWAMP -> new float[] {.2f, .42f, .16f};
            case TAIGA, ALPINE -> new float[] {.22f, .45f, .18f};
            case DESERT, BADLANDS -> new float[] {.55f, .5f, .18f};
            default -> new float[] {.3f, .55f, .22f};
        };
    }
}
