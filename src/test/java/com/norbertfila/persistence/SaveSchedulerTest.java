package com.norbertfila.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.persistence.world.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveSchedulerTest {
    @TempDir
    Path directory;

    @Test
    void failedOlderWriteCannotReplaceNewerPendingSnapshotOrSpinOnRetry() throws Exception {
        Path path = TestWorlds.prepare(directory.resolve("state.vxl"), 42, 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var io = new WorldIo((target, snapshot) -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timeout");
                } catch (InterruptedException interrupted) {
                    throw new IOException(interrupted);
                }
                throw new IOException("Injected disk failure");
            }
            WorldStateCodec.write(target, snapshot);
        })) {
            var saves = new SaveScheduler(io, path);
            saves.request(snapshot(1));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            saves.request(snapshot(2));
            saves.request(snapshot(3));
            release.countDown();
            await(() -> saves.failure() != null);
            assertEquals(3, saves.retained().sequence());
            for (int i = 0; i < 100; i++) saves.poll();
            assertEquals(1, calls.get());
            saves.retry();
            await(saves::isIdle);
            assertEquals(snapshot(3), TestWorlds.load(path));
            assertEquals(2, calls.get());
            assertEquals(3, saves.metrics().savedRevision());
        } finally {
            release.countDown();
        }
    }

    @Test
    void retryAfterCommittedButUnacknowledgedWriteIsIdempotent() throws Exception {
        Path path = TestWorlds.prepare(directory.resolve("state.vxl"), 42, 1);
        var calls = new AtomicInteger();
        try (var io = new WorldIo((target, snapshot) -> {
            calls.incrementAndGet();
            WorldStateCodec.write(target, snapshot);
            throw new IOException("Lost acknowledgement");
        })) {
            var saves = new SaveScheduler(io, path);
            saves.request(snapshot(1));
            await(() -> saves.failure() != null);
            saves.retry();
            await(saves::isIdle);
            assertEquals(snapshot(1), TestWorlds.load(path));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void missingAndStaleTargetsAreNeverRecreatedOrOverwritten() throws Exception {
        Path path = TestWorlds.prepare(directory.resolve("state.vxl"), 42, 1);
        WorldStateCodec.write(path, snapshot(3));
        byte[] before = Files.readAllBytes(path);
        try (var io = new WorldIo()) {
            assertThrows(
                    ExecutionException.class, () -> io.write(path, snapshot(2)).get(5, TimeUnit.SECONDS));
            assertArrayEquals(before, Files.readAllBytes(path));
            Files.delete(path);
            assertThrows(
                    ExecutionException.class, () -> io.write(path, snapshot(4)).get(5, TimeUnit.SECONDS));
            assertFalse(Files.exists(path));
        }
    }

    @Test
    void ioQueueIsBoundedAndAllWorkUsesOneThread() throws Exception {
        Path path = TestWorlds.prepare(directory.resolve("state.vxl"), 42, 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var io = new WorldIo((target, snapshot) -> {
            assertEquals("voxel-world-io", Thread.currentThread().getName());
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timeout");
            } catch (InterruptedException interrupted) {
                throw new IOException(interrupted);
            }
            WorldStateCodec.write(target, snapshot);
        })) {
            var write = io.write(path, snapshot(1));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var reads = new java.util.ArrayList<CompletableFuture<WorldSnapshot>>();
            for (int i = 0; i < 8; i++) reads.add(io.read(path));
            var overflow = io.read(path);
            assertTrue(overflow.isCompletedExceptionally());
            release.countDown();
            write.get(5, TimeUnit.SECONDS);
            for (var read : reads) assertEquals(snapshot(1), read.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    private static WorldSnapshot snapshot(long sequence) {
        var base = TestWorlds.capture(42, 1, new com.norbertfila.world.World());
        return new WorldSnapshot(
                base.identity(),
                sequence,
                Instant.ofEpochMilli(sequence),
                base.deltas(),
                base.fluids(),
                base.inventory(),
                base.player());
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(condition.getAsBoolean());
    }
}
