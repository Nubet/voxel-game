package com.norbertfila.mob.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.DamageSource;
import com.norbertfila.mob.MobAction;
import com.norbertfila.mob.MobType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import org.junit.jupiter.api.Test;

class ZombieBehaviorTest {
    private final AiTestWorld test = new AiTestWorld();

    @Test
    void zombiesChaseAndHitPlayersOnACooldown() {
        var zombie = test.spawn(MobType.ZOMBIE, 8.5, 8.5);
        var player = new FakeTarget(8.5, 0, 18.5);
        test.targets.add(player);
        int firstHit = -1;
        for (int tick = 0; tick < 900 && player.hits.size() < 3; tick++) {
            test.run(1);
            if (firstHit < 0 && !player.hits.isEmpty()) firstHit = tick;
        }
        assertTrue(firstHit > 0, "the zombie never reached the player");
        assertEquals(3, player.hits.size());
        var hit = player.hits.getFirst();
        assertEquals(DamageSource.MOB_ATTACK, hit.source());
        assertEquals(3, hit.amount());
        assertTrue(hit.knockbackZ() > 0, "knockback pushes away from the zombie");
        assertEquals(MobAction.ATTACKING, zombie.action());
        assertTrue(Math.hypot(zombie.x() - player.x, zombie.z() - player.z) < 1.6);
    }

    @Test
    void attacksAreSpacedByTheCooldown() {
        test.spawn(MobType.ZOMBIE, 8.5, 8.5);
        var player = new FakeTarget(8.5, 0, 10.5);
        test.targets.add(player);
        test.run(400);
        int hits = player.hits.size();
        assertTrue(hits >= 4 && hits <= 7, "hits in 400 ticks: " + hits);
    }

    @Test
    void zombiesPathAroundWallsToAPlayerTheyAlreadyTargeted() {
        var zombie = test.spawn(MobType.ZOMBIE, 8.5, 8.5);
        var player = new FakeTarget(8.5, 0, 16.5);
        test.targets.add(player);
        test.run(120);
        for (int x = 4; x <= 13; x++)
            for (int y = 0; y < 3; y++) test.world.setBlock(new BlockPos(x, y, 13), BlockId.STONE, 64);
        zombie.stop();
        for (int tick = 0; tick < 900 && player.hits.isEmpty(); tick++) test.run(1);
        assertFalse(player.hits.isEmpty(), "the zombie did not walk around the wall");
    }

    @Test
    void creativePlayersAreIgnored() {
        test.spawn(MobType.ZOMBIE, 8.5, 8.5);
        var player = new FakeTarget(8.5, 0, 10.5);
        player.attackable = false;
        test.targets.add(player);
        test.run(400);
        assertTrue(player.hits.isEmpty());
    }
}
