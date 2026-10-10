package com.norbertfila.player;

import com.norbertfila.player.PlayerState.Position;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.List;
import java.util.Objects;

public final class PlayerController {
    private static final double SPECTATOR_WALK_SPEED = 12.0;
    private static final double SPECTATOR_SPRINT_SPEED = 45.0;
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
        Movement move = movement(input, fixedDeltaSeconds, yawDegrees, pitchDegrees);
        if (mode == Mode.SURVIVAL) Objects.requireNonNull(world, "Survival requires a world");
        previous = current.position();
        if (mode == Mode.SPECTATOR) {
            current = new PlayerState(
                    new Position(previous.x() + move.x, previous.y() + move.y, previous.z() + move.z),
                    move.x / fixedDeltaSeconds,
                    move.y / fixedDeltaSeconds,
                    move.z / fixedDeltaSeconds,
                    false,
                    0);
        } else {
            current = physics.step(
                    world, current, move.x, move.z, input.jumpHeld() || input.jumpPressed(), fixedDeltaSeconds);
        }
    }

    public List<ChunkPos> requiredCollisionChunks(
            PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        Movement move = movement(input, fixedDeltaSeconds, yawDegrees, pitchDegrees);
        return mode == Mode.SPECTATOR
                ? List.of()
                : physics.requiredChunks(
                        current, move.x, move.z, input.jumpHeld() || input.jumpPressed(), fixedDeltaSeconds);
    }

    public void pauseInterpolation() {
        previous = current.position();
    }

    private Movement movement(PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        Objects.requireNonNull(input);
        if (!Double.isFinite(fixedDeltaSeconds) || fixedDeltaSeconds <= 0) {
            throw new IllegalArgumentException("Fixed delta must be positive and finite");
        }
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("Player orientation must be finite");
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(mode == Mode.SPECTATOR ? pitchDegrees : 0);
        double forwardX = Math.cos(yaw) * Math.cos(pitch);
        double forwardY = Math.sin(pitch);
        double forwardZ = Math.sin(yaw) * Math.cos(pitch);
        double rightX = -Math.sin(yaw);
        double rightZ = Math.cos(yaw);
        double speed = mode == Mode.SPECTATOR
                ? (input.sprintHeld() ? SPECTATOR_SPRINT_SPEED : SPECTATOR_WALK_SPEED) * fixedDeltaSeconds
                : 1;
        double x = 0;
        double y = 0;
        double z = 0;
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
        }
        return new Movement(x, y, z);
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

    private record Movement(double x, double y, double z) {}
}
