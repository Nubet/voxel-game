package com.norbertfila.player;

import com.norbertfila.combat.AttackCharge;
import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.Attacker;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.combat.MeleeAttack;
import com.norbertfila.combat.MeleeWeapon;
import com.norbertfila.world.Aabb;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class PlayerCombat {
    private final AttackCharge charge = new AttackCharge();
    private byte heldItem;

    public PlayerCombat(byte heldItem) {
        this.heldItem = heldItem;
    }

    /** Switching items restarts the recharge, as in Minecraft. */
    public void tick(double dt, byte heldItem) {
        if (heldItem != this.heldItem) {
            this.heldItem = heldItem;
            charge.reset();
        }
        charge.tick(dt);
    }

    public double charge() {
        return charge.charge(MeleeWeapon.of(heldItem));
    }

    /** A swing at nothing still spends the charge. */
    public void miss() {
        charge.reset();
    }

    /** Creative flight never counts as falling, so it cannot land critical hits. */
    public static Attacker attacker(PlayerController player, double yawDegrees, boolean inWater) {
        var state = player.state();
        return new Attacker(
                state.position().x(),
                state.position().y(),
                state.position().z(),
                yawDegrees,
                state.grounded(),
                !state.grounded() && !player.flying() && state.velocityY() < 0,
                player.sprinting(),
                inWater,
                Math.hypot(state.velocityX(), state.velocityZ()));
    }

    /**
     * Empty when the target ignored the hit. Sprint knockback is applied separately, as in Minecraft, so that a hit
     * which only tops up damage during the hurt cooldown still pushes.
     */
    public Optional<MeleeAttack.Outcome> attack(
            AttackTarget target,
            Attacker attacker,
            Function<Aabb, ? extends Collection<? extends AttackTarget>> nearby) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(attacker);
        Objects.requireNonNull(nearby);
        var outcome = MeleeAttack.resolve(MeleeWeapon.of(heldItem), charge, attacker);
        charge.reset();
        double dx = target.x() - attacker.x(), dz = target.z() - attacker.z();
        double length = Math.hypot(dx, dz);
        double pushX = length > 1e-9 ? dx / length * outcome.knockback() : 0;
        double pushZ = length > 1e-9 ? dz / length * outcome.knockback() : 0;
        if (!target.hurt(DamageSource.PLAYER_ATTACK, outcome.damage(), pushX, pushZ)) return Optional.empty();
        if (outcome.sprintHit())
            target.knockback(
                    attacker.facingX() * outcome.sprintKnockback(), attacker.facingZ() * outcome.sprintKnockback());
        if (outcome.sweep()) sweep(target, attacker, nearby);
        return Optional.of(outcome);
    }

    private static void sweep(
            AttackTarget target,
            Attacker attacker,
            Function<Aabb, ? extends Collection<? extends AttackTarget>> nearby) {
        var body = target.body();
        var area = new Aabb(
                body.minX() - MeleeAttack.SWEEP_HORIZONTAL,
                body.minY() - MeleeAttack.SWEEP_VERTICAL,
                body.minZ() - MeleeAttack.SWEEP_HORIZONTAL,
                body.maxX() + MeleeAttack.SWEEP_HORIZONTAL,
                body.maxY() + MeleeAttack.SWEEP_VERTICAL,
                body.maxZ() + MeleeAttack.SWEEP_HORIZONTAL);
        for (var other : nearby.apply(area)) {
            double dx = other.x() - attacker.x(), dy = other.y() - attacker.y(), dz = other.z() - attacker.z();
            if (other == target
                    || !other.attackable()
                    || dx * dx + dy * dy + dz * dz >= MeleeAttack.SWEEP_REACH * MeleeAttack.SWEEP_REACH) continue;
            other.knockback(attacker.facingX() * MeleeAttack.KNOCKBACK, attacker.facingZ() * MeleeAttack.KNOCKBACK);
            other.hurt(DamageSource.PLAYER_ATTACK, MeleeAttack.SWEEP_DAMAGE, 0, 0);
        }
    }
}
