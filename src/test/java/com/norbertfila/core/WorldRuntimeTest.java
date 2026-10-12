package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.ChunkData;
import com.norbertfila.meshing.GreedyMesher;
import com.norbertfila.persistence.WorldSave;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldRuntimeTest {
    @TempDir
    Path directory;

    @Test
    void ownerRunsWaterWhileControlsAreBlockedAndShutdownRestoresItsClock() throws Exception {
        Path save = directory.resolve("water.vxl");
        World world = new World();
        world.setBlock(new BlockPos(8, 1, 8), BlockId.WATER, 64);
        WorldSave.save(save, 42, 1, world);
        Flat terrain = new Flat();
        long stopped;
        try (WorldRuntime runtime = start(terrain, save)) {
            var frame = await(runtime, f -> f.tick() >= 40);
            assertTrue(frame.pendingWater() > 0);
            assertThrows(
                    UnsupportedOperationException.class, () -> frame.residents().clear());
            assertTrue(terrain.threads.contains("voxel-world"));
            assertFalse(terrain.threads.contains(Thread.currentThread().getName()));
            stopped = frame.tick();
        }
        var saved = WorldSave.load(save);
        assertTrue(saved.fluids().tick() >= stopped);
        assertTrue(saved.deltas().stream()
                .flatMap(d -> d.changes().stream())
                .anyMatch(c -> c.value() == BlockId.WATER && c.waterState() > WaterState.SOURCE));
        try (WorldRuntime restarted = start(new Flat(), save)) {
            assertTrue(await(restarted, f -> true).tick() > saved.fluids().tick());
        }
    }

    @Test
    void explicitPauseStopsWorldClockButBlockedControlsDoNot() throws Exception {
        try (WorldRuntime runtime = start(new Flat(), directory.resolve("pause.vxl"))) {
            long before = await(runtime, f -> f.tick() >= 3).tick();
            runtime.controls(controls(true, true));
            var paused = await(runtime, WorldRuntime.Frame::paused);
            long published = paused.publishedAt();
            var later = await(runtime, f -> f.publishedAt() > published + 100_000_000);
            assertEquals(paused.tick(), later.tick());
            runtime.controls(controls(true, false));
            assertTrue(await(runtime, f -> !f.paused() && f.tick() > paused.tick())
                            .tick()
                    > before);
        }
    }

    @Test
    void blockedGenerationDoesNotBlockClockOrPublicationAndCommandsAreBounded() throws Exception {
        Flat terrain = new Flat();
        terrain.blockWorkers = true;
        try (WorldRuntime runtime = start(terrain, directory.resolve("slow.vxl"))) {
            assertTrue(terrain.workerEntered.await(3, TimeUnit.SECONDS));
            var first = await(runtime, f -> f.tick() >= 3);
            var later = await(runtime, f -> f.tick() > first.tick() + 5);
            assertTrue(later.residents().isEmpty());
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
        } finally {
            terrain.release.countDown();
        }
    }

    @Test
    void slowStartupBoundsDiscreteActionsWithoutBlockingTheCaller() throws Exception {
        Flat terrain = new Flat();
        terrain.blockSpawn = true;
        WorldRuntime runtime = start(terrain, directory.resolve("bounded.vxl"));
        try {
            assertTrue(terrain.workerEntered.await(3, TimeUnit.SECONDS));
            for (int count = 0; count < 64; count++) assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            assertFalse(runtime.offer(new WorldRuntime.NextMode()));
            assertNull(runtime.frame());
        } finally {
            terrain.release.countDown();
            runtime.close();
        }
    }

    @Test
    void stalledRendererBoundsMeshPublicationWithoutStoppingWorldTicks() throws Exception {
        try (WorldRuntime runtime = start(new Flat(), directory.resolve("backpressure.vxl"))) {
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, true, false, 3));
            var blocked = await(runtime, f -> f.tick() >= 60);
            var held = new java.util.ArrayList<ChunkManager.MeshResult>();
            ChunkManager.MeshResult mesh;
            while ((mesh = runtime.pollMesh()) != null) {
                held.add(mesh);
            }
            assertTrue(!held.isEmpty() && held.size() <= 64);
            held.forEach(result -> runtime.acknowledge(result, true));
            assertTrue(await(runtime, f -> {
                                var next = runtime.pollMesh();
                                if (next == null) return false;
                                runtime.acknowledge(next, true);
                                return true;
                            })
                            .tick()
                    > blocked.tick());
        }
    }

    @Test
    void lateSuccessfulUploadIsRejectedAfterEditAndFreshMeshCanBeAcknowledged() throws Exception {
        try (WorldRuntime runtime = start(new Flat(), directory.resolve("edit.vxl"))) {
            runtime.controls(controls(false, false));
            var old = awaitMesh(runtime, new ChunkPos(0, 0, 0));
            assertTrue(runtime.frame().isCurrent(old));
            assertTrue(runtime.offer(new WorldRuntime.Edit(true, BlockId.AIR, 0, -90)));
            await(runtime, f -> !f.isCurrent(old));
            runtime.acknowledge(old, true);
            var replacement = awaitMesh(runtime, old.position());
            assertNotEquals(old.token(), replacement.token());
            assertTrue(replacement.dataRevision() > old.dataRevision());
            runtime.acknowledge(replacement, true);
            await(runtime, f -> !f.validUploads().containsKey(replacement.token()));
        }
    }

    @Test
    void queuedJumpEdgesPreserveCreativeDoubleTap() throws Exception {
        try (WorldRuntime runtime = start(new Flat(), directory.resolve("input.vxl"))) {
            runtime.controls(controls(false, false));
            await(runtime, f -> !f.residents().isEmpty());
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(runtime, f -> f.mode() == PlayerController.Mode.CREATIVE);
            assertTrue(runtime.offer(new WorldRuntime.Jump(10)));
            assertTrue(runtime.offer(new WorldRuntime.Jump(10.1)));
            assertTrue(await(runtime, WorldRuntime.Frame::flying).flying());
        }
    }

    @Test
    void wrongGeneratorCannotOverwriteAnExistingSave() throws Exception {
        Path save = directory.resolve("wrong.vxl");
        WorldSave.save(save, 99, 1, new World());
        byte[] before = Files.readAllBytes(save);
        WorldRuntime runtime = start(new Flat(), save);
        assertThrows(IllegalStateException.class, runtime::close);
        assertArrayEquals(before, Files.readAllBytes(save));
    }

    private WorldRuntime start(Flat terrain, Path save) {
        return new WorldRuntime(terrain, new GreedyMesher(), new ViewDemand(0, 0, 1, ViewDemand.Shape.SQUARE), save);
    }

    private static WorldRuntime.Controls controls(boolean blocked, boolean paused) {
        return new WorldRuntime.Controls(PlayerInput.NONE, -90, 0, blocked, paused, 0);
    }

    private static WorldRuntime.Frame await(WorldRuntime runtime, Predicate<WorldRuntime.Frame> predicate)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            var frame = runtime.frame();
            if (frame != null && predicate.test(frame)) return frame;
            Thread.sleep(2);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Runtime did not publish expected state");
    }

    private static ChunkManager.MeshResult awaitMesh(WorldRuntime runtime, ChunkPos position) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            runtime.frame();
            var mesh = runtime.pollMesh();
            if (mesh != null) {
                if (mesh.position().equals(position)) return mesh;
                runtime.acknowledge(mesh, true);
            }
            Thread.sleep(2);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Runtime did not publish expected mesh");
    }

    private static final class Flat implements TerrainSource {
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        final CountDownLatch workerEntered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean blockWorkers;
        volatile boolean blockSpawn;

        public long seed() {
            return 42;
        }

        public int generatorVersion() {
            return 1;
        }

        public int surfaceHeight(int x, int z) {
            return 0;
        }

        public TerrainSummary sample(int x, int z) {
            threads.add(Thread.currentThread().getName());
            return new TerrainSummary(0, BlockId.STONE, BlockId.STONE, BlockId.STONE);
        }

        public ChunkData generate(ChunkPos position) {
            String thread = Thread.currentThread().getName();
            threads.add(thread);
            if ((blockWorkers && !thread.equals("voxel-world")) || (blockSpawn && thread.equals("voxel-world"))) {
                workerEntered.countDown();
                try {
                    release.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] blocks = new byte[Chunk.BLOCK_BYTES];
            for (int y = 0; y < Chunk.SIZE; y++)
                if ((long) position.y() * Chunk.SIZE + y <= 0)
                    for (int z = 0; z < Chunk.SIZE; z++)
                        for (int x = 0; x < Chunk.SIZE; x++)
                            blocks[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = BlockId.STONE;
            return new ChunkData(position, blocks);
        }
    }
}
