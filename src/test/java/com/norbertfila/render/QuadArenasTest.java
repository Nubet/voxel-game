package com.norbertfila.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuadArenasTest {
    private static final int RECORD = 8;

    @Test
    void packsMeshesIntoOneBufferAtRecordAlignedOffsets() {
        var gl = new FakeGl();
        var arenas = new QuadArenas(gl, RECORD, 1024, new GpuMemoryBudget(4096));
        var first = arenas.allocate(records(3, 1));
        var second = arenas.allocate(records(2, 2));
        assertEquals(1, arenas.arenaCount());
        assertEquals(first.buffer(), second.buffer());
        assertEquals(0, first.firstRecord());
        assertEquals(3, second.firstRecord());
        assertArrayEquals(records(2, 2), gl.read(second.buffer(), 3 * RECORD, 4));
    }

    @Test
    void releasedRangeIsReusedOnlyAfterItsFrameFenceSignals() {
        var gl = new FakeGl();
        var arenas = new QuadArenas(gl, RECORD, 32, new GpuMemoryBudget(4096));
        var old = arenas.allocate(records(4, 1));
        arenas.release(old);
        arenas.endFrame();
        var whileGpuBusy = arenas.allocate(records(4, 2));
        assertNotEquals(old.buffer(), whileGpuBusy.buffer(), "an in-flight range must not be overwritten");
        assertArrayEquals(records(4, 1), gl.read(old.buffer(), 0, 8));

        gl.signalAll();
        arenas.endFrame();
        var reused = arenas.allocate(records(4, 3));
        assertEquals(old.buffer(), reused.buffer());
        assertEquals(0, reused.firstRecord());
        assertEquals(0, arenas.pendingReleases());
        assertEquals(0, gl.liveFences.size());
    }

    @Test
    void budgetExhaustionReturnsNullWithoutTouchingExistingMeshes() {
        var gl = new FakeGl();
        var arenas = new QuadArenas(gl, RECORD, 64, new GpuMemoryBudget(64));
        var kept = arenas.allocate(records(8, 5));
        assertNull(arenas.allocate(records(1, 6)));
        assertArrayEquals(records(8, 5), gl.read(kept.buffer(), 0, 16));
        assertEquals(1, arenas.arenaCount());
    }

    @Test
    void meshLargerThanAnArenaGetsAnArenaOfItsOwn() {
        var gl = new FakeGl();
        var arenas = new QuadArenas(gl, RECORD, 32, new GpuMemoryBudget(4096));
        var large = arenas.allocate(records(10, 1));
        assertEquals(0, large.firstRecord());
        assertEquals(80, arenas.capacityBytes());
    }

    @Test
    void releasingTwiceIsRejected() {
        var arenas = new QuadArenas(new FakeGl(), RECORD, 64, new GpuMemoryBudget(4096));
        var slice = arenas.allocate(records(1, 1));
        arenas.release(slice);
        assertThrows(IllegalStateException.class, () -> arenas.release(slice));
    }

    @Test
    void closeDeletesBuffersAndFencesAndReturnsTheBudget() {
        var gl = new FakeGl();
        var budget = new GpuMemoryBudget(4096);
        var arenas = new QuadArenas(gl, RECORD, 64, budget);
        arenas.release(arenas.allocate(records(2, 1)));
        arenas.endFrame();
        arenas.close();
        assertTrue(gl.buffers.isEmpty());
        assertTrue(gl.liveFences.isEmpty());
        assertEquals(0, budget.allocatedBytes());
    }

    @Test
    void churnKeepsMemoryBoundedByLiveMeshes() {
        var gl = new FakeGl();
        var arenas = new QuadArenas(gl, RECORD, 256, new GpuMemoryBudget(1 << 20));
        List<QuadArenas.Slice> live = new ArrayList<>();
        for (int round = 0; round < 2_000; round++) {
            live.add(arenas.allocate(records(1 + round % 7, round)));
            if (live.size() > 10) arenas.release(live.remove(round % live.size()));
            gl.signalAll();
            arenas.endFrame();
        }
        assertTrue(arenas.capacityBytes() <= 4 * 256, "arenas grew to " + arenas.capacityBytes());
    }

    private static int[] records(int quads, int value) {
        int[] records = new int[quads * 2];
        java.util.Arrays.fill(records, value);
        return records;
    }

    private static final class FakeGl implements QuadArenas.Gl {
        final Map<Integer, int[]> buffers = new HashMap<>();
        final Set<Long> liveFences = new HashSet<>();
        final Set<Long> signaled = new HashSet<>();
        int nextBuffer = 1;
        long nextFence = 1;

        @Override
        public int createBuffer(long bytes) {
            buffers.put(nextBuffer, new int[(int) (bytes / Integer.BYTES)]);
            return nextBuffer++;
        }

        @Override
        public void write(int buffer, long offsetBytes, int[] records) {
            System.arraycopy(records, 0, buffers.get(buffer), (int) (offsetBytes / Integer.BYTES), records.length);
        }

        int[] read(int buffer, long offsetBytes, int ints) {
            int start = (int) (offsetBytes / Integer.BYTES);
            return java.util.Arrays.copyOfRange(buffers.get(buffer), start, start + ints);
        }

        @Override
        public void deleteBuffer(int buffer) {
            buffers.remove(buffer);
        }

        @Override
        public long fence() {
            liveFences.add(nextFence);
            return nextFence++;
        }

        void signalAll() {
            signaled.addAll(liveFences);
        }

        @Override
        public boolean signaled(long fence) {
            return signaled.contains(fence);
        }

        @Override
        public void deleteFence(long fence) {
            liveFences.remove(fence);
        }
    }
}
