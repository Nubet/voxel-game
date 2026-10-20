package com.norbertfila.mob.spawn;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.*;
import com.norbertfila.world.*;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class DespawnerTest {
    private static final List<PlayerPosition> PLAYER = List.of(new PlayerPosition(0, 0, 0));
    private static final PopulationKey SURFACE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.SURFACE);
    private final Despawner despawner = new Despawner(SpawnSettings.DEFAULT);

    @Test
    void mobsBeyondTheDespawnDistanceVanishAtOnce() {
        var population = new MobPopulation();
        population.spawn(MobType.ZOMBIE, SpawnZone.SURFACE, 100, 90, 0, 0);
        population.spawn(MobType.ZOMBIE, SpawnZone.SURFACE, 100, 0, 0, 0);
        assertEquals(1, despawner.despawn(population, PLAYER, new SplittableRandom(1)));
        assertEquals(1, population.count(SURFACE));
        assertEquals(0, despawner.despawn(population, List.of(), new SplittableRandom(1)));
    }

    @Test
    void nearbyMobsStayAndResetTheirTimer() {
        var world = flat();
        var population = new MobPopulation();
        var mob = population
                .spawn(MobType.CREEPER, SpawnZone.SURFACE, 10.5, 0, 0.5, 0)
                .orElseThrow();
        for (int i = 0; i < 5000; i++) population.tick(world, 1 / 60.0);
        var random = new SplittableRandom(2);
        for (int i = 0; i < 5000; i++) despawner.despawn(population, PLAYER, random);
        assertEquals(1, population.size());
        assertEquals(0, mob.ticksAwayFromPlayers());
    }

    @Test
    void midRangeMobsDespawnByChanceOnlyAfterTheDelay() {
        var world = flat();
        var population = new MobPopulation();
        for (int i = 0; i < 20; i++) population.spawn(MobType.SKELETON, SpawnZone.SURFACE, 60.5, 0, i * 2 + .5, 0);
        var random = new SplittableRandom(3);
        for (int i = 0; i < SpawnSettings.DEFAULT.despawnDelayTicks(); i++) population.tick(world, 1 / 60.0);
        for (int i = 0; i < 1000; i++) despawner.despawn(population, PLAYER, random);
        assertEquals(20, population.size());
        population.tick(world, 1 / 60.0);
        for (int i = 0; i < 4000; i++) despawner.despawn(population, PLAYER, random);
        assertTrue(population.size() < 10, "left " + population.size());
    }

    private static World flat() {
        var world = new World();
        for (int x = -1; x <= 4; x++)
            for (int z = -1; z <= 4; z++)
                for (int y = -1; y <= 1; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(BlockId.STONE) : new Chunk());
        return world;
    }
}
