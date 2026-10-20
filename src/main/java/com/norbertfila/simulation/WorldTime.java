package com.norbertfila.simulation;

public record WorldTime(int tickOfDay, boolean cycleEnabled) {
    public static final int TICKS_PER_DAY = 1200 * FixedSimulation.TICKS_PER_SECOND;
    public static final int MINECRAFT_TICKS_PER_DAY = 24000;
    private static final int TICKS_PER_MINECRAFT_TICK = TICKS_PER_DAY / MINECRAFT_TICKS_PER_DAY;
    public static final WorldTime INITIAL = new WorldTime(0, true);

    public WorldTime {
        if (tickOfDay < 0 || tickOfDay >= TICKS_PER_DAY)
            throw new IllegalArgumentException("Time of day must be in [0, " + TICKS_PER_DAY + ")");
    }

    public WorldTime tick() {
        return cycleEnabled ? new WorldTime((tickOfDay + 1) % TICKS_PER_DAY, true) : this;
    }

    public WorldTime withCycleEnabled(boolean enabled) {
        return new WorldTime(tickOfDay, enabled);
    }

    public WorldTime withMinecraftTime(int time) {
        if (time < 0 || time >= MINECRAFT_TICKS_PER_DAY)
            throw new IllegalArgumentException("Minecraft time must be in [0, " + MINECRAFT_TICKS_PER_DAY + ")");
        return new WorldTime(time * TICKS_PER_MINECRAFT_TICK, cycleEnabled);
    }

    public int minecraftTime() {
        return tickOfDay / TICKS_PER_MINECRAFT_TICK;
    }
}
