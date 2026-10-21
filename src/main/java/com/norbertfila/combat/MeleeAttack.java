package com.norbertfila.combat;

import java.util.Objects;

/** Minecraft Java 1.9+ melee rules. */
public final class MeleeAttack {
    /** Minecraft pushes a struck body by 0.4 blocks per 20 Hz tick. */
    public static final double KNOCKBACK = 8;
    /** A sprinting hit adds 0.5 blocks per 20 Hz tick along the attacker's facing. */
    public static final double SPRINT_KNOCKBACK = 10;

    public static final float SWEEP_DAMAGE = 1;
    public static final double SWEEP_HORIZONTAL = 1, SWEEP_VERTICAL = .25;
    public static final double SWEEP_REACH = 3;

    static final double CRITICAL_MULTIPLIER = 1.5;
    static final double STRONG_CHARGE = .9;
    /** Minecraft only sweeps when the attacker moved less than 0.1 blocks in the last 20 Hz tick. */
    static final double SWEEP_SPEED_LIMIT = 2;

    private MeleeAttack() {}

    public static Outcome resolve(MeleeWeapon weapon, AttackCharge charge, Attacker attacker) {
        Objects.requireNonNull(weapon);
        Objects.requireNonNull(charge);
        Objects.requireNonNull(attacker);
        boolean strong = charge.charge(weapon) > STRONG_CHARGE;
        boolean sprintHit = strong && attacker.sprinting();
        boolean critical =
                strong && attacker.falling() && !attacker.grounded() && !attacker.sprinting() && !attacker.inWater();
        boolean sweep = strong
                && weapon.sweeps()
                && !critical
                && !sprintHit
                && attacker.grounded()
                && attacker.speed() < SWEEP_SPEED_LIMIT;
        double damage = weapon.damage() * charge.multiplier(weapon) * (critical ? CRITICAL_MULTIPLIER : 1);
        return new Outcome((float) damage, KNOCKBACK, sprintHit ? SPRINT_KNOCKBACK : 0, critical, sweep);
    }

    /** Knockback pushes away from the attacker and sprint knockback along its facing, in blocks per second. */
    public record Outcome(float damage, double knockback, double sprintKnockback, boolean critical, boolean sweep) {
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
