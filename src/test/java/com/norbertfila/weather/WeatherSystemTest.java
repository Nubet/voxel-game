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
    void naturalTransitionsStayContinuousAndCloudsArriveBeforeRain() {
        var weather = new WeatherSystem(42, 0);
        WeatherSample previous;
        long start;
        do {
            start = weather.snapshot().endsAt();
            previous = weather.sample(start);
            weather.update(start);
            var next = weather.sample(start);
            assertEquals(previous.clouds(), next.clouds());
            assertEquals(previous.precipitation(), next.precipitation());
            assertEquals(previous.storm(), next.storm());
        } while (weather.snapshot().type() != WeatherType.RAIN);
        int duration = weather.snapshot().transitionTicks();
        assertTrue(duration > 0);
        var early = weather.sample(start + duration * .25);
        assertTrue(early.clouds() > previous.clouds());
        assertEquals(0, early.precipitation());
        assertEquals(0, early.storm());
        assertTrue(weather.sample(start + duration).precipitation() > 0);
    }

    @Test
    void commandsApplyEveryWeatherImmediatelyAndRestoreWithoutATransition() {
        var weather = new WeatherSystem(42, 0);
        for (var type :
                new WeatherType[] {WeatherType.STORM, WeatherType.CLEAR, WeatherType.RAIN, WeatherType.CLOUDY}) {
            weather.set(type, 100);
            var sample = weather.sample(100);
            assertEquals(0, weather.snapshot().transitionTicks());
            assertEquals(type == WeatherType.STORM ? 1 : 0, sample.storm());
            switch (type) {
                case CLEAR -> assertEquals(WeatherSample.CLEAR, sample);
                case CLOUDY -> {
                    assertEquals(.8f, sample.clouds());
                    assertEquals(0, sample.precipitation());
                }
                case RAIN -> assertTrue(sample.precipitation() > 0);
                case STORM -> assertEquals(.95f, sample.precipitation(), .0001);
            }
            var restored = new WeatherSystem(42, 100);
            restored.restore(weather.snapshot(), 100);
            assertEquals(sample, restored.sample(100));
        }
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
