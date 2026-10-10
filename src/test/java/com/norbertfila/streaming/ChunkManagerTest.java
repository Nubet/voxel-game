package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.generation.WorldGenerator;
import com.norbertfila.meshing.GreedyMesher;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkManagerTest {
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
        assertTrue(manager.markUploadFailed(first));

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
