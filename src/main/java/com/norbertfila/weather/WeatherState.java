package com.norbertfila.weather;

import java.util.Objects;

public record WeatherState(
        WeatherType type,
        long startedAt,
        int transitionTicks,
        long endsAt,
        long cycle,
        long lastStormTick,
        float fromClouds,
        float fromPrecipitation,
        float fromStorm) {
    public WeatherState {
        Objects.requireNonNull(type);
        if (startedAt < 0
                || transitionTicks < 1
                || endsAt <= startedAt
                || endsAt - startedAt < transitionTicks
                || cycle < 0
                || lastStormTick < -1
                || lastStormTick > startedAt) throw new IllegalArgumentException("Invalid weather schedule");
        new WeatherSample(fromClouds, fromPrecipitation, fromStorm, 0);
    }
}
