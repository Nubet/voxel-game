package com.norbertfila.ui.crosshair;

import java.util.Objects;

public final class CrosshairTransition {
    private static final double DURATION_SECONDS = .08;
    private CrosshairStyle style = CrosshairStyle.HIDDEN;
    private double visibility;

    public void update(CrosshairStyle desired, boolean smooth, double deltaSeconds) {
        Objects.requireNonNull(desired);
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0)
            throw new IllegalArgumentException("Crosshair delta must be finite and nonnegative");
        if (desired != CrosshairStyle.HIDDEN) style = desired;
        double target = desired == CrosshairStyle.HIDDEN ? 0 : 1;
        if (!smooth) visibility = target;
        else {
            double step = deltaSeconds / DURATION_SECONDS;
            visibility =
                    target > visibility ? Math.min(target, visibility + step) : Math.max(target, visibility - step);
        }
        if (visibility == 0) style = CrosshairStyle.HIDDEN;
    }

    public void reset() {
        style = CrosshairStyle.HIDDEN;
        visibility = 0;
    }

    public CrosshairStyle style() {
        return style;
    }

    public float visibility() {
        return (float) visibility;
    }
}
