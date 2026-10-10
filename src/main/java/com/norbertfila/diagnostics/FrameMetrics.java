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

    public void reportIfDue(double nowSeconds) {
        double elapsedSeconds = nowSeconds - reportStartSeconds;
        if (elapsedSeconds < REPORT_INTERVAL_SECONDS) return;
        double fps = frameCount / elapsedSeconds;
        double frameTimeMillis = averageFrameTimeMillis();
        if (debug) {
            System.out.printf(
                    "[debug] FPS: %.0f | frame: %.2f ms | sections: %d | vertices: %d | CPU mesh: %d bytes%n",
                    fps, frameTimeMillis, chunks, vertices, meshBytes);
        } else {
            System.out.printf("FPS: %.0f | frame: %.2f ms | sections: %d%n", fps, frameTimeMillis, chunks);
        }
        frameCount = 0;
        frameSeconds = 0.0;
        reportStartSeconds = nowSeconds;
    }
}
