package com.norbertfila.core;

import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.generation.*;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.world.*;
import com.norbertfila.platform.*;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.ui.*;
import com.norbertfila.ui.screens.*;
import com.norbertfila.world.BlockRegistry;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class WorldFlowSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3 || !Files.isDirectory(Path.of(args[0])))
            throw new IllegalArgumentException("Expected existing temp directory and first/reload");
        Path root = Path.of(args[0]).resolve("worlds");
        boolean first = switch (args[1]) {
            case "first" -> true;
            case "reload" -> false;
            default -> throw new IllegalArgumentException("Unknown mode");
        };
        boolean terrain = args.length == 3;
        if (terrain && !args[2].equals("terrain")) throw new IllegalArgumentException("Unknown scene");
        var config = TerrainConfig.defaults();
        int version = terrain ? new TerrainGenerator(0, config).generatorVersion() : 1;
        var catalog = new WorldCatalog(root, "default", version);
        try (var window = new Window(960, 540, "WORLD-05 flow smoke");
                var input = new Input(window.handle());
                var ui = new UiRenderer();
                var io = new WorldIo();
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            System.out.println("WORLD-05 mode=" + args[1] + " seed=0 scene="
                    + (terrain ? "TerrainGenerator/default" + version : "WaterTestScene/default1")
                    + " RD=2 resolution=960x540 GPU="
                    + glGetString(GL_RENDERER) + " GL=" + glGetString(GL_VERSION) + " OS="
                    + System.getProperty("os.name") + " Java=" + Runtime.version());
            var settings = new GameSettings();
            settings.setRenderDistance(2);
            var menu = new WorldMenu(io, catalog, "default", version);
            var screens = new ScreenManager();
            screens.open(menu);
            key(screens, UiKey.ACTIVATE);
            ready(screens, menu);
            if (first) {
                check(catalog.list().isEmpty(), "Fresh fixture contains worlds");
                create(screens, menu, "Świat A ąęł");
                create(screens, menu, "Świat B żźń");
                check(catalog.list().size() == 2, "Create duplicated or lost a world");
                check(
                        catalog.list().stream().allMatch(e -> {
                            try {
                                return catalog.open(e.id()).snapshot().player().isEmpty();
                            } catch (java.io.IOException failure) {
                                throw new java.io.UncheckedIOException(failure);
                            }
                        }),
                        "Creation generated a player");
            } else check(catalog.list().size() == 2, "Restart lost catalog");
            var loop = new GameLoop();
            var bounds = Game.terrainDemand(config);
            var session = new GameSession(
                    io,
                    seed -> terrain ? new TerrainGenerator(seed, config) : new WaterTestScene(),
                    new WaterMesher(),
                    new ViewDemand(
                            2,
                            terrain ? bounds.minChunkY() : 0,
                            terrain ? bounds.maxChunkY() : 1,
                            ViewDemand.Shape.SQUARE));
            List<String> order = first ? List.of("Świat A ąęł") : List.of("Świat B żźń", "Świat A ąęł");
            for (String name : order) {
                var rows = (WorldSelectionScreen) menu.screen();
                int index = 0;
                while (!rows.entries().get(index).name().equals(name)) index++;
                key(screens, UiKey.HOME);
                for (int i = 0; i < index; i++) key(screens, UiKey.DOWN);
                UUID id = rows.selectedId().orElseThrow();
                var before = catalog.open(id).snapshot();
                if (!first && name.startsWith("Świat A"))
                    check(
                            before.player().isPresent() && before.sequence() > 0,
                            "New process did not reload saved player");
                Map<UUID, byte[]> others = new HashMap<>();
                for (var row : catalog.list())
                    if (!row.id().equals(id))
                        others.put(
                                row.id(),
                                Files.readAllBytes(
                                        root.resolve(row.id().toString()).resolve("state.vxl")));
                key(screens, UiKey.ACTIVATE);
                check(
                        loop.runMenu(window, input, ui, settings, menu) instanceof Screen.Action.OpenWorld open
                                && open.id().equals(id),
                        "Selection did not emit UUID");
                try (var closer = Executors.newSingleThreadScheduledExecutor()) {
                    closer.schedule(window::requestClose, terrain ? 8 : 3, TimeUnit.SECONDS);
                    var exit = new Game().runSession(window, input, ui, textures, settings, loop, session, catalog, id);
                    check(exit == GameLoop.Exit.QUIT, "Native close was ignored");
                }
                check(session.state() == GameSession.State.MENU && session.failure() == null, "Final save failed");
                check(glGetError() == GL_NO_ERROR, "Flow GL error");
                var saved = catalog.open(id).snapshot();
                check(
                        saved.player().isPresent() && saved.sequence() > before.sequence(),
                        "No committed player snapshot");
                check(saved.fluids().tick() > before.fluids().tick(), "World clock did not resume");
                if (before.player().isPresent()) {
                    check(before.player().equals(saved.player()), "Idle reload changed saved player");
                    check(before.inventory().equals(saved.inventory()), "Reload changed inventory");
                }
                for (var entry : others.entrySet())
                    check(
                            Arrays.equals(
                                    entry.getValue(),
                                    Files.readAllBytes(
                                            root.resolve(entry.getKey().toString())
                                                    .resolve("state.vxl"))),
                            "Other world's save changed");
                glfwSetWindowShouldClose(window.handle(), false);
                menu.sessionEnded(session.failure());
                ready(screens, menu);
                check(
                        ((WorldSelectionScreen) menu.screen())
                                .entries()
                                .getFirst()
                                .id()
                                .equals(id),
                        "List did not refresh committed save ordering");
                System.out.println("PASS selected=" + name + " id=" + id + " sequence=" + saved.sequence() + " tick="
                        + saved.fluids().tick() + " player=" + saved.player().orElseThrow());
            }
            key(screens, UiKey.BACK);
            check(menu.state() == WorldMenu.State.MAIN, "Back did not return to main menu");
            window.requestClose();
            check(loop.runMenu(window, input, ui, settings, menu) == Screen.Action.QUIT, "Quit not acknowledged");
        }
        check(
                Thread.getAllStackTraces().keySet().stream()
                        .noneMatch(t -> t.isAlive()
                                && (t.getName().startsWith("voxel-")
                                        || t.getName().startsWith("chunk-")
                                        || t.getName().startsWith("far-terrain-"))),
                "Leaked workers");
        System.out.println("WORLD-05 " + args[1] + " PASS root=" + root);
    }

    private static void create(ScreenManager screens, WorldMenu menu, String name) throws Exception {
        screens.beginFrame();
        screens.layout(new UiViewport(960, 540, 960, 540));
        var button = ((WorldSelectionScreen) menu.screen()).createButton().bounds();
        screens.updatePointer(false, false, 0, 0);
        screens.updatePointer(true, true, button.x() + 2, button.y() + 2);
        screens.updatePointer(false, false, button.x() + 2, button.y() + 2);
        screens.beginFrame();
        check(menu.state() == WorldMenu.State.FORM, "Create did not open form");
        name.codePoints().forEach(cp -> screens.input(new UiEvent.Character(cp)));
        key(screens, UiKey.NEXT);
        screens.input(new UiEvent.Character('0'));
        key(screens, UiKey.ACTIVATE);
        ready(screens, menu);
    }

    private static void key(ScreenManager screens, UiKey key) {
        screens.beginFrame();
        screens.keyPress(key);
    }

    private static void ready(ScreenManager screens, WorldMenu menu) throws Exception {
        await(() -> {
            screens.beginFrame();
            return menu.state() == WorldMenu.State.SELECTING;
        });
        screens.beginFrame();
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            check(System.nanoTime() < deadline, "Timed out");
            Thread.sleep(1);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
