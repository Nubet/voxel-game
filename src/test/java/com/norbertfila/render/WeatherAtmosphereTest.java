package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.weather.WeatherSample;
import org.junit.jupiter.api.Test;

class WeatherAtmosphereTest {
    @Test
    void clearWeatherPreservesSkyAndStormHidesCelestialsWithoutMovingTheSun() {
        for (var sky : new SkyState[] {SkyModel.DAY, SkyModel.at(54000)}) {
            assertEquals(sky, SkyModel.weather(sky, WeatherSample.CLEAR));
            var storm = SkyModel.weather(sky, new WeatherSample(1, 1, 1, 0));
            assertEquals(sky.sunX(), storm.sunX());
            assertEquals(sky.sunY(), storm.sunY());
            assertEquals(0, storm.celestialVisibility());
            assertEquals(0, storm.stars());
            assertTrue(storm.light() < sky.light());
            var flash = SkyModel.weather(sky, new WeatherSample(1, 1, 1, 1));
            assertEquals(storm.light(), flash.light());
            assertTrue(flash.zenith().red() > storm.zenith().red());
        }
    }

    @Test
    void fogNeverExtendsTheConfiguredViewAndWeatherInterpolates() {
        var clear = WeatherSample.CLEAR;
        var storm = new WeatherSample(1, 1, 1, 0);
        var half = clear.mix(storm, .5);
        assertEquals(.5f, half.precipitation());
        for (int distance : new int[] {1, 4, 16, 32}) {
            var fog = DistanceFog.forDistances(distance, 0);
            assertEquals(fog, fog.weather(clear));
            assertTrue(fog.weather(storm).end() <= fog.end());
            assertTrue(fog.weather(half).end() >= fog.weather(storm).end());
        }
    }
}
