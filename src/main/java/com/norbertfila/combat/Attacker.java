package com.norbertfila.combat;

/** The attacker's position, facing and movement at the moment of a swing; speed is horizontal, in blocks/s. */
public record Attacker(
        double x,
        double y,
        double z,
        double yawDegrees,
        boolean grounded,
        boolean falling,
        boolean sprinting,
        boolean inWater,
        double speed) {
    public Attacker {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Double.isFinite(yawDegrees)
                || !Double.isFinite(speed)
                || speed < 0) throw new IllegalArgumentException("Invalid attacker");
    }

    public static Attacker standing(double x, double y, double z, double yawDegrees) {
        return new Attacker(x, y, z, yawDegrees, true, false, false, false, 0);
    }

    public double facingX() {
        return Math.cos(Math.toRadians(yawDegrees));
    }

    public double facingZ() {
        return Math.sin(Math.toRadians(yawDegrees));
    }
}
