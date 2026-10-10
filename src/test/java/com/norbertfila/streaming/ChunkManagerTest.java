package com.norbertfila.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.WorldGenerator;
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
