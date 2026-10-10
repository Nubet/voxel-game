package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.world.*;

public final class Game {
    public void run() {
        try (Window window = new Window(1280, 720, "Voxel Engine | M0")) {
            Camera camera = new Camera();
            WorldGenerator generator = new HillsGenerator();
            ChunkMesher mesher = new VisibleFaceMesher();
            try (TerrainRenderer renderer = new TerrainRenderer()) {
                long start = System.nanoTime();
                int chunks = 0;
                // 9x9 horizontal sections, y=0 only. Expand in a later milestone.
                for (int z = -4; z <= 4; z++)
                    for (int x = -4; x <= 4; x++) {
                        renderer.upload(mesher.mesh(generator.generate(x, 0, z), x, 0, z));
                        chunks++;
                    }
                System.out.println(
                        "Generated " + chunks + " sections in " + ((System.nanoTime() - start) / 1_000_000) + " ms");
                double previous = window.time(), reportTime = previous;
                int frames = 0;
                int[] fbw = new int[1], fbh = new int[1];
                while (!window.shouldClose()) {
                    window.pollEvents();
                    if (window.escapePressed()) window.requestClose();
                    double now = window.time();
                    camera.update(window.handle(), now - previous);
                    previous = now;
                    window.framebufferSize(fbw, fbh);
                    if (fbw[0] > 0 && fbh[0] > 0) {
                        glViewport(0, 0, fbw[0], fbh[0]);
                        glClearColor(.55f, .76f, .96f, 1f);
                        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                        renderer.render(camera.viewProjection(fbw[0], fbh[0]));
                        window.swapBuffers();
                    }
                    frames++;
                    if (now - reportTime >= 1.0) {
                        System.out.printf("FPS: %.0f | sections: %d%n", frames / (now - reportTime), chunks);
                        frames = 0;
                        reportTime = now;
                    }
                }
            }
        }
    }
}
