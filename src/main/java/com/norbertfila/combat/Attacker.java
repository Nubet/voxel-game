package com.norbertfila.combat;

/** The attacker's position and movement at the moment of a swing. */
public record Attacker(double x, double z, boolean grounded, boolean falling, boolean sprinting, boolean inWater) {
    public Attacker {
        if (!Double.isFinite(x) || !Double.isFinite(z)) throw new IllegalArgumentException("Invalid attacker position");
    }

    public static Attacker standing(double x, double z) {
        return new Attacker(x, z, true, false, false, false);
    }
}
