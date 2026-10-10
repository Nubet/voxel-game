package com.norbertfila.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FixedSimulationTest {
    @Test
    void producesTheSameTickCountAtThirtyAndOneHundredTwentyFps() {
        FixedSimulation atThirty = new FixedSimulation(1.0 / 60, 5);
        FixedSimulation atOneTwenty = new FixedSimulation(1.0 / 60, 5);
        int thirtyTicks = 0;
        int oneTwentyTicks = 0;
        for (int frame = 0; frame < 30; frame++) thirtyTicks += atThirty.advance(1.0 / 30, ignored -> {});
        for (int frame = 0; frame < 120; frame++) oneTwentyTicks += atOneTwenty.advance(1.0 / 120, ignored -> {});

        assertEquals(60, thirtyTicks);
        assertEquals(60, oneTwentyTicks);
    }

    @Test
    void capsCatchUpAfterALagSpike() {
        FixedSimulation simulation = new FixedSimulation(1.0 / 60, 5);

        assertEquals(5, simulation.advance(1, ignored -> {}));
    }
}
