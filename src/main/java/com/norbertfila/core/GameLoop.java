package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.streaming.ChunkManager;

public final class GameLoop {
    private static final double MAX_DELTA_SECONDS = 0.1;
    private static final long MINIMIZED_SLEEP_MILLIS = 16;

    public void run(
            Window window, Camera camera, TerrainRenderer renderer, FrameMetrics metrics, ChunkManager chunkManager) {
        double previous = window.time();
        Input input = new Input(window.handle());
        int[] framebufferWidth = new int[1];
        int[] framebufferHeight = new int[1];
        while (!window.shouldClose()) {
            window.pollEvents();
            if (window.escapePressed()) {
                window.requestClose();
            }

            double now = window.time();
            double deltaSeconds = clampDelta(now - previous);
            previous = now;
            input.update();
            window.framebufferSize(framebufferWidth, framebufferHeight);
            if (framebufferWidth[0] <= 0 || framebufferHeight[0] <= 0) {
                if (!sleepWhileMinimized()) {
                    return;
                }
                continue;
            }

            camera.update(input, deltaSeconds);
            ChunkManager.UpdateResult update = chunkManager.update(camera.chunkPosition());
            if (update.changed()) {
                update.evicted().forEach(renderer::remove);
                System.out.println("View demand center=" + update.center() + " sections=" + update.requested()
                        + " generated=" + update.generated() + " resident=" + chunkManager.residentCount());
            }
            glViewport(0, 0, framebufferWidth[0], framebufferHeight[0]);
            glClearColor(.55f, .76f, .96f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.render(camera.viewProjection(framebufferWidth[0], framebufferHeight[0]));
            window.swapBuffers();

            metrics.recordFrame(deltaSeconds);
            metrics.reportIfDue(now);
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
