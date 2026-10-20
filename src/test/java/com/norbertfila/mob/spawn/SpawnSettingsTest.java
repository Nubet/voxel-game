package com.norbertfila.mob.spawn;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobCategory;
import com.norbertfila.mob.MobType;
import com.norbertfila.mob.PopulationKey;
import com.norbertfila.mob.SpawnZone;
import com.norbertfila.simulation.WorldTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpawnSettingsTest {
    private static final PopulationKey SURFACE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.SURFACE);
    private static final PopulationKey CAVE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.CAVE);

    @Test
    void surfaceAndCaveHaveSeparateCapsScaledByEligibleColumns() {
        var settings = SpawnSettings.DEFAULT;
        assertEquals(40, settings.cap(SURFACE));
        assertEquals(30, settings.cap(CAVE));
        assertEquals(40, settings.scaledCap(SURFACE, 289));
        assertEquals(20, settings.scaledCap(SURFACE, 145));
        assertEquals(0, settings.scaledCap(CAVE, 0));
    }

    @Test
    void nightFollowsMinecraftTimeIncludingWrappedWindows() {
        var settings = SpawnSettings.DEFAULT;
        assertFalse(settings.isNight(WorldTime.INITIAL.withMinecraftTime(6000)));
        assertFalse(settings.isNight(WorldTime.INITIAL.withMinecraftTime(12999)));
        assertTrue(settings.isNight(WorldTime.INITIAL.withMinecraftTime(13000)));
        assertTrue(settings.isNight(WorldTime.INITIAL.withMinecraftTime(18000)));
        assertFalse(settings.isNight(WorldTime.INITIAL.withMinecraftTime(23000)));
        var wrapped = with(Map.of("nightStart", 20000, "nightEnd", 2000));
        assertTrue(wrapped.isNight(WorldTime.INITIAL.withMinecraftTime(500)));
        assertFalse(wrapped.isNight(WorldTime.INITIAL.withMinecraftTime(10000)));
    }

    @Test
    void rejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> with(Map.of("cycleTicks", 0)));
        assertThrows(IllegalArgumentException.class, () -> with(Map.of("maxSpawnLight", 16)));
        assertThrows(IllegalArgumentException.class, () -> with(Map.of("nightEnd", 24000)));
        var negative = new HashMap<>(SpawnSettings.DEFAULT.caps());
        negative.put(SURFACE, -1);
        var d = SpawnSettings.DEFAULT;
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpawnSettings(
                        negative,
                        d.cycleTicks(),
                        d.chunkRadius(),
                        d.referenceColumns(),
                        d.columnsPerCycle(),
                        d.groupsPerColumn(),
                        d.attemptsPerGroup(),
                        d.groupSpread(),
                        d.minPlayerDistance(),
                        d.despawnDistance(),
                        d.randomDespawnDistance(),
                        d.despawnDelayTicks(),
                        d.randomDespawnChance(),
                        d.maxBlockLight(),
                        d.maxSpawnLight(),
                        d.nightStart(),
                        d.nightEnd()));
    }

    @Test
    void mobTypesResolveByIdAndBuildBodiesFromFeet() {
        assertEquals(MobType.CREEPER, MobType.byId("creeper").orElseThrow());
        assertTrue(MobType.byId("pig").isEmpty());
        var body = MobType.ZOMBIE.body(10.5, 64, -3.5);
        assertEquals(10.2, body.minX(), 1e-9);
        assertEquals(64, body.minY());
        assertEquals(65.95, body.maxY(), 1e-9);
    }

    private static SpawnSettings with(Map<String, Integer> overrides) {
        var d = SpawnSettings.DEFAULT;
        return new SpawnSettings(
                d.caps(),
                overrides.getOrDefault("cycleTicks", d.cycleTicks()),
                d.chunkRadius(),
                d.referenceColumns(),
                d.columnsPerCycle(),
                d.groupsPerColumn(),
                d.attemptsPerGroup(),
                d.groupSpread(),
                d.minPlayerDistance(),
                d.despawnDistance(),
                d.randomDespawnDistance(),
                d.despawnDelayTicks(),
                d.randomDespawnChance(),
                d.maxBlockLight(),
                overrides.getOrDefault("maxSpawnLight", d.maxSpawnLight()),
                overrides.getOrDefault("nightStart", d.nightStart()),
                overrides.getOrDefault("nightEnd", d.nightEnd()));
    }
}
