package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.simulation.FixedSimulation;
import org.junit.jupiter.api.Test;

class AnimationTimeTest {
    @Test
    void interpolatesWorldTimeAndFreezesWhilePaused() {
        double tickTime = 120 * FixedSimulation.TICK_SECONDS;
        assertEquals(tickTime + .01, GameLoop.animationSeconds(120, 1_000_000_000, false, 1_010_000_000), 1e-9);
        assertEquals(tickTime, GameLoop.animationSeconds(120, 1_000_000_000, true, 2_000_000_000));
        assertEquals(
                tickTime + FixedSimulation.TICK_SECONDS,
                GameLoop.animationSeconds(120, 1_000_000_000, false, 2_000_000_000));
        assertEquals(tickTime, GameLoop.animationSeconds(120, 1_000_000_000, false, 999_000_000));
    }
}
