package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.ChunkData;
import com.norbertfila.interaction.BlockInteraction;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.TestWorlds;
import com.norbertfila.player.Inventory;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.player.PlayerState;
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
    void asynchronousStopDoesNotWaitForStartupAndRejectsNewCommands() throws Exception {
        var terrain = new Flat();
        terrain.blockSpawn = true;
        var save = directory.resolve("stop-startup.vxl");
        var runtime = start(terrain, save);
        try {
            assertTrue(terrain.workerEntered.await(3, TimeUnit.SECONDS));
            assertTimeout(java.time.Duration.ofMillis(100), runtime::requestStop);
            assertFalse(runtime.isStopped());
            assertFalse(runtime.offer(new WorldRuntime.NextMode()));
            terrain.release.countDown();
            awaitStopped(runtime);
        } finally {
            terrain.release.countDown();
            runtime.close();
        }
        assertEquals(42, TestWorlds.load(save).identity().seed());
        assertTrue(runtime.isStopped());
    }

    @Test
    void repeatedAsyncStopAndRestartPreserveInventoryAndWorldClock() throws Exception {
        var save = directory.resolve("cycles.vxl");
        var inventory = new Inventory();
        inventory.creativePick(0, BlockId.POPPY);
        inventory.click(0, true, false);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        long tick = 0;
        for (int cycle = 0; cycle < 3; cycle++) {
            var runtime = start(new Flat(), save);
            try {
                long previousTick = tick;
                var frame = await(runtime, f -> f.tick() > previousTick + 2);
                assertEquals(inventory.snapshot(), frame.inventory());
                runtime.controls(controls(true, true));
                var paused = await(runtime, WorldRuntime.Frame::paused);
                tick = paused.tick();
                runtime.requestStop();
                runtime.requestStop();
                awaitStopped(runtime);
            } finally {
                runtime.close();
            }
            var stored = TestWorlds.load(save);
            assertEquals(inventory.snapshot(), stored.inventory());
            assertEquals(tick, stored.fluids().tick());
        }
    }

    @Test
    void failedFinalSaveRetainsSnapshotUntilExplicitRetry() throws Exception {
        Path save = directory.resolve("retry.vxl"), backup = directory.resolve("previous.vxl");
        var runtime = start(new Flat(), save);
        await(runtime, f -> f.tick() >= 2);
        Files.move(save, backup);
        Files.createDirectory(save);
        runtime.requestStop();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (runtime.state() != WorldRuntime.State.SAVE_ERROR && System.nanoTime() < deadline) Thread.sleep(2);
            assertEquals(WorldRuntime.State.SAVE_ERROR, runtime.state());
            assertNotNull(runtime.retainedSnapshot());
            assertFalse(runtime.isStopped());
            Files.delete(save);
            Files.move(backup, save);
            runtime.retrySave();
            awaitStopped(runtime);
            assertEquals(runtime.retainedSnapshot(), TestWorlds.load(save));
        } finally {
            if (runtime.state() == WorldRuntime.State.SAVE_ERROR) runtime.discardChanges();
            runtime.close();
        }
    }

    private static void awaitStopped(WorldRuntime runtime) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!runtime.isStopped() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(runtime.isStopped(), "World thread did not finish its final save and shutdown");
    }

    @Test
    void vegetationAdmissionRemovesPlantsWhoseProceduralSupportWasEdited() throws Exception {
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        TerrainGenerator generator =
                new TerrainGenerator(42, new TerrainConfig(8, disabled, disabled, disabled), (x, z) -> 0);
        ChunkPos position = new ChunkPos(0, 0, 0);
        byte[] generated = generator.generate(position).blocks();
        BlockPos plant = null;
        for (int z = 0; z < 16 && plant == null; z++)
            for (int x = 0; x < 16; x++)
                if (PlantRules.isPlant(generated[(9 * 16 + z) * 16 + x])) {
                    plant = new BlockPos(x, 9, z);
                    break;
                }
        assertNotNull(plant);
        World world = new World();
        world.setBlock(new BlockPos(plant.x(), 8, plant.z()), BlockId.AIR, 64);
        Path save = directory.resolve("vegetation.vxl");
        TestWorlds.save(save, generator.seed(), generator.generatorVersion(), world);
        try (WorldRuntime runtime = new WorldRuntime(
                generator,
                new com.norbertfila.meshing.WaterMesher(),
                new ViewDemand(0, 0, 0, ViewDemand.Shape.SQUARE),
                save)) {
            var mesh = awaitMesh(runtime, position);
            runtime.acknowledge(mesh, true);
            await(runtime, frame -> frame.tick() >= 5);
        }
        World restored = new World();
        TestWorlds.restore(restored, TestWorlds.load(save), 64);
        assertEquals(BlockId.AIR, restored.changes().get(plant).orElseThrow());
    }

    @Test
    void ownerRunsWaterWhileControlsAreBlockedAndShutdownRestoresItsClock() throws Exception {
        Path save = directory.resolve("water.vxl");
        World world = new World();
        world.setBlock(new BlockPos(8, 1, 8), BlockId.WATER, 64);
        TestWorlds.save(save, 42, 1, world);
        Flat terrain = new Flat();
        long stopped;
        try (WorldRuntime runtime = start(terrain, save)) {
            var frame = await(runtime, f -> f.tick() >= 40);
            assertTrue(frame.pendingWater() > 0);
            assertThrows(
                    UnsupportedOperationException.class, () -> frame.residents().clear());
            assertTrue(terrain.threads.stream().anyMatch(name -> name.startsWith("pool-")));
            assertFalse(terrain.threads.contains(Thread.currentThread().getName()));
            stopped = frame.tick();
        }
        var saved = TestWorlds.load(save);
        assertTrue(saved.fluids().tick() >= stopped);
        assertTrue(saved.deltas().stream()
                .flatMap(d -> d.changes().stream())
                .anyMatch(c -> c.value() == BlockId.WATER && c.waterState() > WaterState.SOURCE));
        try (WorldRuntime restarted = start(new Flat(), save)) {
            assertTrue(await(restarted, f -> f.tick() > saved.fluids().tick()).tick()
                    > saved.fluids().tick());
        }
    }

    @Test
    void publishesActualFractionalContactWhileControlsAreBlocked() throws Exception {
        Path save = directory.resolve("contact.vxl");
        World world = new World();
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) world.setBlock(new BlockPos(x, 1, z), BlockId.WATER, 64);
        TestWorlds.save(save, 42, 1, world);
        try (WorldRuntime runtime = new WorldRuntime(
                new Flat(), new WaterMesher(), new ViewDemand(1, 0, 1, ViewDemand.Shape.SQUARE), save)) {
            var frame = await(runtime, f -> f.tick() >= 40 && f.waterContact().available());
            assertEquals(
                    (8 / 9d) / PlayerState.STANDING_HEIGHT, frame.waterContact().submergedFraction(), 1e-7);
            assertFalse(frame.waterContact().eyesSubmerged());
            assertEquals(1, frame.playerState().position().y());
        }
    }

    @Test
    void swimmingCrossesChunkBoundaryWithSixtyFourMeshesAwaitingAck() throws Exception {
        Path save = directory.resolve("swim-backpressure.vxl");
        World world = new World();
        for (int x = -2; x <= 36; x++)
            for (int y = 1; y <= 3; y++)
                for (int z = -2; z <= 2; z++) world.setBlock(new BlockPos(x, y, z), BlockId.WATER, 64);
        TestWorlds.save(save, 42, 1, world);
        try (WorldRuntime runtime = new WorldRuntime(
                new Flat(), new WaterMesher(), new ViewDemand(4, 0, 0, ViewDemand.Shape.SQUARE), save)) {
            var held = new java.util.ArrayList<WorldRuntime.SessionMesh>();
            await(runtime, f -> {
                WorldRuntime.SessionMesh mesh;
                while ((mesh = runtime.pollMesh()) != null) held.add(mesh);
                return held.size() == 64 && f.waterContact().available();
            });
            runtime.controls(new WorldRuntime.Controls(
                    new PlayerInput(true, false, false, false, false, false, false), 0, 0, false, false, 4));
            var previous = await(runtime, f -> f.playerState().position().x() > 2);
            long end = previous.tick() + 390;
            while (previous.tick() < end) {
                long tick = previous.tick();
                var next = await(runtime, f -> f.tick() > tick);
                assertTrue(
                        next.playerState().position().x()
                                > previous.playerState().position().x(),
                        "Swimming stopped at tick " + next.tick());
                assertTrue(next.waterContact().available());
                previous = next;
            }
            assertTrue(previous.playerState().position().x() > 16);
            assertEquals(64, held.size());
        }
        assertTrue(TestWorlds.load(save).fluids().tick() > 390);
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
    void blockedGenerationKeepsLoadingResponsiveWithoutAdvancingSimulation() throws Exception {
        Flat terrain = new Flat();
        terrain.blockWorkers = true;
        try (WorldRuntime runtime = start(terrain, directory.resolve("slow.vxl"))) {
            assertTrue(terrain.workerEntered.await(3, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (runtime.frame() == null && System.nanoTime() < deadline) Thread.sleep(2);
            var first = runtime.frame();
            assertNotNull(first);
            Thread.sleep(80);
            var later = runtime.frame();
            assertTrue(later.publishedAt() > first.publishedAt());
            assertEquals(first.tick(), later.tick());
            assertFalse(later.ready());
            assertEquals(WorldRuntime.State.LOADING, runtime.state());
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
            var held = new java.util.ArrayList<WorldRuntime.SessionMesh>();
            WorldRuntime.SessionMesh mesh;
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
            var old = awaitSettledMesh(runtime, new ChunkPos(0, 0, 0));
            assertTrue(runtime.frame().isCurrent(old));
            assertTrue(runtime.offer(new WorldRuntime.Edit(true, BlockId.AIR, 0, -90)));
            await(runtime, f -> !f.isCurrent(old));
            runtime.acknowledge(old, true);
            var replacement = awaitMesh(runtime, old.mesh().position());
            assertNotEquals(old.mesh().token(), replacement.mesh().token());
            assertTrue(replacement.mesh().dataRevision() > old.mesh().dataRevision());
            runtime.acknowledge(replacement, true);
            await(runtime, f -> !f.validUploads().containsKey(replacement.mesh().token()));
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
    void heldForwardFlightCrossesUnviewedChunksEvenWithoutRendererAcknowledgements() throws Exception {
        try (WorldRuntime runtime = new WorldRuntime(
                new Flat(),
                new WaterMesher(),
                new ViewDemand(4, 0, 0, ViewDemand.Shape.SQUARE),
                TestWorlds.prepare(directory.resolve("continuous.vxl"), 42, 1))) {
            runtime.controls(new WorldRuntime.Controls(PlayerInput.NONE, 0, 0, false, false, 4));
            var heldMeshes = new java.util.ArrayList<WorldRuntime.SessionMesh>();
            await(runtime, f -> {
                WorldRuntime.SessionMesh mesh;
                while ((mesh = runtime.pollMesh()) != null) heldMeshes.add(mesh);
                return heldMeshes.size() == 64;
            });
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(runtime, f -> f.mode() == PlayerController.Mode.CREATIVE);
            assertTrue(runtime.offer(new WorldRuntime.Jump(10)));
            assertTrue(runtime.offer(new WorldRuntime.Jump(10.1)));
            var start = await(runtime, WorldRuntime.Frame::flying);
            runtime.controls(new WorldRuntime.Controls(
                    new PlayerInput(true, false, false, false, true, false, true), 0, 0, false, false, 4));
            var previous = await(runtime, f -> f.tick() > start.tick() + 30);
            long end = previous.tick() + 120;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (previous.tick() < end && System.nanoTime() < deadline) {
                long tick = previous.tick();
                var next = await(runtime, f -> f.tick() > tick);
                assertTrue(
                        next.playerState().position().x()
                                > previous.playerState().position().x(),
                        "Held forward stopped at tick " + next.tick());
                previous = next;
            }
            assertTrue(previous.tick() >= end);
            assertTrue(previous.playerState().position().y() > 16);
            assertEquals(64, heldMeshes.size());
        }
    }

    @Test
    void heldPlacementRetriesAgainstTheNextExposedFace() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 0, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        BlockInteraction interaction = new BlockInteraction(world, 8);
        PlayerController player = new PlayerController();
        player.setPosition(new PlayerState.Position(5.5, 1, .5));
        var controls = new WorldRuntime.Controls(PlayerInput.NONE, 180, 0, false, false, 0, 0, true);
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        inventory.add(BlockId.DIRT);

        assertTrue(WorldRuntime.placeInventory(interaction, player, controls, inventory)
                .isPresent());
        assertEquals(BlockId.DIRT, world.getBlock(new BlockPos(2, 2, 0)).orElseThrow());
        assertTrue(WorldRuntime.placeInventory(interaction, player, controls, inventory)
                .isPresent());
        assertEquals(BlockId.DIRT, world.getBlock(new BlockPos(3, 2, 0)).orElseThrow());
    }

    @Test
    void inventoryPlacementConsumesOnlySuccessfulSurvivalChangesAndMiningCollects() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 0, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        var interaction = new BlockInteraction(world, 8);
        var player = new PlayerController();
        player.setPosition(new PlayerState.Position(5.5, 1, .5));
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);
        var blocked = new WorldRuntime.Controls(PlayerInput.NONE, 180, 0, true, false, 0, 0, true);
        assertTrue(WorldRuntime.placeInventory(interaction, player, blocked, inventory)
                .isEmpty());
        assertEquals(1, inventory.snapshot().count(0));
        var active = new WorldRuntime.Controls(PlayerInput.NONE, 180, 0, false, false, 0, 0, true);
        assertTrue(WorldRuntime.placeInventory(interaction, player, active, inventory)
                .orElseThrow()
                .changed());
        assertEquals(BlockId.DIRT, world.getBlock(new BlockPos(2, 2, 0)).orElseThrow());
        assertEquals(0, inventory.snapshot().count(0));
        assertTrue(WorldRuntime.placeInventory(interaction, player, active, inventory)
                .isEmpty());
        var remove = new WorldRuntime.Edit(true, BlockId.AIR, 180, 0);
        assertTrue(WorldRuntime.remove(interaction, player, remove, inventory)
                .orElseThrow()
                .changed());
        assertEquals(1, inventory.snapshot().count(0));
        inventory.creativePick(0, BlockId.DIRT);
        for (int i = 1; i < 9; i++) inventory.creativePick(i, BlockId.DIRT);
        for (int i = 9; i < 36; i++) {
            inventory.click(0, false, true);
            inventory.creativePick(0, BlockId.DIRT);
        }
        assertTrue(WorldRuntime.remove(interaction, player, remove, inventory).isEmpty());
        assertEquals(BlockId.STONE, world.getBlock(new BlockPos(1, 2, 0)).orElseThrow());
        assertTrue(player.setMode(world, PlayerController.Mode.CREATIVE));
        assertTrue(WorldRuntime.placeInventory(interaction, player, active, inventory)
                .orElseThrow()
                .changed());
        assertEquals(64, inventory.snapshot().count(0));
    }

    @Test
    void inventoryCommandsWorkWhileMenuBlocksGameplayAndPersistAcrossRestart() throws Exception {
        Path save = directory.resolve("inventory-runtime.vxl");
        var inventory = new Inventory();
        inventory.creativePick(0, BlockId.POPPY);
        TestWorlds.save(
                save,
                TestWorlds.capture(
                        42, 1, new World(), com.norbertfila.fluid.FluidSnapshot.EMPTY, inventory.snapshot()));
        try (var runtime = start(new Flat(), save)) {
            runtime.controls(controls(true, false));
            await(runtime, frame -> frame.inventory().count(0) == 64);
            assertTrue(runtime.offer(new WorldRuntime.ClickSlot(0, true, false)));
            assertTrue(runtime.offer(new WorldRuntime.PickCreative(1, BlockId.BEDROCK)));
            assertTrue(runtime.offer(new WorldRuntime.SelectSlot(8)));
            await(
                    runtime,
                    frame -> frame.inventory().count(36) == 32
                            && frame.inventory().selectedSlot() == 8);
            assertEquals(0, runtime.frame().inventory().count(1));
        }
        try (var runtime = start(new Flat(), save)) {
            var restored = await(runtime, frame -> frame.inventory().count(36) == 32);
            assertEquals(8, restored.inventory().selectedSlot());
            assertEquals(32, restored.inventory().count(0));
            assertTrue(runtime.offer(new WorldRuntime.ReturnCarried()));
            await(
                    runtime,
                    frame -> frame.inventory().count(36) == 0
                            && frame.inventory().count(0) == 64);
            long unblockedAt = runtime.frame().tick();
            runtime.controls(controls(false, false));
            await(runtime, frame -> frame.tick() > unblockedAt + 1);
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            await(runtime, frame -> frame.mode() == PlayerController.Mode.CREATIVE && !frame.modePending());
            runtime.controls(controls(true, false));
            assertTrue(runtime.offer(new WorldRuntime.PickCreative(1, BlockId.BEDROCK)));
            await(runtime, frame -> frame.inventory().count(1) == 64);
            long resumedAt = runtime.frame().tick();
            runtime.controls(controls(false, false));
            await(runtime, frame -> frame.tick() > resumedAt + 1);
            assertTrue(runtime.offer(new WorldRuntime.NextMode()));
            var spectator = await(runtime, frame -> frame.mode() == PlayerController.Mode.SPECTATOR);
            runtime.controls(controls(true, false));
            assertTrue(runtime.offer(new WorldRuntime.ClickSlot(1, false, false)));
            assertTrue(runtime.offer(new WorldRuntime.PickCreative(2, BlockId.STONE)));
            var later = await(runtime, frame -> frame.tick() > spectator.tick() + 3);
            assertEquals(spectator.inventory(), later.inventory());
        }
    }

    @Test
    void heldPlacementDoesNothingWhenIntentIsBlocked() {
        World world = new World();
        Chunk chunk = new Chunk();
        chunk.set(1, 2, 0, BlockId.STONE);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        BlockInteraction interaction = new BlockInteraction(world, 8);
        PlayerController player = new PlayerController();
        player.setPosition(new PlayerState.Position(5.5, 1, .5));
        var controls = new WorldRuntime.Controls(PlayerInput.NONE, 180, 0, true, false, 0, 0, true);
        var inventory = new Inventory();
        inventory.add(BlockId.DIRT);

        assertTrue(WorldRuntime.placeInventory(interaction, player, controls, inventory)
                .isEmpty());
        assertEquals(BlockId.AIR, world.getBlock(new BlockPos(2, 2, 0)).orElseThrow());
        assertEquals(1, inventory.snapshot().count(0));
    }

    @Test
    void wrongGeneratorCannotOverwriteAnExistingSave() throws Exception {
        Path save = directory.resolve("wrong.vxl");
        TestWorlds.save(save, 99, 1, new World());
        byte[] before = Files.readAllBytes(save);
        WorldRuntime runtime = start(new Flat(), save);
        assertThrows(IllegalStateException.class, runtime::close);
        assertArrayEquals(before, Files.readAllBytes(save));
    }

    private WorldRuntime start(Flat terrain, Path save) throws Exception {
        return new WorldRuntime(
                terrain,
                new WaterMesher(),
                new ViewDemand(0, 0, 1, ViewDemand.Shape.SQUARE),
                TestWorlds.prepare(save, terrain.seed(), terrain.generatorVersion()));
    }

    @Test
    void controlsMustRemainRepresentableInSavedPlayerState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRuntime.Controls(PlayerInput.NONE, Double.MAX_VALUE, 0, false, false, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRuntime.Controls(PlayerInput.NONE, 0, 91, false, false, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldRuntime.Controls(PlayerInput.NONE, 0, Double.NaN, false, false, 0));
    }

    private static WorldRuntime.Controls controls(boolean blocked, boolean paused) {
        return new WorldRuntime.Controls(PlayerInput.NONE, -90, 0, blocked, paused, 0);
    }

    private static WorldRuntime.Frame await(WorldRuntime runtime, Predicate<WorldRuntime.Frame> predicate)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            var frame = runtime.frame();
            if (frame != null && frame.ready()) runtime.activate();
            if (frame != null && runtime.state() == WorldRuntime.State.PLAYING && predicate.test(frame)) return frame;
            Thread.sleep(2);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Runtime did not publish expected state");
    }

    /** Waits for a mesh that stays current after neighbors finish loading, skipping provisional meshes. */
    private static WorldRuntime.SessionMesh awaitSettledMesh(WorldRuntime runtime, ChunkPos position) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var mesh = awaitMesh(runtime, position);
            long published = runtime.frame().tick();
            await(runtime, f -> f.tick() >= published + 10);
            if (runtime.frame().isCurrent(mesh)) return mesh;
            runtime.acknowledge(mesh, false);
        }
        throw new AssertionError("Runtime did not settle a mesh for " + position);
    }

    private static WorldRuntime.SessionMesh awaitMesh(WorldRuntime runtime, ChunkPos position) throws Exception {
        await(runtime, WorldRuntime.Frame::ready);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            runtime.frame();
            var mesh = runtime.pollMesh();
            if (mesh != null) {
                if (mesh.mesh().position().equals(position)) return mesh;
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
