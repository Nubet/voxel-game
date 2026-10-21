package com.norbertfila.mob;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.VoxelCollision;
import com.norbertfila.world.World;

/** Turns a mob's walk, jump and look intents into motion with gravity, swimming and block collision. */
final class MobPhysics {
    static final double GRAVITY = 32;
    static final double MAX_FALL_SPEED = 78;
    // Minecraft jumps at 0.42 blocks per tick, which clears one block with a little room to spare.
    static final double JUMP_SPEED = 8.4;
    static final double GROUND_ACCELERATION = 25;
    static final double AIR_ACCELERATION = 4;
    static final double WATER_SPEED_FACTOR = .5;
    // Minecraft mobs keep jumping in water, which makes them bob up to the surface.
    static final double SWIM_UP_SPEED = 2.4;
    static final double SWIM_UP_ACCELERATION = 12;
    private final VoxelCollision collision = new VoxelCollision();

    /** Returns false when the mob's surroundings are not loaded, leaving it untouched. */
    boolean step(World world, Mob mob, double dt) {
        var body = mob.body();
        if (!collision.available(world, body)) return false;
        mob.turn();
        mob.inWater = touchesWater(world, body);
        double speed = mob.moveSpeed * (mob.inWater ? WATER_SPEED_FACTOR : 1);
        double response = Math.min(1, (mob.onGround || mob.inWater ? GROUND_ACCELERATION : AIR_ACCELERATION) * dt);
        mob.velocityX += (mob.moveX * speed - mob.velocityX) * response;
        mob.velocityZ += (mob.moveZ * speed - mob.velocityZ) * response;
        if (mob.inWater) mob.velocityY = Math.min(SWIM_UP_SPEED, mob.velocityY + SWIM_UP_ACCELERATION * dt);
        else mob.velocityY = Math.max(-MAX_FALL_SPEED, mob.velocityY - GRAVITY * dt);
        if (mob.jumpRequested && mob.onGround && !mob.inWater) mob.velocityY = JUMP_SPEED;
        mob.jumpRequested = false;
        // Grounded mobs step up low blocks such as snow layers, like Minecraft's 0.6 block step height.
        var moved = collision.move(
                world, body, mob.velocityX * dt, mob.velocityY * dt, mob.velocityZ * dt, false, mob.onGround);
        if (!moved.available()) return false;
        var result = moved.body();
        double newX = (result.minX() + result.maxX()) * .5, newZ = (result.minZ() + result.maxZ()) * .5;
        mob.walkDistance += Math.hypot(newX - mob.x, newZ - mob.z);
        mob.x = newX;
        mob.y = result.minY();
        mob.z = newZ;
        if (moved.blockedX()) mob.velocityX = 0;
        if (moved.blockedZ()) mob.velocityZ = 0;
        if (moved.blockedY()) mob.velocityY = 0;
        mob.horizontalCollision = moved.blockedX() || moved.blockedZ();
        mob.onGround = moved.grounded();
        return true;
    }

    static boolean touchesWater(World world, Aabb body) {
        int y = (int) Math.floor(body.minY() + .4);
        for (int x = (int) Math.floor(body.minX()); x < Math.ceil(body.maxX()); x++)
            for (int z = (int) Math.floor(body.minZ()); z < Math.ceil(body.maxZ()); z++)
                if (world.getBlock(new BlockPos(x, y, z)).orElse(BlockId.AIR) == BlockId.WATER) return true;
        return false;
    }
}
