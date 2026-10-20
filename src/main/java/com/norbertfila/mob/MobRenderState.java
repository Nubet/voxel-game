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
        float yaw,
        boolean burning,
        float hurt) {
    public MobRenderState {
        Objects.requireNonNull(type);
        if (!(hurt >= 0 && hurt <= 1)) throw new IllegalArgumentException("Hurt fade must be in [0, 1]");
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
