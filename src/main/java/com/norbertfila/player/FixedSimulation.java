package com.norbertfila.player;

import java.util.Objects;
import java.util.function.DoubleConsumer;

public final class FixedSimulation {
    private final double fixedDeltaSeconds;
    private final int maxCatchUpTicks;
    private double accumulator;

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
            tick.accept(fixedDeltaSeconds);
            accumulator -= fixedDeltaSeconds;
            ticks++;
        }
        return ticks;
    }

    public double interpolationAlpha() {
        return accumulator / fixedDeltaSeconds;
    }
}
