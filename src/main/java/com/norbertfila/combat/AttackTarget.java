package com.norbertfila.combat;

import com.norbertfila.world.Aabb;

public interface AttackTarget {
    /** Minecraft knockback lifts the target by 0.4 blocks per tick at 20 ticks per second. */
    double KNOCKBACK_LIFT = 8;

    Aabb body();

    double eyeHeight();

    default double x() {
        var body = body();
        return (body.minX() + body.maxX()) * .5;
    }

    default double y() {
        return body().minY();
    }

    default double z() {
        var body = body();
        return (body.minZ() + body.maxZ()) * .5;
    }

    default double eyeY() {
        return y() + eyeHeight();
    }

    /** False for targets that cannot be hurt right now, such as creative players or the dead. */
    boolean attackable();

    /**
     * Applies damage with a horizontal push in blocks per second; returns false when the hit was ignored. The push is
     * skipped when the hit only tops up an earlier one during the hurt cooldown.
     */
    boolean hurt(DamageSource source, float amount, double knockbackX, double knockbackZ);

    /** A push in blocks per second that lands regardless of the hurt cooldown, such as sprint or sweep knockback. */
    void knockback(double pushX, double pushZ);
}
