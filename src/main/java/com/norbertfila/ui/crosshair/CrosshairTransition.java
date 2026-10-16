package com.norbertfila.ui.crosshair;

import java.util.Arrays;
import java.util.Objects;

public final class CrosshairTransition {
    private static final double DURATION_SECONDS = .08;
    private CrosshairStyle style = CrosshairStyle.HIDDEN;
    private double visibility;
    private final double[] weights = new double[CrosshairStyle.values().length];
    private final double[] startWeights = new double[weights.length];
    private double blendSeconds = DURATION_SECONDS;

    public void update(CrosshairStyle desired, boolean smooth, double deltaSeconds) {
        Objects.requireNonNull(desired);
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0)
            throw new IllegalArgumentException("Crosshair delta must be finite and nonnegative");
        if (desired != CrosshairStyle.HIDDEN && desired != style) {
            System.arraycopy(weights, 0, startWeights, 0, weights.length);
            style = desired;
            blendSeconds = visibility == 0 ? DURATION_SECONDS : 0;
        }
        blendSeconds = smooth ? Math.min(DURATION_SECONDS, blendSeconds + deltaSeconds) : DURATION_SECONDS;
        double progress = blendSeconds / DURATION_SECONDS;
        double blend = progress * progress * (3 - 2 * progress);
        for (int i = 0; i < weights.length; i++)
            weights[i] = startWeights[i] * (1 - blend) + (i == style.ordinal() ? blend : 0);
        double target = desired == CrosshairStyle.HIDDEN ? 0 : 1;
        if (!smooth) visibility = target;
        else {
            double step = deltaSeconds / DURATION_SECONDS;
            visibility =
                    target > visibility ? Math.min(target, visibility + step) : Math.max(target, visibility - step);
        }
        if (visibility == 0) reset();
    }

    public void reset() {
        style = CrosshairStyle.HIDDEN;
        visibility = 0;
        Arrays.fill(weights, 0);
        Arrays.fill(startWeights, 0);
        blendSeconds = DURATION_SECONDS;
    }

    public CrosshairStyle style() {
        return style;
    }

    public float visibility() {
        return (float) visibility;
    }

    public float visibility(CrosshairStyle style) {
        return (float) (visibility * weights[Objects.requireNonNull(style).ordinal()]);
    }
}
