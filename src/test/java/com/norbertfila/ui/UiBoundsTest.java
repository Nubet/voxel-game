package com.norbertfila.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UiBoundsTest {
    @Test
    void usesHalfOpenEdgesAndRejectsNonFiniteCoordinates() {
        UiBounds bounds = new UiBounds(10, 20, 30, 40);
        assertTrue(bounds.contains(10, 20));
        assertTrue(bounds.contains(39.999, 59.999));
        assertFalse(bounds.contains(40, 20));
        assertFalse(bounds.contains(10, 60));
        assertFalse(bounds.contains(9.999, 20));
        assertFalse(bounds.contains(Double.NaN, 20));
        assertFalse(bounds.contains(10, Double.POSITIVE_INFINITY));
        assertTrue(new UiBounds(Integer.MAX_VALUE, 0, 10, 10).contains(Integer.MAX_VALUE + 1d, 1));
        assertThrows(IllegalArgumentException.class, () -> new UiBounds(0, 0, 0, 1));
    }
}
