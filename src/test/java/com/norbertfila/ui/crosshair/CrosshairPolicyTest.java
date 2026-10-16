package com.norbertfila.ui.crosshair;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.interaction.BlockTargeting;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class CrosshairPolicyTest {
    @Test
    void usesTheSameReachAndModeRulesAsInteractions() {
        var world = new World();
        world.putChunk(new ChunkPos(0, 0, 0), new Chunk());
        world.getChunk(new ChunkPos(0, 0, 0)).set(6, 1, 1, BlockId.STONE);
        var targeting = new BlockTargeting(world);
        var settings = CrosshairSettings.DEFAULT;
        assertEquals(
                CrosshairStyle.BLOCK,
                CrosshairPolicy.select(settings, targeting.find(Mode.SURVIVAL, 1.5, 1.5, 1.5, 1, 0, 0)));
        var distant = targeting.find(Mode.SURVIVAL, 1.49, 1.5, 1.5, 1, 0, 0);
        assertEquals(CrosshairStyle.HIDDEN, CrosshairPolicy.select(settings, distant));
        assertEquals(
                CrosshairStyle.BLOCK,
                CrosshairPolicy.select(settings, targeting.find(Mode.CREATIVE, 0, 1.5, 1.5, 1, 0, 0)));
        assertEquals(
                CrosshairStyle.HIDDEN,
                CrosshairPolicy.select(settings, targeting.find(Mode.SPECTATOR, 1.5, 1.5, 1.5, 1, 0, 0)));
        assertEquals(
                CrosshairStyle.DEFAULT,
                CrosshairPolicy.select(new CrosshairSettings(false, true, true, 1, 100), distant));
        assertEquals(
                CrosshairStyle.DEFAULT,
                CrosshairPolicy.select(
                        new CrosshairSettings(false, true, true, 1, 100),
                        targeting.find(Mode.SURVIVAL, 1.5, 1.5, 1.5, 1, 0, 0)));
        assertEquals(
                CrosshairStyle.DEFAULT,
                CrosshairPolicy.select(new CrosshairSettings(true, false, true, 1, 100), distant));
    }

    @Test
    void validatesConfigurationAtItsBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new CrosshairSettings(true, true, true, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> new CrosshairSettings(true, true, true, 5, 100));
        assertThrows(IllegalArgumentException.class, () -> new CrosshairSettings(true, true, true, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new CrosshairSettings(true, true, true, 1, 101));
    }
}
