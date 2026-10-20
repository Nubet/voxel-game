package com.norbertfila.mob;

import com.norbertfila.world.VoxelCollision;
import com.norbertfila.world.World;

/** Gravity only: mobs have no movement of their own yet, but must not hang in the air when the ground goes. */
final class MobPhysics {
    static final double GRAVITY = 32;
    static final double MAX_FALL_SPEED = 78;
    private final VoxelCollision collision = new VoxelCollision();

    /** Returns false when the mob's surroundings are not loaded, leaving it untouched. */
    boolean step(World world, Mob mob, double dt) {
        var body = mob.body();
        if (!collision.available(world, body)) return false;
        double velocity = Math.max(-MAX_FALL_SPEED, mob.verticalVelocity - GRAVITY * dt);
        var moved = collision.move(world, body, 0, velocity * dt, 0);
        if (!moved.available()) return false;
        mob.y = moved.body().minY();
        mob.verticalVelocity = moved.blockedY() ? 0 : velocity;
        mob.onGround = moved.grounded();
        return true;
    }
}
