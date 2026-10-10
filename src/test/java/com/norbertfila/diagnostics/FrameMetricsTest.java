package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FrameMetricsTest {
    @Test
    void calculatesAverageFpsAndFrameTime() {
        FrameMetrics metrics = new FrameMetrics(81, 1000, 24000, false, 0.0);
        metrics.recordFrame(0.016);
        metrics.recordFrame(0.016);

        assertEquals(62.5, metrics.averageFps(), 0.0001);
        assertEquals(16.0, metrics.averageFrameTimeMillis(), 0.0001);
    }

    @Test
    void emptyWindowHasZeroAverages() {
        FrameMetrics metrics = new FrameMetrics(0, 0, 0, false, 0.0);

        assertEquals(0.0, metrics.averageFps());
        assertEquals(0.0, metrics.averageFrameTimeMillis());
    }
}
