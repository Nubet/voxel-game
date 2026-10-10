package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.render.TerrainRenderer;

public final class GameLoop {
    private static final double MAX_DELTA_SECONDS = 0.1;
    private static final long MINIMIZED_SLEEP_MILLIS = 16;

    public void run(Window window, Camera camera, TerrainRenderer renderer, int chunks) {
        double previous = window.time();
        double reportTime = previous;
        Input input = new Input(window.handle());
        int frames = 0;
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
            glViewport(0, 0, framebufferWidth[0], framebufferHeight[0]);
            glClearColor(.55f, .76f, .96f, 1f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.render(camera.viewProjection(framebufferWidth[0], framebufferHeight[0]));
            window.swapBuffers();

            frames++;
            if (now - reportTime >= 1.0) {
                System.out.printf("FPS: %.0f | sections: %d%n", frames / (now - reportTime), chunks);
                frames = 0;
                reportTime = now;
            }
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
