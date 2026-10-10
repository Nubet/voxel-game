package com.norbertfila.diagnostics;

import java.util.Arrays;

public final class FrameMetrics {
    private static final double REPORT_INTERVAL_SECONDS = 1.0;
    private static final int DEFAULT_WINDOW_SIZE = 240;

    private final int chunks;
    private final int vertices;
    private final long meshBytes;
    private final boolean debug;
    private final double[] frameMillis;
    private final double[] sortedMillis;
    private double reportStartSeconds;
    private long frameCount;
    private double frameSeconds;
    private int frameSamples;
    private int frameIndex;
    private long uploadBytes;
    private long uploadNanos;
    private int uploadQueueDepth;
    private int visible;
    private int culled;
    private int drawCalls;
    private long workerNanos;
    private long workerCount;
    private int workerQueueDepth;

    public FrameMetrics(int chunks, int vertices, long meshBytes, boolean debug, double startSeconds) {
        this(chunks, vertices, meshBytes, debug, startSeconds, DEFAULT_WINDOW_SIZE);
    }

    public FrameMetrics(int chunks, int vertices, long meshBytes, boolean debug, double startSeconds, int windowSize) {
        if (windowSize <= 0) throw new IllegalArgumentException("Window size must be positive");
        this.chunks = chunks;
        this.vertices = vertices;
        this.meshBytes = meshBytes;
        this.debug = debug;
        frameMillis = new double[windowSize];
        sortedMillis = new double[windowSize];
        reportStartSeconds = startSeconds;
    }

    public void recordFrame(double deltaSeconds) {
        frameCount++;
        frameSeconds += deltaSeconds;
        frameMillis[frameIndex] = deltaSeconds * 1000.0;
        frameIndex = (frameIndex + 1) % frameMillis.length;
        if (frameSamples < frameMillis.length) frameSamples++;
    }

    public double averageFps() {
        return frameSeconds == 0.0 ? 0.0 : frameCount / frameSeconds;
    }

    public double averageFrameTimeMillis() {
        return frameCount == 0 ? 0.0 : frameSeconds * 1000.0 / frameCount;
    }

    public double p50FrameMillis() {
        return percentile(.50);
    }

    public double p95FrameMillis() {
        return percentile(.95);
    }

    public double p99FrameMillis() {
        return percentile(.99);
    }

    public double onePercentLowFps() {
        double slowFrameMillis = p99FrameMillis();
        return slowFrameMillis == 0.0 ? 0.0 : 1000.0 / slowFrameMillis;
    }

    public void recordUploads(long bytes, int queueDepth, long nanos) {
        uploadBytes += bytes;
        uploadNanos += nanos;
        uploadQueueDepth = queueDepth;
    }

    public void recordCulling(int visible, int culled, int drawCalls) {
        this.visible = visible;
        this.culled = culled;
        this.drawCalls = drawCalls;
    }

    public void recordWorker(long nanos, int queueDepth) {
        workerNanos += nanos;
        workerCount++;
        workerQueueDepth = queueDepth;
    }

    public PerformanceReport snapshot(int width, int height, String hardware) {
        return new PerformanceReport(
                averageFps(),
                p50FrameMillis(),
                p95FrameMillis(),
                p99FrameMillis(),
                onePercentLowFps(),
                workerCount == 0 ? 0.0 : workerNanos / 1_000_000.0 / workerCount,
                workerQueueDepth,
                hardware,
                width,
                height);
    }

    public void reportIfDue(double nowSeconds, int width, int height, String hardware) {
        double elapsedSeconds = nowSeconds - reportStartSeconds;
        if (elapsedSeconds < REPORT_INTERVAL_SECONDS) return;
        PerformanceReport report = snapshot(width, height, hardware);
        if (debug) {
            System.out.printf(
                    "[debug] FPS: %.0f | p50: %.2f ms | p95: %.2f ms | p99: %.2f ms | 1%% low: %.0f | sections: %d | visible: %d | culled: %d | draws: %d | worker: %.2f ms | worker queue: %d | GPU upload: %d bytes / %.2f ms | upload queue: %d%n",
                    report.averageFps(),
                    report.p50FrameMillis(),
                    report.p95FrameMillis(),
                    report.p99FrameMillis(),
                    report.onePercentLowFps(),
                    chunks,
                    visible,
                    culled,
                    drawCalls,
                    report.averageWorkerMillis(),
                    report.workerQueueDepth(),
                    uploadBytes,
                    uploadNanos / 1_000_000.0,
                    uploadQueueDepth);
        } else {
            System.out.printf(
                    "FPS: %.0f | frame: %.2f ms | sections: %d%n",
                    report.averageFps(), averageFrameTimeMillis(), chunks);
        }
        frameCount = 0;
        frameSeconds = 0.0;
        frameSamples = 0;
        frameIndex = 0;
        uploadBytes = 0;
        uploadNanos = 0;
        uploadQueueDepth = 0;
        visible = 0;
        culled = 0;
        drawCalls = 0;
        workerNanos = 0;
        workerCount = 0;
        workerQueueDepth = 0;
        reportStartSeconds = nowSeconds;
    }

    public void reportIfDue(double nowSeconds) {
        reportIfDue(nowSeconds, 0, 0, "unknown");
    }

    private double percentile(double quantile) {
        if (frameSamples == 0) return 0.0;
        System.arraycopy(frameMillis, 0, sortedMillis, 0, frameSamples);
        Arrays.sort(sortedMillis, 0, frameSamples);
        int index = Math.min(frameSamples - 1, Math.max(0, (int) Math.ceil(quantile * frameSamples) - 1));
        return sortedMillis[index];
    }
}
