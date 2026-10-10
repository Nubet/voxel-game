package com.norbertfila.player;

import com.norbertfila.world.World;
import java.util.Objects;

public final class PlayerController {
    private static final double WALK_SPEED = 12.0;
    private static final double SPRINT_SPEED = 45.0;
    private Mode mode = Mode.SPECTATOR;
    private Position previous = new Position(0, 22, 34);
    private Position current = previous;
    private final PlayerPhysics physics = new PlayerPhysics();

    public void setMode(Mode mode) {
        Mode next = Objects.requireNonNull(mode);
        if (this.mode != next) physics.resetMotion();
        this.mode = next;
    }

    public Mode mode() {
        return mode;
    }

    public void setPosition(Position position) {
        current = Objects.requireNonNull(position);
        previous = current;
        physics.resetMotion();
    }

    public void tick(InputState input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        tick(null, input, fixedDeltaSeconds, yawDegrees, pitchDegrees);
    }

    public void tick(World world, InputState input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        Objects.requireNonNull(input);
        if (!Double.isFinite(fixedDeltaSeconds) || fixedDeltaSeconds <= 0) {
            throw new IllegalArgumentException("Fixed delta must be positive and finite");
        }
        previous = current;
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(mode == Mode.SPECTATOR ? pitchDegrees : 0);
        double forwardX = Math.cos(yaw) * Math.cos(pitch);
        double forwardY = Math.sin(pitch);
        double forwardZ = Math.sin(yaw) * Math.cos(pitch);
        double rightX = -Math.sin(yaw);
        double rightZ = Math.cos(yaw);
        double speed = (input.sprint() ? SPRINT_SPEED : WALK_SPEED) * fixedDeltaSeconds;
        double x = current.x();
        double y = current.y();
        double z = current.z();
        if (input.forward()) {
            x += forwardX * speed;
            y += forwardY * speed;
            z += forwardZ * speed;
        }
        if (input.backward()) {
            x -= forwardX * speed;
            y -= forwardY * speed;
            z -= forwardZ * speed;
        }
        if (input.right()) {
            x += rightX * speed;
            z += rightZ * speed;
        }
        if (input.left()) {
            x -= rightX * speed;
            z -= rightZ * speed;
        }
        if (mode == Mode.SPECTATOR) {
            if (input.up()) y += speed;
            if (input.down()) y -= speed;
            current = new Position(x, y, z);
            return;
        }
        if (world == null) {
            current = new Position(x, y, z);
            return;
        }
        double horizontalX = x - current.x();
        double horizontalZ = z - current.z();
        current = physics.step(world, current, horizontalX, horizontalZ, input.up(), fixedDeltaSeconds)
                .position();
    }

    public Position renderPosition(double alpha) {
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("Interpolation alpha must be in [0, 1]");
        }
        return new Position(
                previous.x() + (current.x() - previous.x()) * alpha,
                previous.y() + (current.y() - previous.y()) * alpha,
                previous.z() + (current.z() - previous.z()) * alpha);
    }

    public enum Mode {
        SPECTATOR,
        SURVIVAL
    }

    public record InputState(
            boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down, boolean sprint) {}

    public record Position(double x, double y, double z) {}
}
