package com.norbertfila.simulation;

import java.util.Objects;
import java.util.function.DoubleConsumer;

public final class FixedSimulation {
    public static final int TICKS_PER_SECOND = 60;
    public static final double TICK_SECONDS = 1.0 / TICKS_PER_SECOND;
    private final double fixedDeltaSeconds;
    private final int maxCatchUpTicks;
    private double accumulator;
    private long tickCount;

    public FixedSimulation(double fixedDeltaSeconds, int maxCatchUpTicks) {
        if (!Double.isFinite(fixedDeltaSeconds) || fixedDeltaSeconds <= 0 || maxCatchUpTicks <= 0) {
            throw new IllegalArgumentException("Invalid fixed simulation configuration");
        }
        this.fixedDeltaSeconds = fixedDeltaSeconds;
        this.maxCatchUpTicks = maxCatchUpTicks;
    }

    public int advance(double frameDeltaSeconds, DoubleConsumer tick) {
        Objects.requireNonNull(tick);
        if (!Double.isFinite(frameDeltaSeconds) || frameDeltaSeconds < 0) {
            throw new IllegalArgumentException("Frame delta must be finite and non-negative");
        }
        accumulator = Math.min(accumulator + frameDeltaSeconds, fixedDeltaSeconds * maxCatchUpTicks);
        int ticks = 0;
        while (accumulator >= fixedDeltaSeconds && ticks < maxCatchUpTicks) {
            tickCount = Math.incrementExact(tickCount);
            accumulator -= fixedDeltaSeconds;
            ticks++;
            tick.accept(fixedDeltaSeconds);
        }
        return ticks;
    }

    public double interpolationAlpha() {
        return accumulator / fixedDeltaSeconds;
    }

    public long tickCount() {
        return tickCount;
    }
}
