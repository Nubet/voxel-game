package com.norbertfila.player;

import com.norbertfila.player.PlayerState.Position;
import com.norbertfila.world.World;
import java.util.Objects;

public final class PlayerController {
    private static final double WALK_SPEED = 12.0;
    private static final double SPRINT_SPEED = 45.0;
    private Mode mode = Mode.SPECTATOR;
    private Position previous = new Position(0, 22, 34);
    private PlayerState current = PlayerState.at(previous);
    private final PlayerPhysics physics = new PlayerPhysics();

    public void setMode(Mode mode) {
        Mode next = Objects.requireNonNull(mode);
        if (this.mode != next) {
            current = PlayerState.at(current.position());
            previous = current.position();
        }
        this.mode = next;
    }

    public Mode mode() {
        return mode;
    }

    public void setPosition(Position position) {
        current = PlayerState.at(position);
        previous = current.position();
    }

    public PlayerState state() {
        return current;
    }

    public void tick(PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        tick(null, input, fixedDeltaSeconds, yawDegrees, pitchDegrees);
    }

    public void tick(World world, PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        Objects.requireNonNull(input);
        if (!Double.isFinite(fixedDeltaSeconds) || fixedDeltaSeconds <= 0) {
            throw new IllegalArgumentException("Fixed delta must be positive and finite");
        }
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("Player orientation must be finite");
        }
        if (mode == Mode.SURVIVAL) Objects.requireNonNull(world, "Survival requires a world");
        previous = current.position();
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(mode == Mode.SPECTATOR ? pitchDegrees : 0);
        double forwardX = Math.cos(yaw) * Math.cos(pitch);
        double forwardY = Math.sin(pitch);
        double forwardZ = Math.sin(yaw) * Math.cos(pitch);
        double rightX = -Math.sin(yaw);
        double rightZ = Math.cos(yaw);
        double speed = (input.sprintHeld() ? SPRINT_SPEED : WALK_SPEED) * fixedDeltaSeconds;
        double x = previous.x();
        double y = previous.y();
        double z = previous.z();
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
            if (input.jumpHeld()) y += speed;
            if (input.sneakHeld()) y -= speed;
            current = new PlayerState(
                    new Position(x, y, z),
                    (x - previous.x()) / fixedDeltaSeconds,
                    (y - previous.y()) / fixedDeltaSeconds,
                    (z - previous.z()) / fixedDeltaSeconds,
                    false);
            return;
        }
        current = physics.step(
                world, current, x - previous.x(), z - previous.z(), input.jumpPressed(), fixedDeltaSeconds);
    }

    public Position renderPosition(double alpha) {
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("Interpolation alpha must be in [0, 1]");
        }
        return new Position(
                previous.x() + (current.position().x() - previous.x()) * alpha,
                previous.y() + (current.position().y() - previous.y()) * alpha,
                previous.z() + (current.position().z() - previous.z()) * alpha);
    }

    public Position renderEyePosition(double alpha) {
        Position feet = renderPosition(alpha);
        return new Position(feet.x(), feet.y() + PlayerState.EYE_HEIGHT, feet.z());
    }

    public enum Mode {
        SPECTATOR,
        SURVIVAL
    }
}
