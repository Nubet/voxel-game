package com.norbertfila.diagnostics;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Render-thread profiler. Reservoir sampling keeps bounded storage representative of the whole run.
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
    private final long[] gpuSeen = new long[GPU_PASSES];
    private SplittableRandom random = new SplittableRandom(0);
    private long totalFrames;
    private int over6944, over16, over33;
    private long totalNanos, maxNanos;
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
        totalFrames = totalNanos = maxNanos = 0;
        over6944 = over16 = over33 = 0;
        random = new SplittableRandom(0);
        overheadNanos = 0;
        Arrays.fill(gpuSamples, 0);
        Arrays.fill(gpuSeen, 0);
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
        totalFrames++;
        totalNanos += frameDurationNanos;
        maxNanos = Math.max(maxNanos, frameDurationNanos);
        if (frameDurationNanos > 6_944_444) over6944++;
        if (frameDurationNanos > 16_666_667) over16++;
        if (frameDurationNanos > 33_333_333) over33++;
        long slot = totalFrames <= capacity ? frames++ : random.nextLong(totalFrames);
        if (totalFrames > capacity) droppedFrames++;
        if (slot < capacity) {
            frameNanos[(int) slot] = frameDurationNanos;
            for (int segment = 0; segment < SEGMENTS; segment++) segmentNanos[segment][(int) slot] = current[segment];
        }
        overheadNanos += System.nanoTime() - started;
    }

    public void recordGpu(GpuPass pass, long nanos) {
        if (!recording) return;
        int ordinal = pass.ordinal();
        long seen = ++gpuSeen[ordinal];
        long index = seen <= capacity ? gpuSamples[ordinal]++ : random.nextLong(seen);
        if (index < capacity) {
            gpuNanos[ordinal][(int) index] = nanos;
        }
    }

    public int frames() {
        return frames;
    }

    public long droppedFrames() {
        return droppedFrames;
    }

    public long totalFrames() {
        return totalFrames;
    }

    public double meanMillis() {
        return totalFrames == 0 ? 0 : totalNanos / 1e6 / totalFrames;
    }

    public double maxMillis() {
        return maxNanos / 1e6;
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
        if (thresholdNanos == 6_944_444) return over6944;
        if (thresholdNanos == 16_666_667) return over16;
        if (thresholdNanos == 33_333_333) return over33;
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
