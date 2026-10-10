package com.norbertfila.core;

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
                new GameLoop().run(window, camera, renderer, chunks);
            }
        }
    }
}
