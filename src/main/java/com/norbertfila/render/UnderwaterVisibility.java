package com.norbertfila.render;

public final class UnderwaterVisibility {
    static final float SKY_RED = .55f;
    static final float SKY_GREEN = .76f;
    static final float SKY_BLUE = .96f;
    static final float FOG_RED = .035f;
    static final float FOG_GREEN = .20f;
    static final float FOG_BLUE = .27f;
    static final float FOG_DENSITY = .10f;
    static final float CLEAR_DISTANCE = 1.5f;
    private static final double TRANSITION_SECONDS = .08;
    private boolean submerged;
    private double amount;

    public void update(boolean eyesSubmerged, boolean available, double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0)
            throw new IllegalArgumentException("Visibility delta must be finite and nonnegative");
        if (available) submerged = eyesSubmerged;
        double target = submerged ? 1 : 0;
        amount += (target - amount) * -Math.expm1(-deltaSeconds / TRANSITION_SECONDS);
        if (Math.abs(target - amount) < .001) amount = target;
    }

    public float amount() {
        return (float) amount;
    }

    public float backgroundRed() {
        return blend(SKY_RED, FOG_RED);
    }

    public float backgroundGreen() {
        return blend(SKY_GREEN, FOG_GREEN);
    }

    public float backgroundBlue() {
        return blend(SKY_BLUE, FOG_BLUE);
    }

    private float blend(float dry, float wet) {
        return dry + (wet - dry) * amount();
    }
}
