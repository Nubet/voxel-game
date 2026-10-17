package com.norbertfila.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrameProfilerTest {
    @Test
    void storesFramesOnlyWhileRecording() {
        var profiler = new FrameProfiler(8, false);
        frame(profiler, 1_000_000);
        profiler.recording(true);
        frame(profiler, 2_000_000);
        frame(profiler, 4_000_000);

        assertEquals(2, profiler.frames());
        assertEquals(4.0, profiler.frameDistribution().maxMillis(), 1e-9);
        assertEquals(1, profiler.framesOver(3_000_000));
    }

    @Test
    void countsFramesBeyondCapacityAsDropped() {
        var profiler = new FrameProfiler(2, false);
        profiler.recording(true);
        for (int index = 0; index < 5; index++) frame(profiler, 1_000_000);

        assertEquals(2, profiler.frames());
        assertEquals(3, profiler.droppedFrames());
        assertEquals(5, profiler.totalFrames());
    }

    @Test
    void samplesTheWholeRunAndCountsEverySlowFrame() {
        var profiler = new FrameProfiler(256, false);
        profiler.recording(true);
        for (int i = 0; i < 10_000; i++) frame(profiler, i < 5_000 ? 1_000_000 : 40_000_000);
        assertEquals(5_000, profiler.framesOver(33_333_333));
        assertEquals(20.5, profiler.meanMillis());
        assertEquals(40, profiler.maxMillis());
        assertEquals(40, profiler.frameDistribution().p95Millis());
        profiler.reset();
        assertEquals(0, profiler.totalFrames());
        assertEquals(0, profiler.framesOver(33_333_333));
    }

    @Test
    void attributesElapsedTimeToMarkedSegments() {
        var profiler = new FrameProfiler(4, false);
        profiler.recording(true);
        profiler.beginFrame();
        spin(2_000_000);
        profiler.mark(FrameProfiler.Segment.RENDER);
        profiler.mark(FrameProfiler.Segment.SWAP);
        profiler.endFrame(3_000_000);

        assertTrue(profiler.segmentDistribution(FrameProfiler.Segment.RENDER).maxMillis() >= 2.0);
        assertTrue(profiler.segmentDistribution(FrameProfiler.Segment.SWAP).maxMillis() < 2.0);
        assertEquals(
                0.0, profiler.segmentDistribution(FrameProfiler.Segment.INPUT).maxMillis());
    }

    @Test
    void keepsGpuSamplesPerPass() {
        var profiler = new FrameProfiler(4, true);
        profiler.recordGpu(FrameProfiler.GpuPass.TERRAIN, 1_000_000);
        profiler.recording(true);
        profiler.recordGpu(FrameProfiler.GpuPass.TERRAIN, 3_000_000);

        assertEquals(1, profiler.gpuDistribution(FrameProfiler.GpuPass.TERRAIN).samples());
        assertEquals(0, profiler.gpuDistribution(FrameProfiler.GpuPass.UI).samples());
        assertTrue(profiler.gpuTiming());
    }

    @Test
    void disabledProfilerNeverRecords() {
        var profiler = FrameProfiler.disabled();
        profiler.recording(true);
        frame(profiler, 1_000_000);

        assertFalse(profiler.enabled());
        assertFalse(profiler.gpuTiming());
        assertEquals(0, profiler.frames());
    }

    @Test
    void distributionUsesNearestRankPercentiles() {
        long[] values = {5_000_000, 1_000_000, 2_000_000, 4_000_000, 3_000_000};
        var distribution = FrameProfiler.Distribution.of(values, values.length);

        assertEquals(3.0, distribution.p50Millis(), 1e-9);
        assertEquals(5.0, distribution.p95Millis(), 1e-9);
        assertEquals(3.0, distribution.meanMillis(), 1e-9);
    }

    private static void frame(FrameProfiler profiler, long nanos) {
        profiler.beginFrame();
        profiler.mark(FrameProfiler.Segment.INPUT);
        profiler.endFrame(nanos);
    }

    private static void spin(long nanos) {
        long until = System.nanoTime() + nanos;
        while (System.nanoTime() < until) Thread.onSpinWait();
    }
}
