package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.WorldGenerator;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CollisionStreamingTest {
    @Test
    void delayedCollisionDataSurvivesViewChangesAndIsReleasedWithoutAnotherCameraMove() throws Exception {
        World world = new World();
        ChunkPos pinned = new ChunkPos(20, 5, -20);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread owner = Thread.currentThread();
        WorldGenerator generator = new WorldGenerator() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public ChunkData generate(ChunkPos position) {
                assertNotSame(owner, Thread.currentThread());
                if (position.equals(pinned)) {
                    started.countDown();
                    try {
                        if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Worker timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                }
                return new ChunkData(position, new byte[ChunkData.BLOCK_COUNT]);
            }
        };
        try (ChunkManager manager =
                new ChunkManager(world, generator, new ViewDemand(0, 0, 0, ViewDemand.Shape.CIRCLE))) {
            assertFalse(manager.ensureCollisionChunks(List.of(pinned)));
            manager.update(new ChunkPos(0, 0, 0));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            ChunkPos nextCenter = new ChunkPos(20, 0, -20);
            manager.update(nextCenter);
            assertFalse(manager.ensureCollisionChunks(List.of(pinned)));
            release.countDown();
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (!world.hasChunk(pinned) && System.nanoTime() < deadline) {
                manager.update(nextCenter);
                manager.processResults();
                Thread.sleep(1);
            }
            assertTrue(manager.ensureCollisionChunks(List.of(pinned)));
            assertTrue(world.hasChunk(pinned));
            manager.update(nextCenter);
            assertTrue(world.hasChunk(pinned));

            assertTrue(manager.ensureCollisionChunks(List.of()));
            var update = manager.update(nextCenter);
            assertTrue(update.changed());
            assertTrue(update.evicted().contains(pinned));
            assertFalse(world.hasChunk(pinned));
        } finally {
            release.countDown();
        }
    }

    @Test
    void pendingAndReadyOutcomesRespectCollisionProtectionOutsideHorizontalRadius() throws Exception {
        ChunkPos pinned = new ChunkPos(-20, 5, 20);
        ChunkPos origin = new ChunkPos(0, 0, 0);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (ChunkScheduler<Integer> scheduler = new ChunkScheduler<>(1, 2, 3)) {
            assertTrue(scheduler.submit(new ChunkTask<>(pinned, 0, 7, () -> {
                started.countDown();
                if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Worker timed out");
                return 42;
            })));
            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertEquals(0, scheduler.cancelOutside(origin, 0, true, Set.of(pinned)));
            release.countDown();
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (scheduler.activeTasks() > 0 && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(0, scheduler.activeTasks());
            assertEquals(0, scheduler.discardReadyOutside(origin, 0, true, Set.of(pinned)));
            var outcome = scheduler.pollReady();
            assertNotNull(outcome);
            assertEquals(42, outcome.result());
            assertEquals(7, outcome.token());
            assertEquals(0, scheduler.outstandingJobs());
        } finally {
            release.countDown();
        }
    }

    @Test
    void requestsCollisionSectionsBeforeTheLargeVisualPrefetch() throws Exception {
        World world = new World();
        ChunkPos body = new ChunkPos(0, 10, 0);
        CountDownLatch bodyStarted = new CountDownLatch(1), visualRelease = new CountDownLatch(1);
        WorldGenerator generator = new WorldGenerator() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public ChunkData generate(ChunkPos position) {
                if (position.equals(body)) bodyStarted.countDown();
                else
                    try {
                        if (!visualRelease.await(3, TimeUnit.SECONDS)) throw new AssertionError("Worker timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                    }
                return new ChunkData(position, new byte[ChunkData.BLOCK_COUNT]);
            }
        };
        try (ChunkManager manager =
                new ChunkManager(world, generator, new ViewDemand(4, -1, 1, ViewDemand.Shape.SQUARE))) {
            manager.ensureCollisionChunks(List.of(body));
            manager.update(new ChunkPos(0, 0, 0));
            assertTrue(bodyStarted.await(1, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
            while (!world.hasChunk(body) && System.nanoTime() < deadline) {
                manager.processResults();
                Thread.sleep(1);
            }
            assertTrue(manager.ensureCollisionChunks(List.of(body)));
            assertFalse(world.hasChunk(new ChunkPos(0, 0, 0)));
            visualRelease.countDown();
        } finally {
            visualRelease.countDown();
        }
    }
}
