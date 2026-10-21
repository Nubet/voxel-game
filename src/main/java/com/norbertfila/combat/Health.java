package com.norbertfila.combat;

import java.util.Objects;

/** Health points with Minecraft's hurt cooldown, shared by players and mobs. */
public final class Health {
    /** Minecraft ignores further damage for half a second after a hit. */
    public static final int HURT_TICKS = 30;

    private final float max;
    private float value;
    private int hurtTicks;

    public Health(float max) {
        if (!Float.isFinite(max) || max <= 0) throw new IllegalArgumentException("Maximum health must be positive");
        this.max = max;
        value = max;
    }

    public float value() {
        return value;
    }

    public float max() {
        return max;
    }

    public boolean dead() {
        return value <= 0;
    }

    public int hurtTicks() {
        return hurtTicks;
    }

    /** Returns false when the hit landed during the hurt cooldown, or on a dead body, and was ignored. */
    public boolean damage(DamageSource source, float amount) {
        Objects.requireNonNull(source);
        if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid damage");
        if (dead() || hurtTicks > 0) return false;
        value = Math.max(0, value - amount);
        hurtTicks = HURT_TICKS;
        return true;
    }

    public void tick() {
        if (hurtTicks > 0) hurtTicks--;
    }

    public void restore() {
        value = max;
        hurtTicks = 0;
    }
}
