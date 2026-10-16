package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CloudMotionTest {
    @Test
    void wrapsPositiveNegativeAndLargeCoordinates() {
        assertEquals(1023.75f, CloudRenderer.phase(-.25, 1024));
        assertEquals(.25f, CloudRenderer.phase(1024.25, 1024));
        assertEquals(1023.75f, CloudRenderer.phase(-1024.0 * 1_000_000 - .25, 1024));
    }

    @Test
    void motionIsContinuousAcrossItsPeriod() {
        double period = 1024, seconds = period / CloudRenderer.SPEED;
        assertEquals(
                CloudRenderer.movingPhase(15.25, 0, period), CloudRenderer.movingPhase(15.25, seconds, period), .0001);
        assertEquals(
                CloudRenderer.movingPhase(15.25, 10, period),
                CloudRenderer.movingPhase(15.25 + period * 1_000_000, seconds * 1_000_000 + 10, period),
                .001);
        assertEquals(.06, CloudRenderer.movingPhase(0, seconds - .1, period), .001);
        assertEquals(1023.94, CloudRenderer.movingPhase(0, seconds + .1, period), .001);
    }
}
