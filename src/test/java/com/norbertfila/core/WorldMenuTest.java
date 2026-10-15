package com.norbertfila.core;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.persistence.world.*;
import com.norbertfila.ui.*;
import com.norbertfila.ui.screens.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldMenuTest {
    @TempDir
    Path directory;

    @Test
    void optionsShareLanguageWithTheGameAndReturnToMainMenu() {
        var settings = new GameSettings();
        try (var io = new WorldIo()) {
            var menu = new WorldMenu(
                    io, new WorldCatalog(directory.resolve("worlds"), "default", 11), "default", 11, settings);
            key(menu, UiKey.NEXT);
            key(menu, UiKey.NEXT);
            key(menu, UiKey.ACTIVATE);
            assertEquals(WorldMenu.State.SETTINGS, menu.state());
            var screen = assertInstanceOf(SettingsScreen.class, menu.screen());
            var box = screen.languageButton().bounds();
            menu.update();
            menu.updatePointer(false, false, 0, 0);
            menu.updatePointer(true, true, box.x() + 1, box.y() + 1);
            menu.updatePointer(false, false, box.x() + 1, box.y() + 1);
            assertEquals(
                    com.norbertfila.i18n.Language.POLISH,
                    settings.translations().language());
            key(menu, UiKey.BACK);
            assertEquals(WorldMenu.State.MAIN, menu.state());
            assertInstanceOf(MainMenuScreen.class, menu.screen());
            assertFalse(Files.exists(directory.resolve("worlds")));
        }
    }

    @Test
    void createSelectFailureBackAndRestartRetainWorldAndTypedIdentity() throws Exception {
        var catalog = new WorldCatalog(directory.resolve("worlds"), "default", 11);
        try (var io = new WorldIo()) {
            var menu = new WorldMenu(io, catalog, "default", 11);
            select(menu);
            assertFalse(Files.exists(directory.resolve("worlds")));
            form(menu);
            "Zażółć gęślą jaźń".codePoints().forEach(menu::textInput);
            key(menu, UiKey.NEXT);
            "-9223372036854775808".codePoints().forEach(menu::textInput);
            key(menu, UiKey.ACTIVATE);
            for (int i = 0; i < 100; i++) menu.keyPress(UiKey.ACTIVATE);
            await(() -> {
                menu.update();
                return menu.state() == WorldMenu.State.SELECTING;
            });
            var entries = ((WorldSelectionScreen) menu.screen()).entries();
            assertEquals(1, entries.size());
            var metadata = entries.getFirst().metadata().orElseThrow();
            assertEquals(Long.MIN_VALUE, metadata.identity().seed());
            assertEquals("Zażółć gęślą jaźń", metadata.name());
            var bytes = Files.readAllBytes(directory
                    .resolve("worlds")
                    .resolve(metadata.identity().id().toString())
                    .resolve("state.vxl"));
            key(menu, UiKey.ACTIVATE);
            assertEquals(new Screen.Action.OpenWorld(metadata.identity().id()), menu.update());
            menu.sessionEnded(new java.io.IOException("Injected load failure"));
            assertInstanceOf(WorldErrorScreen.class, menu.screen());
            key(menu, UiKey.BACK);
            await(() -> {
                menu.update();
                return menu.state() == WorldMenu.State.SELECTING;
            });
            assertEquals(1, catalog.list().size());
            assertArrayEquals(
                    bytes,
                    Files.readAllBytes(directory
                            .resolve("worlds")
                            .resolve(metadata.identity().id().toString())
                            .resolve("state.vxl")));
            var restarted =
                    new WorldMenu(io, new WorldCatalog(directory.resolve("worlds"), "default", 11), "default", 11);
            select(restarted);
            assertEquals(entries, ((WorldSelectionScreen) restarted.screen()).entries());
        }
    }

    @Test
    void scanAndCreationErrorsAreVisibleAndFormCanBeCorrectedWithoutDataLoss() throws Exception {
        Path root = directory.resolve("worlds");
        Files.writeString(root, "keep me");
        var catalog = new WorldCatalog(root, "default", 11);
        try (var io = new WorldIo()) {
            var menu = new WorldMenu(io, catalog, "default", 11);
            key(menu, UiKey.ACTIVATE);
            await(() -> {
                menu.update();
                return menu.state() == WorldMenu.State.ERROR;
            });
            assertInstanceOf(WorldErrorScreen.class, menu.screen());
            key(menu, UiKey.BACK);
            assertEquals(WorldMenu.State.MAIN, menu.state());
            Files.delete(root);
            select(menu);
            form(menu);
            "Nazwa ą".codePoints().forEach(menu::textInput);
            Files.writeString(root, "keep me");
            key(menu, UiKey.ACTIVATE);
            await(() -> {
                menu.update();
                return menu.state() == WorldMenu.State.ERROR;
            });
            assertEquals("keep me", Files.readString(root));
            key(menu, UiKey.BACK);
            assertEquals(
                    "Nazwa ą", ((CreateWorldScreen) menu.screen()).nameField().text());
            Files.delete(root);
            key(menu, UiKey.ACTIVATE);
            await(() -> {
                menu.update();
                return menu.state() == WorldMenu.State.SELECTING;
            });
            assertEquals(1, catalog.list().size());
        }
    }

    @Test
    void cancelledQueuedScanDrainsAndLateResultCannotReopenSelection() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 11);
        var metadata = new WorldMetadata(new WorldIdentity(UUID.randomUUID(), 0, "default", 11), "Gate", Instant.now());
        catalog.create(metadata);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var io = blockedIo(entered, release)) {
            var snapshot = catalog.open(metadata.identity().id()).snapshot();
            var next = next(snapshot);
            var gate = io.write(
                    directory.resolve(metadata.identity().id().toString()).resolve("state.vxl"), next);
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var menu = new WorldMenu(io, catalog, "default", 11);
                key(menu, UiKey.ACTIVATE);
                key(menu, UiKey.BACK);
                for (int i = 0; i < 100; i++) key(menu, UiKey.ACTIVATE);
                assertEquals(WorldMenu.State.SCANNING, menu.state());
                release.countDown();
                gate.get(5, TimeUnit.SECONDS);
                await(() -> {
                    menu.update();
                    return menu.state() == WorldMenu.State.MAIN;
                });
                assertInstanceOf(MainMenuScreen.class, menu.screen());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void nativeCloseDrainsCommittedCreationAndDoesNotOpenANewSession() throws Exception {
        var catalog = new WorldCatalog(directory, "default", 11);
        var metadata = new WorldMetadata(new WorldIdentity(UUID.randomUUID(), 0, "default", 11), "Gate", Instant.now());
        catalog.create(metadata);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var io = blockedIo(entered, release)) {
            var menu = new WorldMenu(io, catalog, "default", 11);
            select(menu);
            form(menu);
            "Świat po zamknięciu".codePoints().forEach(menu::textInput);
            var gate = io.write(
                    directory.resolve(metadata.identity().id().toString()).resolve("state.vxl"),
                    next(catalog.open(metadata.identity().id()).snapshot()));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                key(menu, UiKey.ACTIVATE);
                assertEquals(WorldMenu.State.CREATING, menu.state());
                menu.requestClose();
                assertEquals(Screen.Action.NONE, menu.update());
                assertEquals(WorldMenu.State.CREATING, menu.state());
                release.countDown();
                gate.get(5, TimeUnit.SECONDS);
                await(() -> menu.update() == Screen.Action.QUIT);
                assertEquals(WorldMenu.State.CLOSED, menu.state());
                assertEquals(2, catalog.list().size());
                assertTrue(catalog.list().stream().allMatch(e -> e.status() == WorldEntry.Status.READY));
            } finally {
                release.countDown();
            }
        }
    }

    private static WorldSnapshot next(WorldSnapshot snapshot) {
        return new WorldSnapshot(
                snapshot.identity(),
                1,
                Instant.now(),
                snapshot.deltas(),
                snapshot.fluids(),
                snapshot.inventory(),
                java.util.Optional.of(new SavedPlayerState(
                        0, 4, 0, 0, 0, com.norbertfila.player.PlayerController.Mode.SURVIVAL, false)));
    }

    static void key(WorldMenu menu, UiKey key) {
        menu.update();
        menu.keyPress(key);
    }

    static void select(WorldMenu menu) throws Exception {
        key(menu, UiKey.ACTIVATE);
        await(() -> {
            menu.update();
            return menu.state() == WorldMenu.State.SELECTING;
        });
    }

    static void form(WorldMenu menu) {
        menu.update();
        menu.layout(new UiViewport(320, 240, 320, 240));
        var button = ((WorldSelectionScreen) menu.screen()).createButton().bounds();
        menu.updatePointer(false, false, 0, 0);
        menu.updatePointer(true, true, button.x() + 2, button.y() + 2);
        menu.updatePointer(false, false, button.x() + 2, button.y() + 2);
        menu.update();
        assertEquals(WorldMenu.State.FORM, menu.state());
    }

    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) fail("Timed out");
            Thread.sleep(1);
        }
    }

    private static WorldIo blockedIo(CountDownLatch entered, CountDownLatch release) {
        return new WorldIo((path, snapshot) -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Gate timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException(e);
            }
            WorldStateCodec.write(path, snapshot);
        });
    }
}
