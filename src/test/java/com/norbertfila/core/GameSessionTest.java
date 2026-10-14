package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.*;
import com.norbertfila.generation.ChunkData;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.world.*;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameSessionTest {
    @TempDir
    Path directory;

    @Test
    void aToBToARestoresPlayerAndRejectsForeignOutcomesWithoutLeakingWorkers() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 1);
        var a = create(catalog, 1, 42);
        var b = create(catalog, 2, -42);
        try (var io = new WorldIo()) {
            var demand = new ViewDemand(0, -1, 1, ViewDemand.Shape.SQUARE);
            var session = new GameSession(io, Flat::new, new WaterMesher(), demand);
            session.open(catalog, a.identity().id());
            assertThrows(
                    IllegalStateException.class,
                    () -> session.open(catalog, b.identity().id()));
            await(() -> {
                session.poll();
                return session.runtime() != null
                        && session.runtime().frame() != null
                        && session.runtime().frame().ready();
            });
            assertEquals(GameSession.State.LOADING, session.state());
            assertEquals(0, session.runtime().frame().tick());
            var stale = session.runtime().pollMesh();
            assertNotNull(stale);
            assertTrue(session.isCurrent(stale));
            long firstSessionId = stale.sessionId();
            assertTrue(session.activate());
            session.runtime().controls(new WorldRuntime.Controls(PlayerInput.NONE, 137.5, -31.25, true, false, 2));
            await(() -> session.runtime().frame().tick() >= 3);
            session.saveAndQuit();
            finish(session);
            var storedA = catalog.open(a.identity().id()).snapshot();
            assertEquals(0, demand.horizontalRadius());
            assertEquals(137.5f, storedA.player().orElseThrow().yaw());
            assertEquals(-31.25f, storedA.player().orElseThrow().pitch());
            assertTrue(storedA.player().orElseThrow().y() >= 1);
            assertEquals(0, worldThreads());
            session.open(catalog, b.identity().id());
            ready(session);
            assertFalse(session.isCurrent(stale));
            assertNotEquals(firstSessionId, session.runtime().sessionId());
            session.runtime().acknowledge(stale, true);
            assertTrue(session.activate());
            session.saveAndQuit();
            finish(session);
            assertEquals(storedA, catalog.open(a.identity().id()).snapshot());
            session.open(catalog, a.identity().id());
            ready(session);
            var restored = session.runtime().frame();
            assertEquals(storedA.fluids().tick(), restored.tick());
            assertEquals(
                    storedA.player().orElseThrow().x(),
                    restored.playerState().position().x());
            assertEquals(
                    storedA.player().orElseThrow().y(),
                    restored.playerState().position().y());
            assertEquals(137.5, restored.yaw());
            assertEquals(-31.25, restored.pitch());
            assertEquals(0, restored.playerState().velocityX());
            assertTrue(session.activate());
            session.saveAndQuit();
            finish(session);
            assertEquals(0, worldThreads());
        }
    }

    @Test
    void failedFinalSaveRetainsSnapshotForRetryOrExplicitDiscard() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 1);
        var a = create(catalog, 1, 42);
        var fail = new AtomicBoolean(true);
        var calls = new AtomicInteger();
        try (var io = new WorldIo((path, snapshot) -> {
            calls.incrementAndGet();
            if (fail.get()) throw new IOException("Injected failure");
            WorldStateCodec.write(path, snapshot);
        })) {
            var session = session(io);
            session.open(catalog, a.identity().id());
            ready(session);
            session.activate();
            await(() -> session.runtime().frame().tick() >= 2);
            session.saveAndQuit();
            await(() -> {
                session.poll();
                return session.state() == GameSession.State.SAVE_ERROR;
            });
            var retained = session.runtime().retainedSnapshot();
            assertNotNull(retained);
            assertEquals(0, catalog.open(a.identity().id()).snapshot().sequence());
            for (int i = 0; i < 100; i++) session.poll();
            assertEquals(1, calls.get());
            assertThrows(
                    IllegalStateException.class,
                    () -> session.open(catalog, a.identity().id()));
            fail.set(false);
            session.retrySave();
            finish(session);
            assertEquals(retained, catalog.open(a.identity().id()).snapshot());

            fail.set(true);
            session.open(catalog, a.identity().id());
            ready(session);
            session.activate();
            session.saveAndQuit();
            await(() -> {
                session.poll();
                return session.state() == GameSession.State.SAVE_ERROR;
            });
            session.discardChanges();
            finish(session);
            assertEquals(retained, catalog.open(a.identity().id()).snapshot());
            assertEquals(0, worldThreads());
        }
    }

    @Test
    void cancellationDrainsIoAndNeverPublishesOrSavesTheCancelledSession() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 1);
        var a = create(catalog, 1, 42);
        var loaded = catalog.open(a.identity().id());
        var blocker = create(catalog, 2, 42);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var io = new WorldIo((path, snapshot) -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timeout");
            } catch (InterruptedException interrupted) {
                throw new IOException(interrupted);
            }
            WorldStateCodec.write(path, snapshot);
        })) {
            var initial = catalog.open(blocker.identity().id());
            var snapshot = new WorldSnapshot(
                    blocker.identity(),
                    1,
                    Instant.ofEpochMilli(1),
                    List.of(),
                    com.norbertfila.fluid.FluidSnapshot.EMPTY,
                    com.norbertfila.player.InventorySnapshot.EMPTY,
                    Optional.of(new SavedPlayerState(.5, 1, .5, 0, 0, Mode.SURVIVAL, false)));
            var writing = io.write(initial.statePath(), snapshot);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var session = session(io);
            session.open(catalog, a.identity().id());
            session.cancelLoading();
            assertThrows(
                    IllegalStateException.class,
                    () -> session.open(catalog, a.identity().id()));
            release.countDown();
            writing.get(5, TimeUnit.SECONDS);
            finish(session);
            assertNull(session.runtime());
            assertEquals(loaded.snapshot(), catalog.open(a.identity().id()).snapshot());
            assertEquals(0, worldThreads());
        } finally {
            release.countDown();
        }
    }

    @Test
    void missingAndCorruptLoadsReturnToMenuWithoutWritingAnything() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 1);
        var a = create(catalog, 1, 42);
        Path state = catalog.open(a.identity().id()).statePath();
        try (var io = new WorldIo()) {
            var session = session(io);
            Files.write(state, new byte[] {1});
            session.open(catalog, a.identity().id());
            finish(session);
            assertNotNull(session.failure());
            assertArrayEquals(new byte[] {1}, Files.readAllBytes(state));
            Files.delete(state);
            session.open(catalog, a.identity().id());
            finish(session);
            assertNotNull(session.failure());
            assertFalse(Files.exists(state));
        }
    }

    @Test
    void restoredCreativeFlightAndInventorySurviveWithoutMotionOrInputGestures() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 1);
        var a = create(catalog, 1, 42);
        var loaded = catalog.open(a.identity().id());
        var inventory = new com.norbertfila.player.Inventory();
        inventory.creativePick(8, BlockId.POPPY);
        inventory.select(8);
        inventory.click(8, true, false);
        var player = new SavedPlayerState(32.125, 7.25, -16.75, 71, 23, Mode.CREATIVE, true);
        WorldStateCodec.write(
                loaded.statePath(),
                new WorldSnapshot(
                        a.identity(),
                        1,
                        Instant.ofEpochMilli(1),
                        List.of(),
                        new com.norbertfila.fluid.FluidSnapshot(100, List.of()),
                        inventory.snapshot(),
                        Optional.of(player)));
        try (var io = new WorldIo()) {
            var session = session(io);
            session.open(catalog, a.identity().id());
            ready(session);
            var frame = session.runtime().frame();
            assertEquals(Mode.CREATIVE, frame.mode());
            assertTrue(frame.flying());
            assertEquals(inventory.snapshot(), frame.inventory());
            assertEquals(player.x(), frame.playerState().position().x());
            assertEquals(player.y(), frame.playerState().position().y());
            assertEquals(player.z(), frame.playerState().position().z());
            assertEquals(0, frame.playerState().velocityY());
            session.cancelLoading();
            finish(session);
            assertEquals(
                    player, catalog.open(a.identity().id()).snapshot().player().orElseThrow());
        }
    }

    @Test
    void restoredPoseFitsLowCeilingAndObstructedPositionDoesNotOverwriteSave() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 1);
        var metadata = create(catalog, 1, 42);
        Path path = catalog.open(metadata.identity().id()).statePath();
        var world = new World();
        world.setBlock(new BlockPos(0, 3, 0), BlockId.STONE, 64);
        var snapshot = WorldSnapshots.capture(
                metadata.identity(),
                1,
                world,
                com.norbertfila.fluid.FluidSnapshot.EMPTY,
                com.norbertfila.player.InventorySnapshot.EMPTY,
                new SavedPlayerState(.5, 1.5, .5, 0, 0, Mode.SURVIVAL, false));
        WorldStateCodec.write(path, snapshot);
        try (var io = new WorldIo()) {
            var session = session(io);
            session.open(catalog, metadata.identity().id());
            ready(session);
            assertTrue(session.runtime().frame().playerState().crouching());
            session.cancelLoading();
            finish(session);
            var obstructed = new WorldSnapshot(
                    metadata.identity(),
                    2,
                    Instant.ofEpochMilli(2),
                    snapshot.deltas(),
                    snapshot.fluids(),
                    snapshot.inventory(),
                    Optional.of(new SavedPlayerState(.5, 0, .5, 0, 0, Mode.SURVIVAL, false)));
            WorldStateCodec.write(path, obstructed);
            byte[] before = Files.readAllBytes(path);
            session.open(catalog, metadata.identity().id());
            finish(session);
            assertNotNull(session.failure());
            assertArrayEquals(before, Files.readAllBytes(path));
        }
    }

    private static GameSession session(WorldIo io) {
        return new GameSession(io, Flat::new, new WaterMesher(), new ViewDemand(0, -1, 1, ViewDemand.Shape.SQUARE));
    }

    private static WorldMetadata create(WorldCatalog catalog, long id, long seed) throws Exception {
        var metadata =
                new WorldMetadata(new WorldIdentity(new UUID(0, id), seed, "default", 1), "World " + id, Instant.EPOCH);
        catalog.create(metadata);
        return metadata;
    }

    private static void ready(GameSession session) throws Exception {
        await(() -> {
            session.poll();
            return session.runtime() != null
                    && session.runtime().frame() != null
                    && session.runtime().frame().ready();
        });
    }

    private static void finish(GameSession session) throws Exception {
        await(() -> {
            session.poll();
            return session.state() == GameSession.State.MENU;
        });
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        assertTrue(condition.getAsBoolean());
    }

    private static long worldThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive()
                        && (t.getName().equals("voxel-world") || t.getName().startsWith("pool-")))
                .count();
    }

    private record Flat(long seed) implements TerrainSource {
        public int generatorVersion() {
            return 1;
        }

        public int surfaceHeight(int x, int z) {
            return 0;
        }

        public TerrainSummary sample(int x, int z) {
            return new TerrainSummary(0, BlockId.STONE, BlockId.STONE, BlockId.STONE);
        }

        public ChunkData generate(ChunkPos position) {
            byte[] data = new byte[Chunk.BLOCK_BYTES];
            for (int y = 0; y < 16; y++)
                if ((long) position.y() * 16 + y <= 0)
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++) data[(y * 16 + z) * 16 + x] = BlockId.STONE;
            return new ChunkData(position, data);
        }
    }
}
