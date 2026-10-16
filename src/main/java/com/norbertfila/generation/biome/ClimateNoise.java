package com.norbertfila.generation.biome;

import com.norbertfila.generation.Noise2D;
import com.norbertfila.generation.PerlinNoise;
import com.norbertfila.generation.TerrainConfig;

public final class ClimateNoise {
    private final Noise2D temperature;
    private final Noise2D humidity;
    private final double temperatureAmplitude;
    private final double humidityAmplitude;

    public ClimateNoise(long seed, TerrainConfig config) {
        temperature = config.temperature().fractal(new PerlinNoise(seed ^ 0x4CF5AD432745937FL));
        humidity = config.humidity().fractal(new PerlinNoise(seed ^ 0x9E3779B97F4A7C15L));
        temperatureAmplitude = config.temperature().amplitude();
        humidityAmplitude = config.humidity().amplitude();
    }

    public ClimateSample sample(double x, double z) {
        return new ClimateSample(
                value(temperature.sample(x, z), temperatureAmplitude), value(humidity.sample(x, z), humidityAmplitude));
    }

    private static float value(double value, double amplitude) {
        if (!Double.isFinite(value) || value < -1 || value > 1)
            throw new IllegalStateException("Climate noise must be finite and within [-1, 1]");
        if (amplitude == 0) return 0;
        return (float) Math.clamp(value * amplitude, -1, 1);
    }
}
