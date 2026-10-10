package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.generation.WorldGenerator;
import com.norbertfila.meshing.GreedyMesher;
import com.norbertfila.render.UploadQueue;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ChunkManagerTest {
    @Test
    void rejectsAnUploadEditedWhileQueuedAndIgnoresItsLateAcknowledgements() {
        World world = new World();
        try (ChunkManager manager =
                new ChunkManager(world, emptyGenerator(), new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE))) {
            ChunkPos center = new ChunkPos(0, 0, 0);
            manager.update(center);
            var stale = awaitMesh(manager).meshes().get(0);
            UploadQueue queue = new UploadQueue();
            queue.enqueue(stale);
            manager.requestRemesh(new WorldEdit(world, 8).setBlock(new BlockPos(1, 1, 1), BlockId.STONE));

            assertFalse(manager.isCurrentMesh(stale));
            assertNull(queue.poll(Long.MAX_VALUE, true, manager::isCurrentMesh));
            assertEquals(0, queue.size());
            assertFalse(manager.markUploaded(stale));
            assertTrue(manager.rejectUpload(stale));
            assertFalse(manager.rejectUpload(stale));
            var latest = awaitMesh(manager).meshes().get(0);
            assertFalse(manager.markUploaded(stale));
            assertFalse(manager.rejectUpload(stale));
            assertTrue(manager.isCurrentMesh(latest));
            assertEquals(1, latest.dataRevision());
            assertEquals(1, latest.meshDependencyRevision());
            assertTrue(manager.markUploaded(latest));
            assertFalse(manager.rejectUpload(latest));
            assertFalse(manager.markUploaded(latest));
        }
    }

    @Test
    void neighborChangesInvalidateQueuedGeometryWithoutChangingItsDataRevision() {
        World world = new World();
        try (ChunkManager manager =
                new ChunkManager(world, emptyGenerator(), new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE))) {
            ChunkPos center = new ChunkPos(0, 0, 0);
            manager.update(center);
            assertTrue(manager.markUploaded(awaitMesh(manager).meshes().get(0)));
            manager.requestRemesh(new WorldEdit(world, 8).setBlock(new BlockPos(15, 1, 1), BlockId.STONE));
            var beforeNeighbor = awaitMesh(manager).meshes().get(0);
            assertEquals(6, beforeNeighbor.mesh().faceCount());
            Chunk neighbor = new Chunk();
            neighbor.set(0, 1, 1, BlockId.STONE);
            world.putChunk(new ChunkPos(1, 0, 0), neighbor);

            assertFalse(manager.isCurrentMesh(beforeNeighbor));
            assertTrue(manager.rejectUpload(beforeNeighbor));
            var hiddenFace = awaitMesh(manager).meshes().get(0);
            assertEquals(beforeNeighbor.dataRevision(), hiddenFace.dataRevision());
            assertTrue(hiddenFace.meshDependencyRevision() > beforeNeighbor.meshDependencyRevision());
            assertEquals(5, hiddenFace.mesh().faceCount());

            new WorldEdit(world, 8).setBlock(new BlockPos(16, 1, 1), BlockId.AIR);
            assertFalse(manager.isCurrentMesh(hiddenFace));
            assertTrue(manager.rejectUpload(hiddenFace));
            var restoredFace = awaitMesh(manager).meshes().get(0);
            assertEquals(hiddenFace.dataRevision(), restoredFace.dataRevision());
            assertEquals(6, restoredFace.mesh().faceCount());
            assertTrue(manager.markUploaded(restoredFace));
        }
    }

    @Test
    void staleUploadCannotAcknowledgeARegeneratedChunkAtTheSamePosition() {
        try (ChunkManager manager =
                new ChunkManager(new World(), emptyGenerator(), new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE))) {
            ChunkPos center = new ChunkPos(0, 0, 0);
            manager.update(center);
            var stale = awaitMesh(manager).meshes().get(0);
            manager.update(new ChunkPos(10, 0, 0));
            assertFalse(manager.isCurrentMesh(stale));
            manager.update(center);
            var latest = awaitMesh(manager).meshes().get(0);
            assertEquals(stale.meshRevision(), latest.meshRevision());
            assertTrue(stale.token() != latest.token());
            assertFalse(manager.markUploaded(stale));
            assertFalse(manager.rejectUpload(stale));
            assertTrue(manager.isCurrentMesh(latest));
            assertTrue(manager.markUploaded(latest));
        }
    }

    @Test
    void neighborArrivalInvalidatesAnInFlightCpuResultWithoutEditingItsData() throws Exception {
        World world = new World();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GreedyMesher mesher = new GreedyMesher();
        try (ChunkManager manager = new ChunkManager(
                world,
                emptyGenerator(),
                (snapshot, neighborhood, x, y, z) -> {
                    if (calls.incrementAndGet() == 1) {
                        started.countDown();
                        try {
                            if (!release.await(2, TimeUnit.SECONDS))
                                throw new AssertionError("Worker release timed out");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    }
                    return mesher.mesh(snapshot, neighborhood, x, y, z);
                },
                new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE))) {
            manager.update(new ChunkPos(0, 0, 0));
            long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
            while (started.getCount() != 0 && System.nanoTime() < deadline) {
                manager.processResults();
                Thread.sleep(1);
            }
            assertEquals(0, started.getCount());
            world.putChunk(new ChunkPos(1, 0, 0), new Chunk());
            release.countDown();
            var latest = awaitMesh(manager).meshes().get(0);
            assertEquals(0, latest.dataRevision());
            assertEquals(1, latest.meshDependencyRevision());
            assertEquals(2, calls.get());
            assertTrue(manager.markUploaded(latest));
        } finally {
            release.countDown();
        }
    }

    @Test
    void coalescesTenThousandEditsIntoOneLatestMeshWithoutMeshingOnCallerThread() {
        World world = new World();
        AtomicInteger calls = new AtomicInteger();
        Thread owner = Thread.currentThread();
        GreedyMesher mesher = new GreedyMesher();
        try (ChunkManager manager = new ChunkManager(
                world,
                emptyGenerator(),
                (snapshot, neighborhood, x, y, z) -> {
                    assertFalse(Thread.currentThread() == owner);
                    calls.incrementAndGet();
                    return mesher.mesh(snapshot, neighborhood, x, y, z);
                },
                new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE))) {
            ChunkPos center = new ChunkPos(0, 0, 0);
            manager.update(center);
            assertTrue(manager.markUploaded(awaitMesh(manager).meshes().get(0)));
            int initialCalls = calls.get();
            WorldEdit edits = new WorldEdit(world, 8);
            BlockPos position = new BlockPos(1, 1, 1);

            for (int index = 0; index < 10_000; index++)
                manager.requestRemesh(edits.setBlock(position, index % 2 == 0 ? BlockId.STONE : BlockId.DIRT));

            assertEquals(initialCalls, calls.get());
            var result = awaitMesh(manager).meshes().get(0);
            var neighborhood = world.blockNeighborhood(center);
            assertArrayEquals(
                    mesher.mesh(neighborhood.center(), neighborhood, 0, 0, 0).vertices(),
                    result.mesh().vertices());
            assertEquals(initialCalls + 1, calls.get());
            assertEquals(10_000, world.getChunk(center).revision());
            assertTrue(manager.markUploaded(result));
            manager.requestRemesh(edits.setBlock(position, BlockId.DIRT));
            manager.requestRemesh(edits.setBlock(new BlockPos(16, 0, 0), BlockId.STONE));
            manager.processResults();
            assertEquals(initialCalls + 1, calls.get());
            assertEquals(1, world.residentCount());
        }
    }

    @Test
    void coalescesEditsWhileWorkerUsesAnImmutableOldSnapshot() throws Exception {
        World world = new World();
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean blockNext = new AtomicBoolean();
        AtomicReference<com.norbertfila.world.ChunkSnapshot> blockedSnapshot = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GreedyMesher mesher = new GreedyMesher();
        try (ChunkManager manager = new ChunkManager(
                world,
                emptyGenerator(),
                (snapshot, neighborhood, x, y, z) -> {
                    calls.incrementAndGet();
                    if (blockNext.getAndSet(false)) {
                        blockedSnapshot.set(snapshot);
                        started.countDown();
                        try {
                            if (!release.await(2, TimeUnit.SECONDS))
                                throw new AssertionError("Worker release timed out");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    }
                    return mesher.mesh(snapshot, neighborhood, x, y, z);
                },
                new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE))) {
            ChunkPos center = new ChunkPos(0, 0, 0);
            manager.update(center);
            assertTrue(manager.markUploaded(awaitMesh(manager).meshes().get(0)));
            WorldEdit edits = new WorldEdit(world, 8);
            BlockPos position = new BlockPos(1, 1, 1);
            blockNext.set(true);
            manager.requestRemesh(edits.setBlock(position, BlockId.STONE));
            manager.processResults();
            assertTrue(started.await(1, TimeUnit.SECONDS));
            try {
                for (int index = 0; index < 20; index++) {
                    manager.requestRemesh(edits.setBlock(position, index % 2 == 0 ? BlockId.GRASS : BlockId.DIRT));
                    manager.processResults();
                }
                assertEquals(BlockId.STONE, blockedSnapshot.get().get(1, 1, 1));
                assertEquals(2, calls.get());
            } finally {
                release.countDown();
            }
            var result = awaitMesh(manager).meshes().get(0);
            var neighborhood = world.blockNeighborhood(center);
            assertArrayEquals(
                    mesher.mesh(neighborhood.center(), neighborhood, 0, 0, 0).vertices(),
                    result.mesh().vertices());
            assertEquals(3, calls.get());
            assertTrue(manager.markUploaded(result));
        } finally {
            release.countDown();
        }
    }

    @Test
    void queuesOnlyResidentChunksForACornerEdit() {
        World world = new World();
        AtomicInteger calls = new AtomicInteger();
        GreedyMesher mesher = new GreedyMesher();
        try (ChunkManager manager = new ChunkManager(
                world,
                emptyGenerator(),
                (snapshot, neighborhood, x, y, z) -> {
                    calls.incrementAndGet();
                    return mesher.mesh(snapshot, neighborhood, x, y, z);
                },
                new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE))) {
            ChunkPos center = new ChunkPos(0, 0, 0);
            manager.update(center);
            assertTrue(manager.markUploaded(awaitMesh(manager).meshes().get(0)));
            var change = new WorldEdit(world, 8).setBlock(new BlockPos(15, 15, 15), BlockId.STONE);
            assertEquals(4, change.dirtyChunks().size());
            manager.requestRemesh(change);
            var result = awaitMesh(manager).meshes();
            assertEquals(1, result.size());
            assertEquals(center, result.get(0).position());
            assertEquals(6, result.get(0).mesh().faceCount());
            assertEquals(2, calls.get());
            assertEquals(1, world.residentCount());
        }
    }

    @Test
    void loadsSpawnAndPublishesCompleteTerrainAfterDistanceChanges() throws Exception {
        World world = new World();
        ViewDemand demand = new ViewDemand(4, -1, 1, ViewDemand.Shape.SQUARE);
        ChunkPos center = new ChunkPos(0, 0, 0);
        var uploaded = new java.util.HashMap<ChunkPos, ChunkManager.MeshResult>();
        try (ChunkManager manager = new ChunkManager(world, new HillsGenerator(), demand)) {
            for (int radius : new int[] {4, 6, 16, 2, 5}) {
                manager.setRenderDistance(radius);
                long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                int quietUpdates = 0;
                while (quietUpdates < 20 && System.nanoTime() < deadline) {
                    var update = manager.update(center);
                    update.evicted().forEach(uploaded::remove);
                    var processed = manager.processResults();
                    for (var mesh : processed.meshes()) {
                        assertTrue(manager.markUploaded(mesh));
                        uploaded.put(mesh.position(), mesh);
                    }
                    if (processed.generated() == 0
                            && processed.meshes().isEmpty()
                            && demand.requested().stream().allMatch(uploaded::containsKey)) quietUpdates++;
                    else quietUpdates = 0;
                    Thread.sleep(2);
                }
                assertEquals(20, quietUpdates, "Streaming did not settle for radius " + radius);
                assertTrue(manager.readyForPlayer(center), "Spawn chunk missing");
                var spawn = new com.norbertfila.player.SpawnResolver(new HillsGenerator()).resolve(0, 0);
                assertTrue(world.isSolid(new com.norbertfila.world.BlockPos(0, (int) spawn.y() - 1, 0)));
                assertFalse(world.isSolid(new com.norbertfila.world.BlockPos(0, (int) spawn.y(), 0)));
                GreedyMesher mesher = new GreedyMesher();
                for (ChunkPos position : demand.requested()) {
                    var neighborhood = world.blockNeighborhood(position);
                    var expected =
                            mesher.mesh(neighborhood.center(), neighborhood, position.x(), position.y(), position.z());
                    assertArrayEquals(
                            expected.vertices(),
                            uploaded.get(position).mesh().vertices(),
                            "Outdated terrain at " + position);
                }
            }
        }
    }

    @Test
    void generatesMissingChunksOnceAndKeepsExistingChunks() {
        List<ChunkPos> generated = new ArrayList<>();
        World world = new World();
        ViewDemand demand = new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE);
        ChunkManager manager = new ChunkManager(
                world,
                new WorldGenerator() {
                    @Override
                    public long seed() {
                        return 0;
                    }

                    @Override
                    public int generatorVersion() {
                        return 1;
                    }

                    @Override
                    public ChunkData generate(ChunkPos position) {
                        generated.add(position);
                        return emptyData(position);
                    }
                },
                demand);

        ChunkManager.UpdateResult first = manager.update(new ChunkPos(-1, 0, -1));
        await(manager);
        ChunkManager.UpdateResult same = manager.update(new ChunkPos(-1, 0, -1));
        ChunkManager.UpdateResult next = manager.update(new ChunkPos(0, 0, -1));
        await(manager);

        assertEquals(1, first.scheduled());
        assertEquals(0, same.scheduled());
        assertEquals(1, next.scheduled());
        assertEquals(Set.of(new ChunkPos(-1, 0, -1), new ChunkPos(0, 0, -1)), new HashSet<>(generated));
        assertEquals(2, manager.residentPositions().size());
        assertTrue(world.hasChunk(new ChunkPos(-1, 0, -1)));
        assertFalse(world.isChunkRequested(new ChunkPos(-1, 0, -1)));
        manager.close();
    }

    @Test
    void residentPositionsHaveDeterministicOrder() {
        World world = new World();
        ViewDemand demand = new ViewDemand(1, -1, 1, ViewDemand.Shape.SQUARE);
        ChunkManager manager = new ChunkManager(world, emptyGenerator(), demand);

        ChunkManager.UpdateResult result = manager.update(new ChunkPos(0, 0, 0));
        await(manager);

        assertEquals(27, result.scheduled());
        assertEquals(new ChunkPos(-1, -1, -1), manager.residentPositions().get(0));
        assertEquals(new ChunkPos(1, 1, 1), manager.residentPositions().get(26));
        manager.close();
    }

    @Test
    void hysteresisUnloadingKeepsResidentCountBounded() {
        World world = new World();
        ViewDemand demand = new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE);
        ChunkManager manager = new ChunkManager(world, emptyGenerator(), demand);

        manager.update(new ChunkPos(0, 0, 0));
        await(manager);
        ChunkManager.UpdateResult moved = manager.update(new ChunkPos(2, 0, 0));
        await(manager);
        ChunkManager.UpdateResult steady = manager.update(new ChunkPos(2, 0, 0));

        assertEquals(List.of(new ChunkPos(0, 0, 0)), moved.evicted());
        assertEquals(1, moved.scheduled());
        assertEquals(1, manager.residentCount());
        assertTrue(steady.evicted().isEmpty());
        assertEquals(0, steady.generated());
        manager.close();
    }

    @Test
    void supportsSeparateLoadAndUnloadRadius() {
        World world = new World();
        ViewDemand demand = new ViewDemand(1, 0, 0, ViewDemand.Shape.SQUARE);
        ChunkManager manager = new ChunkManager(world, emptyGenerator(), demand, 3);

        assertEquals(1, manager.loadRadius());
        assertEquals(3, manager.unloadRadius());
        manager.close();
    }

    @Test
    void publishesGeneratedChunkMeshWithCurrentRevision() {
        ChunkManager manager =
                new ChunkManager(new World(), emptyGenerator(), new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE));
        manager.update(new ChunkPos(0, 0, 0));

        ChunkManager.ProcessResult result = awaitMesh(manager);

        assertEquals(1, result.generated());
        assertEquals(1, result.meshes().size());
        ChunkManager.MeshResult mesh = result.meshes().get(0);
        assertEquals(new ChunkPos(0, 0, 0), mesh.position());
        manager.markUploaded(mesh);
        manager.close();
    }

    @Test
    void workerFailureIsDiscardedWithoutLeavingAResidentChunk() {
        WorldGenerator failing = new WorldGenerator() {
            @Override
            public long seed() {
                return 0;
            }

            @Override
            public int generatorVersion() {
                return 1;
            }

            @Override
            public ChunkData generate(ChunkPos position) {
                throw new IllegalStateException("test generation failure");
            }
        };
        ChunkManager manager = new ChunkManager(new World(), failing, new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE));

        manager.update(new ChunkPos(0, 0, 0));
        await(manager);

        assertEquals(0, manager.residentCount());
        manager.close();
    }

    @Test
    void retriesMeshAfterUploadFailure() {
        ChunkManager manager =
                new ChunkManager(new World(), emptyGenerator(), new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE));
        manager.update(new ChunkPos(0, 0, 0));

        ChunkManager.MeshResult first = awaitMesh(manager).meshes().get(0);
        assertTrue(manager.rejectUpload(first));

        ChunkManager.MeshResult retry = awaitMesh(manager).meshes().get(0);
        assertEquals(first.position(), retry.position());
        assertTrue(retry.meshRevision() > first.meshRevision());
        manager.close();
    }

    private static void await(ChunkManager manager) {
        try {
            manager.awaitGeneration(Duration.ofSeconds(2));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static ChunkManager.ProcessResult awaitMesh(ChunkManager manager) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        int generated = 0;
        while (System.nanoTime() < deadline) {
            ChunkManager.ProcessResult result = manager.processResults();
            generated += result.generated();
            if (!result.meshes().isEmpty()) {
                return new ChunkManager.ProcessResult(generated, result.meshes(), result.discarded());
            }
            Thread.yield();
        }
        throw new AssertionError("Chunk mesh was not published");
    }

    private static WorldGenerator emptyGenerator() {
        return new WorldGenerator() {
            @Override
            public long seed() {
                return 0;
            }

            @Override
            public int generatorVersion() {
                return 1;
            }

            @Override
            public ChunkData generate(ChunkPos position) {
                return emptyData(position);
            }
        };
    }

    private static ChunkData emptyData(ChunkPos position) {
        return new ChunkData(position, new byte[ChunkData.BLOCK_COUNT]);
    }
}
