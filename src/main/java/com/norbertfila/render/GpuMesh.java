package com.norbertfila.render;

import com.norbertfila.meshing.QuadMesh;
import com.norbertfila.meshing.QuadRanges;
import java.util.Objects;

/** One uploaded quad buffer owned by the GL thread. Closing it deletes the buffer exactly once. */
public final class GpuMesh implements AutoCloseable {
    interface Deleter {
        void delete(int buffer);
    }

    private final int buffer;
    private final QuadMesh.Format format;
    private final QuadRanges ranges;
    private final long bytes;
    private final Deleter deleter;
    private boolean closed;

    GpuMesh(int buffer, QuadMesh mesh, Deleter deleter) {
        this(buffer, mesh.format(), mesh.ranges(), mesh.bytes(), deleter);
    }

    GpuMesh(int buffer, QuadMesh.Format format, QuadRanges ranges, long bytes, Deleter deleter) {
        this.buffer = buffer;
        this.format = Objects.requireNonNull(format);
        this.ranges = Objects.requireNonNull(ranges);
        if (bytes < 0) throw new IllegalArgumentException("Mesh size must not be negative");
        this.bytes = bytes;
        this.deleter = Objects.requireNonNull(deleter);
    }

    int buffer() {
        return buffer;
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
        deleter.delete(buffer);
    }
}
