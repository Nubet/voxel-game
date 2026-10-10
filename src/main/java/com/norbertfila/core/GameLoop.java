package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.diagnostics.DiagnosticsLog;
import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.player.FixedSimulation;
import com.norbertfila.player.PlayerController;
import com.norbertfila.render.Frustum;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.render.UiRenderer;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import java.util.HashMap;
import java.util.Map;

public final class GameLoop {
    private static final double MAX_DELTA_SECONDS = 0.1;
    private static final long MINIMIZED_SLEEP_MILLIS = 16;
    private static final double FIXED_DELTA_SECONDS = 1.0 / 60.0;
    private static final int MAX_CATCH_UP_TICKS = 5;

    public void run(
            Window window,
            Camera camera,
            TerrainRenderer renderer,
            UiRenderer uiRenderer,
            FrameMetrics metrics,
            ChunkManager chunkManager,
            PlayerController player,
            SettingsPanel settingsPanel) {
        double previous = window.time();
        Input input = new Input(window.handle());
        FixedSimulation simulation = new FixedSimulation(FIXED_DELTA_SECONDS, MAX_CATCH_UP_TICKS);
        String hardware =
                System.getProperty("os.name") + " " + System.getProperty("os.arch") + " Java " + Runtime.version();
        int[] framebufferWidth = new int[1];
        int[] framebufferHeight = new int[1];
        boolean jumpQueued = false;
        Map<ChunkPos, ChunkManager.MeshResult> pendingUploads = new HashMap<>();
        while (!window.shouldClose()) {
            window.pollEvents();

            double now = window.time();
            double deltaSeconds = clampDelta(now - previous);
            previous = now;
            input.update();
            DiagnosticsLog.context("frame=" + now + " mode=" + player.mode() + " settingsOpen=" + settingsPanel.open()
                    + " renderDistance=" + settingsPanel.renderDistance());
            if (input.settingsPressed()) {
                settingsPanel.toggle();
                window.setCursorCaptured(!settingsPanel.open());
                input.resetMouseTracking();
            }
            window.framebufferSize(framebufferWidth, framebufferHeight);
            if (framebufferWidth[0] <= 0 || framebufferHeight[0] <= 0) {
                if (!sleepWhileMinimized()) return;
                continue;
            }
            if (settingsPanel.update(
                    input.leftMouseDown(), input.leftMousePressed(), input.cursorX(), input.cursorY())) {
                chunkManager.setRenderDistance(settingsPanel.renderDistance());
                window.setTitle("Voxel Engine | Render distance: " + settingsPanel.renderDistance());
                DiagnosticsLog.logger(GameLoop.class)
                        .info("Render distance changed to " + settingsPanel.renderDistance());
            }
            if (!settingsPanel.open() && input.toggleModePressed()) {
                player.setMode(
                        player.mode() == PlayerController.Mode.SURVIVAL
                                ? PlayerController.Mode.SPECTATOR
                                : PlayerController.Mode.SURVIVAL);
                DiagnosticsLog.logger(GameLoop.class).info("Player mode changed to " + player.mode());
            }
            if (!settingsPanel.open() && player.mode() == PlayerController.Mode.SURVIVAL) {
                jumpQueued |= input.jumpPressed();
            } else {
                jumpQueued = false;
            }
            if (!settingsPanel.open()) camera.updateLook(input);
            PlayerController.InputState playerInput = new PlayerController.InputState(
                    input.moveForward(),
                    input.moveBackward(),
                    input.moveLeft(),
                    input.moveRight(),
                    player.mode() == PlayerController.Mode.SPECTATOR ? input.moveUp() : jumpQueued,
                    input.moveDown(),
                    input.sprint());
            ChunkPos playerChunk = playerChunk(player);
            ChunkManager.UpdateResult update = chunkManager.update(playerChunk);
            ChunkManager.ProcessResult processed = chunkManager.processResults();
            for (ChunkManager.MeshResult mesh : processed.meshes()) {
                renderer.enqueueUpload(mesh.position(), mesh.mesh());
                pendingUploads.put(mesh.position(), mesh);
            }
            boolean[] jumpForTick = {jumpQueued};
            if (!settingsPanel.open()
                    && (player.mode() == PlayerController.Mode.SPECTATOR || chunkManager.readyForPlayer(playerChunk))) {
                simulation.advance(deltaSeconds, fixedDelta -> {
                    player.tick(chunkManager.world(), playerInput, fixedDelta, camera.yaw(), camera.pitch());
                    jumpForTick[0] = false;
                });
            }
            jumpQueued = jumpForTick[0];
            PlayerController.Position renderPosition = player.renderPosition(simulation.interpolationAlpha());
            float eyeOffset = player.mode() == PlayerController.Mode.SURVIVAL ? Camera.PLAYER_EYE_HEIGHT : 0;
            camera.setPosition(
                    (float) renderPosition.x(), (float) renderPosition.y() + eyeOffset, (float) renderPosition.z());
            if (update.changed()) {
                update.evicted().forEach(position -> {
                    renderer.remove(position);
                    pendingUploads.remove(position);
                });
                DiagnosticsLog.logger(GameLoop.class)
                        .info("View demand center=" + update.center() + " sections="
                                + update.requested() + " scheduled=" + update.scheduled() + " generated="
                                + processed.generated()
                                + " discarded=" + processed.discarded() + " resident=" + chunkManager.residentCount());
            }
            TerrainRenderer.UploadStats uploads = renderer.processUploads(4L * 1024 * 1024, 2_000_000);
            for (ChunkPos position : uploads.uploaded()) {
                ChunkManager.MeshResult mesh = pendingUploads.remove(position);
                if (mesh != null && !chunkManager.markUploaded(mesh)) {
                    DiagnosticsLog.logger(GameLoop.class)
                            .warning("Ignoring stale completed upload position=" + position);
                }
            }
            for (ChunkPos position : uploads.failed()) {
                ChunkManager.MeshResult mesh = pendingUploads.remove(position);
                if (mesh != null) {
                    chunkManager.markUploadFailed(mesh);
                    DiagnosticsLog.logger(GameLoop.class)
                            .warning("Upload failed and mesh was returned to retry state position=" + position);
                }
            }
            metrics.recordUploads(uploads.bytesUploaded(), uploads.queueDepth(), uploads.uploadNanos());
            metrics.recordResidentChunks(chunkManager.residentCount());
            glViewport(0, 0, framebufferWidth[0], framebufferHeight[0]);
            glClearColor(.55f, .76f, .96f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            var viewProjection = camera.viewProjection(framebufferWidth[0], framebufferHeight[0]);
            TerrainRenderer.RenderStats renderStats = renderer.render(viewProjection, Frustum.from(viewProjection));
            metrics.recordCulling(renderStats.visible(), renderStats.culled(), renderStats.drawCalls());
            uiRenderer.render(settingsPanel, framebufferWidth[0], framebufferHeight[0]);
            window.swapBuffers();

            metrics.recordFrame(deltaSeconds);
            metrics.reportIfDue(now, framebufferWidth[0], framebufferHeight[0], hardware);
        }
    }

    private static ChunkPos playerChunk(PlayerController player) {
        PlayerController.Position position = player.renderPosition(1);
        return Coordinates.worldToChunk(new BlockPos(
                (int) Math.floor(position.x()), (int) Math.floor(position.y()), (int) Math.floor(position.z())));
    }

    static double clampDelta(double deltaSeconds) {
        return Math.min(Math.max(deltaSeconds, 0.0), MAX_DELTA_SECONDS);
    }

    private static boolean sleepWhileMinimized() {
        try {
            Thread.sleep(MINIMIZED_SLEEP_MILLIS);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
