package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class PlayerControllerTest {
    private static final PlayerInput FORWARD_AND_UP = new PlayerInput(true, false, false, false, true, false, false);

    @Test
    void spectatorAllowsVerticalMovementAndInterpolatesPosition() {
        PlayerController player = new PlayerController();

        player.tick(FORWARD_AND_UP, 1.0 / 60, -90, 0);
        PlayerState.Position position = player.renderPosition(1);

        assertEquals(22.2, position.y(), 0.000001);
        assertEquals(33.8, position.z(), 0.000001);
    }

    @Test
    void survivalRequiresWorldInsteadOfSilentlyBypassingPhysics() {
        PlayerController player = new PlayerController();
        player.setMode(PlayerController.Mode.SURVIVAL);

        assertThrows(NullPointerException.class, () -> player.tick(FORWARD_AND_UP, 1.0 / 60, -90, 0));
        assertEquals(new PlayerState.Position(0, 22, 34), player.state().position());
    }

    @Test
    void interpolationUsesPreviousAndCurrentSimulationStates() {
        PlayerController player = new PlayerController();

        player.tick(new PlayerInput(true, false, false, false, false, false, false), 1.0 / 60, -90, 0);

        assertEquals(34, player.renderPosition(0).z(), 0.000001);
        assertEquals(33.8, player.renderPosition(1).z(), 0.000001);
    }

    @Test
    void survivalJumpStartsAfterPlayerHasLanded() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(0, 0, 0, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        PlayerController player = new PlayerController();
        player.setMode(PlayerController.Mode.SURVIVAL);
        player.setPosition(new PlayerState.Position(.5, 1, .5));

        player.tick(world, PlayerInput.NONE, 1.0 / 60, 0, 0);
        player.tick(
                world, new PlayerInput(false, false, false, false, true, false, false, true, 1.0 / 60), 1.0 / 60, 0, 0);

        assertTrue(player.renderPosition(1).y() > 1);
    }

    @Test
    void modeChangePreservesFeetAndEyesAndResetsMotionAndInterpolation() {
        PlayerController player = new PlayerController();
        player.tick(FORWARD_AND_UP, 1.0 / 60, -90, 0);
        PlayerState.Position feet = player.state().position();
        PlayerState.Position eyes = player.renderEyePosition(1);
        assertEquals(12, player.state().velocityY(), 1e-6);

        player.setMode(PlayerController.Mode.SURVIVAL);

        assertEquals(feet, player.renderPosition(0));
        assertEquals(feet, player.renderPosition(1));
        assertEquals(eyes, player.renderEyePosition(.5));
        assertEquals(PlayerState.at(feet), player.state());
        assertEquals(PlayerState.EYE_HEIGHT, eyes.y() - feet.y(), 1e-6);
        player.setMode(PlayerController.Mode.SPECTATOR);
        assertEquals(eyes, player.renderEyePosition(1));
    }

    @Test
    void teleportResetsVelocityAndDoesNotInterpolateFromOldPosition() {
        PlayerController player = new PlayerController();
        player.tick(FORWARD_AND_UP, 1.0 / 60, 0, 0);
        PlayerState.Position target = new PlayerState.Position(-16.5, 30, 16.5);

        player.setPosition(target);

        assertEquals(PlayerState.at(target), player.state());
        assertEquals(target, player.renderPosition(.25));
        assertThrows(IllegalArgumentException.class, () -> new PlayerState.Position(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> player.tick(PlayerInput.NONE, 1.0 / 60, Double.NaN, 0));
    }
}
