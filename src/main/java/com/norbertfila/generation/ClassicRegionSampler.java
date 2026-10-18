package com.norbertfila.generation;

import com.norbertfila.generation.biome.BiomeType;

final class ClassicRegionSampler {
    static final int CELL_SIZE = 1024;
    private final long seed;
    private final PerlinNoise coast;

    ClassicRegionSampler(long seed) {
        this.seed = seed;
        coast = new PerlinNoise(seed ^ 0xCBBB9D5DC1059ED8L);
    }

    Region sample(double x, double z) {
        long cx = (long) Math.floor(x / CELL_SIZE), cz = (long) Math.floor(z / CELL_SIZE);
        long hash = GenerationHash.hash(seed ^ 0x510E527FADE682D1L, cx, cz);
        int roll = (int) Long.remainderUnsigned(hash, 1000);
        BiomeType biome = roll < 8
                ? BiomeType.FLOATING_ISLES_CLASSIC
                : roll < 28
                        ? BiomeType.ARCHLANDS_CLASSIC
                        : roll < 72
                                ? BiomeType.OVERHANG_HIGHLANDS_CLASSIC
                                : roll < 116 ? BiomeType.HIDDEN_VALLEYS_CLASSIC : null;
        if (biome == null) return null;
        double centerX = cx * (double) CELL_SIZE + 448 + Long.remainderUnsigned(GenerationHash.hash(hash, 1, 0), 128);
        double centerZ = cz * (double) CELL_SIZE + 448 + Long.remainderUnsigned(GenerationHash.hash(hash, 2, 0), 128);
        double radius = 320 + Long.remainderUnsigned(GenerationHash.hash(hash, 3, 0), 80);
        double distance = Math.hypot(x - centerX, z - centerZ);
        double weight = smooth((radius - distance) / (radius * .35));
        return weight == 0 ? null : new Region(biome, centerX, centerZ, radius, weight, hash);
    }

    Region coast(double x, double z) {
        double weight = smooth((coast.sample(x * .0008, z * .0008) + .35) / .45);
        return weight == 0 ? null : new Region(BiomeType.COASTAL_CLIFFS_CLASSIC, x, z, 1, weight, seed);
    }

    static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    record Region(BiomeType biome, double x, double z, double radius, double weight, long seed) {}
}
