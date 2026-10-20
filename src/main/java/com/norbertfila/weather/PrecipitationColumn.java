package com.norbertfila.weather;

import com.norbertfila.world.WorldBounds;
import java.util.Objects;

public record PrecipitationColumn(int x, int z, float surface, LocalWeather weather) {
    public PrecipitationColumn {
        Objects.requireNonNull(weather);
        if (!Float.isFinite(surface) || surface < WorldBounds.MIN_Y || surface > WorldBounds.MAX_Y_EXCLUSIVE)
            throw new IllegalArgumentException("Invalid precipitation surface");
    }
}
