package com.norbertfila.mob;

import java.util.List;

/** Feet position of a player that mobs spawn and despawn around. */
public record PlayerPosition(double x, double y, double z) {
    public PlayerPosition {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Player position must be finite");
    }

    public double distanceTo(double px, double py, double pz) {
        double dx = px - x, dy = py - y, dz = pz - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Distance to the closest player, or infinity when there is none. */
    public static double nearest(List<PlayerPosition> players, double x, double y, double z) {
        double nearest = Double.POSITIVE_INFINITY;
        for (var player : players) nearest = Math.min(nearest, player.distanceTo(x, y, z));
        return nearest;
    }
}
