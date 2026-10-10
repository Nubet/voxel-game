package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
