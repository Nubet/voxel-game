package com.norbertfila.weather;

import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.simulation.WorldTime;
import java.util.Objects;

public final class WeatherSystem {
    private static final int SECOND = FixedSimulation.TICKS_PER_SECOND;
    public static final long STORM_COOLDOWN = 3L * WorldTime.TICKS_PER_DAY;
    private final long seed;
    private WeatherState state;

    public WeatherSystem(long seed, long tick) {
        if (tick < 0) throw new IllegalArgumentException("Negative weather clock");
        this.seed = seed;
        state = schedule(WeatherType.CLEAR, tick, 0, -1, new WeatherSample(.15f, 0, 0, 0), false);
    }

    public WeatherState snapshot() {
        return state;
    }

    public void restore(WeatherState saved, long tick) {
        Objects.requireNonNull(saved);
        if (tick < saved.startedAt() || tick >= saved.endsAt())
            throw new IllegalArgumentException("Weather schedule does not contain the world clock");
        state = saved;
    }

    public void update(long tick) {
        if (tick < state.startedAt()) throw new IllegalArgumentException("Weather clock moved backwards");
        while (tick >= state.endsAt()) {
            long nextTick = state.endsAt();
            WeatherType next = switch (state.type()) {
                case CLEAR -> WeatherType.CLOUDY;
                case CLOUDY -> random(state.cycle(), 1) < .55 ? WeatherType.RAIN : WeatherType.CLEAR;
                case RAIN ->
                    (state.lastStormTick() == -1 || nextTick - state.lastStormTick() >= STORM_COOLDOWN)
                                    && random(state.cycle(), 2) < .1
                            ? WeatherType.STORM
                            : WeatherType.CLOUDY;
                case STORM -> WeatherType.RAIN;
            };
            set(next, nextTick, false);
        }
    }

    public void set(WeatherType type, long tick) {
        set(type, tick, true);
    }

    private void set(WeatherType type, long tick, boolean instant) {
        Objects.requireNonNull(type);
        WeatherSample previous = sample(tick);
        state = schedule(
                type,
                tick,
                Math.incrementExact(state.cycle()),
                type == WeatherType.STORM ? tick : state.lastStormTick(),
                previous,
                instant);
    }

    public WeatherSample sample(double tick) {
        if (!Double.isFinite(tick) || tick < state.startedAt())
            throw new IllegalArgumentException("Invalid weather clock");
        double progress = state.transitionTicks() == 0 ? 1 : (tick - state.startedAt()) / state.transitionTicks();
        float clouds = switch (state.type()) {
            case CLEAR -> .15f;
            case CLOUDY -> .8f;
            case RAIN -> .95f;
            case STORM -> 1;
        };
        float rain = switch (state.type()) {
            case CLEAR, CLOUDY -> 0;
            case RAIN ->
                (float) (.35
                        + .35 * random(state.cycle(), 3)
                        + .08 * Math.sin((tick - state.startedAt()) / (SECOND * 25d)));
            case STORM -> .95f;
        };
        float storm = mix(state.fromStorm(), state.type() == WeatherType.STORM ? 1 : 0, smooth((progress - .65) / .35));
        return new WeatherSample(
                mix(state.fromClouds(), clouds, smooth(progress)),
                mix(state.fromPrecipitation(), rain, smooth((progress - .3) / .7)),
                storm,
                flash(tick) * storm);
    }

    private float flash(double tick) {
        if (state.type() != WeatherType.STORM) return 0;
        double elapsed = tick - state.startedAt() - state.transitionTicks();
        if (elapsed < 0) return 0;
        long slot = (long) (elapsed / (SECOND * 12));
        if (random(state.cycle() ^ slot, 7) > .35) return 0;
        double age = elapsed - slot * SECOND * 12 - random(state.cycle() ^ slot, 8) * SECOND * 10;
        return age < 0 || age >= SECOND * .2 ? 0 : (float) (1 - age / (SECOND * .2));
    }

    private WeatherState schedule(
            WeatherType type, long tick, long cycle, long lastStorm, WeatherSample from, boolean instant) {
        int seconds = switch (type) {
            case CLEAR -> 300 + (int) (random(cycle, 4) * 780);
            case CLOUDY -> 60 + (int) (random(cycle, 4) * 180);
            case RAIN -> 180 + (int) (random(cycle, 4) * 300);
            case STORM -> 60 + (int) (random(cycle, 4) * 120);
        };
        int transition = instant ? 0 : (45 + (int) (random(cycle, 5) * 45)) * SECOND;
        return new WeatherState(
                type,
                tick,
                transition,
                Math.addExact(tick, transition + (long) seconds * SECOND),
                cycle,
                lastStorm,
                from.clouds(),
                from.precipitation(),
                from.storm());
    }

    private double random(long cycle, long channel) {
        long value = seed ^ 0x57454154484552L ^ cycle * 0x9E3779B97F4A7C15L ^ channel * 0xC2B2AE3D27D4EB4FL;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }

    private static float smooth(double value) {
        float t = (float) Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float mix(float from, float to, float alpha) {
        return alpha == 1 ? to : from + (to - from) * alpha;
    }
}
