package com.norbertfila.weather;

public record WeatherSample(float clouds, float precipitation, float storm, float flash) {
    public WeatherSample {
        if (!unit(clouds) || !unit(precipitation) || !unit(storm) || !unit(flash))
            throw new IllegalArgumentException("Weather values must be in [0, 1]");
    }

    private static boolean unit(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 1;
    }
}
