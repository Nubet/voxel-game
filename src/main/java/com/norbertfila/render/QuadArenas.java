package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sub-allocates quad records of one format from a few large GPU storage buffers instead of one buffer per mesh.
 *
 * <p>A released slice is not reused until the GPU has finished every frame that could still read it: releases are
 * grouped by frame, each frame ends with a fence, and a group returns its ranges to the allocator only after its
 * fence has signaled. Polling a fence never blocks.
 */
final class QuadArenas implements AutoCloseable {
    /** GL operations used by the arenas; tests substitute a fake so the lifecycle runs without a context. */
    interface Gl {
        int createBuffer(long bytes);

        void write(int buffer, long offsetBytes, int[] records);

        void deleteBuffer(int buffer);

        long fence();

        boolean signaled(long fence);

        void deleteFence(long fence);
    }

    static final Gl OPENGL = new Gl() {
        @Override
        public int createBuffer(long bytes) {
            int buffer = glGenBuffers();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL_DYNAMIC_DRAW);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            return buffer;
        }

        @Override
        public void write(int buffer, long offsetBytes, int[] records) {
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, buffer);
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, offsetBytes, records);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }

        @Override
        public void deleteBuffer(int buffer) {
            glDeleteBuffers(buffer);
        }

        @Override
        public long fence() {
            return glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        }

        @Override
        public boolean signaled(long fence) {
            int status = glClientWaitSync(fence, 0, 0);
            if (status == GL_WAIT_FAILED) throw new IllegalStateException("GPU fence wait failed");
            return status == GL_ALREADY_SIGNALED || status == GL_CONDITION_SATISFIED;
        }

        @Override
        public void deleteFence(long fence) {
            glDeleteSync(fence);
        }
    };

    private final Gl gl;
    private final int recordBytes;
    private final long arenaBytes;
    private final GpuMemoryBudget budget;
    private final List<Arena> arenas = new ArrayList<>();
    private final ArrayDeque<Release> releasing = new ArrayDeque<>();
    private List<Slice> releasedThisFrame = new ArrayList<>();
    private boolean closed;

    /** {@code budget} limits the total size of arena buffers, not the bytes of live meshes. */
    QuadArenas(Gl gl, int recordBytes, long arenaBytes, GpuMemoryBudget budget) {
        this.gl = Objects.requireNonNull(gl);
        if (recordBytes <= 0 || arenaBytes < recordBytes || arenaBytes % recordBytes != 0)
            throw new IllegalArgumentException("Arena size must be a positive multiple of the record size");
        this.recordBytes = recordBytes;
        this.arenaBytes = arenaBytes;
        this.budget = Objects.requireNonNull(budget);
    }

    /**
     * Copies {@code records} into a free range, adding an arena if none has room. Returns null when the budget allows
     * no further arena; the caller keeps any previous mesh in that case.
     */
    Slice allocate(int[] records) {
        if (closed) throw new IllegalStateException("Arenas are closed");
        long bytes = (long) records.length * Integer.BYTES;
        if (bytes == 0 || bytes % recordBytes != 0) throw new IllegalArgumentException("Incomplete quad records");
        for (Arena arena : arenas) {
            Slice slice = arena.tryAllocate(bytes);
            if (slice != null) return write(slice, records);
        }
        long size = Math.max(arenaBytes, (bytes + recordBytes - 1) / recordBytes * recordBytes);
        if (!budget.tryReserve(size)) return null;
        Arena arena;
        try {
            arena = new Arena(gl.createBuffer(size), size);
        } catch (RuntimeException | Error failure) {
            budget.release(size);
            throw failure;
        }
        arenas.add(arena);
        return write(arena.tryAllocate(bytes), records);
    }

    private Slice write(Slice slice, int[] records) {
        try {
            gl.write(slice.arena.buffer, slice.allocation.offset(), records);
            return slice;
        } catch (RuntimeException | Error failure) {
            slice.arena.allocator.free(slice.allocation);
            throw failure;
        }
    }

    /** Returns the range for reuse once the GPU has finished the current frame. */
    void release(Slice slice) {
        if (slice.released) throw new IllegalStateException("Slice released twice");
        slice.released = true;
        releasedThisFrame.add(slice);
    }

    /** Ends the frame: fences this frame's releases and recycles earlier ones the GPU has finished. */
    void endFrame() {
        if (!releasedThisFrame.isEmpty()) {
            releasing.addLast(new Release(gl.fence(), releasedThisFrame));
            releasedThisFrame = new ArrayList<>();
        }
        while (!releasing.isEmpty() && gl.signaled(releasing.peekFirst().fence)) {
            Release release = releasing.removeFirst();
            gl.deleteFence(release.fence);
            for (Slice slice : release.slices) slice.arena.allocator.free(slice.allocation);
        }
    }

    int arenaCount() {
        return arenas.size();
    }

    long capacityBytes() {
        long bytes = 0;
        for (Arena arena : arenas) bytes += arena.capacity;
        return bytes;
    }

    long allocatedBytes() {
        long bytes = 0;
        for (Arena arena : arenas) bytes += arena.allocator.allocatedBytes();
        return bytes;
    }

    long fragmentationBytes() {
        long bytes = 0;
        for (Arena arena : arenas) bytes += arena.allocator.fragmentationBytes();
        return bytes;
    }

    int pendingReleases() {
        int count = releasedThisFrame.size();
        for (Release release : releasing) count += release.slices.size();
        return count;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Release release : releasing) gl.deleteFence(release.fence);
        releasing.clear();
        for (Arena arena : arenas) {
            gl.deleteBuffer(arena.buffer);
            budget.release(arena.capacity);
        }
        arenas.clear();
    }

    private final class Arena {
        private final int buffer;
        private final long capacity;
        private final GpuArena allocator;

        Arena(int buffer, long capacity) {
            this.buffer = buffer;
            this.capacity = capacity;
            allocator = new GpuArena(capacity, recordBytes);
        }

        Slice tryAllocate(long bytes) {
            if (allocator.largestFreeBlockBytes() < bytes) return null;
            return new Slice(this, allocator.allocate(bytes));
        }
    }

    /** A range of one arena holding one mesh's records. */
    final class Slice {
        private final Arena arena;
        private final GpuArena.Allocation allocation;
        private boolean released;

        private Slice(Arena arena, GpuArena.Allocation allocation) {
            this.arena = arena;
            this.allocation = allocation;
        }

        int buffer() {
            return arena.buffer;
        }

        /** Index of the slice's first record within its arena buffer. */
        int firstRecord() {
            return Math.toIntExact(allocation.offset() / recordBytes);
        }

        long bytes() {
            return allocation.size();
        }
    }

    private record Release(long fence, List<Slice> slices) {}
}
