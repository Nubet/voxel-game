package com.norbertfila.mob;

import java.util.Objects;

public record MobRenderState(
        long id,
        MobType type,
        double previousX,
        double previousY,
        double previousZ,
        double x,
        double y,
        double z,
        float yaw) {
    public MobRenderState {
        Objects.requireNonNull(type);
    }

    public double interpolatedX(double alpha) {
        return previousX + (x - previousX) * alpha;
    }

    public double interpolatedY(double alpha) {
        return previousY + (y - previousY) * alpha;
    }

    public double interpolatedZ(double alpha) {
        return previousZ + (z - previousZ) * alpha;
    }
}
