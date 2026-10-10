package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GameLoopTest {
    @Test
    void clampsDeltaToSafeRange() {
        assertEquals(0.0, GameLoop.clampDelta(-1.0));
        assertEquals(0.016, GameLoop.clampDelta(0.016));
        assertEquals(0.1, GameLoop.clampDelta(2.0));
    }
}
