package com.norbertfila.mob.hazard;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.*;
import com.norbertfila.world.*;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class MobHazardsTest {
    private final World world = flat();
    private final MobPopulation population = new MobPopulation();
    private final MobHazards hazards = new MobHazards();
    private final SplittableRandom random = new SplittableRandom(1);

    @Test
    void undeadCatchFireInDirectDaylightButCreepersDoNot() {
        var zombie = spawn(MobType.ZOMBIE, 4.5);
        var skeleton = spawn(MobType.SKELETON, 8.5);
        var creeper = spawn(MobType.CREEPER, 12.5);
        run(new Sky(), 600);
        assertTrue(zombie.burning() && skeleton.burning());
        assertFalse(creeper.burning());
        assertTrue(zombie.health() < MobType.ZOMBIE.maxHealth());
        assertEquals(MobType.CREEPER.maxHealth(), creeper.health());
    }

    @Test
    void shadeNightAndRainKeepUndeadSafe() {
        var zombie = spawn(MobType.ZOMBIE, 4.5);
        var shaded = new Sky();
        shaded.sky = false;
        run(shaded, 1000);
        var night = new Sky();
        night.day = false;
        run(night, 1000);
        var rain = new Sky();
        rain.rain = true;
        run(rain, 1000);
        assertFalse(zombie.burning());
    }

    @Test
    void waterAndRainPutFiresOut() {
        var zombie = spawn(MobType.ZOMBIE, 4.5);
        zombie.ignite(MobHazards.SUNLIGHT_FIRE_TICKS);
        var wet = new Sky();
        wet.day = false;
        wet.water = true;
        run(wet, 1);
        assertFalse(zombie.burning());
        zombie.ignite(MobHazards.SUNLIGHT_FIRE_TICKS);
        var rain = new Sky();
        rain.rain = true;
        run(rain, 1);
        assertFalse(zombie.burning());
    }

    @Test
    void fireCostsOnePointPerSecondUntilTheMobDies() {
        var zombie = spawn(MobType.ZOMBIE, 4.5);
        zombie.ignite(MobHazards.SUNLIGHT_FIRE_TICKS);
        var night = new Sky();
        night.day = false;
        run(night, 60);
        assertEquals(19, zombie.health());
        run(night, 59);
        assertEquals(19, zombie.health());
        run(night, 1);
        assertEquals(18, zombie.health());
        run(new Sky(), 60 * 30);
        assertTrue(zombie.dead());
        assertEquals(0, population.size());
        assertEquals(0, population.count(new PopulationKey(MobCategory.HOSTILE, SpawnZone.SURFACE)));
    }

    @Test
    void hitsDuringTheHurtCooldownAreIgnored() {
        var zombie = spawn(MobType.ZOMBIE, 4.5);
        assertTrue(zombie.damage(DamageSource.FIRE, 3));
        assertFalse(zombie.damage(DamageSource.FIRE, 3));
        assertEquals(17, zombie.health());
        for (int i = 0; i < Mob.HURT_TICKS; i++) population.tick(world, 1 / 60.0);
        assertTrue(zombie.damage(DamageSource.FIRE, 3));
        assertEquals(1, population.renderSnapshot().getFirst().hurt(), 1e-6);
    }

    private Mob spawn(MobType type, double x) {
        return population.spawn(type, SpawnZone.SURFACE, x, 0, 4.5, 0).orElseThrow();
    }

    private void run(HazardEnvironment environment, int ticks) {
        for (int i = 0; i < ticks; i++) {
            population.tick(world, 1 / 60.0);
            hazards.tick(population, environment, random);
        }
    }

    private static final class Sky implements HazardEnvironment {
        boolean day = true, sky = true, rain, water;

        public boolean daylight() {
            return day;
        }

        public boolean seesSky(BlockPos position) {
            return sky;
        }

        public boolean rainingAt(BlockPos position) {
            return rain;
        }

        public boolean touchesWater(Aabb body) {
            return water;
        }
    }

    private static World flat() {
        var world = new World();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = -1; y <= 1; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(BlockId.STONE) : new Chunk());
        return world;
    }
}
