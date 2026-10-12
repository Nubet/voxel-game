package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.WaterTestScene;
import com.norbertfila.meshing.GreedyMesher;
import com.norbertfila.render.UploadQueue;
import com.norbertfila.world.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WaterStreamingTest {
    private static final ChunkPos CENTER = new ChunkPos(-1, 1, -1);
    private final GreedyMesher mesher = new GreedyMesher();

    @Test
    void stateOnlyEditsRejectQueuedMeshesAndSurviveStreamingReload() throws Exception {
        World world = new World();
        ViewDemand demand = new ViewDemand(0, 1, 1, ViewDemand.Shape.SQUARE);
        Map<ChunkPos, ChunkManager.MeshResult> uploaded = new HashMap<>();
        try (ChunkManager manager = new ChunkManager(world, new WaterTestScene(), mesher::meshTextured, demand)) {
            settle(manager, demand, CENTER, uploaded);
            BlockPos position = new BlockPos(-2, 17, -6);
            assertEquals(WaterState.SOURCE, world.getWaterState(position).orElseThrow());
            WorldEdit edit = new WorldEdit(world, 8);
            manager.requestRemesh(edit.setBlock(position, BlockId.WATER, WaterState.flowing(1)));
            var stale = awaitMesh(manager);
            var oldSnapshot = world.getChunk(CENTER).snapshot();
            UploadQueue queue = new UploadQueue();
            queue.enqueue(stale);
            manager.requestRemesh(edit.setBlock(position, BlockId.WATER, WaterState.flowing(7)));
            assertEquals(WaterState.flowing(1), oldSnapshot.waterState(14, 1, 10));
            assertNull(queue.poll(Long.MAX_VALUE, true, result -> {
                if (manager.isCurrentMesh(result)) return true;
                manager.rejectUpload(result);
                return false;
            }));
            assertEquals(0, queue.size());
            assertFalse(manager.markUploaded(stale));
            settle(manager, demand, CENTER, uploaded);
            assertEquals(2, uploaded.get(CENTER).dataRevision());
            assertNotEquals(stale.token(), uploaded.get(CENTER).token());

            settle(manager, demand, new ChunkPos(10, 1, -1), uploaded);
            assertFalse(world.hasChunk(CENTER));
            settle(manager, demand, CENTER, uploaded);
            assertEquals(WaterState.flowing(7), world.getWaterState(position).orElseThrow());
            assertEquals(
                    WaterState.flowing(7), world.getChunk(CENTER).snapshot().waterState(14, 1, 10));
            assertFalse(manager.markUploaded(stale));
        }
    }

    @Test
    void waterEditsRemeshHorizontalAndVerticalNeighborsAndSurviveReload() throws Exception {
        World world = new World();
        ViewDemand demand = new ViewDemand(1, 0, 1, ViewDemand.Shape.SQUARE);
        Map<ChunkPos, ChunkManager.MeshResult> uploaded = new HashMap<>();
        try (ChunkManager manager = new ChunkManager(world, new WaterTestScene(), mesher::meshTextured, demand)) {
            settle(manager, demand, CENTER, uploaded);
            ChunkPos horizontal = new ChunkPos(0, 1, -1);
            ChunkPos vertical = new ChunkPos(-1, 0, -1);
            var ownBefore = uploaded.get(CENTER);
            var horizontalBefore = uploaded.get(horizontal);
            var verticalBefore = uploaded.get(vertical);
            WorldEdit edit = new WorldEdit(world, 8);
            BlockPos removed = new BlockPos(-1, 16, -6);
            BlockPos stone = new BlockPos(-2, 17, -6);
            var change = edit.setBlock(removed, BlockId.AIR);
            assertEquals(3, change.dirtyChunks().size());
            manager.requestRemesh(change);
            manager.requestRemesh(edit.setBlock(stone, BlockId.STONE));
            assertFalse(manager.isCurrentMesh(ownBefore));
            assertFalse(manager.isCurrentMesh(horizontalBefore));
            assertFalse(manager.isCurrentMesh(verticalBefore));
            settle(manager, demand, CENTER, uploaded);
            assertNotEquals(ownBefore.token(), uploaded.get(CENTER).token());
            assertNotEquals(horizontalBefore.token(), uploaded.get(horizontal).token());
            assertNotEquals(verticalBefore.token(), uploaded.get(vertical).token());
            assertEquals(
                    horizontalBefore.dataRevision(), uploaded.get(horizontal).dataRevision());
            assertEquals(verticalBefore.dataRevision(), uploaded.get(vertical).dataRevision());
            assertFalse(Arrays.equals(
                    horizontalBefore.mesh().vertices(),
                    uploaded.get(horizontal).mesh().vertices()));
            assertFalse(Arrays.equals(
                    verticalBefore.mesh().vertices(),
                    uploaded.get(vertical).mesh().vertices()));
            var edited = uploaded.get(CENTER).mesh();

            settle(manager, demand, new ChunkPos(10, 1, -1), uploaded);
            assertFalse(world.hasChunk(CENTER));
            settle(manager, demand, CENTER, uploaded);
            assertEquals(BlockId.AIR, world.getBlock(removed).orElseThrow());
            assertEquals(BlockId.STONE, world.getBlock(stone).orElseThrow());
            assertArrayEquals(edited.vertices(), uploaded.get(CENTER).mesh().vertices());
            assertEquals(edited.ranges(), uploaded.get(CENTER).mesh().ranges());
            assertFalse(manager.markUploaded(ownBefore));
        }
    }

    @Test
    void queuedWetMeshCannotOverwriteTheLatestWaterEdit() throws Exception {
        World world = new World();
        ViewDemand demand = new ViewDemand(0, 1, 1, ViewDemand.Shape.SQUARE);
        Map<ChunkPos, ChunkManager.MeshResult> uploaded = new HashMap<>();
        try (ChunkManager manager = new ChunkManager(world, new WaterTestScene(), mesher::meshTextured, demand)) {
            settle(manager, demand, CENTER, uploaded);
            WorldEdit edit = new WorldEdit(world, 8);
            BlockPos position = new BlockPos(-2, 17, -6);
            manager.requestRemesh(edit.setBlock(position, BlockId.AIR));
            var stale = awaitMesh(manager);
            UploadQueue queue = new UploadQueue();
            queue.enqueue(stale);
            manager.requestRemesh(edit.setBlock(position, BlockId.STONE));
            assertNull(queue.poll(Long.MAX_VALUE, true, result -> {
                if (manager.isCurrentMesh(result)) return true;
                manager.rejectUpload(result);
                return false;
            }));
            assertEquals(0, queue.size());
            assertFalse(manager.markUploaded(stale));
            settle(manager, demand, CENTER, uploaded);
            assertEquals(BlockId.STONE, world.getBlock(position).orElseThrow());
            assertNotEquals(stale.token(), uploaded.get(CENTER).token());
        }
    }

    @Test
    void inFlightWaterSnapshotCannotUndoAReplacement() throws Exception {
        World world = new World();
        ViewDemand demand = new ViewDemand(0, 1, 1, ViewDemand.Shape.SQUARE);
        AtomicBoolean blockNext = new AtomicBoolean();
        AtomicReference<ChunkSnapshot> oldSnapshot = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        Map<ChunkPos, ChunkManager.MeshResult> uploaded = new HashMap<>();
        Thread owner = Thread.currentThread();
        try (ChunkManager manager = new ChunkManager(
                world,
                new WaterTestScene(),
                (snapshot, neighborhood, x, y, z) -> {
                    assertNotSame(owner, Thread.currentThread());
                    if (blockNext.getAndSet(false)) {
                        oldSnapshot.set(snapshot);
                        started.countDown();
                        try {
                            if (!release.await(3, TimeUnit.SECONDS))
                                throw new AssertionError("Worker release timed out");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(e);
                        }
                    }
                    return mesher.meshTextured(snapshot, neighborhood, x, y, z);
                },
                demand)) {
            settle(manager, demand, CENTER, uploaded);
            WorldEdit edit = new WorldEdit(world, 8);
            BlockPos position = new BlockPos(-2, 17, -6);
            blockNext.set(true);
            manager.requestRemesh(edit.setBlock(position, BlockId.AIR));
            manager.processResults();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            manager.requestRemesh(edit.setBlock(position, BlockId.STONE));
            assertEquals(BlockId.AIR, oldSnapshot.get().get(14, 1, 10));
            release.countDown();
            settle(manager, demand, CENTER, uploaded);
            assertEquals(BlockId.STONE, world.getBlock(position).orElseThrow());
            assertEquals(2, uploaded.get(CENTER).dataRevision());
        } finally {
            release.countDown();
        }
    }

    private void settle(
            ChunkManager manager, ViewDemand demand, ChunkPos center, Map<ChunkPos, ChunkManager.MeshResult> uploaded)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            manager.update(center).evicted().forEach(uploaded::remove);
            for (var result : manager.processResults().meshes()) {
                assertTrue(manager.isCurrentMesh(result));
                assertTrue(manager.markUploaded(result));
                uploaded.put(result.position(), result);
            }
            boolean complete = true;
            for (ChunkPos position : demand.requested()) {
                Chunk chunk = manager.world().getChunk(position);
                var result = uploaded.get(position);
                if (chunk == null
                        || result == null
                        || result.dataRevision() != chunk.revision()
                        || result.meshDependencyRevision() != chunk.meshDependencyRevision()) {
                    complete = false;
                    break;
                }
                var n = manager.world().blockNeighborhood(position);
                var expected = mesher.meshTextured(n.center(), n, position.x(), position.y(), position.z());
                assertArrayEquals(expected.vertices(), result.mesh().vertices());
                assertEquals(expected.ranges(), result.mesh().ranges());
            }
            if (complete) return;
            Thread.sleep(1);
        } while (System.nanoTime() < deadline);
        fail("Water streaming did not converge to current complete meshes");
    }

    private static ChunkManager.MeshResult awaitMesh(ChunkManager manager) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        do {
            var results = manager.processResults().meshes();
            if (!results.isEmpty()) return results.getFirst();
            Thread.sleep(1);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Water mesh was not published");
    }
}
