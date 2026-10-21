package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.mob.MobDeathEvent;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.Aabb;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoofParticlesTest {
    private static final float[] RIGHT = {1, 0, 0}, UP = {0, 1, 0};
    private static final long TICK = 600;
    private static final double REMOVED = TICK * FixedSimulation.TICK_SECONDS;
    private static final List<MobDeathEvent> EVENTS =
            List.of(new MobDeathEvent(TICK, new Aabb(-.3, 0, -.3, .3, 1.95, .3)));

    private static int particles(double seconds) {
        return Arrays.stream(PoofParticles.vertices(EVENTS, seconds, 0, 0, 0, RIGHT, UP))
                .mapToInt(frame -> frame.length / (6 * PoofParticles.FLOATS_PER_VERTEX))
                .sum();
    }

    @Test
    void twentyPuffsStartLargeAndFadeOutWithinTheirLifetime() {
        assertEquals(PoofParticles.PARTICLES, particles(REMOVED));
        var start = PoofParticles.vertices(EVENTS, REMOVED, 0, 0, 0, RIGHT, UP);
        assertEquals(PoofParticles.PARTICLES * 6 * PoofParticles.FLOATS_PER_VERTEX, start[0].length);
        assertTrue(particles(REMOVED + 2) < PoofParticles.PARTICLES);
        assertEquals(0, particles(REMOVED + PoofParticles.MAX_LIFETIME));
        assertEquals(0, particles(REMOVED - .01));
    }
}
