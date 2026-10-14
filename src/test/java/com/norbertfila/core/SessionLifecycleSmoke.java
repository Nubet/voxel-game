package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.generation.WaterTestScene;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.world.*;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.ui.*;
import com.norbertfila.ui.screens.MainMenuScreen;
import com.norbertfila.ui.screens.PauseScreen;
import com.norbertfila.world.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class SessionLifecycleSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !Files.isDirectory(Path.of(args[0])))
            throw new IllegalArgumentException("Expected an existing temporary output directory");
        Path root = Files.createTempDirectory(Path.of(args[0]), "world03-");
        var catalog = new WorldCatalog(root, "default", 1);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        catalog.create(new WorldMetadata(new WorldIdentity(a, 0, "default", 1), "A", Instant.now()));
        catalog.create(new WorldMetadata(new WorldIdentity(b, 0, "default", 1), "B", Instant.now()));
        var settings = new GameSettings();
        settings.setRenderDistance(2);
        try (var window = new Window(960, 540, "WORLD-03 lifecycle smoke");
                var input = new Input(window.handle());
                var ui = new UiRenderer();
                var io = new WorldIo();
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            System.out.println("seed=0 scene=WaterTestScene RD=2 resolution=960x540 GPU=" + glGetString(GL_RENDERER)
                    + " GL=" + glGetString(GL_VERSION) + " Java=" + Runtime.version());
            var screens = new ScreenManager();
            var viewport = new UiViewport(960, 540, 960, 540);
            var canvas = new UiCanvas();
            var loop = new GameLoop();
            var session = new GameSession(
                    io,
                    seed -> new WaterTestScene(),
                    new WaterMesher(),
                    new ViewDemand(2, 0, 1, ViewDemand.Shape.SQUARE));
            WorldRuntime.SessionMesh stale = null;
            int cycle = 0;
            for (UUID selected : List.of(a, b, a)) {
                long previousTick = catalog.open(selected).snapshot().fluids().tick();
                screens.open(new MainMenuScreen(false));
                screens.layout(viewport);
                canvas.clear();
                screens.render(canvas, viewport);
                ui.render(canvas, viewport);
                screens.keyPress(UiKey.ACTIVATE);
                check(screens.pollAction() == Screen.Action.PLAY, "Menu did not request Play");
                session.open(catalog, selected);
                loop.awaitLoad(window, input, ui, settings, session);
                check(session.runtime() != null, "World load failed: " + session.failure());
                var runtime = session.runtime();
                if (stale != null) check(!session.isCurrent(stale), "Foreign session mesh accepted");
                var renderer = new TerrainRenderer(textures);
                try (renderer) {
                    check(renderer.memoryStats().allocated() == 0, "New session retained old GPU meshes");
                    var camera = new Camera(settings);
                    boolean initializedCamera = false, uploadedView = false;
                    var pending = new HashMap<ChunkPos, WorldRuntime.SessionMesh>();
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
                    WorldRuntime.Frame frame;
                    do {
                        window.pollEvents();
                        frame = runtime.frame();
                        WorldRuntime.SessionMesh mesh;
                        while ((mesh = runtime.pollMesh()) != null) {
                            if (stale == null) stale = mesh;
                            var replaced = pending.put(mesh.mesh().position(), mesh);
                            if (replaced != null) runtime.acknowledge(replaced, false);
                            renderer.enqueueUpload(mesh.mesh());
                        }
                        var uploads = renderer.processUploads(
                                4L * 1024 * 1024,
                                2_000_000,
                                result -> runtime.frame() != null
                                        && runtime.frame().isCurrent(result));
                        for (var result : uploads.uploaded()) {
                            var issued = pending.remove(result.position());
                            if (issued != null) runtime.acknowledge(issued, true);
                        }
                        for (var result : uploads.discarded()) {
                            var issued = pending.remove(result.position());
                            if (issued != null) runtime.acknowledge(issued, false);
                        }
                        check(uploads.failed().isEmpty(), "GPU upload failed");
                        uploadedView |= uploads.uploaded().stream()
                                .anyMatch(result -> result.position().equals(runtime.initialView()));
                        if (frame != null) {
                            if (!initializedCamera) {
                                camera.restoreLook((float) frame.yaw(), (float) frame.pitch());
                                initializedCamera = true;
                            }
                            var eye = frame.interpolatedEye(System.nanoTime());
                            camera.setPosition((float) eye.x(), (float) eye.y(), (float) eye.z());
                            if (frame.ready() && uploadedView && session.state() == GameSession.State.LOADING)
                                session.activate();
                        }
                        glViewport(0, 0, 960, 540);
                        glClearColor(.45f, .65f, .85f, 1);
                        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                        var vp = camera.viewProjection(960, 540);
                        renderer.render(vp, Frustum.from(vp), camera.position());
                        check(glGetError() == GL_NO_ERROR, "Session render GL error");
                        window.swapBuffers();
                        Thread.sleep(2);
                    } while ((frame == null || frame.tick() < previousTick + 30) && System.nanoTime() < deadline);
                    check(frame != null && frame.tick() >= previousTick + 30, "Session did not advance its clock");
                    check(renderer.memoryStats().allocated() > 0, "Session did not upload GPU meshes");
                    screens.open(new PauseScreen());
                    screens.keyPress(UiKey.NEXT);
                    screens.keyPress(UiKey.NEXT);
                    screens.keyPress(UiKey.ACTIVATE);
                    check(screens.pollAction() == Screen.Action.BACK_TO_MENU, "Pause did not request menu");
                    session.saveAndQuit();
                    loop.awaitStop(window, input, ui, settings, session);
                    check(
                            session.state() == GameSession.State.MENU && session.failure() == null,
                            "Shutdown/save failed");
                }
                check(renderer.memoryStats().allocated() == 0, "Session leaked GPU mesh memory");
                long tick = catalog.open(selected).snapshot().fluids().tick();
                check(sessionWorkers() == 0, "Session leaked worker threads");
                System.out.println(
                        "PASS cycle=" + cycle++ + " world=" + selected + " tick=" + tick + " GPUBytes=0 workers=0");
            }
            screens.open(new MainMenuScreen(false));
            screens.keyPress(UiKey.NEXT);
            screens.keyPress(UiKey.ACTIVATE);
            check(screens.pollAction() == Screen.Action.QUIT, "Menu did not request Quit");
            session.open(catalog, a);
            loop.awaitLoad(window, input, ui, settings, session);
            try (var renderer = new TerrainRenderer(textures);
                    var closer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()) {
                closer.schedule(window::requestClose, 3, TimeUnit.SECONDS);
                var exit = loop.run(
                        window,
                        input,
                        new Camera(settings),
                        renderer,
                        ui,
                        new com.norbertfila.diagnostics.FrameMetrics(0, 0, 0, false, window.time()),
                        session,
                        settings);
                check(exit == GameLoop.Exit.QUIT, "Production game loop ignored window-close");
                check(
                        session.state() == GameSession.State.PLAYING,
                        "Production game loop did not activate loaded world");
                session.saveAndQuit();
                loop.awaitStop(window, input, ui, settings, session);
            }
            check(session.state() == GameSession.State.MENU && session.failure() == null, "Window-close save failed");
            catalog.open(a);
            check(sessionWorkers() == 0, "Window-close leaked workers");
            check(glGetError() == GL_NO_ERROR, "Shutdown GL error");
            System.out.println(
                    "WORLD-03 lifecycle smoke PASS: A/B/A, GPU cleanup, save/reload, stale mesh, window-close root="
                            + root);
        }
    }

    private static long sessionWorkers() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive()
                        && (thread.getName().equals("voxel-world")
                                || thread.getName().startsWith("pool-")))
                .count();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
