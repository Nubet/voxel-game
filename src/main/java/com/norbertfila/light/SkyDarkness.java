package com.norbertfila.light;

import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.WeatherSample;

/** Minecraft's sky darkening: how many levels to subtract from sky light, 0 at noon and 11 at midnight. */
public final class SkyDarkness {
    public static final int MAX = 11;

    private SkyDarkness() {}

    public static int of(WorldTime time, WeatherSample weather) {
        double day = (double) time.minecraftTime() / WorldTime.MINECRAFT_TICKS_PER_DAY - .25;
        day -= Math.floor(day);
        double celestial = (day * 2 + .5 - Math.cos(day * Math.PI) / 2) / 3;
        double brightness = 1 - Math.clamp(1 - (Math.cos(celestial * Math.TAU) * 2 + .5), 0, 1);
        brightness *= 1 - weather.precipitation() * 5 / 16.0;
        brightness *= 1 - weather.storm() * 5 / 16.0;
        return (int) ((1 - brightness) * MAX);
    }
}
