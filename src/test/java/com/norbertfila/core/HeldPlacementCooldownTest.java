package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HeldPlacementCooldownTest {
    @Test
    void waitsTwelveSimulationTicksBetweenPlacementAttempts() {
        HeldPlacementCooldown cooldown = new HeldPlacementCooldown();

        assertTrue(cooldown.ready());
        cooldown.attempted();
        for (int tick = 1; tick < HeldPlacementCooldown.REPEAT_DELAY_TICKS; tick++) {
            cooldown.tick();
            assertFalse(cooldown.ready());
        }
        cooldown.tick();
        assertTrue(cooldown.ready());

        cooldown.attempted();
        assertFalse(cooldown.ready());
    }
}
