package com.norbertfila.mob.ai;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.combat.DamageSource;
import com.norbertfila.world.Aabb;
import java.util.ArrayList;
import java.util.List;

/** A player-sized target that records the hits it takes. */
final class FakeTarget implements AttackTarget {
    record Hit(DamageSource source, float amount, double knockbackX, double knockbackZ) {}

    double x, y, z;
    boolean attackable = true;
    final List<Hit> hits = new ArrayList<>();

    FakeTarget(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public Aabb body() {
        return new Aabb(x - .3, y, z - .3, x + .3, y + 1.8, z + .3);
    }

    @Override
    public double eyeHeight() {
        return 1.62;
    }

    @Override
    public boolean attackable() {
        return attackable;
    }

    @Override
    public boolean hurt(DamageSource source, float amount, double knockbackX, double knockbackZ) {
        if (!attackable) return false;
        hits.add(new Hit(source, amount, knockbackX, knockbackZ));
        return true;
    }

    @Override
    public void knockback(double pushX, double pushZ) {}
}
