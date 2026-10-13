package com.norbertfila.player;

import com.norbertfila.player.PlayerState.Position;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class PlayerController {
    private static final double SPECTATOR_WALK_SPEED = 12.0;
    private static final double SPECTATOR_SPRINT_SPEED = 45.0;
    private static final double DOUBLE_TAP_SECONDS = .35;
    private Mode mode = Mode.SURVIVAL;
    private Mode requestedMode;
    private boolean flying;
    private double lastJumpPressSeconds = Double.NaN;
    private Position previous = new Position(0, 22, 34);
    private double previousEyeHeight = PlayerState.EYE_HEIGHT;
    private PlayerState current = PlayerState.at(previous);
    private final PlayerPhysics physics = new PlayerPhysics();
    private final VoxelCollision collision = new VoxelCollision();

    public boolean setMode(World world, Mode mode) {
        Mode next = Objects.requireNonNull(mode);
        if (this.mode == Mode.SPECTATOR
                && next != Mode.SPECTATOR
                && !collision.spaceFree(Objects.requireNonNull(world), current.body())) return false;
        if (this.mode != next) {
            current = current.resetMotion().withCrouching(next != Mode.SPECTATOR && current.crouching());
            previous = current.position();
            previousEyeHeight = current.eyeHeight();
            flying = false;
            clearInputGestures();
        }
        this.mode = next;
        requestedMode = null;
        return true;
    }

    public void requestNextMode() {
        requestedMode = switch (requestedMode == null ? mode : requestedMode) {
            case SURVIVAL -> Mode.CREATIVE;
            case CREATIVE -> Mode.SPECTATOR;
            case SPECTATOR -> Mode.SURVIVAL;
        };
        clearInputGestures();
    }

    public boolean modeChangePending() {
        return requestedMode != null;
    }

    public boolean flying() {
        return flying;
    }

    public void clearInputGestures() {
        lastJumpPressSeconds = Double.NaN;
    }

    public Mode mode() {
        return mode;
    }

    public void setPosition(Position position) {
        current = PlayerState.at(position).withCrouching(current.crouching());
        previous = current.position();
        previousEyeHeight = current.eyeHeight();
        flying = false;
        requestedMode = null;
        clearInputGestures();
    }

    public PlayerState state() {
        return current;
    }

    public void tick(PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        tick(null, input, fixedDeltaSeconds, yawDegrees, pitchDegrees);
    }

    public void tick(World world, PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        Movement move = movement(input, fixedDeltaSeconds, yawDegrees, pitchDegrees);
        if (requestedMode != null) {
            if (requestedMode != Mode.SPECTATOR
                    && !collision.available(Objects.requireNonNull(world), current.body())) {
                pauseInterpolation();
                return;
            }
            Mode requested = requestedMode;
            if (!setMode(world, requested)) requestedMode = null;
            pauseInterpolation();
            return;
        }
        if (mode != Mode.SPECTATOR) Objects.requireNonNull(world, "Colliding modes require a world");
        PlayerState start = movementStart(input, move);
        if (mode != Mode.SPECTATOR && start.crouching() && !wantsCrouch(input, move)) {
            PlayerState standing = start.withCrouching(false);
            if (!collision.available(world, standing.body())) {
                pauseInterpolation();
                return;
            }
            if (collision.spaceFree(world, standing.body())) start = standing;
        }
        PlayerState result;
        if (mode == Mode.SPECTATOR) {
            Position feet = current.position();
            result = new PlayerState(
                    new Position(feet.x() + move.x, feet.y() + move.y, feet.z() + move.z),
                    move.x / fixedDeltaSeconds,
                    move.y / fixedDeltaSeconds,
                    move.z / fixedDeltaSeconds,
                    false,
                    0,
                    false);
        } else if (move.flying) {
            result = physics.fly(world, start, move.x, move.y, move.z, input.sprintHeld(), fixedDeltaSeconds);
        } else {
            WaterContact.Result water = WaterContact.sample(world, start);
            if (!water.available()) {
                pauseInterpolation();
                return;
            }
            result = physics.step(
                    world,
                    start,
                    move.x,
                    move.z,
                    !move.toggled && (input.jumpHeld() || input.jumpPressed()),
                    input.sneakHeld(),
                    sprinting(input),
                    yawDegrees,
                    fixedDeltaSeconds,
                    water);
        }
        if (result == start) {
            pauseInterpolation();
            return;
        }
        previous = current.position();
        previousEyeHeight = current.eyeHeight();
        current = result;
        flying = move.flying && !result.grounded();
        if (move.flying && !flying) clearInputGestures();
        else if (mode == Mode.CREATIVE && input.jumpPressed())
            lastJumpPressSeconds = move.toggled ? Double.NaN : input.jumpPressedAtSeconds();
    }

    public List<ChunkPos> requiredCollisionChunks(
            PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        return requiredCollisionChunks(null, input, fixedDeltaSeconds, yawDegrees, pitchDegrees);
    }

    public List<ChunkPos> requiredCollisionChunks(
            World world, PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        Movement move = movement(input, fixedDeltaSeconds, yawDegrees, pitchDegrees);
        if (requestedMode != null)
            return requestedMode == Mode.SPECTATOR
                    ? List.of()
                    : VoxelCollision.requiredChunks(VoxelCollision.requiredRegion(current.body(), 0, 0, 0));
        if (mode == Mode.SPECTATOR) return List.of();
        PlayerState start = movementStart(input, move);
        List<ChunkPos> required = requiredMovementChunks(world, start, input, move, fixedDeltaSeconds, yawDegrees);
        if (!start.crouching() || wantsCrouch(input, move)) return required;
        // Expansion and either movement path must be known before choosing the actual pose.
        var combined = new LinkedHashSet<>(required);
        combined.addAll(
                requiredMovementChunks(world, start.withCrouching(false), input, move, fixedDeltaSeconds, yawDegrees));
        return List.copyOf(combined);
    }

    private List<ChunkPos> requiredMovementChunks(
            World world,
            PlayerState start,
            PlayerInput input,
            Movement move,
            double fixedDeltaSeconds,
            double yawDegrees) {
        if (world != null && !move.flying) {
            var required = new LinkedHashSet<>(WaterContact.requiredChunks(start));
            var water = WaterContact.sample(world, start);
            if (water.available())
                required.addAll(physics.requiredChunks(
                        start,
                        move.x,
                        move.z,
                        !move.toggled && (input.jumpHeld() || input.jumpPressed()),
                        input.sneakHeld(),
                        sprinting(input),
                        yawDegrees,
                        fixedDeltaSeconds,
                        water));
            return List.copyOf(required);
        }
        return move.flying
                ? physics.requiredFlightChunks(start, move.x, move.y, move.z, input.sprintHeld(), fixedDeltaSeconds)
                : physics.requiredChunks(
                        start,
                        move.x,
                        move.z,
                        !move.toggled && (input.jumpHeld() || input.jumpPressed()),
                        sprinting(input),
                        yawDegrees,
                        fixedDeltaSeconds);
    }

    public void pauseInterpolation() {
        previous = current.position();
        previousEyeHeight = current.eyeHeight();
    }

    private PlayerState movementStart(PlayerInput input, Movement move) {
        PlayerState start = move.toggled ? current.resetMotion() : current;
        return wantsCrouch(input, move) ? start.withCrouching(true) : start;
    }

    private boolean wantsCrouch(PlayerInput input, Movement move) {
        return mode != Mode.SPECTATOR && !move.flying && input.sneakHeld();
    }

    private Movement movement(PlayerInput input, double fixedDeltaSeconds, double yawDegrees, double pitchDegrees) {
        Objects.requireNonNull(input);
        if (!Double.isFinite(fixedDeltaSeconds) || fixedDeltaSeconds <= 0) {
            throw new IllegalArgumentException("Fixed delta must be positive and finite");
        }
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("Player orientation must be finite");
        }
        boolean toggled = mode == Mode.CREATIVE
                && input.jumpPressed()
                && !Double.isNaN(lastJumpPressSeconds)
                && input.jumpPressedAtSeconds() > lastJumpPressSeconds
                && input.jumpPressedAtSeconds() - lastJumpPressSeconds <= DOUBLE_TAP_SECONDS + 1e-9;
        boolean nextFlying = mode == Mode.CREATIVE && (toggled ? !flying : flying);
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
        if (mode == Mode.SPECTATOR || nextFlying) {
            if (input.jumpHeld()) y += speed;
            if (input.sneakHeld()) y -= speed;
        }
        return new Movement(x, y, z, nextFlying, toggled);
    }

    private static boolean sprinting(PlayerInput input) {
        return input.sprintHeld() && input.forward() && !input.backward() && !input.sneakHeld();
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
        double eyeHeight = previousEyeHeight + (current.eyeHeight() - previousEyeHeight) * alpha;
        return new Position(feet.x(), feet.y() + eyeHeight, feet.z());
    }

    public enum Mode {
        SPECTATOR,
        SURVIVAL,
        CREATIVE
    }

    private record Movement(double x, double y, double z, boolean flying, boolean toggled) {}
}
