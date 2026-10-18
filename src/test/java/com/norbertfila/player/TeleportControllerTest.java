package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class TeleportControllerTest {
    @Test
    void waitsForDestinationAndPreservesModeOrRejectsBlockedTarget() {
        var teleports = new TeleportController();
        var player = new PlayerController();
        var world = new World();
        var before = player.state().position();
        var destination = new PlayerState.Position(1024.5, 80, -200.5);
        assertTrue(teleports.request(destination, 0));
        assertFalse(teleports.request(destination, 0));
        assertTrue(teleports.update(world, player, ignored -> false, 1).isEmpty());
        assertEquals(before, player.state().position());
        for (var pos : teleports.requiredChunks()) world.putChunk(pos, new Chunk());
        var mode = player.mode();
        assertFalse(teleports
                .update(world, player, world::hasChunk, 2)
                .orElseThrow()
                .error());
        assertEquals(destination, player.state().position());
        assertEquals(mode, player.mode());
        assertEquals(0, player.state().velocityY());
        assertTrue(teleports.request(new PlayerState.Position(10, 40, 10), 3));
        assertTrue(teleports
                .update(world, player, ignored -> true, 4)
                .orElseThrow()
                .error());
        assertEquals(destination, player.state().position());
        assertFalse(teleports.request(new PlayerState.Position(0, 255, 0), 0));
        assertTrue(teleports.request(destination, 0));
        assertTrue(teleports
                .update(world, player, ignored -> false, 15_000_000_000L)
                .orElseThrow()
                .error());
    }
}
