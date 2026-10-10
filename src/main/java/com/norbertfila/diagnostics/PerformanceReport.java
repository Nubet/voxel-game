package com.norbertfila.diagnostics;

import java.util.Objects;

public record PerformanceReport(
        double averageFps,
        double p50FrameMillis,
        double p95FrameMillis,
        double p99FrameMillis,
        double onePercentLowFps,
        double averageWorkerMillis,
        int workerQueueDepth,
        String hardware,
        int width,
        int height) {
    public PerformanceReport {
        Objects.requireNonNull(hardware);
    }

    public static String csvHeader() {
        return "averageFps,p50FrameMillis,p95FrameMillis,p99FrameMillis,onePercentLowFps,"
                + "averageWorkerMillis,workerQueueDepth,hardware,width,height";
    }

    public String csvRow() {
        return averageFps + "," + p50FrameMillis + "," + p95FrameMillis + "," + p99FrameMillis + ","
                + onePercentLowFps + "," + averageWorkerMillis + "," + workerQueueDepth + ",\""
                + hardware.replace("\"", "\"\"") + "\"," + width + "," + height;
    }

    public String toJson() {
        return "{\"averageFps\":" + averageFps + ",\"p50FrameMillis\":" + p50FrameMillis
                + ",\"p95FrameMillis\":" + p95FrameMillis + ",\"p99FrameMillis\":" + p99FrameMillis
                + ",\"onePercentLowFps\":" + onePercentLowFps + ",\"averageWorkerMillis\":"
                + averageWorkerMillis + ",\"workerQueueDepth\":" + workerQueueDepth + ",\"hardware\":\""
                + hardware.replace("\"", "\\\"") + "\",\"width\":" + width + ",\"height\":" + height + "}";
    }
}
