package com.norbertfila.generation;

final class CaveRegionSampler {
    private final PerlinNoise density;
    private final PerlinNoise character;
    private final double scale;

    CaveRegionSampler(long seed, CaveConfig config) {
        density = new PerlinNoise(seed ^ 0xD6E8FEB86659FD93L);
        character = new PerlinNoise(seed ^ 0x94D049BB133111EBL);
        scale = config.regionScale();
    }

    Sample sample(double x, double z) {
        double amount = Math.clamp(.5 + density.sample(x / scale + .37, z / scale - .61) * 1.8, 0, 1);
        double style = Math.clamp(.5 + character.sample(x / scale - 4.7, z / scale + 8.1) * 1.5, 0, 1);
        return new Sample(
                amount,
                .7 + style * .6,
                .12 + amount * .24,
                .3 + style * .5,
                .025 + amount * .045,
                .008 + style * .012);
    }

    record Sample(
            double density,
            double winding,
            double branchChance,
            double width,
            double chamberChance,
            double verticalChance) {}
}
