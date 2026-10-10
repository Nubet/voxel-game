package com.norbertfila.player;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.List;
import java.util.Objects;

public final class PlayerPhysics {
    public static final double HALF_WIDTH = 0.3;
    public static final double HEIGHT = 1.8;
    public static final double WALK_SPEED = 4.317;
    public static final double GRAVITY = -32.0;
    // Calibrated for a ~1.25-block apex with the production 1/60s semi-implicit step.
    public static final double JUMP_VELOCITY = 9.6;
    public static final double JUMP_COOLDOWN_SECONDS = 0.5;
    private static final double GROUND_DRAG = -Math.log(.546) / .05;
    private static final double AIR_DRAG = -Math.log(.91) / .05;
    private static final double VERTICAL_DRAG = -Math.log(.98) / .05;
    private static final double AIR_ACCELERATION = 8.0;
    private final VoxelCollision collision = new VoxelCollision();

    public PlayerState step(
            World world, PlayerState start, double inputX, double inputZ, boolean jump, double deltaSeconds) {
        Objects.requireNonNull(world);
        Motion motion = motion(start, inputX, inputZ, jump, deltaSeconds);
        double moveX = motion.x * deltaSeconds, moveZ = motion.z * deltaSeconds;
        VoxelCollision.Result moved =
                collision.move(world, body(start.position()), moveX, motion.y * deltaSeconds, moveZ);
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
                moved.blockedX() ? 0 : motion.x,
                moved.blockedY() ? 0 : motion.y,
                moved.blockedZ() ? 0 : motion.z,
                moved.grounded(),
                motion.jumpCooldownSeconds);
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
            PlayerState start, double inputX, double inputZ, boolean jump, double deltaSeconds) {
        Motion motion = motion(start, inputX, inputZ, jump, deltaSeconds);
        return VoxelCollision.requiredChunks(VoxelCollision.requiredRegion(
                body(start.position()), motion.x * deltaSeconds, motion.y * deltaSeconds, motion.z * deltaSeconds));
    }

    private static Motion motion(PlayerState start, double inputX, double inputZ, boolean jump, double deltaSeconds) {
        Objects.requireNonNull(start);
        if (!Double.isFinite(inputX)
                || !Double.isFinite(inputZ)
                || !Double.isFinite(deltaSeconds)
                || deltaSeconds <= 0) {
            throw new IllegalArgumentException("Physics values must be finite and delta must be positive");
        }
        double length = Math.hypot(inputX, inputZ);
        if (!Double.isFinite(length)) throw new IllegalArgumentException("Input direction exceeds finite range");
        if (length > 1) {
            inputX /= length;
            inputZ /= length;
        }
        double drag = start.grounded() ? GROUND_DRAG : AIR_DRAG;
        double acceleration = start.grounded() ? WALK_SPEED * drag : AIR_ACCELERATION;
        double decay = Math.exp(-drag * deltaSeconds);
        double response = -Math.expm1(-drag * deltaSeconds) / drag;
        double x = start.velocityX() * decay + inputX * acceleration * response;
        double z = start.velocityZ() * decay + inputZ * acceleration * response;
        double cooldown = Math.max(0, start.jumpCooldownSeconds() - deltaSeconds);
        boolean jumping = jump && start.grounded() && cooldown <= 1e-9;
        double y = jumping ? JUMP_VELOCITY : start.velocityY();
        // Gravity before sweeping lets a stationary player detect downward floor contact every tick.
        y = y * Math.exp(-VERTICAL_DRAG * deltaSeconds)
                + GRAVITY * (-Math.expm1(-VERTICAL_DRAG * deltaSeconds) / VERTICAL_DRAG);
        return new Motion(x, y, z, jumping ? JUMP_COOLDOWN_SECONDS : cooldown);
    }

    private record Motion(double x, double y, double z, double jumpCooldownSeconds) {}

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
