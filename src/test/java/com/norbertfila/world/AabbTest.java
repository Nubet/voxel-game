package com.norbertfila.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AabbTest {
    @Test
    void sweptVolumeIncludesBothEndpointsForMixedSignedMovement() {
        Aabb body = new Aabb(-1, 2, 3, 0, 4, 4);
        assertEquals(new Aabb(-6, 2, 3, 0, 11, 6), body.swept(-5, 7, 2));
        assertEquals(new Aabb(-6, 9, 5, -5, 11, 6), body.move(-5, 7, 2));
        assertEquals(new Aabb(-1, 1.4, 3, 0, 4.6, 4), body.expandVertical(.6, .6));
    }

    @Test
    void faceContactIsNotIntersection() {
        Aabb box = new Aabb(0, 0, 0, 1, 1, 1);
        assertFalse(box.intersects(box.move(1, 0, 0)));
        assertTrue(box.intersects(box.move(.99, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> box.swept(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> box.expandVertical(-1, 0));
    }
}
