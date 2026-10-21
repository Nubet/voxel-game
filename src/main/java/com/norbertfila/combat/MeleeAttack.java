package com.norbertfila.combat;

import java.util.Objects;

/** Resolves one melee swing into damage and knockback following Minecraft Java 1.9+ rules. */
public final class MeleeAttack {
    /** Minecraft pushes a struck body by 0.4 blocks per 20 Hz tick. */
    public static final double KNOCKBACK = 8;

    private MeleeAttack() {}

    public static Outcome resolve(MeleeWeapon weapon, AttackCharge charge) {
        Objects.requireNonNull(weapon);
        Objects.requireNonNull(charge);
        return new Outcome((float) (weapon.damage() * charge.multiplier(weapon)), KNOCKBACK);
    }

    /** Damage in health points and horizontal push in blocks per second. */
    public record Outcome(float damage, double knockback) {
        public Outcome {
            if (!Float.isFinite(damage) || damage < 0 || !Double.isFinite(knockback) || knockback < 0)
                throw new IllegalArgumentException("Invalid attack outcome");
        }
    }
}
