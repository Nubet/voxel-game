package com.norbertfila.mob;

/**
 * Per-type tuning, in the spirit of Minecraft's entity attributes. Speeds are in blocks per second and ranges in
 * blocks.
 */
public record MobAttributes(float maxHealth, double walkSpeed, double followRange, boolean burnsInDaylight) {
    public MobAttributes {
        if (!Float.isFinite(maxHealth) || maxHealth <= 0) throw new IllegalArgumentException("Invalid max health");
        if (!Double.isFinite(walkSpeed) || walkSpeed <= 0) throw new IllegalArgumentException("Invalid walk speed");
        if (!Double.isFinite(followRange) || followRange <= 0)
            throw new IllegalArgumentException("Invalid follow range");
    }
}
