package com.norbertfila.player;

import com.norbertfila.combat.AttackCharge;
import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.Attacker;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.combat.MeleeAttack;
import com.norbertfila.combat.MeleeWeapon;
import java.util.Objects;

/** The local player's melee swings: the weapon in hand, its recharge and the hits it lands. */
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
    public static Attacker attacker(PlayerController player, boolean inWater) {
        var state = player.state();
        return new Attacker(
                state.position().x(),
                state.position().z(),
                state.grounded(),
                !state.grounded() && !player.flying() && state.velocityY() < 0,
                player.sprinting(),
                inWater);
    }

    public boolean attack(AttackTarget target, Attacker attacker) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(attacker);
        var outcome = MeleeAttack.resolve(MeleeWeapon.of(heldItem), charge, attacker);
        charge.reset();
        double dx = target.x() - attacker.x(), dz = target.z() - attacker.z();
        double length = Math.hypot(dx, dz);
        double pushX = length < 1e-9 ? 0 : dx / length * outcome.knockback();
        double pushZ = length < 1e-9 ? 0 : dz / length * outcome.knockback();
        return target.hurt(DamageSource.PLAYER_ATTACK, outcome.damage(), pushX, pushZ);
    }
}
