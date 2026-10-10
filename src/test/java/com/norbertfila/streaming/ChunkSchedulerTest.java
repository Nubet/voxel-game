package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ChunkSchedulerTest {
    @Test
    void returnsOutcomeWithPositionAndToken() throws Exception {
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 2, 2)) {
            ChunkPos position = new ChunkPos(-2, 1, 3);
            assertTrue(scheduler.submit(new ChunkTask(position, 9L, () -> new MeshData(new float[0]))));

            ChunkTask.Outcome outcome = waitForOutcome(scheduler);

            assertEquals(position, outcome.position());
            assertEquals(9L, outcome.token());
            assertTrue(outcome.succeeded());
        }
    }

    @Test
    void limitsQueuedTasks() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 1, 4)) {
            assertTrue(scheduler.submit(new ChunkTask(new ChunkPos(0, 0, 0), 0, () -> {
                release.await();
                return new MeshData(new float[0]);
            })));
            waitForActiveTask(scheduler);
            assertTrue(scheduler.submit(new ChunkTask(new ChunkPos(1, 0, 0), 1, () -> new MeshData(new float[0]))));
            assertFalse(scheduler.submit(new ChunkTask(new ChunkPos(2, 0, 0), 2, () -> new MeshData(new float[0]))));
            release.countDown();
        }
    }

    @Test
    void reportsWorkerFailureWithoutKillingScheduler() throws Exception {
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 1, 2)) {
            assertTrue(scheduler.submit(new ChunkTask(new ChunkPos(0, 0, 0), 4, () -> {
                throw new IllegalStateException("generation failed");
            })));

            ChunkTask.Outcome outcome = waitForOutcome(scheduler);

            assertFalse(outcome.succeeded());
            assertNotNull(outcome.failure());
            assertTrue(scheduler.submit(new ChunkTask(new ChunkPos(1, 0, 0), 5, () -> new MeshData(new float[0]))));
        }
    }

    @Test
    void interruptsSlowWorkDuringShutdown() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 1, 1)) {
            assertTrue(scheduler.submit(new ChunkTask(new ChunkPos(0, 0, 0), 1, () -> {
                started.countDown();
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                return new MeshData(new float[0]);
            })));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            scheduler.shutdownNow();
            assertTrue(scheduler.awaitTermination(Duration.ofSeconds(1)));
        }
    }

    private static ChunkTask.Outcome waitForOutcome(ChunkScheduler scheduler) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        ChunkTask.Outcome outcome;
        while ((outcome = scheduler.pollReady()) == null && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertNotNull(outcome);
        return outcome;
    }

    private static void waitForActiveTask(ChunkScheduler scheduler) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (scheduler.activeTasks() == 0 && System.nanoTime() < deadline) Thread.sleep(1);
        assertEquals(1, scheduler.activeTasks());
    }
}
