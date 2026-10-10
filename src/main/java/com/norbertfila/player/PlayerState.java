package com.norbertfila.player;

import java.util.Objects;

public record PlayerState(Position position, double velocityX, double velocityY, double velocityZ, boolean grounded) {
    public static final double EYE_HEIGHT = 1.62;

    public PlayerState {
        Objects.requireNonNull(position);
        if (!Double.isFinite(velocityX) || !Double.isFinite(velocityY) || !Double.isFinite(velocityZ)) {
            throw new IllegalArgumentException("Player velocity must be finite");
        }
    }

    public static PlayerState at(Position position) {
        return new PlayerState(position, 0, 0, 0, false);
    }

    public record Position(double x, double y, double z) {
        public Position {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Player position must be finite");
            }
        }
    }
}
