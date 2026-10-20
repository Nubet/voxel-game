package com.norbertfila.weather;

public record LocalWeather(float rain, float snow) {
    public LocalWeather {
        if (!Float.isFinite(rain) || !Float.isFinite(snow) || rain < 0 || snow < 0 || rain + snow > 1.5f)
            throw new IllegalArgumentException("Invalid local precipitation");
    }

    public LocalWeather mix(LocalWeather other, float alpha) {
        if (!Float.isFinite(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("Invalid biome interpolation");
        return new LocalWeather(rain + (other.rain - rain) * alpha, snow + (other.snow - snow) * alpha);
    }
}
