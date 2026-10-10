package com.norbertfila.generation;

import java.util.Objects;

public final class FractalNoise implements Noise2D {
    private final Noise2D noise;
    private final double[] frequencies;
    private final double[] weights;

    public FractalNoise(Noise2D noise, double frequency, int octaves, double persistence, double lacunarity) {
        this.noise = Objects.requireNonNull(noise);
        validateParameters(frequency, octaves, persistence, lacunarity);
        frequencies = new double[octaves];
        weights = new double[octaves];
        double amplitude = 1;
        double totalAmplitude = 0;
        for (int i = 0; i < octaves; i++) {
            frequencies[i] = frequency;
            weights[i] = amplitude;
            totalAmplitude += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        for (int i = 0; i < octaves; i++) weights[i] /= totalAmplitude;
    }

    @Override
    public double sample(double x, double z) {
        double sum = 0;
        for (int i = 0; i < frequencies.length; i++) {
            if (weights[i] != 0) sum += noise.sample(x * frequencies[i], z * frequencies[i]) * weights[i];
        }
        return sum;
    }

    static double validateParameters(double frequency, int octaves, double persistence, double lacunarity) {
        if (!Double.isFinite(frequency)
                || frequency <= 0
                || octaves < 1
                || octaves > 32
                || !Double.isFinite(persistence)
                || persistence < 0
                || persistence > 1
                || !Double.isFinite(lacunarity)
                || lacunarity <= 0)
            throw new IllegalArgumentException("Invalid fBm parameters (octaves must be in [1, 32])");
        double maximumFrequency = frequency;
        for (int i = 1; i < octaves; i++) {
            frequency *= lacunarity;
            if (!Double.isFinite(frequency) || frequency <= 0)
                throw new IllegalArgumentException("Octave frequency overflow or underflow");
            maximumFrequency = Math.max(maximumFrequency, frequency);
        }
        return maximumFrequency;
    }
}
