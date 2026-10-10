package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.player.FixedSimulation;
import com.norbertfila.player.PlayerController;
import com.norbertfila.render.Frustum;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.streaming.ChunkManager;

public final class GameLoop {
    private static final double MAX_DELTA_SECONDS = 0.1;
    private static final long MINIMIZED_SLEEP_MILLIS = 16;
    private static final double FIXED_DELTA_SECONDS = 1.0 / 60.0;
    private static final int MAX_CATCH_UP_TICKS = 5;

    public void run(
            Window window,
            Camera camera,
            TerrainRenderer renderer,
            FrameMetrics metrics,
            ChunkManager chunkManager,
            PlayerController player) {
        double previous = window.time();
        Input input = new Input(window.handle());
        FixedSimulation simulation = new FixedSimulation(FIXED_DELTA_SECONDS, MAX_CATCH_UP_TICKS);
        String hardware =
                System.getProperty("os.name") + " " + System.getProperty("os.arch") + " Java " + Runtime.version();
        int[] framebufferWidth = new int[1];
        int[] framebufferHeight = new int[1];
        boolean jumpQueued = false;
        while (!window.shouldClose()) {
            window.pollEvents();
            if (window.escapePressed()) {
                window.requestClose();
            }

            double now = window.time();
            double deltaSeconds = clampDelta(now - previous);
            previous = now;
            input.update();
            if (input.toggleModePressed()) {
                player.setMode(
                        player.mode() == PlayerController.Mode.SURVIVAL
                                ? PlayerController.Mode.SPECTATOR
                                : PlayerController.Mode.SURVIVAL);
                System.out.println("Player mode: " + player.mode());
            }
            if (player.mode() == PlayerController.Mode.SURVIVAL) {
                jumpQueued |= input.jumpPressed();
            } else {
                jumpQueued = false;
            }
            window.framebufferSize(framebufferWidth, framebufferHeight);
            if (framebufferWidth[0] <= 0 || framebufferHeight[0] <= 0) {
                if (!sleepWhileMinimized()) {
                    return;
                }
                continue;
            }

            camera.updateLook(input);
            PlayerController.InputState playerInput = new PlayerController.InputState(
                    input.moveForward(),
                    input.moveBackward(),
                    input.moveLeft(),
                    input.moveRight(),
                    player.mode() == PlayerController.Mode.SPECTATOR ? input.moveUp() : jumpQueued,
                    input.moveDown(),
                    input.sprint());
            boolean[] jumpForTick = {jumpQueued};
            simulation.advance(deltaSeconds, fixedDelta -> {
                player.tick(chunkManager.world(), playerInput, fixedDelta, camera.yaw(), camera.pitch());
                jumpForTick[0] = false;
            });
            jumpQueued = jumpForTick[0];
            PlayerController.Position renderPosition = player.renderPosition(simulation.interpolationAlpha());
            float eyeOffset = player.mode() == PlayerController.Mode.SURVIVAL ? Camera.PLAYER_EYE_HEIGHT : 0;
            camera.setPosition(
                    (float) renderPosition.x(), (float) renderPosition.y() + eyeOffset, (float) renderPosition.z());
            ChunkManager.UpdateResult update = chunkManager.update(camera.chunkPosition());
            if (update.changed()) {
                update.evicted().forEach(renderer::remove);
                System.out.println("View demand center=" + update.center() + " sections=" + update.requested()
                        + " generated=" + update.generated() + " resident=" + chunkManager.residentCount());
            }
            TerrainRenderer.UploadStats uploads = renderer.processUploads(4L * 1024 * 1024, 2_000_000);
            metrics.recordUploads(uploads.bytesUploaded(), uploads.queueDepth(), uploads.uploadNanos());
            glViewport(0, 0, framebufferWidth[0], framebufferHeight[0]);
            glClearColor(.55f, .76f, .96f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            var viewProjection = camera.viewProjection(framebufferWidth[0], framebufferHeight[0]);
            TerrainRenderer.RenderStats renderStats = renderer.render(viewProjection, Frustum.from(viewProjection));
            metrics.recordCulling(renderStats.visible(), renderStats.culled(), renderStats.drawCalls());
            window.swapBuffers();

            metrics.recordFrame(deltaSeconds);
            metrics.reportIfDue(now, framebufferWidth[0], framebufferHeight[0], hardware);
        }
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
