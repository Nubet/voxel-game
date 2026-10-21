package com.norbertfila.mob;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.DamageSource;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class MobDeathTest {
    private final World world = new World();
    private final MobPopulation population = new MobPopulation();

    MobDeathTest() {
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
    }

    @Test
    void slainMobsStayForTheirDeathAnimationAndThenLeave() {
        var zombie = population
                .spawn(MobType.ZOMBIE, SpawnZone.SURFACE, 4.5, 1, 4.5, 0)
                .orElseThrow();
        zombie.damage(DamageSource.PLAYER_ATTACK, 100);
        assertFalse(zombie.attackable());
        for (int tick = 1; tick < Mob.DEATH_TICKS; tick++) {
            assertTrue(population.tick(world, 1 / 60.0).isEmpty());
            assertEquals(tick / (float) Mob.DEATH_TICKS, zombie.deathProgress(), 1e-6);
        }
        assertEquals(1, population.size());
        assertEquals(java.util.List.of(zombie), population.tick(world, 1 / 60.0));
        assertEquals(0, population.size());
    }

    @Test
    void discardedMobsLeaveAtOnceWithoutDying() {
        var creeper = population
                .spawn(MobType.CREEPER, SpawnZone.SURFACE, 4.5, 1, 4.5, 0)
                .orElseThrow();
        creeper.discard();
        assertTrue(population.tick(world, 1 / 60.0).isEmpty());
        assertEquals(0, population.size());
    }
}
