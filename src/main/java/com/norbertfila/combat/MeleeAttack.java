package com.norbertfila.combat;

import java.util.Objects;

/** Resolves one melee swing into damage and knockback following Minecraft Java 1.9+ rules. */
public final class MeleeAttack {
    /** Minecraft pushes a struck body by 0.4 blocks per 20 Hz tick. */
    public static final double KNOCKBACK = 8;

    static final double CRITICAL_MULTIPLIER = 1.5;
    /** Critical hits need an almost recharged swing. */
    static final double CRITICAL_CHARGE = .9;

    private MeleeAttack() {}

    public static Outcome resolve(MeleeWeapon weapon, AttackCharge charge, Attacker attacker) {
        Objects.requireNonNull(weapon);
        Objects.requireNonNull(charge);
        Objects.requireNonNull(attacker);
        boolean critical = charge.charge(weapon) > CRITICAL_CHARGE
                && attacker.falling()
                && !attacker.grounded()
                && !attacker.sprinting()
                && !attacker.inWater();
        double damage = weapon.damage() * charge.multiplier(weapon) * (critical ? CRITICAL_MULTIPLIER : 1);
        return new Outcome((float) damage, KNOCKBACK, critical);
    }

    /** Damage in health points and horizontal push in blocks per second. */
    public record Outcome(float damage, double knockback, boolean critical) {
        public Outcome {
            if (!Float.isFinite(damage) || damage < 0 || !Double.isFinite(knockback) || knockback < 0)
                throw new IllegalArgumentException("Invalid attack outcome");
        }
    }
}
