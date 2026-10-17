package com.norbertfila.player.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.player.*;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class PlayerRenderStateTest {
    private static final WaterContact.Result DRY = new WaterContact.Result(0, false, true);

    @Test
    void interpolatesDoubleFeetAndShortestLookWithoutRetainingTheController() {
        var player = new PlayerController();
        player.restore(
                new PlayerState.Position(-1_000_000_000.25, 20, -1_000_000_000.75),
                PlayerController.Mode.SPECTATOR,
                false);
        var extractor = new PlayerRenderStateExtractor();
        extractor.extract(player, 179, 10, DRY, false);
        player.tick(new PlayerInput(true, false, false, false, false, false, true), .05, 0, 0);
        var snapshot = extractor.extract(player, -179, 20, DRY, false);
        var middle = snapshot.interpolate(.5);
        assertEquals(-1_000_000_000.25 + 1.125, middle.feet().x());
        assertEquals(180, middle.yaw());
        assertEquals(15, middle.pitch());
        assertTrue(snapshot.sprinting());
        assertEquals(45, snapshot.velocityX());
        player.setPosition(new PlayerState.Position(0, 0, 0));
        assertEquals(-1_000_000_000.25 + 2.25, snapshot.current().feet().x());
        assertThrows(IllegalArgumentException.class, () -> snapshot.interpolate(Double.NaN));
    }

    @Test
    void crouchChangesEyeHeightWithoutMovingTheFeet() {
        var world = new World();
        var chunk = new Chunk();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) chunk.set(x, 0, z, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        var player = new PlayerController();
        player.setPosition(new PlayerState.Position(8, 1, 8));
        var extractor = new PlayerRenderStateExtractor();
        extractor.extract(player, -90, 0, DRY, false);
        player.tick(world, new PlayerInput(false, false, false, false, false, true, true), .05, -90, 0);
        var snapshot = extractor.extract(player, -90, 0, DRY, false);
        assertTrue(snapshot.crouching());
        assertTrue(snapshot.grounded());
        assertFalse(snapshot.sprinting());
        assertEquals(1, snapshot.interpolate(.5).feet().y());
        assertEquals(
                1 + (PlayerState.EYE_HEIGHT + PlayerState.CROUCH_EYE_HEIGHT) / 2,
                snapshot.interpolate(.5).eye().y(),
                1e-12);
    }

    @Test
    void teleportPauseBlockedAndStallResetAllInterpolationHistory() {
        var player = new PlayerController();
        var extractor = new PlayerRenderStateExtractor();
        var initial = extractor.extract(player, 0, 0, DRY, false);
        assertEquals(initial.previous(), initial.current());
        player.restore(new PlayerState.Position(-500, 40, -800), PlayerController.Mode.SPECTATOR, false);
        var teleported = extractor.extract(player, 90, -20, DRY, false);
        assertEquals(teleported.previous(), teleported.current());
        for (int i = 0; i < 3; i++) {
            player.pauseInterpolation();
            var reset = extractor.extract(player, 120 + i, 30, new WaterContact.Result(.8, true, true), true);
            assertEquals(reset.previous(), reset.current());
            assertTrue(reset.frozen());
            assertTrue(reset.eyesSubmerged());
            assertEquals(.8, reset.submergedFraction());
        }
        player.tick(PlayerInput.NONE, .05, 0, 0);
        var resumed = extractor.extract(player, 125, 30, DRY, false);
        assertEquals(122, resumed.previous().yaw());
        assertEquals(123.5, resumed.interpolate(.5).yaw());
    }
}
