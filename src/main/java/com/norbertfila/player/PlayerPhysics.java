package com.norbertfila.player;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.List;
import java.util.Objects;

public final class PlayerPhysics {
    public static final double HALF_WIDTH = 0.3;
    public static final double HEIGHT = 1.8;
    public static final double GRAVITY = -25.0;
    public static final double JUMP_VELOCITY = 9.0;
    private final VoxelCollision collision = new VoxelCollision();

    public PlayerState step(
            World world, PlayerState start, double moveX, double moveZ, boolean jump, double deltaSeconds) {
        Objects.requireNonNull(world);
        double velocityY = verticalVelocity(start, moveX, moveZ, jump, deltaSeconds);
        VoxelCollision.Result moved =
                collision.move(world, body(start.position()), moveX, velocityY * deltaSeconds, moveZ);
        if (!moved.available()) return start;
        Aabb box = moved.body();
        double x = moved.blockedX()
                ? contactCenter(box.minX(), box.maxX(), moveX)
                : start.position().x() + moved.x();
        double z = moved.blockedZ()
                ? contactCenter(box.minZ(), box.maxZ(), moveZ)
                : start.position().z() + moved.z();
        return new PlayerState(
                new PlayerState.Position(x, box.minY(), z),
                moved.blockedX() ? 0 : moveX / deltaSeconds,
                moved.blockedY() ? 0 : velocityY,
                moved.blockedZ() ? 0 : moveZ / deltaSeconds,
                moved.grounded());
    }

    private static double contactCenter(double min, double max, double requested) {
        // Round outward if reconstructing the hitbox would cross the contacted face by one ULP.
        if (requested > 0) {
            double center = max - HALF_WIDTH;
            return center + HALF_WIDTH > max ? Math.nextDown(center) : center;
        }
        double center = min + HALF_WIDTH;
        return center - HALF_WIDTH < min ? Math.nextUp(center) : center;
    }

    public List<ChunkPos> requiredChunks(
            PlayerState start, double moveX, double moveZ, boolean jump, double deltaSeconds) {
        double velocityY = verticalVelocity(start, moveX, moveZ, jump, deltaSeconds);
        return VoxelCollision.requiredChunks(
                VoxelCollision.requiredRegion(body(start.position()), moveX, velocityY * deltaSeconds, moveZ));
    }

    private static double verticalVelocity(
            PlayerState start, double moveX, double moveZ, boolean jump, double deltaSeconds) {
        Objects.requireNonNull(start);
        if (!Double.isFinite(moveX) || !Double.isFinite(moveZ) || !Double.isFinite(deltaSeconds) || deltaSeconds <= 0) {
            throw new IllegalArgumentException("Physics values must be finite and delta must be positive");
        }
        return (jump && start.grounded() ? JUMP_VELOCITY : start.velocityY()) + GRAVITY * deltaSeconds;
    }

    public static Aabb body(PlayerState.Position feet) {
        return new Aabb(
                feet.x() - HALF_WIDTH,
                feet.y(),
                feet.z() - HALF_WIDTH,
                feet.x() + HALF_WIDTH,
                feet.y() + HEIGHT,
                feet.z() + HALF_WIDTH);
    }
}
