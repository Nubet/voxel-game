package com.norbertfila.generation;

import com.norbertfila.generation.biome.ClimateSample;

final class BadlandsLandforms {
    private final Noise2D noise;

    BadlandsLandforms(long seed) {
        noise = new PerlinNoise(seed ^ 0xA54FF53A5F1D36F1L);
    }

    static double weight(ClimateSample climate) {
        double temperature = climate.temperature();
        double humidity = climate.humidity();
        double desert = smooth(temperature, .30, .35) * (1 - smooth(humidity, -.05, .02));
        return smooth(temperature, .2, .28) * (1 - smooth(humidity, .10, .25)) * (1 - desert);
    }

    double sample(double x, double z) {
        double wx = x + 65 * noise.sample(x / 180, z / 180);
        double wz = z + 65 * noise.sample(x / 180 + 71.3, z / 180 - 39.1);
        double character = noise.sample(wx / 320 + 53.7, wz / 320 - 81.9);
        double mesa = smooth(noise.sample(wx / 140, wz / 140), -.04, .035) * (.32 + .16 * smooth(character, -.3, .3));
        double channel = Math.abs(noise.sample(wx / 110 - 123.4, wz / 110 + 89.7));
        double tributary = Math.abs(noise.sample(wx / 48 + 31.7, wz / 48 + 63.1));
        double erosion = Math.max(
                1 - smooth(channel, .018, .095), (1 - smooth(tributary, .012, .055)) * (1 - smooth(channel, .10, .24)));
        double pinnacle = smooth(
                Math.min(noise.sample(wx / 48 + 179.1, wz / 48 - 113.7), noise.sample(wx / 61 - 47.3, wz / 61 + 237.9)),
                .08,
                .20);
        double relief = mesa + pinnacle * (.62 + .08 * smooth(character, -.2, .2) - mesa);
        double floor = .04
                + .05 * noise.sample(wx / 35 - 17.1, wz / 35 + 93.7)
                + .025 * noise.sample(wx / 12 + 73.1, wz / 12 - 29.3);
        return Math.clamp(
                floor + relief * (1 - erosion) - .10 * erosion,
                RegionalLandforms.MIN_ELEVATION,
                RegionalLandforms.MAX_ELEVATION);
    }

    private static double smooth(double value, double start, double end) {
        return TerrainGenerator.mountainMask(value, start, end);
    }
}
