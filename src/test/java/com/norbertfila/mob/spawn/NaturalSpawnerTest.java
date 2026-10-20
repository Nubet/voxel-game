package com.norbertfila.mob.spawn;

import static com.norbertfila.mob.spawn.SpawnTestWorld.CAVE_FLOOR;
import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.*;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class NaturalSpawnerTest {
    private static final int DAY = 6000, NIGHT = 18000;
    private static final PopulationKey SURFACE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.SURFACE);
    private static final PopulationKey CAVE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.CAVE);
    private static final List<PlayerPosition> PLAYER = List.of(new PlayerPosition(8, 0, 8));

    @Test
    void nightSpawnsPacksOnTheSurfaceAwayFromThePlayer() {
        var population = run(new SpawnTestWorld(false), NIGHT, 300, 1);
        assertTrue(population.count(SURFACE) > 0);
        for (var mob : population.mobs()) {
            assertEquals(SpawnZone.SURFACE, mob.zone());
            assertEquals(0, mob.y());
            double distance = PlayerPosition.nearest(PLAYER, mob.x(), mob.y(), mob.z());
            assertTrue(distance >= 24 && distance <= 128, "distance " + distance);
        }
    }

    @Test
    void daylightKeepsTheSurfaceEmptyWhileCavesFill() {
        var population = run(new SpawnTestWorld(true), DAY, 300, 2);
        assertEquals(0, population.count(SURFACE));
        assertTrue(population.count(CAVE) > 0);
        for (var mob : population.mobs()) assertEquals(CAVE_FLOOR, mob.y());
    }

    @Test
    void eachZoneFillsUpToItsOwnCap() {
        var population = run(new SpawnTestWorld(true), NIGHT, 600, 3);
        assertEquals(40, population.count(SURFACE));
        assertEquals(30, population.count(CAVE));
    }

    @Test
    void sameSeedGivesTheSameMobs() {
        var first = run(new SpawnTestWorld(true), NIGHT, 50, 4).renderSnapshot();
        var second = run(new SpawnTestWorld(true), NIGHT, 50, 4).renderSnapshot();
        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    void nothingSpawnsWithoutPlayersOrActiveColumns() {
        var test = new SpawnTestWorld(false);
        var population = new MobPopulation();
        var spawner = spawner();
        var random = new SplittableRandom(5);
        assertEquals(0, spawner.spawn(test.environment(NIGHT), population, List.of(), random));
        var farAway = List.of(new PlayerPosition(10_000, 0, 10_000));
        for (int i = 0; i < 50; i++) spawner.spawn(test.environment(NIGHT), population, farAway, random);
        assertEquals(0, population.size());
    }

    private static MobPopulation run(SpawnTestWorld test, int time, int cycles, long seed) {
        var population = new MobPopulation();
        var spawner = spawner();
        var random = new SplittableRandom(seed);
        for (int i = 0; i < cycles; i++) spawner.spawn(test.environment(time), population, PLAYER, random);
        return population;
    }

    private static NaturalSpawner spawner() {
        return new NaturalSpawner(
                SpawnTestWorld.settings(40, 30), SpawnTable.OVERWORLD, Map.of(MobCategory.HOSTILE, SpawnRules.HOSTILE));
    }
}
