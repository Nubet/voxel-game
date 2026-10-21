package com.norbertfila.mob.ai;

import com.norbertfila.mob.ai.goal.RangedBowAttackGoal;

/** A skeleton's shot: Minecraft's arrow speed, an arc that compensates for drop, and normal-difficulty spread. */
final class SkeletonBow implements RangedBowAttackGoal.Bow {
    // 1.6 blocks per tick at 20 ticks per second.
    static final double ARROW_SPEED = 32;
    // Minecraft raises the aim by a fifth of the horizontal distance to make up for gravity.
    static final double ARC = .2;
    // Minecraft spreads skeleton arrows by 0.0075 times (14 - 4 * difficulty) with a gaussian on normal difficulty.
    static final double SPREAD = .0075 * 6;

    @Override
    public void shoot(MobContext context, double targetX, double targetY, double targetZ) {
        var mob = context.mob();
        double x = mob.x(), y = mob.eyeY() - .1, z = mob.z();
        double dx = targetX - x, dz = targetZ - z, horizontal = Math.hypot(dx, dz);
        double dy = targetY - y + horizontal * ARC;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-6) return;
        var random = context.random();
        double vx = dx / length + random.nextGaussian() * SPREAD;
        double vy = dy / length + random.nextGaussian() * SPREAD;
        double vz = dz / length + random.nextGaussian() * SPREAD;
        double scale = ARROW_SPEED / Math.sqrt(vx * vx + vy * vy + vz * vz);
        context.world().shootArrow(mob, x, y, z, vx * scale, vy * scale, vz * scale);
    }
}
