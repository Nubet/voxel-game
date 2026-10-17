package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DurationHistogramTest {
    @Test
    void percentilesExcludeEarlierScenesAndBoundRounding() {
        var histogram = new DurationHistogram();
        histogram.record(1_000_000_000);
        var before = histogram.snapshot();
        for (int i = 1; i <= 100; i++) histogram.record(i * 1_000_000L);
        var after = histogram.snapshot();
        for (double q : new double[] {.5, .95, .99}) {
            double expected = Math.ceil(100 * q);
            double actual = after.percentileMillisSince(before, q);
            assertTrue(actual >= expected && actual <= expected * 1.0625);
        }
        assertEquals(0, after.percentileMillisSince(after, .99));
        assertThrows(IllegalArgumentException.class, () -> histogram.record(-1));
    }
}
