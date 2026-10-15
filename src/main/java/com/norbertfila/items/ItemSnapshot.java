package com.norbertfila.items;

public record ItemSnapshot(
        long id,
        int itemId,
        int count,
        double x,
        double y,
        double z,
        double vx,
        double vy,
        double vz,
        int pickupDelayTicks,
        int ageTicks,
        boolean onGround) {
    public ItemSnapshot {
        new ItemStack(itemId, count);
        if (id < 1
                || !coordinate(x)
                || !coordinate(y)
                || !coordinate(z)
                || !Double.isFinite(vx)
                || !Double.isFinite(vy)
                || !Double.isFinite(vz)
                || Math.abs(vx) > 32
                || Math.abs(vy) > 32
                || Math.abs(vz) > 32
                || pickupDelayTicks < 0
                || pickupDelayTicks > ItemSystem.THROW_PICKUP_DELAY_TICKS
                || ageTicks < 0
                || ageTicks >= ItemSystem.DESPAWN_TICKS) throw new IllegalArgumentException("Invalid item entity");
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && value >= Integer.MIN_VALUE + 1d && value <= Integer.MAX_VALUE - 1d;
    }
}
