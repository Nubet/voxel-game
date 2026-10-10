package com.norbertfila.player;

import com.norbertfila.world.Aabb;
import java.util.Objects;

public record PlayerState(
        Position position,
        double velocityX,
        double velocityY,
        double velocityZ,
        boolean grounded,
        double jumpCooldownSeconds,
        boolean crouching) {
    public static final double HALF_WIDTH = .3;
    public static final double STANDING_HEIGHT = 1.8;
    public static final double CROUCH_HEIGHT = 1.5;
    public static final double EYE_HEIGHT = 1.62;
    public static final double CROUCH_EYE_HEIGHT = 1.27;

    public PlayerState {
        Objects.requireNonNull(position);
        if (!Double.isFinite(velocityX) || !Double.isFinite(velocityY) || !Double.isFinite(velocityZ)) {
            throw new IllegalArgumentException("Player velocity must be finite");
        }
        if (!Double.isFinite(jumpCooldownSeconds) || jumpCooldownSeconds < 0) {
            throw new IllegalArgumentException("Jump cooldown must be finite and non-negative");
        }
    }

    public static PlayerState at(Position position) {
        return new PlayerState(position, 0, 0, 0, false, 0, false);
    }

    public PlayerState withCrouching(boolean crouching) {
        return this.crouching == crouching
                ? this
                : new PlayerState(position, velocityX, velocityY, velocityZ, grounded, jumpCooldownSeconds, crouching);
    }

    public PlayerState resetMotion() {
        return new PlayerState(position, 0, 0, 0, false, 0, crouching);
    }

    public double height() {
        return crouching ? CROUCH_HEIGHT : STANDING_HEIGHT;
    }

    public double eyeHeight() {
        return crouching ? CROUCH_EYE_HEIGHT : EYE_HEIGHT;
    }

    public Aabb body() {
        return new Aabb(
                position.x() - HALF_WIDTH,
                position.y(),
                position.z() - HALF_WIDTH,
                position.x() + HALF_WIDTH,
                position.y() + height(),
                position.z() + HALF_WIDTH);
    }

    public record Position(double x, double y, double z) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Player position must be finite");
            }
        }
    }
}
