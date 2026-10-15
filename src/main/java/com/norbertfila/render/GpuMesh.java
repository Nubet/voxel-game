package com.norbertfila.render;

import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.meshing.QuadRanges;
import java.util.Objects;

/** Quad records of one mesh part inside a shared GPU buffer. Closing it releases the range exactly once. */
public final class GpuMesh implements AutoCloseable {
    private final int buffer;
    private final int firstRecord;
    private final QuadMesh.Format format;
    private final QuadRanges ranges;
    private final long bytes;
    private final Runnable release;
    private boolean closed;

    GpuMesh(int buffer, int firstRecord, QuadMesh.Format format, QuadRanges ranges, long bytes, Runnable release) {
        if (firstRecord < 0 || bytes < 0) throw new IllegalArgumentException("Invalid mesh range");
        this.buffer = buffer;
        this.firstRecord = firstRecord;
        this.format = Objects.requireNonNull(format);
        this.ranges = Objects.requireNonNull(ranges);
        this.bytes = bytes;
        this.release = Objects.requireNonNull(release);
    }

    int buffer() {
        return buffer;
    }

    /** Index of the first quad record of this mesh within {@link #buffer()}. */
    int firstRecord() {
        return firstRecord;
    }

    QuadMesh.Format format() {
        return format;
    }

    QuadRanges ranges() {
        return ranges;
    }

    long bytes() {
        return bytes;
    }

    boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        release.run();
    }
}
