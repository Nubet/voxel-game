package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.diagnostics.FrameProfiler;
import com.norbertfila.diagnostics.FrameProfiler.Segment;
import com.norbertfila.interaction.BlockBreaking;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.render.BlockEffectsRenderer;
import com.norbertfila.render.BlockOutlineRenderer;
import com.norbertfila.render.CloudRenderer;
import com.norbertfila.render.GpuFrameTimer;
import com.norbertfila.render.ItemEntityRenderer;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.render.UiRenderer;
import com.norbertfila.render.UnderwaterVisibility;
import com.norbertfila.simulation.FixedSimulation;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.ui.Hud;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.ScreenManager;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiEvent;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.ui.screens.InventoryScreen;
import com.norbertfila.ui.screens.PauseScreen;
import com.norbertfila.ui.screens.SaveErrorScreen;
import com.norbertfila.ui.screens.SessionStatusScreen;
import com.norbertfila.ui.screens.SettingsScreen;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GameLoop {
    private static final double MAX_DELTA_SECONDS = .1;
    private static final long FAR_UPLOAD_BYTES = 2L * 1024 * 1024;
    private static final long FAR_UPLOAD_NANOS = 1_000_000;
    // Far terrain is clipped inside the near area, which is at least two chunks wide, so its near plane can sit far
    // out for depth precision.

    public enum Exit {
        BACK_TO_MENU,
        QUIT
    }

    /** Scripted replacement for player look and movement, used by the game benchmark. */
    interface FrameDriver {
        /** Positions the camera and issues world commands for the current frame. */
        void update(WorldRuntime runtime, WorldRuntime.Frame state, Camera camera);

        /** Observes a completed frame; returns true when the session should end. */
        boolean frameCompleted(
                WorldRuntime runtime, WorldRuntime.Frame state, TerrainRenderer.RenderStats stats, int uploadQueue);
    }

    public Exit run(
            Window window,
            Input input,
            Camera camera,
            TerrainRenderer renderer,
            UiRenderer uiRenderer,
            FrameMetrics metrics,
            GameSession session,
            GameSettings settings) {
        return run(
                window,
                input,
                camera,
                renderer,
                uiRenderer,
                metrics,
                session,
                settings,
                null,
                FrameProfiler.disabled());
    }

    Exit run(
            Window window,
            Input input,
            Camera camera,
            TerrainRenderer renderer,
            UiRenderer uiRenderer,
            FrameMetrics metrics,
            GameSession session,
            GameSettings settings,
            FrameDriver driver,
            FrameProfiler profiler) {
        try (GpuFrameTimer gpuTimer =
                        profiler.gpuTiming() ? new GpuFrameTimer(FrameProfiler.GpuPass.values().length) : null;
                BlockOutlineRenderer outline = new BlockOutlineRenderer();
                BlockEffectsRenderer effects = new BlockEffectsRenderer(renderer.blockTextures());
                ItemEntityRenderer items = new ItemEntityRenderer(renderer.blockTextures());
                CloudRenderer clouds = new CloudRenderer()) {
            return run(
                    window,
                    input,
                    camera,
                    renderer,
                    uiRenderer,
                    metrics,
                    session,
                    settings,
                    driver,
                    profiler,
                    gpuTimer,
                    outline,
                    effects,
                    items,
                    clouds);
        }
    }

    private Exit run(
            Window window,
            Input input,
            Camera camera,
            TerrainRenderer renderer,
            UiRenderer uiRenderer,
            FrameMetrics metrics,
            GameSession session,
            GameSettings settings,
            FrameDriver driver,
            FrameProfiler profiler,
            GpuFrameTimer gpuTimer,
            BlockOutlineRenderer outline,
            BlockEffectsRenderer effects,
            ItemEntityRenderer items,
            CloudRenderer clouds) {
        FrameProfiler.GpuPass[] gpuPasses = FrameProfiler.GpuPass.values();
        GpuFrameTimer.Listener gpuListener = (pass, nanos) -> profiler.recordGpu(gpuPasses[pass], nanos);
        long frameStarted = System.nanoTime();
        WorldRuntime runtime = session.runtime();
        double previous = window.time();
        Map<ChunkPos, Long> residents = Map.of();
        long residencyRevision = 0;
        Map<ChunkPos, WorldRuntime.SessionMesh> pending = new HashMap<>();
        UnderwaterVisibility visibility = new UnderwaterVisibility();
        UiCanvas canvas = new UiCanvas();
        Hud hud = new Hud();
        ScreenManager screens = new ScreenManager();
        SettingsScreen settingsScreen = new SettingsScreen(settings);
        PauseScreen pauseScreen = new PauseScreen(settings.translations());
        boolean settingsFromPause = false;
        input.discardTransientInput();
        window.setCursorCaptured(false);
        var loading = new SessionStatusScreen(settings.translations().text("status.loading"));
        boolean pointerReady = !input.leftMouseDown() && !input.rightMouseDown();
        Hotbar hotbar = new Hotbar();
        var dropKeys = new ArrayDeque<Boolean>();
        long placePressId = 0;
        boolean inventoryInitialized = false;
        boolean initialViewUploaded = false;
        String hardware =
                System.getProperty("os.name") + " " + System.getProperty("os.arch") + " Java " + Runtime.version();
        int[] width = new int[1], height = new int[1];
        int[] windowWidth = new int[1], windowHeight = new int[1];
        while (!window.shouldClose() && !Thread.currentThread().isInterrupted()) {
            profiler.beginFrame();
            screens.beginFrame();
            Screen previousScreen = screens.current();
            int previousDistance = settings.renderDistance();
            window.pollEvents();
            double now = window.time(), delta = clampDelta(now - previous);
            previous = now;
            input.update();
            if (!input.leftMouseDown() && !input.rightMouseDown()) pointerReady = true;
            WorldRuntime.Frame state = runtime.frame();
            if (state != null) {
                hotbar.inventory(state.inventory());
                if (!inventoryInitialized) {
                    camera.restoreLook((float) state.yaw(), (float) state.pitch());
                    hotbar.select(state.inventory().selectedSlot());
                    inventoryInitialized = true;
                }
            }
            int previousSlot = hotbar.selectedSlot();
            int scroll = input.hotbarScrollSteps();
            if (input.focused() && input.settingsPressed()) {
                if (screens.isOpen()) screens.close();
                else screens.open(settingsScreen);
            }
            window.framebufferSize(width, height);
            window.windowSize(windowWidth, windowHeight);
            boolean minimized = width[0] <= 0 || height[0] <= 0 || windowWidth[0] <= 0 || windowHeight[0] <= 0;
            UiViewport viewport = minimized
                    ? null
                    : new UiViewport(windowWidth[0], windowHeight[0], width[0], height[0], settings.guiScale());
            if (viewport != null) screens.layout(viewport);
            boolean inventoryPressed = input.inventoryPressed();
            if (!minimized && input.focused() && inventoryPressed && !input.settingsPressed()) {
                if (screens.current() instanceof InventoryScreen) screens.close();
                else if (!screens.isOpen()
                        && state != null
                        && !state.modePending()
                        && state.mode() != PlayerController.Mode.SPECTATOR)
                    screens.open(new InventoryScreen(
                            state.mode() == PlayerController.Mode.CREATIVE, settings.translations()));
            }
            if (screens.current() instanceof InventoryScreen inventoryScreen && state != null) {
                if (state.modePending()
                        || state.mode() == PlayerController.Mode.SPECTATOR
                        || inventoryScreen.creative() != (state.mode() == PlayerController.Mode.CREATIVE))
                    screens.close();
                else if (!minimized) {
                    if (input.focused() && inventoryScreen.creative()) {
                        if (input.hotbarSlotPressed() >= 0) hotbar.select(input.hotbarSlotPressed());
                        else hotbar.scroll(scroll);
                    }
                    inventoryScreen.update(state.inventory(), hotbar.selectedSlot(), viewport);
                }
            }
            if (!minimized && input.focused()) {
                screens.updatePointer(new Screen.Pointer(
                        input.leftMouseDown(),
                        input.leftMousePressed(),
                        input.rightMouseDown(),
                        input.rightMousePressed(),
                        input.shiftHeld(),
                        viewport.cursorX(input.cursorX()),
                        viewport.cursorY(input.cursorY()),
                        now));
            } else screens.cancelInteraction();
            UiEvent event;
            while ((event = input.pollUiEvent()) != null) {
                if (!minimized && input.focused()) {
                    if (event instanceof UiEvent.Key(var key) && key == UiKey.BACK) {
                        if (runtime.state() == WorldRuntime.State.LOADING) return Exit.BACK_TO_MENU;
                        if (!screens.isOpen()) screens.open(pauseScreen);
                        else screens.input(event);
                    } else if (event instanceof UiEvent.Key(var key)
                            && (key == UiKey.DROP_ONE || key == UiKey.DROP_STACK)
                            && !screens.blocksGameplay()) dropKeys.addLast(key == UiKey.DROP_STACK);
                    else screens.input(event);
                }
            }
            if (settingsFromPause && previousScreen == settingsScreen && !screens.isOpen()) {
                screens.open(pauseScreen);
                settingsFromPause = false;
            }
            if (previousScreen != screens.current()) {
                window.setCursorCaptured(!screens.isOpen());
                input.resetMouseTracking();
            }
            if (previousDistance != settings.renderDistance())
                window.setTitle("Voxel Engine | Render distance: " + settings.renderDistance());
            if ((previousScreen != screens.current() || !input.focused())
                    && (input.leftMouseDown() || input.rightMouseDown())) pointerReady = false;
            boolean blocked = minimized
                    || screens.blocksGameplay()
                    || !input.focused()
                    || state == null
                    || !pointerReady
                    || runtime.state() != WorldRuntime.State.PLAYING;
            int slotPressed = input.hotbarSlotPressed();
            if (!blocked) {
                if (slotPressed >= 0) hotbar.select(slotPressed);
                else hotbar.scroll(scroll);
                if (input.rightMousePressed()) placePressId++;
            }
            camera.updateZoom(!blocked && input.zoomHeld(), delta);
            if (blocked) input.clearPlayerInput();
            else camera.updateLook(input.mouseDeltaX(), input.mouseDeltaY());
            PlayerInput held = input.peekPlayerTick();
            if (state != null)
                runtime.controls(new WorldRuntime.Controls(
                        new PlayerInput(
                                held.forward(),
                                held.backward(),
                                held.left(),
                                held.right(),
                                held.jumpHeld(),
                                held.sneakHeld(),
                                held.sprintHeld()),
                        camera.yaw(),
                        camera.pitch(),
                        blocked,
                        minimized
                                || screens.current() instanceof PauseScreen
                                || screens.current() instanceof SettingsScreen,
                        settings.renderDistance(),
                        placePressId,
                        !blocked && input.rightMouseDown(),
                        settings.farDistance(),
                        settings.simulationDistance(),
                        !blocked && input.leftMouseDown()));
            if (previousSlot != hotbar.selectedSlot())
                submit(runtime, new WorldRuntime.SelectSlot(hotbar.selectedSlot()));
            Screen.Action uiAction;
            while ((uiAction = screens.pollAction()) != null) {
                if (uiAction == Screen.Action.BACK_TO_MENU) return Exit.BACK_TO_MENU;
                if (uiAction == Screen.Action.OPTIONS) {
                    settingsFromPause = screens.current() == pauseScreen;
                    screens.open(settingsScreen);
                }
                switch (uiAction) {
                    case Screen.Action.SlotClick click ->
                        submit(runtime, new WorldRuntime.ClickSlot(click.slot(), click.right(), click.quickMove()));
                    case Screen.Action.CreativePick pick ->
                        submit(runtime, new WorldRuntime.PickCreative(pick.slot(), pick.block()));
                    case Screen.Action.DropSlot drop -> {
                        if (!minimized && input.focused())
                            submit(
                                    runtime,
                                    new WorldRuntime.DropItem(
                                            drop.slot(), drop.wholeStack(), true, camera.yaw(), camera.pitch()));
                    }
                    default -> {}
                }
            }
            if (previousScreen instanceof InventoryScreen && previousScreen != screens.current())
                submit(runtime, new WorldRuntime.ReturnCarried());
            if (!blocked && input.toggleModePressed()) {
                submit(runtime, new WorldRuntime.NextMode());
                input.clearPlayerInput();
            }
            if (!blocked && input.peekPlayerTick().jumpPressed()) {
                if (runtime.offer(new WorldRuntime.Jump(input.peekPlayerTick().jumpPressedAtSeconds())))
                    input.nextPlayerTick();
            }
            boolean editingBlocked = blocked
                    || input.toggleModePressed()
                    || state == null
                    || state.modePending()
                    || state.mode() == PlayerController.Mode.SPECTATOR;
            while (!dropKeys.isEmpty()) {
                boolean wholeStack = dropKeys.removeFirst();
                if (!editingBlocked)
                    submit(
                            runtime,
                            new WorldRuntime.DropItem(
                                    hotbar.selectedSlot(), wholeStack, false, camera.yaw(), camera.pitch()));
            }
            editCommand(hotbar, editingBlocked, input.leftMousePressed(), false, -1, 0, camera.yaw(), camera.pitch())
                    .ifPresent(action -> submit(runtime, action));
            if (minimized) {
                if (!sleepWhileIdle()) return Exit.QUIT;
                continue;
            }
            profiler.mark(Segment.INPUT);
            WorldRuntime.SessionMesh mesh;
            while ((mesh = runtime.pollMesh()) != null) {
                var replaced = pending.put(mesh.mesh().position(), mesh);
                if (replaced != null) runtime.acknowledge(replaced, false);
                renderer.enqueueUpload(mesh.mesh());
            }
            state = runtime.frame();
            if (state != null) {
                if (state.residency().revision() != residencyRevision) {
                    var current = state.residents();
                    for (var entry : residents.entrySet()) {
                        if (entry.getValue().equals(current.get(entry.getKey()))) continue;
                        var removed = pending.remove(entry.getKey());
                        if (removed != null) runtime.acknowledge(removed, false);
                        renderer.remove(entry.getKey());
                    }
                    residents = current;
                    residencyRevision = state.residency().revision();
                }
                var eye = state.interpolatedEye(System.nanoTime());
                camera.setPosition(eye.x(), eye.y(), eye.z());
                renderer.far().reconcile(state.far(), eye.x(), eye.z());
            }
            if (driver != null) driver.update(runtime, state, camera);
            profiler.mark(Segment.RECONCILE);
            var uploads = renderer.processUploads(4L * 1024 * 1024, 2_000_000, result -> {
                var current = runtime.frame();
                return current != null && current.isCurrent(result);
            });
            for (var uploaded : uploads.uploaded()) {
                acknowledge(runtime, pending, uploaded, true);
            }
            for (var failed : uploads.failed()) {
                acknowledge(runtime, pending, failed, false);
            }
            for (var discarded : uploads.discarded()) {
                acknowledge(runtime, pending, discarded, false);
            }
            initialViewUploaded |= uploads.uploaded().stream()
                    .anyMatch(result -> result.position().equals(runtime.initialView()));
            if (runtime.state() == WorldRuntime.State.LOADING && initialViewUploaded && session.activate()) {
                input.discardTransientInput();
                window.setCursorCaptured(!screens.isOpen());
            }
            renderer.far().upload(FAR_UPLOAD_BYTES, FAR_UPLOAD_NANOS);
            metrics.recordUploads(uploads.bytesUploaded(), uploads.queueDepth(), uploads.uploadNanos());
            metrics.recordResidentChunks(residents.size());
            profiler.mark(Segment.UPLOAD);
            if (state != null)
                visibility.update(
                        state.waterContact().eyesSubmerged(),
                        state.waterContact().available(),
                        delta);
            if (gpuTimer != null) gpuTimer.beginFrame(gpuListener);
            glViewport(0, 0, width[0], height[0]);
            glClearColor(visibility.backgroundRed(), visibility.backgroundGreen(), visibility.backgroundBlue(), 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            var viewProjection =
                    camera.relativeViewProjection(width[0], height[0], .1f, farPlane(settings.farDistance()));
            if (state != null) {
                renderer.far()
                        .renderOpaque(
                                viewProjection,
                                camera.x(),
                                camera.y(),
                                camera.z(),
                                visibility.amount(),
                                state.coverage());
            }
            if (gpuTimer != null) gpuTimer.stamp();
            var opaque = renderer.renderOpaque(viewProjection, camera.x(), camera.y(), camera.z(), visibility.amount());
            if (gpuTimer != null) gpuTimer.stamp();
            double effectSeconds = state == null
                    ? 0
                    : animationSeconds(state.tick(), state.publishedAt(), state.paused(), System.nanoTime());
            clouds.render(viewProjection, camera.x(), camera.y(), camera.z(), effectSeconds, visibility.amount());
            if (gpuTimer != null) gpuTimer.stamp();
            if (state != null)
                renderer.far()
                        .renderWater(
                                viewProjection,
                                camera.x(),
                                camera.y(),
                                camera.z(),
                                visibility.amount(),
                                state.coverage());
            int waterCalls =
                    renderer.renderWater(viewProjection, camera.x(), camera.y(), camera.z(), visibility.amount());
            var stats = new TerrainRenderer.RenderStats(
                    opaque.visible(),
                    opaque.culled(),
                    opaque.drawCalls() + waterCalls,
                    opaque.draws(),
                    opaque.cullNanos());
            Optional<BlockBreaking.Progress> mining = Optional.empty();
            if (!editingBlocked && input.leftMouseDown() && state.target().isPresent()) {
                var selected = state.target().orElseThrow();
                mining = state.breaking()
                        .filter(progress ->
                                progress.block().equals(selected.block()) && progress.blockId() == selected.blockId());
            }
            if (state != null) {
                double itemAlpha = state.paused()
                        ? 0
                        : Math.clamp(
                                (System.nanoTime() - state.publishedAt()) / (1e9 * FixedSimulation.TICK_SECONDS), 0, 1);
                items.render(viewProjection, state.items(), itemAlpha, camera.x(), camera.y(), camera.z());
                effects.render(
                        viewProjection,
                        mining,
                        state.brokenBlocks(),
                        effectSeconds,
                        camera.x(),
                        camera.y(),
                        camera.z());
            }
            if (!editingBlocked && state.target().isPresent())
                outline.render(
                        viewProjection, state.target().orElseThrow().block(), camera.x(), camera.y(), camera.z());
            if (gpuTimer != null) gpuTimer.stamp();
            metrics.recordCulling(stats.visible(), stats.culled(), stats.drawCalls());
            profiler.mark(Segment.RENDER);
            canvas.clear();
            if (screens.isOpen()) screens.render(canvas, viewport);
            else if (runtime.state() == WorldRuntime.State.LOADING) loading.render(canvas, viewport);
            else hud.render(canvas, hotbar, viewport);
            uiRenderer.render(canvas, viewport);
            if (gpuTimer != null) {
                gpuTimer.stamp();
                gpuTimer.endFrame();
            }
            profiler.mark(Segment.UI);
            window.swapBuffers();
            profiler.mark(Segment.SWAP);
            long frameEnded = System.nanoTime();
            profiler.endFrame(frameEnded - frameStarted);
            frameStarted = frameEnded;
            metrics.recordFrame(delta);
            metrics.reportIfDue(now, width[0], height[0], hardware);
            if (driver != null && driver.frameCompleted(runtime, state, stats, uploads.queueDepth()))
                return Exit.BACK_TO_MENU;
        }
        return Exit.QUIT;
    }

    /** Far clip plane covering far terrain diagonally across its radius, with margin for tall terrain. */
    static float farPlane(int farDistanceChunks) {
        return Math.max(800f, (farDistanceChunks + 4) * Chunk.SIZE * 1.5f);
    }

    static double animationSeconds(long tick, long publishedAt, boolean paused, long now) {
        return tick * FixedSimulation.TICK_SECONDS
                + (paused ? 0 : Math.clamp((now - publishedAt) * 1e-9, 0, FixedSimulation.TICK_SECONDS));
    }

    public Screen.Action runMenu(Window window, Input input, UiRenderer renderer, GameSettings settings, Screen menu) {
        return runScreen(window, input, renderer, settings, menu, null, null);
    }

    private static void acknowledge(
            WorldRuntime runtime,
            Map<ChunkPos, WorldRuntime.SessionMesh> pending,
            ChunkManager.MeshResult result,
            boolean uploaded) {
        var issued = pending.get(result.position());
        if (issued != null && issued.mesh().equals(result)) {
            pending.remove(result.position());
            runtime.acknowledge(issued, uploaded);
        }
    }

    public void awaitStop(
            Window window, Input input, UiRenderer renderer, GameSettings settings, WorldRuntime runtime) {
        runtime.requestStop();
        runScreen(
                window,
                input,
                renderer,
                settings,
                new SessionStatusScreen(settings.translations().text("status.saving")),
                runtime,
                null);
    }

    public void awaitLoad(Window window, Input input, UiRenderer renderer, GameSettings settings, GameSession session) {
        runScreen(
                window,
                input,
                renderer,
                settings,
                new SessionStatusScreen(settings.translations().text("status.loading")),
                null,
                session);
    }

    public void awaitStop(Window window, Input input, UiRenderer renderer, GameSettings settings, GameSession session) {
        if (session.state() == GameSession.State.MENU) return;
        runScreen(
                window,
                input,
                renderer,
                settings,
                new SessionStatusScreen(settings.translations().text("status.saving")),
                session.runtime(),
                session);
    }

    private Screen.Action runScreen(
            Window window,
            Input input,
            UiRenderer renderer,
            GameSettings settings,
            Screen screen,
            WorldRuntime stopping,
            GameSession session) {
        input.discardTransientInput();
        window.setCursorCaptured(false);
        var screens = new ScreenManager();
        screens.open(screen);
        var canvas = new UiCanvas();
        int[] width = new int[1], height = new int[1], ww = new int[1], wh = new int[1];
        boolean interrupted = Thread.interrupted();
        boolean saveError = false;
        try {
            if (interrupted && stopping == null && session == null) screens.requestClose();
            if (interrupted && stopping == null && session != null && session.state() == GameSession.State.LOADING)
                session.cancelLoading();
            while (true) {
                if (session != null) {
                    session.poll();
                    if (session.state() == GameSession.State.MENU) return Screen.Action.NONE;
                    if (stopping == null) {
                        if (window.shouldClose() && session.state() == GameSession.State.LOADING)
                            session.cancelLoading();
                        if (session.state() == GameSession.State.LOADING && session.runtime() != null)
                            return Screen.Action.NONE;
                    }
                }
                if (stopping != null && stopping.isStopped()) return Screen.Action.NONE;
                if (stopping != null && (stopping.state() == WorldRuntime.State.SAVE_ERROR) != saveError) {
                    saveError = stopping.state() == WorldRuntime.State.SAVE_ERROR;
                    screens.open(
                            saveError
                                    ? new SaveErrorScreen(settings.translations())
                                    : new SessionStatusScreen(
                                            settings.translations().text("status.saving")));
                    input.discardTransientInput();
                }
                if (stopping == null && session == null && window.shouldClose()) screens.requestClose();
                screens.beginFrame();
                window.pollEvents();
                input.update();
                input.clearPlayerInput();
                input.inventoryPressed();
                double scroll = input.consumeScroll();
                window.framebufferSize(width, height);
                window.windowSize(ww, wh);
                boolean drawable = width[0] > 0 && height[0] > 0 && ww[0] > 0 && wh[0] > 0;
                UiViewport viewport =
                        drawable ? new UiViewport(ww[0], wh[0], width[0], height[0], settings.guiScale()) : null;
                if (drawable) screens.layout(viewport);
                if (drawable && input.focused() && (stopping != null || !window.shouldClose())) {
                    screens.updatePointer(new Screen.Pointer(
                            input.leftMouseDown(),
                            input.leftMousePressed(),
                            input.rightMouseDown(),
                            input.rightMousePressed(),
                            input.shiftHeld(),
                            viewport.cursorX(input.cursorX()),
                            viewport.cursorY(input.cursorY()),
                            window.time()));
                    screens.scroll(scroll, viewport.cursorX(input.cursorX()), viewport.cursorY(input.cursorY()));
                } else screens.cancelInteraction();
                UiEvent event;
                while ((event = input.pollUiEvent()) != null)
                    if (drawable && input.focused() && (stopping != null || !window.shouldClose())) {
                        if (session != null
                                && stopping == null
                                && event instanceof UiEvent.Key(var key)
                                && key == UiKey.BACK
                                && session.state() == GameSession.State.LOADING) session.cancelLoading();
                        else screens.input(event);
                    }
                Screen.Action action = screens.pollAction();
                if (stopping != null && action == Screen.Action.RETRY_SAVE) {
                    if (session != null) session.retrySave();
                    else stopping.retrySave();
                }
                if (stopping != null && action == Screen.Action.DISCARD_CHANGES) {
                    if (session != null) session.discardChanges();
                    else stopping.discardChanges();
                }
                if (stopping == null && session == null && action != null) return action;
                if (stopping == null && session == null && !screens.isOpen()) return Screen.Action.CLOSE;
                if (drawable) {
                    glViewport(0, 0, width[0], height[0]);
                    glClearColor(.045f, .06f, .085f, 1);
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                    canvas.clear();
                    screens.render(canvas, viewport);
                    renderer.render(canvas, viewport);
                    window.swapBuffers();
                }
                if (!sleepWhileIdle()) {
                    if (stopping == null && session == null) screens.requestClose();
                    if (session != null && stopping == null && session.state() == GameSession.State.LOADING)
                        session.cancelLoading();
                    interrupted |= Thread.interrupted();
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void submit(WorldRuntime runtime, WorldRuntime.Action action) {
        if (!runtime.offer(action)) throw new IllegalStateException("World command queue is full");
    }

    static Optional<WorldRuntime.Edit> editCommand(
            Hotbar hotbar,
            boolean blocked,
            boolean removePressed,
            boolean placePressed,
            int slotPressed,
            int hotbarScrollSteps,
            double yaw,
            double pitch) {
        if (blocked) return Optional.empty();
        if (slotPressed >= 0) hotbar.select(slotPressed);
        else hotbar.scroll(hotbarScrollSteps);
        if (!removePressed && (!placePressed || hotbar.selectedBlock() == BlockId.AIR)) return Optional.empty();
        return Optional.of(new WorldRuntime.Edit(removePressed, hotbar.selectedBlock(), yaw, pitch));
    }

    static double clampDelta(double deltaSeconds) {
        return Math.min(Math.max(deltaSeconds, 0), MAX_DELTA_SECONDS);
    }

    private static boolean sleepWhileIdle() {
        try {
            Thread.sleep(16);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
