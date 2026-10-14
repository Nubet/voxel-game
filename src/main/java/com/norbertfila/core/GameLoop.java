package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.render.Frustum;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.render.UiRenderer;
import com.norbertfila.render.UnderwaterVisibility;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.ui.Hud;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.ScreenManager;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiKey;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.ui.screens.InventoryScreen;
import com.norbertfila.ui.screens.PauseScreen;
import com.norbertfila.ui.screens.SettingsScreen;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GameLoop {
    private static final double MAX_DELTA_SECONDS = .1;

    public void run(
            Window window,
            Camera camera,
            TerrainRenderer renderer,
            UiRenderer uiRenderer,
            FrameMetrics metrics,
            WorldRuntime runtime,
            GameSettings settings) {
        double previous = window.time();
        Map<ChunkPos, Long> residents = Map.of();
        Map<ChunkPos, ChunkManager.MeshResult> pending = new HashMap<>();
        UnderwaterVisibility visibility = new UnderwaterVisibility();
        UiCanvas canvas = new UiCanvas();
        Hud hud = new Hud();
        ScreenManager screens = new ScreenManager();
        SettingsScreen settingsScreen = new SettingsScreen(settings);
        PauseScreen pauseScreen = new PauseScreen();
        try (Input input = new Input(window.handle())) {
            Hotbar hotbar = new Hotbar();
            long placePressId = 0;
            boolean inventoryInitialized = false;
            String hardware =
                    System.getProperty("os.name") + " " + System.getProperty("os.arch") + " Java " + Runtime.version();
            int[] width = new int[1], height = new int[1];
            int[] windowWidth = new int[1], windowHeight = new int[1];
            while (!window.shouldClose()) {
                screens.beginFrame();
                Screen previousScreen = screens.current();
                int previousDistance = settings.renderDistance();
                window.pollEvents();
                double now = window.time(), delta = clampDelta(now - previous);
                previous = now;
                input.update();
                WorldRuntime.Frame state = runtime.frame();
                if (state != null) {
                    hotbar.inventory(state.inventory());
                    if (!inventoryInitialized) {
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
                        screens.open(new InventoryScreen(state.mode() == PlayerController.Mode.CREATIVE));
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
                            viewport.cursorY(input.cursorY())));
                } else screens.cancelInteraction();
                UiKey key;
                while ((key = input.pollUiKey()) != null) {
                    if (!minimized && input.focused()) {
                        if (key == UiKey.BACK && !screens.isOpen()) screens.open(pauseScreen);
                        else screens.keyPress(key);
                    }
                }
                if (previousScreen != screens.current()) {
                    window.setCursorCaptured(!screens.isOpen());
                    input.resetMouseTracking();
                }
                if (previousDistance != settings.renderDistance())
                    window.setTitle("Voxel Engine | Render distance: " + settings.renderDistance());
                boolean blocked = minimized || screens.blocksGameplay() || !input.focused();
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
                        minimized || screens.current() instanceof PauseScreen,
                        settings.renderDistance(),
                        placePressId,
                        !blocked && input.rightMouseDown()));
                if (previousSlot != hotbar.selectedSlot())
                    submit(runtime, new WorldRuntime.SelectSlot(hotbar.selectedSlot()));
                Screen.Action uiAction;
                while ((uiAction = screens.pollAction()) != null) {
                    switch (uiAction) {
                        case Screen.Action.SlotClick click ->
                            submit(runtime, new WorldRuntime.ClickSlot(click.slot(), click.right(), click.quickMove()));
                        case Screen.Action.CreativePick pick ->
                            submit(runtime, new WorldRuntime.PickCreative(pick.slot(), pick.block()));
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
                    if (runtime.offer(
                            new WorldRuntime.Jump(input.peekPlayerTick().jumpPressedAtSeconds())))
                        input.nextPlayerTick();
                }
                boolean editingBlocked = blocked
                        || input.toggleModePressed()
                        || state == null
                        || state.modePending()
                        || state.mode() == PlayerController.Mode.SPECTATOR;
                editCommand(
                                hotbar,
                                editingBlocked,
                                input.leftMousePressed(),
                                false,
                                -1,
                                0,
                                camera.yaw(),
                                camera.pitch())
                        .ifPresent(action -> submit(runtime, action));
                if (minimized) {
                    if (!sleepWhileMinimized()) return;
                    continue;
                }
                ChunkManager.MeshResult mesh;
                while ((mesh = runtime.pollMesh()) != null) {
                    var replaced = pending.put(mesh.position(), mesh);
                    if (replaced != null) runtime.acknowledge(replaced, false);
                    renderer.enqueueUpload(mesh);
                }
                state = runtime.frame();
                if (state != null) {
                    for (var entry : residents.entrySet()) {
                        if (entry.getValue().equals(state.residents().get(entry.getKey()))) continue;
                        var removed = pending.remove(entry.getKey());
                        if (removed != null) runtime.acknowledge(removed, false);
                        renderer.remove(entry.getKey());
                    }
                    residents = state.residents();
                    var eye = state.interpolatedEye(System.nanoTime());
                    camera.setPosition((float) eye.x(), (float) eye.y(), (float) eye.z());
                }
                var uploads = renderer.processUploads(4L * 1024 * 1024, 2_000_000, result -> {
                    var current = runtime.frame();
                    return current != null && current.isCurrent(result);
                });
                for (var uploaded : uploads.uploaded()) {
                    pending.remove(uploaded.position(), uploaded);
                    runtime.acknowledge(uploaded, true);
                }
                for (var failed : uploads.failed()) {
                    pending.remove(failed.position(), failed);
                    runtime.acknowledge(failed, false);
                }
                for (var discarded : uploads.discarded()) {
                    pending.remove(discarded.position(), discarded);
                    runtime.acknowledge(discarded, false);
                }
                metrics.recordUploads(uploads.bytesUploaded(), uploads.queueDepth(), uploads.uploadNanos());
                metrics.recordResidentChunks(residents.size());
                if (state != null)
                    visibility.update(
                            state.waterContact().eyesSubmerged(),
                            state.waterContact().available(),
                            delta);
                glViewport(0, 0, width[0], height[0]);
                glClearColor(visibility.backgroundRed(), visibility.backgroundGreen(), visibility.backgroundBlue(), 1f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                var vp = camera.viewProjection(width[0], height[0]);
                var stats = renderer.render(vp, Frustum.from(vp), camera.position(), visibility.amount());
                metrics.recordCulling(stats.visible(), stats.culled(), stats.drawCalls());
                canvas.clear();
                if (screens.isOpen()) screens.render(canvas, viewport);
                else hud.render(canvas, hotbar, viewport);
                uiRenderer.render(canvas, viewport);
                window.swapBuffers();
                metrics.recordFrame(delta);
                metrics.reportIfDue(now, width[0], height[0], hardware);
            }
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

    private static boolean sleepWhileMinimized() {
        try {
            Thread.sleep(16);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
