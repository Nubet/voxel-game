package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.ChunkPos;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ChunkSchedulerTest {
    @Test
    void reservesResultCapacityUntilConsumerDrainsIt() throws Exception {
        try (ChunkScheduler<Integer> scheduler = new ChunkScheduler<>(1, 8, 2)) {
            assertTrue(scheduler.submit(new ChunkTask<>(new ChunkPos(0, 0, 0), 0, 0, () -> 0)));
            assertTrue(scheduler.submit(new ChunkTask<>(new ChunkPos(1, 0, 0), 0, 1, () -> 1)));
            assertFalse(scheduler.submit(new ChunkTask<>(new ChunkPos(2, 0, 0), 0, 2, () -> 2)));
            assertFalse(scheduler.hasCapacity());
            scheduler.shutdown();
            assertTrue(scheduler.awaitTermination(Duration.ofSeconds(2)));
            assertEquals(2, scheduler.outstandingJobs());
            var outcomes = scheduler.drainReady();
            assertEquals(2, outcomes.size());
            assertEquals(0, scheduler.outstandingJobs());
            assertTrue(scheduler.hasCapacity());
            assertEquals(
                    java.util.Set.of(0, 1),
                    outcomes.stream().map(ChunkTask.Outcome::result).collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void returnsOutcomeWithPositionAndToken() throws Exception {
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 2, 2)) {
            ChunkPos position = new ChunkPos(-2, 1, 3);
            assertTrue(scheduler.submit(new ChunkTask(
                    position, 8L, 12L, 9L, ChunkPriority.unprioritized(), () -> new MeshData(new float[0]))));

            ChunkTask.Outcome outcome = waitForOutcome(scheduler);

            assertEquals(position, outcome.position());
            assertEquals(8L, outcome.revision());
            assertEquals(12L, outcome.meshDependencyRevision());
            assertEquals(9L, outcome.token());
            assertTrue(outcome.succeeded());
            outcome.discard();
            assertNull(outcome.mesh());
        }
    }

    @Test
    void limitsOutstandingTasksIncludingReadyResults() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 1, 2)) {
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
            assertTrue(scheduler.submit(
                    new ChunkTask(new ChunkPos(0, 0, 0), 4, 8, 11, ChunkPriority.unprioritized(), () -> {
                        throw new IllegalStateException("generation failed");
                    })));

            ChunkTask.Outcome outcome = waitForOutcome(scheduler);

            assertFalse(outcome.succeeded());
            assertNotNull(outcome.failure());
            assertEquals(8, outcome.meshDependencyRevision());
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

    @Test
    void runsNearestQueuedTaskFirst() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 20, 21)) {
            assertTrue(scheduler.submit(new ChunkTask(
                    new ChunkPos(99, 0, 0),
                    0,
                    ChunkPriority.forPosition(new ChunkPos(99, 0, 0), new ChunkPos(0, 0, 0), 0, 0),
                    () -> {
                        release.await();
                        return new MeshData(new float[0]);
                    })));
            waitForActiveTask(scheduler);
            for (int x = 20; x >= 1; x--) {
                ChunkPos position = new ChunkPos(x, 0, 0);
                assertTrue(scheduler.submit(new ChunkTask(
                        position,
                        x,
                        ChunkPriority.forPosition(position, new ChunkPos(0, 0, 0), 0, 0),
                        () -> new MeshData(new float[0]))));
            }
            release.countDown();
            waitForOutcome(scheduler);
            ChunkTask.Outcome first = waitForOutcome(scheduler);
            assertEquals(new ChunkPos(1, 0, 0), first.position());
        }
    }

    @Test
    void directionBonusPrefersChunkAheadAtEqualDistance() {
        ChunkPriority ahead = ChunkPriority.forPosition(new ChunkPos(2, 0, 0), new ChunkPos(0, 0, 0), 1, 0);
        ChunkPriority behind = ChunkPriority.forPosition(new ChunkPos(-2, 0, 0), new ChunkPos(0, 0, 0), 1, 0);

        assertTrue(ahead.score() < behind.score());
    }

    @Test
    void cancelRemovesQueuedTaskAndReleasesCapacity() throws Exception {
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 1, 2)) {
            CountDownLatch release = new CountDownLatch(1);
            assertTrue(scheduler.submit(new ChunkTask(new ChunkPos(0, 0, 0), 1, 10, () -> {
                release.await();
                return new MeshData(new float[0]);
            })));
            waitForActiveTask(scheduler);
            ChunkTask queued = new ChunkTask(new ChunkPos(1, 0, 0), 2, 11, () -> new MeshData(new float[0]));
            assertTrue(scheduler.submit(queued));
            assertTrue(scheduler.cancel(queued.position(), queued.revision(), queued.token()));
            assertTrue(scheduler.submit(new ChunkTask(new ChunkPos(2, 0, 0), 3, 12, () -> new MeshData(new float[0]))));
            release.countDown();
        }
    }

    @Test
    void cancelMarksSlowActiveTaskWithoutWaitingForIt() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 1, 1)) {
            ChunkTask task = new ChunkTask(new ChunkPos(0, 0, 0), 4, 20, () -> {
                started.countDown();
                release.await();
                return new MeshData(new float[] {1, 2, 3, 1, 1, 1});
            });
            assertTrue(scheduler.submit(task));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(scheduler.cancel(task.position(), task.revision(), task.token()));
            assertEquals(1, scheduler.activeTasks());
            release.countDown();
            assertTrue(scheduler.awaitTermination(Duration.ofSeconds(1)) || scheduler.activeTasks() == 0);
            assertNull(scheduler.pollReady());
        }
    }

    @Test
    void teleportCancelsOldQueuedWork() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 3, 4)) {
            assertTrue(scheduler.submit(new ChunkTask(new ChunkPos(20, 0, 0), 1, 1, () -> {
                release.await();
                return new MeshData(new float[0]);
            })));
            waitForActiveTask(scheduler);
            ChunkTask queued = new ChunkTask(new ChunkPos(21, 0, 0), 1, 2, () -> new MeshData(new float[0]));
            assertTrue(scheduler.submit(queued));
            assertEquals(2, scheduler.cancelOutside(new ChunkPos(0, 0, 0), 2));
            release.countDown();
            scheduler.shutdown();
            assertTrue(scheduler.awaitTermination(Duration.ofSeconds(1)));
            while (scheduler.pollReady() != null) {}
            assertEquals(0, scheduler.outstandingJobs());
        }
    }

    @Test
    void replacingCompletedResultKeepsNewerTaskTrackedUntilPolled() throws Exception {
        try (ChunkScheduler<Integer> scheduler = new ChunkScheduler<>(1, 2, 2)) {
            ChunkPos position = new ChunkPos(0, 0, 0);
            assertTrue(scheduler.submitLatest(new ChunkTask<>(position, 1, 10, () -> 1)));
            waitForReady(scheduler, 1);
            CountDownLatch release = new CountDownLatch(1);
            assertTrue(scheduler.submitLatest(new ChunkTask<>(position, 2, 11, () -> {
                release.await();
                return 2;
            })));
            ChunkTask.Outcome<Integer> old = waitForOutcome(scheduler);
            assertEquals(10, old.token());
            assertTrue(scheduler.metadataEntries() > 0, "newer task must survive the older outcome being polled");
            release.countDown();
            assertEquals(11, waitForOutcome(scheduler).token());
            waitFor(() -> scheduler.metadataEntries() == 0);
        }
    }

    @Test
    void repeatedReplacementOfOnePositionLeavesNoMetadata() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler<Integer> scheduler = new ChunkScheduler<>(1, 4, 5)) {
            assertTrue(scheduler.submit(new ChunkTask<>(new ChunkPos(9, 0, 0), 0, 0, () -> {
                release.await();
                return 0;
            })));
            waitForActiveTask(scheduler);
            ChunkPos position = new ChunkPos(1, 0, 0);
            for (int revision = 1; revision <= 50; revision++) {
                int value = revision;
                assertTrue(scheduler.submitLatest(new ChunkTask<>(position, revision, revision, () -> value)));
            }
            assertEquals(1, scheduler.queuedTasks());
            release.countDown();
            assertEquals(0, waitForOutcome(scheduler).result());
            assertEquals(50, waitForOutcome(scheduler).result());
            waitFor(() -> scheduler.metadataEntries() == 0);
        }
    }

    @Test
    void cancellingRunningTaskReleasesItsPermitAndMetadata() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler<Integer> scheduler = new ChunkScheduler<>(1, 1, 2)) {
            ChunkPos position = new ChunkPos(3, 0, 0);
            assertTrue(scheduler.submitLatest(new ChunkTask<>(position, 1, 1, () -> {
                started.countDown();
                release.await();
                return 1;
            })));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            scheduler.cancel(position, 1, 1);
            release.countDown();
            waitFor(() -> scheduler.outstandingJobs() == 0);
            assertNull(scheduler.pollReady());
            waitFor(() -> scheduler.metadataEntries() == 0);
        }
    }

    @Test
    void travelThroughManyPositionsKeepsMetadataBoundedByInFlightWork() throws Exception {
        try (ChunkScheduler<Integer> scheduler = new ChunkScheduler<>(2, 8, 10)) {
            int submitted = 0, polled = 0;
            while (polled < 5_000) {
                while (submitted < 5_000 && scheduler.hasCapacity()) {
                    int x = submitted;
                    assertTrue(scheduler.submitLatest(new ChunkTask<>(new ChunkPos(x, 0, 0), 0, x, () -> x)));
                    submitted++;
                }
                assertTrue(scheduler.metadataEntries() <= 3 * 10);
                if (scheduler.pollReady() != null) polled++;
                else Thread.onSpinWait();
            }
            waitFor(() -> scheduler.metadataEntries() == 0);
        }
    }

    @Test
    void cancelledOutsideRadiusThenResubmittedPositionCompletes() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler<Integer> scheduler = new ChunkScheduler<>(1, 2, 3)) {
            assertTrue(scheduler.submit(new ChunkTask<>(new ChunkPos(0, 0, 0), 0, 0, () -> {
                release.await();
                return 0;
            })));
            waitForActiveTask(scheduler);
            ChunkPos far = new ChunkPos(40, 0, 0);
            assertTrue(scheduler.submitLatest(new ChunkTask<>(far, 0, 1, () -> 1)));
            assertEquals(
                    1,
                    scheduler.cancelOutside(new ChunkPos(0, 0, 0), 4, true, java.util.Set.of(new ChunkPos(0, 0, 0))));
            assertTrue(scheduler.submitLatest(new ChunkTask<>(far, 0, 2, () -> 2)));
            release.countDown();
            assertEquals(0, waitForOutcome(scheduler).result());
            assertEquals(2, waitForOutcome(scheduler).result());
            waitFor(() -> scheduler.metadataEntries() == 0);
        }
    }

    private static void waitForReady(ChunkScheduler<?> scheduler, int completed) throws InterruptedException {
        waitFor(() -> scheduler.completedJobs() >= completed);
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "condition not reached");
            Thread.sleep(1);
        }
    }

    @Test
    void latestSubmissionCancelsQueuedWorkInsteadOfGrowingQueue() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler scheduler = new ChunkScheduler(1, 1, 2)) {
            ChunkPos activePosition = new ChunkPos(0, 0, 0);
            assertTrue(scheduler.submit(new ChunkTask(activePosition, 1, 1, () -> {
                release.await();
                return new MeshData(new float[0]);
            })));
            waitForActiveTask(scheduler);
            ChunkPos position = new ChunkPos(1, 0, 0);
            assertTrue(scheduler.submitLatest(new ChunkTask(position, 1, 2, () -> new MeshData(new float[0]))));
            assertTrue(scheduler.submitLatest(new ChunkTask(position, 2, 3, () -> new MeshData(new float[0]))));
            assertEquals(1, scheduler.queuedTasks());
            release.countDown();
        }
    }

    @Test
    void editedChunkPriorityRunsBeforeQueuedBackgroundMeshing() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ChunkScheduler<Integer> scheduler = new ChunkScheduler<>(1, 3, 4)) {
            assertTrue(scheduler.submit(new ChunkTask<>(new ChunkPos(0, 0, 0), 0, 0, () -> {
                started.countDown();
                release.await();
                return 0;
            })));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(scheduler.submit(
                    new ChunkTask<>(new ChunkPos(10, 0, 0), 0, 1, ChunkPriority.unprioritized(), () -> 1)));
            ChunkPos edited = new ChunkPos(1, 0, 0);
            assertTrue(scheduler.submit(new ChunkTask<>(
                    edited, 1, 2, ChunkPriority.forPosition(edited, new ChunkPos(0, 0, 0), 0, 0), () -> 2)));
            release.countDown();
            waitForOutcome(scheduler);
            assertEquals(edited, waitForOutcome(scheduler).position());
        } finally {
            release.countDown();
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
