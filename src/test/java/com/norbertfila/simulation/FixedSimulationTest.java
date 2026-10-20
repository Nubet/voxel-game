package com.norbertfila.simulation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
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
        assertEquals(60, atThirty.tickCount());
        assertEquals(60, atOneTwenty.tickCount());
    }

    @Test
    void capsCatchUpAfterALagSpike() {
        FixedSimulation simulation = new FixedSimulation(1.0 / 60, 5);

        assertEquals(5, simulation.advance(1, ignored -> {}));
        assertEquals(5, simulation.tickCount());
    }

    @Test
    void clockAdvancesBeforeEachCallbackEvenWhenPlayerUpdateIsSkipped() {
        FixedSimulation simulation = new FixedSimulation(FixedSimulation.TICK_SECONDS, 5);
        List<Long> observed = new ArrayList<>();
        assertEquals(0, simulation.tickCount());
        simulation.advance(2 * FixedSimulation.TICK_SECONDS, dt -> {
            assertEquals(FixedSimulation.TICK_SECONDS, dt);
            observed.add(simulation.tickCount());
        });
        assertEquals(List.of(1L, 2L), observed);
        assertEquals(0, simulation.advance(0, ignored -> fail("No elapsed time")));
        assertEquals(2, simulation.tickCount());
    }

    @Test
    void invalidFrameDoesNotAdvanceClockOrConsumeAccumulatedTime() {
        FixedSimulation simulation = new FixedSimulation(.25, 5);
        simulation.advance(.125, ignored -> fail());
        assertThrows(IllegalArgumentException.class, () -> simulation.advance(Double.NaN, ignored -> {}));
        assertThrows(IllegalArgumentException.class, () -> simulation.advance(-1, ignored -> {}));
        assertThrows(NullPointerException.class, () -> simulation.advance(.25, null));
        assertEquals(0, simulation.tickCount());
        assertEquals(.5, simulation.interpolationAlpha());
        assertEquals(1, simulation.advance(.125, ignored -> {}));
        assertEquals(1, simulation.tickCount());
    }

    @Test
    void failedCallbackDoesNotReplayTheSameWorldTick() {
        FixedSimulation simulation = new FixedSimulation(.25, 5);
        assertThrows(
                IllegalStateException.class,
                () -> simulation.advance(.25, ignored -> {
                    throw new IllegalStateException("update failed");
                }));
        assertEquals(1, simulation.tickCount());
        assertEquals(0, simulation.advance(0, ignored -> fail()));
        simulation.advance(.25, ignored -> assertEquals(2, simulation.tickCount()));
    }

    @Test
    void tickRateChangesSchedulingButNotThePhysicsStep() {
        for (double rate : new double[] {1, 20, 60, 120.5, 10000}) {
            var simulation = new FixedSimulation(FixedSimulation.TICK_SECONDS, 5);
            simulation.setTickRate(rate);
            int ticks = 0;
            for (int frame = 0; frame < 100; frame++)
                ticks += simulation.advance(1.0 / rate, dt -> assertEquals(FixedSimulation.TICK_SECONDS, dt));
            assertEquals(100, ticks);
            assertEquals(rate, simulation.tickRate(), 1e-9);
            assertEquals(5, simulation.advance(10, ignored -> {}));
        }
    }

    @Test
    void changingRatePreservesFractionalProgressAndRejectsInvalidRates() {
        var simulation = new FixedSimulation(FixedSimulation.TICK_SECONDS, 5);
        simulation.advance(FixedSimulation.TICK_SECONDS * .5, ignored -> fail());
        simulation.setTickRate(1);
        assertEquals(.5, simulation.interpolationAlpha());
        assertEquals(0, simulation.advance(.49, ignored -> fail()));
        assertEquals(1, simulation.advance(.02, ignored -> {}));
        for (double rate :
                new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, .99, 10001}) {
            double progress = simulation.interpolationAlpha();
            assertThrows(IllegalArgumentException.class, () -> simulation.setTickRate(rate));
            assertEquals(1, simulation.tickRate());
            assertEquals(progress, simulation.interpolationAlpha());
        }
    }
}
