package com.norbertfila.weather;

public record WeatherSample(float clouds, float precipitation, float storm, float flash) {
    public static final WeatherSample CLEAR = new WeatherSample(.15f, 0, 0, 0);

    public WeatherSample mix(WeatherSample other, double alpha) {
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("Invalid weather interpolation");
        float t = (float) alpha;
        return new WeatherSample(
                clouds + (other.clouds - clouds) * t,
                precipitation + (other.precipitation - precipitation) * t,
                storm + (other.storm - storm) * t,
                flash + (other.flash - flash) * t);
    }

    public WeatherSample {
        if (!unit(clouds) || !unit(precipitation) || !unit(storm) || !unit(flash))
            throw new IllegalArgumentException("Weather values must be in [0, 1]");
    }

    private static boolean unit(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 1;
    }
}
