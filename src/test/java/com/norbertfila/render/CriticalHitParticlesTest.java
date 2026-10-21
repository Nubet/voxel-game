package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.combat.CriticalHitEvent;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.world.Aabb;
import java.util.List;
import org.junit.jupiter.api.Test;

class CriticalHitParticlesTest {
    private static final float[] RIGHT = {1, 0, 0}, UP = {0, 1, 0};
    private static final long TICK = 600;
    private static final double HIT = TICK * FixedSimulation.TICK_SECONDS;
    private static final List<CriticalHitEvent> EVENTS =
            List.of(new CriticalHitEvent(TICK, new Aabb(-.3, 0, -.3, .3, 1.95, .3)));

    private static float[] at(double seconds) {
        return CriticalHitParticles.vertices(EVENTS, seconds, 0, 0, 0, RIGHT, UP);
    }

    private static int particles(float[] vertices) {
        return vertices.length / (6 * CriticalHitParticles.FLOATS_PER_VERTEX);
    }

    @Test
    void burstsInsideTheBodyAndSpreadsOutWithEachWave() {
        int first = particles(at(HIT));
        assertTrue(first > 4 && first <= CriticalHitParticles.ATTEMPTS_PER_WAVE, "first wave: " + first);
        assertTrue(particles(at(HIT + .11)) > first);
        var start = at(HIT);
        for (int vertex = 0; vertex < start.length; vertex += CriticalHitParticles.FLOATS_PER_VERTEX) {
            assertTrue(Math.abs(start[vertex]) < .4);
            assertTrue(start[vertex + 1] > .4 && start[vertex + 1] < 1.6);
        }
        assertArrayEquals(start, at(HIT));
    }

    @Test
    void particlesExpireAndIgnoreHitsFromTheFuture() {
        assertEquals(0, at(HIT - .01).length);
        assertEquals(0, at(HIT + .7).length);
        assertTrue(particles(at(HIT + .3)) < particles(at(HIT + .12)));
    }
}
