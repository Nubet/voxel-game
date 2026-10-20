package com.norbertfila.simulation;

import java.util.Objects;
import java.util.function.DoubleConsumer;

public final class FixedSimulation {
    public static final int TICKS_PER_SECOND = 60;
    public static final double TICK_SECONDS = 1.0 / TICKS_PER_SECOND;
    public static final double MIN_TICK_RATE = 1;
    public static final double MAX_TICK_RATE = 10000;
    private final double fixedDeltaSeconds;
    // Tick rate changes wall-clock scheduling, not the fixed physics step.
    private double tickIntervalSeconds;
    private final int maxCatchUpTicks;
    private double accumulator;
    private long tickCount;

    public FixedSimulation(double fixedDeltaSeconds, int maxCatchUpTicks) {
        if (!Double.isFinite(fixedDeltaSeconds) || fixedDeltaSeconds <= 0 || maxCatchUpTicks <= 0) {
            throw new IllegalArgumentException("Invalid fixed simulation configuration");
        }
        this.fixedDeltaSeconds = fixedDeltaSeconds;
        this.tickIntervalSeconds = fixedDeltaSeconds;
        this.maxCatchUpTicks = maxCatchUpTicks;
    }

    public int advance(double frameDeltaSeconds, DoubleConsumer tick) {
        Objects.requireNonNull(tick);
        if (!Double.isFinite(frameDeltaSeconds) || frameDeltaSeconds < 0) {
            throw new IllegalArgumentException("Frame delta must be finite and non-negative");
        }
        accumulator = Math.min(accumulator + frameDeltaSeconds, tickIntervalSeconds * maxCatchUpTicks);
        int ticks = 0;
        while (accumulator >= tickIntervalSeconds && ticks < maxCatchUpTicks) {
            tickCount = Math.incrementExact(tickCount);
            accumulator -= tickIntervalSeconds;
            ticks++;
            tick.accept(fixedDeltaSeconds);
        }
        return ticks;
    }

    public double interpolationAlpha() {
        return accumulator / tickIntervalSeconds;
    }

    public double tickRate() {
        return 1.0 / tickIntervalSeconds;
    }

    public void setTickRate(double rate) {
        if (!Double.isFinite(rate) || rate < MIN_TICK_RATE || rate > MAX_TICK_RATE)
            throw new IllegalArgumentException("Tick rate must be finite and in [1, 10000]");
        double progress = Math.clamp(interpolationAlpha(), 0, 1);
        tickIntervalSeconds = 1.0 / rate;
        accumulator = progress * tickIntervalSeconds;
    }

    public long tickCount() {
        return tickCount;
    }
}
