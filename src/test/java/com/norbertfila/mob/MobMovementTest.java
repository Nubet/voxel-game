package com.norbertfila.mob;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class MobMovementTest {
    private static final double TICK = 1 / 60.0;
    private final World world = flat();
    private final MobPopulation population = new MobPopulation();

    @Test
    void walkingAcceleratesToTheTypeSpeedAndTurnsGradually() {
        var zombie = spawn(MobType.ZOMBIE, 8.5, 8.5);
        zombie.walkToward(40, 8.5, 1);
        population.tick(world, TICK);
        assertEquals(10, zombie.yaw(), 1e-4, "turns at most ten degrees per tick");
        run(60);
        assertEquals(90, zombie.yaw(), 1e-4);
        assertEquals(MobType.ZOMBIE.attributes().walkSpeed(), zombie.horizontalSpeed(), .05);
        assertTrue(zombie.x() > 9.5);
        double before = zombie.x();
        zombie.stop();
        run(30);
        assertTrue(zombie.x() - before < .4, "friction stops the mob quickly");
        assertEquals(0, zombie.horizontalSpeed(), .01);
    }

    @Test
    void wallsBlockAndAJumpClimbsOneBlock() {
        for (int x = 12; x < 16; x++)
            for (int z = 0; z < 16; z++) world.setBlock(new BlockPos(x, 0, z), BlockId.STONE, 64);
        var zombie = spawn(MobType.ZOMBIE, 8.5, 8.5);
        zombie.walkToward(40, 8.5, 1);
        run(120);
        assertTrue(zombie.horizontalCollision());
        assertEquals(11.7, zombie.x(), .01);
        for (int i = 0; i < 60 && zombie.y() < 1; i++) {
            zombie.jump();
            run(1);
        }
        run(30);
        assertEquals(1, zombie.y(), 1e-6);
        assertTrue(zombie.x() > 12);
    }

    @Test
    void waterLiftsMobsAndKnockbackPushesThemOffTheGround() {
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = -3; y < 0; y++) world.setBlock(new BlockPos(x, y, z), BlockId.WATER, 64);
        var skeleton = spawn(MobType.SKELETON, 8.5, 8.5);
        skeleton.y = -3;
        run(120);
        assertTrue(skeleton.inWater() || skeleton.y() > -1.5);
        assertTrue(skeleton.y() > -2);
        var creeper = spawn(MobType.CREEPER, 4.5, 4.5);
        creeper.y = 0;
        run(1);
        creeper.knockback(6, 8, 0);
        run(1);
        assertFalse(creeper.onGround());
        assertTrue(creeper.x() > 4.5);
    }

    @Test
    void renderStateInterpolatesYawTheShortWayAndWalkDistance() {
        var state =
                new MobRenderState(1, MobType.ZOMBIE, 0, 0, 0, 0, 0, 0, 170, -170, 2, 3, false, 0, MobAction.NONE, 0);
        assertEquals(180, Math.abs(state.interpolatedYaw(.5)), 1e-4);
        assertEquals(2.5, state.interpolatedWalkDistance(.5), 1e-9);
    }

    private Mob spawn(MobType type, double x, double z) {
        return population.spawn(type, SpawnZone.SURFACE, x, 0, z, 0).orElseThrow();
    }

    private void run(int ticks) {
        for (int i = 0; i < ticks; i++) population.tick(world, TICK);
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
