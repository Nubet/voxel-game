package com.norbertfila.diagnostics;

import java.util.Arrays;

/**
 * Render-thread frame profiler with preallocated sample storage. Recording never allocates; when the capacity is
 * exhausted further frames are counted but not stored.
 */
public final class FrameProfiler {
    public enum Segment {
        INPUT,
        RECONCILE,
        UPLOAD,
        RENDER,
        UI,
        SWAP
    }

    /** GPU passes in submission order; terrain passes contain only opaque and cutout geometry. */
    public enum GpuPass {
        FAR_TERRAIN,
        TERRAIN,
        CLOUDS,
        TRANSLUCENT_AND_EFFECTS,
        UI
    }

    private static final int SEGMENTS = Segment.values().length;
    private static final int GPU_PASSES = GpuPass.values().length;
    private static final FrameProfiler DISABLED = new FrameProfiler(0, false);

    private final int capacity;
    private final boolean gpuTiming;
    private final long[] frameNanos;
    private final long[][] segmentNanos;
    private final long[][] gpuNanos;
    private final int[] gpuSamples = new int[GPU_PASSES];
    private final long[] current = new long[SEGMENTS];
    private int frames;
    private long droppedFrames;
    private long markedAt;
    private long overheadNanos;
    private boolean recording;

    public FrameProfiler(int capacity, boolean gpuTiming) {
        if (capacity < 0) throw new IllegalArgumentException("Capacity must not be negative");
        this.capacity = capacity;
        this.gpuTiming = gpuTiming && capacity > 0;
        frameNanos = new long[capacity];
        segmentNanos = new long[SEGMENTS][capacity];
        gpuNanos = new long[GPU_PASSES][capacity];
    }

    public static FrameProfiler disabled() {
        return DISABLED;
    }

    public boolean enabled() {
        return capacity > 0;
    }

    public boolean gpuTiming() {
        return gpuTiming;
    }

    /** Starts or stops storing samples; segment timing still runs so that overhead stays constant. */
    public void recording(boolean value) {
        recording = value && enabled();
    }

    public boolean recording() {
        return recording;
    }

    public void reset() {
        frames = 0;
        droppedFrames = 0;
        overheadNanos = 0;
        Arrays.fill(gpuSamples, 0);
    }

    public void beginFrame() {
        if (!enabled()) return;
        Arrays.fill(current, 0);
        markedAt = System.nanoTime();
    }

    /** Attributes the time since the previous mark to {@code segment}. */
    public void mark(Segment segment) {
        if (!enabled()) return;
        long now = System.nanoTime();
        current[segment.ordinal()] += now - markedAt;
        markedAt = now;
    }

    public void endFrame(long frameDurationNanos) {
        if (!recording) return;
        long started = System.nanoTime();
        if (frames == capacity) droppedFrames++;
        else {
            frameNanos[frames] = frameDurationNanos;
            for (int segment = 0; segment < SEGMENTS; segment++) segmentNanos[segment][frames] = current[segment];
            frames++;
        }
        overheadNanos += System.nanoTime() - started;
    }

    public void recordGpu(GpuPass pass, long nanos) {
        if (!recording) return;
        int index = gpuSamples[pass.ordinal()];
        if (index < capacity) {
            gpuNanos[pass.ordinal()][index] = nanos;
            gpuSamples[pass.ordinal()] = index + 1;
        }
    }

    public int frames() {
        return frames;
    }

    public long droppedFrames() {
        return droppedFrames;
    }

    public long overheadNanos() {
        return overheadNanos;
    }

    public Distribution frameDistribution() {
        return Distribution.of(frameNanos, frames);
    }

    public Distribution segmentDistribution(Segment segment) {
        return Distribution.of(segmentNanos[segment.ordinal()], frames);
    }

    public Distribution gpuDistribution(GpuPass pass) {
        return Distribution.of(gpuNanos[pass.ordinal()], gpuSamples[pass.ordinal()]);
    }

    public int framesOver(long thresholdNanos) {
        int count = 0;
        for (int index = 0; index < frames; index++) if (frameNanos[index] > thresholdNanos) count++;
        return count;
    }

    public record Distribution(
            int samples, double meanMillis, double p50Millis, double p95Millis, double p99Millis, double maxMillis) {
        public static Distribution of(long[] values, int count) {
            if (count == 0) return new Distribution(0, 0, 0, 0, 0, 0);
            long[] sorted = Arrays.copyOf(values, count);
            Arrays.sort(sorted);
            double sum = 0;
            for (long value : sorted) sum += value;
            return new Distribution(
                    count,
                    sum / count / 1e6,
                    percentile(sorted, .50),
                    percentile(sorted, .95),
                    percentile(sorted, .99),
                    sorted[count - 1] / 1e6);
        }

        private static double percentile(long[] sorted, double quantile) {
            int index = Math.min(sorted.length - 1, Math.max(0, (int) Math.ceil(quantile * sorted.length) - 1));
            return sorted[index] / 1e6;
        }
    }
}
