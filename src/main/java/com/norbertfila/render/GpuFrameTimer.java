package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

/**
 * Nonblocking GPU pass timer built on timestamp queries. Each frame writes {@code passes + 1} timestamps into one
 * slot of a small ring; results are read only once the GPU reports them available, so the CPU never waits. A frame
 * whose slot is still in flight is skipped instead of stalling.
 */
public final class GpuFrameTimer implements AutoCloseable {
    @FunctionalInterface
    public interface Listener {
        void pass(int pass, long nanos);
    }

    private static final int SLOTS = 6;
    private final int stamps;
    private final int[][] queries;
    private final boolean[] pending = new boolean[SLOTS];
    private final long[] values;
    private int slot;
    private int written;
    private boolean active;
    private boolean closed;

    public GpuFrameTimer(int passes) {
        if (passes < 1) throw new IllegalArgumentException("At least one pass is required");
        stamps = passes + 1;
        values = new long[stamps];
        queries = new int[SLOTS][stamps];
        for (int[] slotQueries : queries) glGenQueries(slotQueries);
    }

    /** Reads every completed slot without blocking, then starts timing the next frame if a slot is free. */
    public void beginFrame(Listener listener) {
        for (int index = 0; index < SLOTS; index++) {
            if (!pending[index]) continue;
            int[] slotQueries = queries[index];
            if (glGetQueryObjecti(slotQueries[stamps - 1], GL_QUERY_RESULT_AVAILABLE) == GL_FALSE) continue;
            for (int stamp = 0; stamp < stamps; stamp++)
                values[stamp] = glGetQueryObjecti64(slotQueries[stamp], GL_QUERY_RESULT);
            for (int pass = 0; pass + 1 < stamps; pass++) listener.pass(pass, values[pass + 1] - values[pass]);
            pending[index] = false;
        }
        active = !pending[slot];
        written = 0;
        stamp();
    }

    /** Marks the end of the next pass. */
    public void stamp() {
        if (!active || written == stamps) return;
        glQueryCounter(queries[slot][written++], GL_TIMESTAMP);
    }

    public void endFrame() {
        if (!active) return;
        if (written == stamps) {
            pending[slot] = true;
            slot = (slot + 1) % SLOTS;
        }
        active = false;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (int[] slotQueries : queries) glDeleteQueries(slotQueries);
    }
}
