package com.norbertfila.player;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.combat.Health;
import com.norbertfila.world.Aabb;
import java.util.Objects;

/** The local player as seen by attackers: only survival players take damage, as in Minecraft. */
public final class PlayerTarget implements AttackTarget {
    public static final float MAX_HEALTH = 20;
    private final PlayerController player;
    private final Health health;

    public PlayerTarget(PlayerController player, Health health) {
        this.player = Objects.requireNonNull(player);
        this.health = Objects.requireNonNull(health);
    }

    @Override
    public Aabb body() {
        return player.state().body();
    }

    @Override
    public double eyeHeight() {
        return player.state().eyeHeight();
    }

    @Override
    public boolean attackable() {
        return player.mode() == PlayerController.Mode.SURVIVAL && !player.modeChangePending() && !health.dead();
    }

    @Override
    public boolean hurt(DamageSource source, float amount, double knockbackX, double knockbackZ) {
        if (!attackable()) return false;
        var hit = health.damage(source, amount);
        if (hit == Health.Hit.FRESH && (knockbackX != 0 || knockbackZ != 0))
            player.knockback(knockbackX, KNOCKBACK_LIFT, knockbackZ);
        return hit.landed();
    }
}
