package com.norbertfila.mob;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class MobPopulationTest {
    private static final PopulationKey SURFACE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.SURFACE);
    private static final PopulationKey CAVE = new PopulationKey(MobCategory.HOSTILE, SpawnZone.CAVE);

    @Test
    void countsFollowSpawnZoneAndRemoval() {
        var population = new MobPopulation();
        var zombie = population
                .spawn(MobType.ZOMBIE, SpawnZone.SURFACE, 1.5, 0, 1.5, 0)
                .orElseThrow();
        population.spawn(MobType.CREEPER, SpawnZone.CAVE, 3.5, -10, 3.5, 0).orElseThrow();
        population.spawn(MobType.SKELETON, SpawnZone.CAVE, 5.5, -10, 3.5, 0).orElseThrow();
        assertEquals(1, population.count(SURFACE));
        assertEquals(2, population.count(CAVE));
        population.remove(zombie);
        population.remove(zombie);
        assertEquals(0, population.count(SURFACE));
        assertEquals(2, population.size());
        population.clear();
        assertEquals(0, population.count(CAVE));
    }

    @Test
    void mobsFallLandAndMoveBetweenChunkBuckets() {
        var world = groundBelowZero();
        var population = new MobPopulation();
        var mob = population
                .spawn(MobType.ZOMBIE, SpawnZone.SURFACE, 4.5, 20, 4.5, 0)
                .orElseThrow();
        for (int i = 0; i < 120; i++) population.tick(world, 1 / 60.0);
        assertEquals(0, mob.y(), 1e-6);
        assertTrue(mob.onGround());
        assertEquals(120, mob.ticksAwayFromPlayers());
        assertEquals(1, population.near(new Aabb(4, 0, 4, 5, 1, 5)).size());
        assertTrue(population.near(new Aabb(4, 19, 4, 5, 21, 5)).isEmpty());
        var state = population.renderSnapshot().getFirst();
        assertEquals(MobType.ZOMBIE, state.type());
        assertEquals(0, state.interpolatedY(1), 1e-6);
    }

    @Test
    void mobsInUnloadedChunksAreDropped() {
        var world = groundBelowZero();
        var population = new MobPopulation();
        population.spawn(MobType.CREEPER, SpawnZone.SURFACE, 4.5, 0, 4.5, 0).orElseThrow();
        world.removeChunk(new ChunkPos(0, 0, 0));
        population.tick(world, 1 / 60.0);
        assertEquals(0, population.size());
        assertEquals(0, population.count(SURFACE));
    }

    @Test
    void hardLimitStopsSpawning() {
        var population = new MobPopulation();
        for (int i = 0; i < MobPopulation.MAX_MOBS; i++)
            assertTrue(
                    population.spawn(MobType.ZOMBIE, SpawnZone.CAVE, i, 0, 0, 0).isPresent());
        assertTrue(population.spawn(MobType.ZOMBIE, SpawnZone.CAVE, 0, 0, 0, 0).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MobPopulation().spawn(MobType.ZOMBIE, SpawnZone.CAVE, Double.NaN, 0, 0, 0));
    }

    private static World groundBelowZero() {
        var world = new World();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = -2; y <= 2; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(BlockId.STONE) : new Chunk());
        return world;
    }
}
