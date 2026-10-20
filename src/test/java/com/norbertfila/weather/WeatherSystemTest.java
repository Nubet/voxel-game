package com.norbertfila.weather;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WeatherSystemTest {
    @Test
    void catchUpMatchesIndividualTicksAndRestoreContinuesTheSameSchedule() {
        var continuous = new WeatherSystem(42, 0);
        var jumped = new WeatherSystem(42, 0);
        for (int tick = 0; tick <= 500_000; tick++) continuous.update(tick);
        jumped.update(500_000);
        assertEquals(continuous.snapshot(), jumped.snapshot());
        var restored = new WeatherSystem(42, 500_000);
        restored.restore(jumped.snapshot(), 500_000);
        for (int tick = 500_000; tick < 1_000_000; tick += 1000) {
            jumped.update(tick);
            restored.update(tick);
            assertEquals(jumped.sample(tick), restored.sample(tick));
        }
    }

    @Test
    void cloudsArriveBeforeRainAndCommandsPreserveContinuity() {
        var weather = new WeatherSystem(42, 0);
        var clear = weather.sample(100);
        weather.set(WeatherType.STORM, 100);
        assertEquals(clear, weather.sample(100));
        int duration = weather.snapshot().transitionTicks();
        var early = weather.sample(100 + duration * .25);
        assertTrue(early.clouds() > clear.clouds());
        assertEquals(0, early.precipitation());
        assertEquals(0, early.storm());
        var storm = weather.sample(100 + duration);
        assertEquals(.95f, storm.precipitation(), .0001);
        assertEquals(1, storm.storm());
        weather.set(WeatherType.CLEAR, 100 + duration);
        var resumed = weather.sample(100 + duration);
        assertEquals(storm.clouds(), resumed.clouds());
        assertEquals(storm.precipitation(), resumed.precipitation());
    }

    @Test
    void naturalStormsRemainRareAndRespectCooldown() {
        var weather = new WeatherSystem(1234, 0);
        long lastStorm = -1;
        int storms = 0;
        for (int i = 0; i < 1000; i++) {
            weather.update(weather.snapshot().endsAt());
            if (weather.snapshot().type() != WeatherType.STORM) continue;
            long start = weather.snapshot().startedAt();
            if (lastStorm >= 0) assertTrue(start - lastStorm >= WeatherSystem.STORM_COOLDOWN);
            lastStorm = start;
            storms++;
        }
        assertTrue(storms > 0 && storms < 50);
    }

    @Test
    void rejectsInvalidSavedSchedulesAndClock() {
        var weather = new WeatherSystem(42, 100);
        assertThrows(IllegalArgumentException.class, () -> weather.update(99));
        assertThrows(IllegalArgumentException.class, () -> weather.sample(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> weather.restore(weather.snapshot(), Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new WeatherSample(Float.NaN, 0, 0, 0));
    }
}
