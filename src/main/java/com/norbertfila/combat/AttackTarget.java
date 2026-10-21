package com.norbertfila.combat;

import com.norbertfila.world.Aabb;

/** Something mobs can attack, such as a player. */
public interface AttackTarget {
    Aabb body();

    double eyeHeight();

    /** False for targets that cannot be hurt right now, such as creative players or the dead. */
    boolean attackable();

    /** Applies damage with a horizontal push in blocks per second; returns false when the hit was ignored. */
    boolean hurt(DamageSource source, float amount, double knockbackX, double knockbackZ);
}
