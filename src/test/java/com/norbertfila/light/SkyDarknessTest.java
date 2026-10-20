package com.norbertfila.light;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.WeatherSample;
import org.junit.jupiter.api.Test;

class SkyDarknessTest {
    @Test
    void darkeningFollowsTheDayAndDeepensInStorms() {
        assertEquals(0, at(6000, WeatherSample.CLEAR));
        assertEquals(11, at(18000, WeatherSample.CLEAR));
        assertTrue(at(13000, WeatherSample.CLEAR) > 0 && at(13000, WeatherSample.CLEAR) < 11);
        assertEquals(3, at(6000, new WeatherSample(1, 1, 0, 0)));
        assertEquals(5, at(6000, new WeatherSample(1, 1, 1, 0)));
        assertEquals(11, at(18000, new WeatherSample(1, 1, 1, 0)));
    }

    private static int at(int minecraftTime, WeatherSample weather) {
        return SkyDarkness.of(WorldTime.INITIAL.withMinecraftTime(minecraftTime), weather);
    }
}
