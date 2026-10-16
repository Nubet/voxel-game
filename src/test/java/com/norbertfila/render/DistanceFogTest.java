package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DistanceFogTest {
    @Test
    void followsEffectiveRangeWithoutHidingLandscapeAtTheDetailBoundary() {
        var landscape = DistanceFog.forDistances(4, 32);
        assertEquals(512, landscape.end());
        assertTrue(landscape.start() > 4 * 16);
        assertEquals(64, DistanceFog.forDistances(4, 0).end());
        assertEquals(DistanceFog.forDistances(8, 0), DistanceFog.forDistances(8, 4));
        assertEquals(128, DistanceFog.forDistances(8, 8).end());
        assertEquals(2048, DistanceFog.forDistances(32, 128).end());
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> DistanceFog.forDistances(0, 32));
        assertThrows(IllegalArgumentException.class, () -> DistanceFog.forDistances(4, -1));
        assertThrows(IllegalArgumentException.class, () -> new DistanceFog(Float.NaN, 64));
        assertThrows(IllegalArgumentException.class, () -> new DistanceFog(32, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new DistanceFog(-1, 64));
        assertThrows(IllegalArgumentException.class, () -> new DistanceFog(64, 64));
    }
}
