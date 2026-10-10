package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import org.junit.jupiter.api.Test;

class PlayerControllerTest {
    private static final PlayerController.InputState FORWARD_AND_UP =
            new PlayerController.InputState(true, false, false, false, true, false, false);

    @Test
    void spectatorAllowsVerticalMovementAndInterpolatesPosition() {
        PlayerController player = new PlayerController();

        player.tick(FORWARD_AND_UP, 1.0 / 60, -90, 0);
        PlayerController.Position position = player.renderPosition(1);

        assertEquals(22.2, position.y(), 0.000001);
        assertEquals(33.8, position.z(), 0.000001);
    }

    @Test
    void survivalModeDoesNotUseSpectatorVerticalMovement() {
        PlayerController player = new PlayerController();
        player.setMode(PlayerController.Mode.SURVIVAL);

        player.tick(FORWARD_AND_UP, 1.0 / 60, -90, 0);

        assertEquals(22, player.renderPosition(1).y(), 0.000001);
        assertEquals(33.8, player.renderPosition(1).z(), 0.000001);
    }

    @Test
    void interpolationUsesPreviousAndCurrentSimulationStates() {
        PlayerController player = new PlayerController();

        player.tick(new PlayerController.InputState(true, false, false, false, false, false, false), 1.0 / 60, -90, 0);

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
        player.setPosition(new PlayerController.Position(.5, 1, .5));

        player.tick(
                world,
                new PlayerController.InputState(false, false, false, false, false, false, false),
                1.0 / 60,
                0,
                0);
        player.tick(
                world, new PlayerController.InputState(false, false, false, false, true, false, false), 1.0 / 60, 0, 0);

        assertTrue(player.renderPosition(1).y() > 1);
    }
}
