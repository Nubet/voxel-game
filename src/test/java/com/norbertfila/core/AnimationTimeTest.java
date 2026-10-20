package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.simulation.FixedSimulation;
import org.junit.jupiter.api.Test;

class AnimationTimeTest {
    @Test
    void interpolatesWorldTimeAndFreezesWhilePaused() {
        double tickTime = 120 * FixedSimulation.TICK_SECONDS;
        assertEquals(tickTime + .01, GameLoop.animationSeconds(120, 1_000_000_000, false, 1_010_000_000, 60), 1e-9);
        assertEquals(tickTime, GameLoop.animationSeconds(120, 1_000_000_000, true, 2_000_000_000, 60));
        assertEquals(
                tickTime + FixedSimulation.TICK_SECONDS,
                GameLoop.animationSeconds(120, 1_000_000_000, false, 2_000_000_000, 60));
        assertEquals(tickTime, GameLoop.animationSeconds(120, 1_000_000_000, false, 999_000_000, 60));
    }

    @Test
    void animationInterpolationUsesTheConfiguredTickRate() {
        double tickTime = 120 * FixedSimulation.TICK_SECONDS;
        for (double rate : new double[] {1, 20, 60, 120, 10000}) {
            long halfTickLater = 1_000_000_000L + Math.round(.5e9 / rate);
            assertEquals(
                    tickTime + FixedSimulation.TICK_SECONDS * .5,
                    GameLoop.animationSeconds(120, 1_000_000_000, false, halfTickLater, rate),
                    1e-8);
        }
    }
}
