package com.norbertfila.render;

import com.norbertfila.weather.WeatherSample;
import com.norbertfila.world.Chunk;

public record DistanceFog(float start, float end) {
    public DistanceFog {
        if (!Float.isFinite(start) || !Float.isFinite(end) || start < 0 || end <= start)
            throw new IllegalArgumentException("Fog range must be finite with 0 <= start < end");
    }

    public static DistanceFog forDistances(int detailChunks, int landscapeChunks) {
        if (detailChunks < 1 || landscapeChunks < 0)
            throw new IllegalArgumentException("View distances must be positive, or zero for disabled landscape");
        float end = (float) Math.max(detailChunks, landscapeChunks) * Chunk.SIZE;
        return new DistanceFog(end * .65f, end);
    }

    public DistanceFog weather(WeatherSample weather) {
        float amount = Math.max(weather.precipitation() * .75f, weather.storm());
        float stormEnd = Math.min(end, 80);
        float fogEnd = end + (stormEnd - end) * amount;
        return new DistanceFog(Math.min(start, fogEnd * (.65f - amount * .2f)), fogEnd);
    }
}
