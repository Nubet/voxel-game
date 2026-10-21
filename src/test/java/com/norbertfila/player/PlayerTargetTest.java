package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.DamageSource;
import com.norbertfila.combat.Health;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class PlayerTargetTest {
    @Test
    void survivalPlayersTakeDamageAndKnockback() {
        var player = new PlayerController();
        player.setPosition(new PlayerState.Position(0, 64, 0));
        var health = new Health(PlayerTarget.MAX_HEALTH);
        var target = new PlayerTarget(player, health);
        assertTrue(target.attackable());
        assertTrue(target.hurt(DamageSource.MOB_ATTACK, 3, 6, 0));
        assertEquals(17, health.value());
        assertEquals(6, player.state().velocityX(), 1e-9);
        assertTrue(player.state().velocityY() > 0);
        assertFalse(player.state().grounded());
    }

    @Test
    void creativeAndSpectatorPlayersAreNotTargets() {
        var world = new World();
        world.putChunk(new ChunkPos(0, 4, 0), new Chunk());
        var player = new PlayerController();
        player.setPosition(new PlayerState.Position(8, 64, 8));
        var health = new Health(PlayerTarget.MAX_HEALTH);
        var target = new PlayerTarget(player, health);
        assertTrue(player.setMode(world, PlayerController.Mode.CREATIVE));
        assertFalse(target.attackable());
        assertFalse(target.hurt(DamageSource.EXPLOSION, 10, 0, 0));
        assertEquals(20, health.value());
        assertTrue(player.setMode(world, PlayerController.Mode.SPECTATOR));
        player.knockback(5, 5, 5);
        assertEquals(0, player.state().velocityX());
    }
}
