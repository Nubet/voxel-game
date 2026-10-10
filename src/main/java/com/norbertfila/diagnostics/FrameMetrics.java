package com.norbertfila.diagnostics;

public final class FrameMetrics {
    private static final double REPORT_INTERVAL_SECONDS = 1.0;

    private final int chunks;
    private final int vertices;
    private final long meshBytes;
    private final boolean debug;
    private double reportStartSeconds;
    private long frameCount;
    private double frameSeconds;
    private long uploadBytes;
    private long uploadNanos;
    private int uploadQueueDepth;
    private int visible;
    private int culled;
    private int drawCalls;

    public FrameMetrics(int chunks, int vertices, long meshBytes, boolean debug, double startSeconds) {
        this.chunks = chunks;
        this.vertices = vertices;
        this.meshBytes = meshBytes;
        this.debug = debug;
        reportStartSeconds = startSeconds;
    }

    public void recordFrame(double deltaSeconds) {
        frameCount++;
        frameSeconds += deltaSeconds;
    }

    public double averageFps() {
        return frameSeconds == 0.0 ? 0.0 : frameCount / frameSeconds;
    }

    public double averageFrameTimeMillis() {
        return frameCount == 0 ? 0.0 : frameSeconds * 1000.0 / frameCount;
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

    public void reportIfDue(double nowSeconds) {
        double elapsedSeconds = nowSeconds - reportStartSeconds;
        if (elapsedSeconds < REPORT_INTERVAL_SECONDS) return;
        double fps = frameCount / elapsedSeconds;
        double frameTimeMillis = averageFrameTimeMillis();
        if (debug) {
            System.out.printf(
                    "[debug] FPS: %.0f | frame: %.2f ms | sections: %d | visible: %d | culled: %d | draws: %d | vertices: %d | CPU mesh: %d bytes | GPU upload: %d bytes / %.2f ms | upload queue: %d%n",
                    fps,
                    frameTimeMillis,
                    chunks,
                    visible,
                    culled,
                    drawCalls,
                    vertices,
                    meshBytes,
                    uploadBytes,
                    uploadNanos / 1_000_000.0,
                    uploadQueueDepth);
        } else {
            System.out.printf("FPS: %.0f | frame: %.2f ms | sections: %d%n", fps, frameTimeMillis, chunks);
        }
        frameCount = 0;
        frameSeconds = 0.0;
        uploadBytes = 0;
        uploadNanos = 0;
        uploadQueueDepth = 0;
        visible = 0;
        culled = 0;
        drawCalls = 0;
        reportStartSeconds = nowSeconds;
    }
}
