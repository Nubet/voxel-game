package com.norbertfila.mob.spawn;

import com.norbertfila.mob.MobCategory;
import com.norbertfila.simulation.WorldTime;
import java.util.Map;

/**
 * Tunable spawn and despawn rules. Durations are in world ticks (60 per second); chances are per spawn cycle, which
 * runs every {@code cycleTicks} so Minecraft's per-tick odds at 20 TPS keep their meaning.
 */
public record SpawnSettings(
        Map<PopulationKey, Integer> caps,
        int cycleTicks,
        int chunkRadius,
        int referenceColumns,
        int columnsPerCycle,
        int groupsPerColumn,
        int attemptsPerGroup,
        int groupSpread,
        double minPlayerDistance,
        double despawnDistance,
        double randomDespawnDistance,
        int despawnDelayTicks,
        int randomDespawnChance,
        int maxBlockLight,
        int maxSpawnLight,
        int nightStart,
        int nightEnd) {
    public static final SpawnSettings DEFAULT = new SpawnSettings(
            Map.of(
                    new PopulationKey(MobCategory.HOSTILE, SpawnZone.SURFACE), 40,
                    new PopulationKey(MobCategory.HOSTILE, SpawnZone.CAVE), 30),
            3,
            8,
            289,
            48,
            3,
            4,
            6,
            24,
            128,
            32,
            1800,
            800,
            0,
            7,
            13000,
            23000);

    public SpawnSettings {
        caps = Map.copyOf(caps);
        for (int cap : caps.values()) if (cap < 0) throw new IllegalArgumentException("Population cap is negative");
        if (cycleTicks < 1
                || chunkRadius < 0
                || referenceColumns < 1
                || columnsPerCycle < 0
                || groupsPerColumn < 1
                || attemptsPerGroup < 1
                || groupSpread < 0
                || randomDespawnChance < 1
                || despawnDelayTicks < 0) throw new IllegalArgumentException("Invalid spawn cycle settings");
        if (!(minPlayerDistance >= 0
                && randomDespawnDistance >= 0
                && despawnDistance > minPlayerDistance
                && despawnDistance >= randomDespawnDistance))
            throw new IllegalArgumentException("Invalid spawn distances");
        if (maxBlockLight < 0 || maxBlockLight > 15 || maxSpawnLight < 0 || maxSpawnLight > 15)
            throw new IllegalArgumentException("Light limits must be in [0, 15]");
        if (nightStart < 0
                || nightEnd < 0
                || nightStart >= WorldTime.MINECRAFT_TICKS_PER_DAY
                || nightEnd >= WorldTime.MINECRAFT_TICKS_PER_DAY)
            throw new IllegalArgumentException("Invalid night window");
    }

    public int cap(PopulationKey key) {
        return caps.getOrDefault(key, 0);
    }

    /** Minecraft scales each cap by the eligible chunk columns around the players. */
    public int scaledCap(PopulationKey key, int eligibleColumns) {
        if (eligibleColumns < 0) throw new IllegalArgumentException("Column count is negative");
        return (int) ((long) cap(key) * eligibleColumns / referenceColumns);
    }

    public boolean isNight(WorldTime time) {
        int now = time.minecraftTime();
        return nightStart <= nightEnd ? now >= nightStart && now < nightEnd : now >= nightStart || now < nightEnd;
    }
}
