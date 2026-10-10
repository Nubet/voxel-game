package com.norbertfila.render;

public final class GpuMemoryBudget {
    private final long limitBytes;
    private long allocatedBytes;
    private long peakBytes;
    private long uploadedBytes;
    private long deletedBytes;

    public GpuMemoryBudget(long limitBytes) {
        if (limitBytes <= 0) throw new IllegalArgumentException("GPU memory limit must be positive");
        this.limitBytes = limitBytes;
    }

    public boolean tryReserve(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("Bytes must not be negative");
        if (bytes > limitBytes - allocatedBytes) return false;
        allocatedBytes += bytes;
        uploadedBytes += bytes;
        peakBytes = Math.max(peakBytes, allocatedBytes);
        return true;
    }

    public void release(long bytes) {
        if (bytes < 0 || bytes > allocatedBytes) throw new IllegalArgumentException("Invalid release size");
        allocatedBytes -= bytes;
        deletedBytes += bytes;
    }

    public long limitBytes() {
        return limitBytes;
    }

    public long allocatedBytes() {
        return allocatedBytes;
    }

    public long availableBytes() {
        return limitBytes - allocatedBytes;
    }

    public long peakBytes() {
        return peakBytes;
    }

    public long uploadedBytes() {
        return uploadedBytes;
    }

    public long deletedBytes() {
        return deletedBytes;
    }
}
