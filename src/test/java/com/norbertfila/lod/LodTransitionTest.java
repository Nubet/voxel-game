package com.norbertfila.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LodTransitionTest {
    @Test
    void hysteresisPreventsBoundaryFlapping() {
        LodLevel near = new LodLevel(0, 8, 4);
        LodLevel far = new LodLevel(9, 16, 8);

        assertEquals(near, LodTransition.selectLevel(java.util.List.of(near, far), near, 81, 1));
        assertEquals(far, LodTransition.selectLevel(java.util.List.of(near, far), near, 100, 1));
        assertEquals(far, LodTransition.selectLevel(java.util.List.of(near, far), far, 64, 1));
        assertEquals(near, LodTransition.selectLevel(java.util.List.of(near, far), far, 49, 1));
    }
}
