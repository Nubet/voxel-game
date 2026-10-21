package com.norbertfila.combat;

import java.util.Objects;

/** Minecraft 1.9 attack strength in seconds, so that it does not depend on the tick rate. */
public final class AttackCharge {
    /** Minecraft samples strength half of its 20 Hz tick ahead. */
    static final double LEAD_SECONDS = .025;

    private static final double SETTLED_SECONDS = 60;
    private double seconds = SETTLED_SECONDS;

    public void tick(double dt) {
        if (!Double.isFinite(dt) || dt < 0) throw new IllegalArgumentException("Invalid attack charge delta");
        seconds = Math.min(SETTLED_SECONDS, seconds + dt);
    }

    public void reset() {
        seconds = 0;
    }

    public double charge(MeleeWeapon weapon) {
        Objects.requireNonNull(weapon);
        return Math.clamp((seconds + LEAD_SECONDS) / weapon.cooldownSeconds(), 0, 1);
    }

    public double multiplier(MeleeWeapon weapon) {
        double charge = charge(weapon);
        return .2 + .8 * charge * charge;
    }
}
