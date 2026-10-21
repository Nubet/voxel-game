package com.norbertfila.mob.ai.navigation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.*;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class PathNavigatorTest {
    private final World world = flat();
    private final MobPopulation population = new MobPopulation();
    private final PathNavigator navigator = new PathNavigator(new PathFinder(2000));
    private final WorldPathTerrain terrain = new WorldPathTerrain(world);

    @Test
    void walksAroundAWallToTheTarget() {
        for (int z = 2; z <= 14; z++)
            for (int y = 0; y < 2; y++) world.setBlock(new BlockPos(12, y, z), BlockId.STONE, 64);
        var zombie = spawn(8.5, 8.5);
        navigator.moveTo(16.5, 0, 8.5, 1, 0);
        run(zombie, 900);
        assertFalse(navigator.active());
        assertEquals(16.5, zombie.x(), .6);
        assertEquals(8.5, zombie.z(), .6);
    }

    @Test
    void jumpsOntoAStepAndStopsWithinReach() {
        for (int x = 12; x < 16; x++)
            for (int z = 0; z < 16; z++) world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
        var zombie = spawn(8.5, 8.5);
        navigator.moveTo(14.5, 1, 8.5, 1, 0);
        run(zombie, 600);
        assertEquals(1, zombie.y(), 1e-6);
        assertEquals(14.5, zombie.x(), .6);
        var far = spawn(2.5, 2.5);
        var second = new PathNavigator(new PathFinder(2000));
        second.moveTo(9.5, 0, 2.5, 1, 3);
        for (int i = 0; i < 600 && second.active(); i++) {
            second.tick(far, terrain);
            population.tick(world, 1 / 60.0);
        }
        assertTrue(far.x() < 7.6 && far.x() > 5.5, "stops about three blocks short: " + far.x());
    }

    @Test
    void givesUpOnUnreachableTargetsAfterReachingTheClosestCell() {
        for (int x = -16; x < 48; x++)
            for (int y = 0; y < 3; y++) world.setBlock(new BlockPos(x, y, 12), BlockId.STONE, 64);
        var zombie = spawn(8.5, 8.5);
        navigator.moveTo(8.5, 0, 14.5, 1, 0);
        run(zombie, 900);
        assertFalse(navigator.active());
        assertTrue(zombie.z() < 12);
    }

    private Mob spawn(double x, double z) {
        return population.spawn(MobType.ZOMBIE, SpawnZone.SURFACE, x, 0, z, 0).orElseThrow();
    }

    private void run(Mob mob, int ticks) {
        for (int i = 0; i < ticks && navigator.active(); i++) {
            navigator.tick(mob, terrain);
            population.tick(world, 1 / 60.0);
        }
    }

    private static World flat() {
        var world = new World();
        for (int x = -1; x <= 2; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = -1; y <= 1; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(BlockId.STONE) : new Chunk());
        return world;
    }
}
