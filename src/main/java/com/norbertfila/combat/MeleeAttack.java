package com.norbertfila.combat;

import java.util.Objects;

/** Resolves one melee swing into damage and knockback following Minecraft Java 1.9+ rules. */
public final class MeleeAttack {
    /** Minecraft pushes a struck body by 0.4 blocks per 20 Hz tick. */
    public static final double KNOCKBACK = 8;
    /** A sprinting hit adds 0.5 blocks per 20 Hz tick along the attacker's facing. */
    public static final double SPRINT_KNOCKBACK = 10;

    static final double CRITICAL_MULTIPLIER = 1.5;
    /** Critical and sprint hits need an almost recharged swing. */
    static final double STRONG_CHARGE = .9;

    private MeleeAttack() {}

    public static Outcome resolve(MeleeWeapon weapon, AttackCharge charge, Attacker attacker) {
        Objects.requireNonNull(weapon);
        Objects.requireNonNull(charge);
        Objects.requireNonNull(attacker);
        boolean strong = charge.charge(weapon) > STRONG_CHARGE;
        boolean critical =
                strong && attacker.falling() && !attacker.grounded() && !attacker.sprinting() && !attacker.inWater();
        double damage = weapon.damage() * charge.multiplier(weapon) * (critical ? CRITICAL_MULTIPLIER : 1);
        double sprintKnockback = strong && attacker.sprinting() ? SPRINT_KNOCKBACK : 0;
        return new Outcome((float) damage, KNOCKBACK, sprintKnockback, critical);
    }

    /**
     * Damage in health points; knockback pushes away from the attacker and sprint knockback along its facing, both in
     * blocks per second.
     */
    public record Outcome(float damage, double knockback, double sprintKnockback, boolean critical) {
        public Outcome {
            if (!Float.isFinite(damage)
                    || damage < 0
                    || !Double.isFinite(knockback)
                    || knockback < 0
                    || !Double.isFinite(sprintKnockback)
                    || sprintKnockback < 0) throw new IllegalArgumentException("Invalid attack outcome");
        }

        public boolean sprintHit() {
            return sprintKnockback > 0;
        }
    }
}
