package com.norbertfila.player;

import com.norbertfila.world.BlockPos;
import com.norbertfila.world.World;
import java.util.Objects;

public final class PlayerPhysics {
    public static final double HALF_WIDTH = 0.3;
    public static final double HEIGHT = 1.8;
    public static final double GRAVITY = -25.0;
    public static final double JUMP_VELOCITY = 9.0;
    private static final double EPSILON = 1e-9;
    private double verticalVelocity;
    private boolean grounded;

    public Step step(
            World world,
            PlayerController.Position start,
            double moveX,
            double moveZ,
            boolean jump,
            double deltaSeconds) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(start);
        if (!Double.isFinite(moveX) || !Double.isFinite(moveZ) || !Double.isFinite(deltaSeconds) || deltaSeconds <= 0) {
            throw new IllegalArgumentException("Physics values must be finite and delta must be positive");
        }
        if (jump && grounded) {
            verticalVelocity = JUMP_VELOCITY;
            grounded = false;
        }
        verticalVelocity += GRAVITY * deltaSeconds;
        PlayerController.Position position = start;
        AxisResult x = moveAxis(world, position, moveX, Axis.X);
        position = x.position();
        AxisResult z = moveAxis(world, position, moveZ, Axis.Z);
        position = z.position();
        double verticalMove = verticalVelocity * deltaSeconds;
        AxisResult y = moveAxis(world, position, verticalMove, Axis.Y);
        position = y.position();
        if (y.collided()) {
            if (verticalVelocity < 0) grounded = true;
            verticalVelocity = 0;
        } else {
            grounded = false;
        }
        return new Step(position, grounded, verticalVelocity);
    }

    public boolean grounded() {
        return grounded;
    }

    public double verticalVelocity() {
        return verticalVelocity;
    }

    private AxisResult moveAxis(World world, PlayerController.Position start, double delta, Axis axis) {
        if (Math.abs(delta) <= EPSILON) return new AxisResult(start, false);
        int steps = Math.max(1, (int) Math.ceil(Math.abs(delta) / 0.25));
        PlayerController.Position position = start;
        for (int step = 1; step <= steps; step++) {
            double targetDelta = delta / steps;
            PlayerController.Position target = offset(position, targetDelta, axis);
            if (!collides(world, target)) {
                position = target;
                continue;
            }
            double low = 0;
            double high = 1;
            for (int iteration = 0; iteration < 24; iteration++) {
                double middle = (low + high) * 0.5;
                if (collides(world, offset(position, targetDelta * middle, axis))) high = middle;
                else low = middle;
            }
            return new AxisResult(offset(position, targetDelta * low, axis), true);
        }
        return new AxisResult(position, false);
    }

    private static boolean collides(World world, PlayerController.Position position) {
        int minX = floor(position.x() - HALF_WIDTH);
        int maxX = floor(position.x() + HALF_WIDTH - EPSILON);
        int minY = floor(position.y());
        int maxY = floor(position.y() + HEIGHT - EPSILON);
        int minZ = floor(position.z() - HALF_WIDTH);
        int maxZ = floor(position.z() + HALF_WIDTH - EPSILON);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.isSolid(new BlockPos(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    private static PlayerController.Position offset(PlayerController.Position position, double delta, Axis axis) {
        return switch (axis) {
            case X -> new PlayerController.Position(position.x() + delta, position.y(), position.z());
            case Y -> new PlayerController.Position(position.x(), position.y() + delta, position.z());
            case Z -> new PlayerController.Position(position.x(), position.y(), position.z() + delta);
        };
    }

    private static int floor(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Player position is outside block coordinate range");
        }
        return (int) Math.floor(value);
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    private record AxisResult(PlayerController.Position position, boolean collided) {}

    public record Step(PlayerController.Position position, boolean grounded, double verticalVelocity) {}
}
