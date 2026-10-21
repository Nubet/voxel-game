package com.norbertfila.combat;

/** The attacker's position, facing and movement at the moment of a swing. */
public record Attacker(
        double x, double z, double yawDegrees, boolean grounded, boolean falling, boolean sprinting, boolean inWater) {
    public Attacker {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(yawDegrees))
            throw new IllegalArgumentException("Invalid attacker");
    }

    public static Attacker standing(double x, double z, double yawDegrees) {
        return new Attacker(x, z, yawDegrees, true, false, false, false);
    }

    public double facingX() {
        return Math.cos(Math.toRadians(yawDegrees));
    }

    public double facingZ() {
        return Math.sin(Math.toRadians(yawDegrees));
    }
}
