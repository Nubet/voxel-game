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
        float previousYaw,
        float yaw,
        double previousWalkDistance,
        double walkDistance,
        boolean burning,
        float hurt,
        MobAction action,
        float actionProgress,
        float death) {
    public MobRenderState(
            long id,
            MobType type,
            double previousX,
            double previousY,
            double previousZ,
            double x,
            double y,
            double z,
            float previousYaw,
            float yaw,
            double previousWalkDistance,
            double walkDistance,
            boolean burning,
            float hurt,
            MobAction action,
            float actionProgress) {
        this(
                id,
                type,
                previousX,
                previousY,
                previousZ,
                x,
                y,
                z,
                previousYaw,
                yaw,
                previousWalkDistance,
                walkDistance,
                burning,
                hurt,
                action,
                actionProgress,
                0);
    }

    public MobRenderState {
        Objects.requireNonNull(type);
        Objects.requireNonNull(action);
        if (!(hurt >= 0 && hurt <= 1)) throw new IllegalArgumentException("Hurt fade must be in [0, 1]");
        if (!(actionProgress >= 0 && actionProgress <= 1))
            throw new IllegalArgumentException("Action progress must be in [0, 1]");
        if (!(death >= 0 && death <= 1)) throw new IllegalArgumentException("Death progress must be in [0, 1]");
    }

    /** A standing mob, for tools and tests that only place models. */
    public static MobRenderState standing(long id, MobType type, double x, double y, double z, float yaw) {
        return new MobRenderState(id, type, x, y, z, x, y, z, yaw, yaw, 0, 0, false, 0, MobAction.NONE, 0);
    }

    /** Interpolates along the shorter way around, so turning across 180 degrees does not spin the model. */
    public float interpolatedYaw(double alpha) {
        float delta = Mob.wrapDegrees(yaw - previousYaw);
        return (float) (previousYaw + delta * alpha);
    }

    public double interpolatedWalkDistance(double alpha) {
        return previousWalkDistance + (walkDistance - previousWalkDistance) * alpha;
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
