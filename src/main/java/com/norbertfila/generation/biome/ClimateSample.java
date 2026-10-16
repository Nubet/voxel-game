package com.norbertfila.generation.biome;

public record ClimateSample(float temperature, float humidity) {
    public ClimateSample {
        if (!Float.isFinite(temperature)
                || temperature < -1
                || temperature > 1
                || !Float.isFinite(humidity)
                || humidity < -1
                || humidity > 1) throw new IllegalArgumentException("Climate values must be finite and within [-1, 1]");
    }
}
