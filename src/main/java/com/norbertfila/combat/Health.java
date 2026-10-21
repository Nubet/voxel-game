package com.norbertfila.combat;

import java.util.Objects;

/** Health points with Minecraft's hurt cooldown, shared by players and mobs. */
public final class Health {
    /** Minecraft's half-second hurt cooldown. */
    public static final int HURT_TICKS = 30;

    private final float max;
    private float value;
    private int hurtTicks;
    private float lastDamage;

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

    /**
     * During the hurt cooldown only a stronger hit counts, and it deals just the difference to the previous one, as
     * in Minecraft; such a top-up neither restarts the cooldown nor knocks back.
     */
    public Hit damage(DamageSource source, float amount) {
        Objects.requireNonNull(source);
        if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid damage");
        if (dead()) return Hit.IGNORED;
        if (hurtTicks > 0) {
            if (amount <= lastDamage) return Hit.IGNORED;
            value = Math.max(0, value - (amount - lastDamage));
            lastDamage = amount;
            return Hit.TOP_UP;
        }
        value = Math.max(0, value - amount);
        lastDamage = amount;
        hurtTicks = HURT_TICKS;
        return Hit.FRESH;
    }

    public void tick() {
        if (hurtTicks > 0) hurtTicks--;
    }

    public void restore() {
        value = max;
        hurtTicks = 0;
        lastDamage = 0;
    }

    public enum Hit {
        IGNORED,
        TOP_UP,
        FRESH;

        public boolean landed() {
            return this != IGNORED;
        }
    }
}
