package com.norbertfila.player;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.VoxelCollision;
import com.norbertfila.world.World;
import java.util.List;
import java.util.Objects;

public final class PlayerPhysics {
    public static final double WALK_SPEED = 4.317;
    public static final double GRAVITY = -32.0;
    // Calibrated for a ~1.25-block apex with the production 1/60s semi-implicit step.
    public static final double JUMP_VELOCITY = 9.6;
    public static final double JUMP_COOLDOWN_SECONDS = 0.5;
    public static final double FLIGHT_SPEED = 10.92;
    private static final double SPRINT_MULTIPLIER = 1.3;
    private static final double SPRINT_JUMP_IMPULSE = 4;
    private static final double FLIGHT_DRAG = -Math.log(.6) / .05;
    private static final double GROUND_DRAG = -Math.log(.546) / .05;
    private static final double AIR_DRAG = -Math.log(.91) / .05;
    private static final double VERTICAL_DRAG = -Math.log(.98) / .05;
    private static final double AIR_ACCELERATION = 8.0;
    private static final double CROUCH_MULTIPLIER = .3;
    public static final double WATER_DRAG = 4;
    public static final double SWIM_SPEED = 2.5;
    public static final double SUBMERGED_GRAVITY = -2;
    public static final double SWIM_ACCELERATION = 16;
    public static final double CURRENT_ACCELERATION = 4;
    private static final double SHORE_EXIT_VELOCITY = 6;
    private final VoxelCollision collision = new VoxelCollision();

    public PlayerState step(
            World world, PlayerState start, double inputX, double inputZ, boolean jump, double deltaSeconds) {
        return step(world, start, inputX, inputZ, jump, false, 0, deltaSeconds);
    }

    public PlayerState step(
            World world,
            PlayerState start,
            double inputX,
            double inputZ,
            boolean jump,
            boolean sprint,
            double yawDegrees,
            double deltaSeconds) {
        return resolve(
                world,
                start,
                motion(start, inputX, inputZ, jump, sprint, yawDegrees, deltaSeconds),
                deltaSeconds,
                true);
    }

    public PlayerState fly(
            World world,
            PlayerState start,
            double inputX,
            double inputY,
            double inputZ,
            boolean sprint,
            double deltaSeconds) {
        return resolve(
                world, start, flightMotion(start, inputX, inputY, inputZ, sprint, deltaSeconds), deltaSeconds, false);
    }

    public PlayerState step(
            World world,
            PlayerState start,
            double inputX,
            double inputZ,
            boolean jump,
            boolean sneak,
            boolean sprint,
            double yawDegrees,
            double dt,
            WaterContact.Result water) {
        Objects.requireNonNull(water);
        if (!water.available()) return start;
        return resolve(
                world,
                start,
                waterMotion(
                        start,
                        inputX,
                        inputZ,
                        jump,
                        sneak,
                        sprint,
                        yawDegrees,
                        dt,
                        water,
                        shoreExitAvailable(world, start, inputX, inputZ, jump, sneak, water)),
                dt,
                water.submergedFraction() < .5);
    }

    public List<ChunkPos> requiredChunks(
            World world,
            PlayerState start,
            double inputX,
            double inputZ,
            boolean jump,
            boolean sneak,
            boolean sprint,
            double yawDegrees,
            double dt,
            WaterContact.Result water) {
        return requiredChunks(
                start,
                waterMotion(
                        start,
                        inputX,
                        inputZ,
                        jump,
                        sneak,
                        sprint,
                        yawDegrees,
                        dt,
                        water,
                        shoreExitAvailable(world, start, inputX, inputZ, jump, sneak, water)),
                dt);
    }

    private boolean shoreExitAvailable(
            World world,
            PlayerState start,
            double inputX,
            double inputZ,
            boolean jump,
            boolean sneak,
            WaterContact.Result water) {
        if (!jump || sneak || water.submergedFraction() == 0 || !water.available()) return false;
        double length = Math.hypot(inputX, inputZ);
        if (length == 0) return false;
        double scale = .2 / Math.max(1, length);
        Aabb ahead = start.body().move(inputX * scale, 0, inputZ * scale);
        // Only a clear low lip gets the classic water exit lift; a tall wall or roof must still block it.
        return collision.available(world, ahead)
                && !collision.spaceFree(world, ahead)
                && collision.spaceFree(world, ahead.move(0, .6, 0))
                && collision.spaceFree(world, start.body().swept(0, .6, 0));
    }

    private static Motion waterMotion(
            PlayerState start,
            double inputX,
            double inputZ,
            boolean jump,
            boolean sneak,
            boolean sprint,
            double yawDegrees,
            double dt,
            WaterContact.Result water,
            boolean shoreExit) {
        Motion dry = motion(start, inputX, inputZ, jump, sprint, yawDegrees, dt);
        double f = water.submergedFraction();
        if (f == 0) return dry;
        double length = Math.hypot(inputX, inputZ);
        if (length > 1) {
            inputX /= length;
            inputZ /= length;
        }
        double dryDrag = start.grounded() ? GROUND_DRAG : AIR_DRAG;
        double drag = dryDrag * (1 - f) + WATER_DRAG * f;
        double acceleration = (start.grounded() ? WALK_SPEED * dryDrag : AIR_ACCELERATION)
                        * (start.crouching() ? CROUCH_MULTIPLIER : sprint ? SPRINT_MULTIPLIER : 1)
                        * (1 - f)
                + SWIM_SPEED * WATER_DRAG * f;
        double decay = Math.exp(-drag * dt), response = -Math.expm1(-drag * dt) / drag;
        double cooldown = Math.max(0, start.jumpCooldownSeconds() - dt);
        double x = start.velocityX() * decay
                + (inputX * acceleration + water.currentX() * CURRENT_ACCELERATION * f) * response;
        double z = start.velocityZ() * decay
                + (inputZ * acceleration + water.currentZ() * CURRENT_ACCELERATION * f) * response;
        // Volume-weighting vertical control traps an upright body at the surface before its feet can clear a bank.
        double verticalAcceleration = SUBMERGED_GRAVITY
                + ((jump ? 1 : 0) - (sneak ? 1 : 0)) * SWIM_ACCELERATION
                + water.currentY() * CURRENT_ACCELERATION * f;
        double y = start.velocityY() * Math.exp(-WATER_DRAG * dt)
                + verticalAcceleration * (-Math.expm1(-WATER_DRAG * dt) / WATER_DRAG);
        if (shoreExit) y = Math.max(y, SHORE_EXIT_VELOCITY);
        return new Motion(x, y, z, cooldown);
    }

    private PlayerState resolve(World world, PlayerState start, Motion motion, double deltaSeconds, boolean walking) {
        Objects.requireNonNull(world);
        double moveX = motion.x * deltaSeconds, moveZ = motion.z * deltaSeconds;
        VoxelCollision.Result moved = collision.move(
                world,
                start.body(),
                moveX,
                motion.y * deltaSeconds,
                moveZ,
                walking && start.crouching() && start.grounded() && motion.y <= 0,
                walking && motion.y <= 0);
        if (!moved.available()) return start;
        Aabb box = moved.body();
        double x = moved.blockedX()
                ? contactCenter(box.minX(), box.maxX(), moveX)
                : start.position().x() + moved.x();
        double z = moved.blockedZ()
                ? contactCenter(box.minZ(), box.maxZ(), moveZ)
                : start.position().z() + moved.z();
        double y = moved.blockedY() && motion.y > 0 ? belowFace(box.maxY(), start.height()) : box.minY();
        return new PlayerState(
                new PlayerState.Position(x, y, z),
                moved.blockedX() ? 0 : motion.x,
                moved.blockedY() ? 0 : motion.y,
                moved.blockedZ() ? 0 : motion.z,
                moved.grounded(),
                motion.jumpCooldownSeconds,
                start.crouching());
    }

    private static double contactCenter(double min, double max, double requested) {
        // Round outward if reconstructing the hitbox would cross the contacted face by one ULP.
        if (requested > 0) return belowFace(max, PlayerState.HALF_WIDTH);
        double center = min + PlayerState.HALF_WIDTH;
        return center - PlayerState.HALF_WIDTH < min ? Math.nextUp(center) : center;
    }

    private static double belowFace(double face, double offset) {
        double position = face - offset;
        return position + offset > face ? Math.nextDown(position) : position;
    }

    public List<ChunkPos> requiredChunks(
            PlayerState start, double inputX, double inputZ, boolean jump, double deltaSeconds) {
        return requiredChunks(start, inputX, inputZ, jump, false, 0, deltaSeconds);
    }

    public List<ChunkPos> requiredChunks(
            PlayerState start,
            double inputX,
            double inputZ,
            boolean jump,
            boolean sprint,
            double yawDegrees,
            double deltaSeconds) {
        return requiredChunks(
                start, motion(start, inputX, inputZ, jump, sprint, yawDegrees, deltaSeconds), deltaSeconds);
    }

    public List<ChunkPos> requiredFlightChunks(
            PlayerState start, double inputX, double inputY, double inputZ, boolean sprint, double deltaSeconds) {
        return requiredChunks(start, flightMotion(start, inputX, inputY, inputZ, sprint, deltaSeconds), deltaSeconds);
    }

    private static List<ChunkPos> requiredChunks(PlayerState start, Motion motion, double deltaSeconds) {
        return VoxelCollision.requiredChunks(VoxelCollision.requiredRegion(
                start.body(), motion.x * deltaSeconds, motion.y * deltaSeconds, motion.z * deltaSeconds));
    }

    private static Motion motion(
            PlayerState start,
            double inputX,
            double inputZ,
            boolean jump,
            boolean sprint,
            double yawDegrees,
            double deltaSeconds) {
        Objects.requireNonNull(start);
        if (!Double.isFinite(inputX)
                || !Double.isFinite(inputZ)
                || !Double.isFinite(yawDegrees)
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
        double acceleration = (start.grounded() ? WALK_SPEED * drag : AIR_ACCELERATION)
                * (start.crouching() ? CROUCH_MULTIPLIER : sprint ? SPRINT_MULTIPLIER : 1);
        double decay = Math.exp(-drag * deltaSeconds);
        double response = -Math.expm1(-drag * deltaSeconds) / drag;
        double cooldown = Math.max(0, start.jumpCooldownSeconds() - deltaSeconds);
        boolean jumping = jump && start.grounded() && cooldown <= 1e-9;
        double impulse = jumping && sprint && !start.crouching() ? SPRINT_JUMP_IMPULSE : 0;
        double yaw = Math.toRadians(yawDegrees);
        double x = (start.velocityX() + Math.cos(yaw) * impulse) * decay + inputX * acceleration * response;
        double z = (start.velocityZ() + Math.sin(yaw) * impulse) * decay + inputZ * acceleration * response;
        double y = jumping ? JUMP_VELOCITY : start.velocityY();
        // Gravity before sweeping lets a stationary player detect downward floor contact every tick.
        y = y * Math.exp(-VERTICAL_DRAG * deltaSeconds)
                + GRAVITY * (-Math.expm1(-VERTICAL_DRAG * deltaSeconds) / VERTICAL_DRAG);
        return new Motion(x, y, z, jumping ? JUMP_COOLDOWN_SECONDS : cooldown);
    }

    private static Motion flightMotion(
            PlayerState start, double inputX, double inputY, double inputZ, boolean sprint, double dt) {
        Objects.requireNonNull(start);
        if (!Double.isFinite(inputX)
                || !Double.isFinite(inputY)
                || !Double.isFinite(inputZ)
                || !Double.isFinite(dt)
                || dt <= 0) {
            throw new IllegalArgumentException("Flight values must be finite and delta must be positive");
        }
        double length = Math.hypot(Math.hypot(inputX, inputZ), inputY);
        if (!Double.isFinite(length)) throw new IllegalArgumentException("Flight direction exceeds finite range");
        double scale = FLIGHT_SPEED * (sprint ? 2 : 1) / Math.max(1, length);
        double decay = Math.exp(-FLIGHT_DRAG * dt);
        double response = -Math.expm1(-FLIGHT_DRAG * dt);
        return new Motion(
                start.velocityX() * decay + inputX * scale * response,
                start.velocityY() * decay + inputY * scale * response,
                start.velocityZ() * decay + inputZ * scale * response,
                0);
    }

    private record Motion(double x, double y, double z, double jumpCooldownSeconds) {}

    public static Aabb body(PlayerState.Position feet) {
        return PlayerState.at(feet).body();
    }
}
