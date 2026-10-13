package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeldPlacementCooldownTest {
    @Test
    void waitsFourSimulationTicksBetweenSuccessfulPlacements() {
        HeldPlacementCooldown cooldown = new HeldPlacementCooldown();

        assertTrue(cooldown.ready());
        cooldown.placed();
        for (int tick = 1; tick < HeldPlacementCooldown.REPEAT_DELAY_TICKS; tick++) {
            cooldown.tick();
            assertFalse(cooldown.ready());
        }
        cooldown.tick();
        assertTrue(cooldown.ready());
    }
}
