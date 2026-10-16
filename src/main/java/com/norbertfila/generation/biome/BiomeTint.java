package com.norbertfila.generation.biome;

import com.norbertfila.world.BlockRegistry;

public final class BiomeTint {
    private BiomeTint() {}

    public static boolean tintedTexture(int layer) {
        return layer == BlockRegistry.GRASS_TOP_TEXTURE
                || layer == BlockRegistry.GRASS_SIDE_TEXTURE
                || foliageTexture(layer);
    }

    public static boolean foliageTexture(int layer) {
        return layer == BlockRegistry.LEAVES_TEXTURE
                || layer == BlockRegistry.PINE_NEEDLES_TEXTURE
                || layer == BlockRegistry.SWAMP_LEAVES_TEXTURE;
    }

    public static int style(BiomeType biome) {
        return switch (biome) {
            case DESERT, BADLANDS -> 1;
            case TAIGA, ALPINE -> 2;
            case SWAMP -> 3;
            default -> 0;
        };
    }

    public static int blend(BiomeType a, BiomeType b, BiomeType c, BiomeType d, double x, double z) {
        double[] weights = new double[4];
        weights[style(a)] += (1 - x) * (1 - z);
        weights[style(b)] += x * (1 - z);
        weights[style(c)] += (1 - x) * z;
        weights[style(d)] += x * z;
        int style = 1;
        for (int i = 2; i < 4; i++) if (weights[i] > weights[style]) style = i;
        int strength = (int) Math.round(weights[style] * 7);
        return strength == 0 ? 0 : style * 8 + strength;
    }

    public static float[] color(int index, boolean foliage) {
        BiomeType biome = switch (index / 8) {
            case 1 -> BiomeType.DESERT;
            case 2 -> BiomeType.TAIGA;
            case 3 -> BiomeType.SWAMP;
            default -> BiomeType.PLAINS;
        };
        float[] base = foliage ? foliage(BiomeType.PLAINS) : grass(BiomeType.PLAINS);
        float[] target = foliage ? foliage(biome) : grass(biome);
        float strength = (index % 8) / 7f;
        for (int i = 0; i < 3; i++) base[i] += (target[i] - base[i]) * strength;
        return base;
    }

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
