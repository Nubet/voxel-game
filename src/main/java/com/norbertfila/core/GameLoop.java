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
            SettingsPanel settingsPanel) {
        double previous = window.time();
        Map<ChunkPos, Long> residents = Map.of();
        Map<ChunkPos, ChunkManager.MeshResult> pending = new HashMap<>();
        UnderwaterVisibility visibility = new UnderwaterVisibility();
        try (Input input = new Input(window.handle())) {
            Hotbar hotbar = new Hotbar();
            String hardware =
                    System.getProperty("os.name") + " " + System.getProperty("os.arch") + " Java " + Runtime.version();
            int[] width = new int[1], height = new int[1];
            while (!window.shouldClose()) {
                window.pollEvents();
                double now = window.time(), delta = clampDelta(now - previous);
                previous = now;
                input.update();
                int scroll = input.hotbarScrollSteps();
                if (input.focused() && input.settingsPressed()) {
                    settingsPanel.toggle();
                    window.setCursorCaptured(!settingsPanel.open());
                    input.resetMouseTracking();
                }
                window.framebufferSize(width, height);
                boolean minimized = width[0] <= 0 || height[0] <= 0;
                boolean blocked = minimized || settingsPanel.open() || !input.focused();
                if (settingsPanel.update(
                        input.leftMouseDown(), input.leftMousePressed(), input.cursorX(), input.cursorY()))
                    window.setTitle("Voxel Engine | Render distance: " + settingsPanel.renderDistance());
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
                        minimized,
                        settingsPanel.renderDistance()));
                WorldRuntime.Frame state = runtime.frame();
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
                        || input.settingsPressed()
                        || input.toggleModePressed()
                        || state == null
                        || state.modePending()
                        || state.mode() == PlayerController.Mode.SPECTATOR;
                editCommand(
                                hotbar,
                                editingBlocked,
                                input.leftMousePressed(),
                                input.rightMousePressed(),
                                input.hotbarSlotPressed(),
                                scroll,
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
                uiRenderer.render(settingsPanel, hotbar, width[0], height[0]);
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
