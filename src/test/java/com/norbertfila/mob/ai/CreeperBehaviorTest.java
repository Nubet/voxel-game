package com.norbertfila.mob.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.DamageSource;
import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.MobType;
import com.norbertfila.mob.hazard.MobHazards;
import org.junit.jupiter.api.Test;

class CreeperBehaviorTest {
    private final AiTestWorld test = new AiTestWorld();
    private final MobHazards hazards = new MobHazards();

    @Test
    void creepersSneakUpSwellAndExplode() {
        var creeper = test.spawn(MobType.CREEPER, 8.5, 8.5);
        var player = new FakeTarget(8.5, 0, 16.5);
        test.targets.add(player);
        int charging = -1;
        for (int tick = 0; tick < 900 && player.hits.isEmpty(); tick++) {
            run(1);
            if (charging < 0 && creeper.action() == MobAction.CHARGING) charging = tick;
        }
        assertTrue(charging > 0, "the creeper never lit its fuse");
        assertFalse(player.hits.isEmpty(), "the creeper never exploded");
        assertEquals(DamageSource.EXPLOSION, player.hits.getFirst().source());
        assertTrue(
                player.hits.getFirst().amount() > 10,
                "damage " + player.hits.getFirst().amount() + " at "
                        + Math.hypot(creeper.x() - player.x, creeper.z() - player.z));
        assertEquals(0, test.population.size(), "the creeper survived its own blast");
        assertFalse(test.destroyed.isEmpty(), "the blast broke no blocks");
    }

    @Test
    void escapingBeyondSevenBlocksDefusesTheCreeper() {
        var creeper = test.spawn(MobType.CREEPER, 8.5, 8.5);
        var player = new FakeTarget(8.5, 0, 10.5);
        test.targets.add(player);
        for (int tick = 0; tick < 300 && creeper.action() != MobAction.CHARGING; tick++) run(1);
        assertEquals(MobAction.CHARGING, creeper.action());
        run(30);
        float progress = creeper.actionProgress();
        assertTrue(progress > 0 && progress < 1);
        player.z = 8.5 + 9;
        player.x = 8.5;
        run(1);
        float after = creeper.actionProgress();
        run(5);
        assertTrue(creeper.actionProgress() < after || creeper.action() == MobAction.NONE, "the fuse kept burning");
        assertTrue(player.hits.isEmpty());
    }

    private void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            test.run(1);
            hazards.tick(
                    test.population,
                    new com.norbertfila.mob.hazard.HazardEnvironment() {
                        public boolean daylight() {
                            return false;
                        }

                        public boolean seesSky(com.norbertfila.world.BlockPos position) {
                            return false;
                        }

                        public boolean rainingAt(com.norbertfila.world.BlockPos position) {
                            return false;
                        }

                        public boolean touchesWater(com.norbertfila.world.Aabb body) {
                            return false;
                        }
                    },
                    test.random);
        }
    }
}
