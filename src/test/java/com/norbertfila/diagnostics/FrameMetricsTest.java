package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void calculatesPercentilesAndOnePercentLowFromKnownSamples() {
        FrameMetrics metrics = new FrameMetrics(0, 0, 0, false, 0.0, 5);
        metrics.recordFrame(0.010);
        metrics.recordFrame(0.020);
        metrics.recordFrame(0.030);
        metrics.recordFrame(0.040);
        metrics.recordFrame(0.050);

        assertEquals(30.0, metrics.p50FrameMillis());
        assertEquals(50.0, metrics.p95FrameMillis());
        assertEquals(50.0, metrics.p99FrameMillis());
        assertEquals(20.0, metrics.onePercentLowFps());
    }

    @Test
    void reportContainsConfigurationAndWorkerMetrics() {
        FrameMetrics metrics = new FrameMetrics(0, 0, 0, false, 0.0, 4);
        metrics.recordFrame(0.016);
        metrics.recordWorker(2_000_000, 3);

        PerformanceReport report = metrics.snapshot(1920, 1080, "test hardware");

        assertEquals(1920, report.width());
        assertEquals(1080, report.height());
        assertEquals(2.0, report.averageWorkerMillis());
        assertTrue(report.toJson().contains("test hardware"));
        assertTrue(report.csvRow().contains("1920"));
    }
}
