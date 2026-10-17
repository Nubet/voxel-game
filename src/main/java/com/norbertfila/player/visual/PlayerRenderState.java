package com.norbertfila.player.visual;

import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerState.Position;
import java.util.Objects;

public record PlayerRenderState(
        PlayerAppearance appearance,
        Sample previous,
        Sample current,
        double velocityX,
        double velocityY,
        double velocityZ,
        boolean grounded,
        boolean crouching,
        boolean sprinting,
        boolean flying,
        PlayerController.Mode mode,
        double submergedFraction,
        boolean eyesSubmerged,
        boolean waterAvailable,
        boolean frozen) {
    public PlayerRenderState {
        Objects.requireNonNull(appearance);
        Objects.requireNonNull(previous);
        Objects.requireNonNull(current);
        Objects.requireNonNull(mode);
        if (!Double.isFinite(velocityX)
                || !Double.isFinite(velocityY)
                || !Double.isFinite(velocityZ)
                || !Double.isFinite(submergedFraction)
                || submergedFraction < 0
                || submergedFraction > 1) throw new IllegalArgumentException("Invalid player render motion");
    }

    public Sample interpolate(double alpha) {
        if (!Double.isFinite(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("Interpolation alpha must be in [0, 1]");
        return new Sample(
                new Position(
                        lerp(previous.feet().x(), current.feet().x(), alpha),
                        lerp(previous.feet().y(), current.feet().y(), alpha),
                        lerp(previous.feet().z(), current.feet().z(), alpha)),
                lerp(previous.eyeHeight(), current.eyeHeight(), alpha),
                previous.yaw() + shortestAngle(current.yaw() - previous.yaw()) * alpha,
                previous.pitch() + shortestAngle(current.pitch() - previous.pitch()) * alpha);
    }

    private static double lerp(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }

    private static double shortestAngle(double angle) {
        return ((angle % 360 + 540) % 360) - 180;
    }

    public record Sample(Position feet, double eyeHeight, double yaw, double pitch) {
        public Sample {
            Objects.requireNonNull(feet);
            if (!Double.isFinite(eyeHeight) || eyeHeight < 0 || !Double.isFinite(yaw) || !Double.isFinite(pitch))
                throw new IllegalArgumentException("Invalid player render sample");
        }

        public Position eye() {
            return new Position(feet.x(), feet.y() + eyeHeight, feet.z());
        }
    }
}
