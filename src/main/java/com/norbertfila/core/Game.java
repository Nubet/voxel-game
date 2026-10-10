package com.norbertfila.core;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.meshing.*;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.world.*;

public final class Game {
    private static final int TERRAIN_RADIUS = 4;
    private static final int TERRAIN_CHUNK_Y = 0;

    public void run() {
        try (Window window = new Window(1280, 720, "Voxel Engine | M0")) {
            Camera camera = new Camera();
            WorldGenerator generator = new HillsGenerator();
            ChunkMesher mesher = new VisibleFaceMesher();
            try (TerrainRenderer renderer = new TerrainRenderer()) {
                long start = System.nanoTime();
                int chunks = 0;
                int vertices = 0;
                long meshBytes = 0;
                // Static 9x9 horizontal terrain at y=0 until chunk streaming is implemented.
                for (int z = -TERRAIN_RADIUS; z <= TERRAIN_RADIUS; z++)
                    for (int x = -TERRAIN_RADIUS; x <= TERRAIN_RADIUS; x++) {
                        MeshData mesh = mesher.mesh(generator.generate(x, TERRAIN_CHUNK_Y, z), x, TERRAIN_CHUNK_Y, z);
                        renderer.upload(mesh);
                        vertices += mesh.vertexCount();
                        meshBytes += (long) mesh.vertexCount() * MeshData.VERTEX_STRIDE * Float.BYTES;
                        chunks++;
                    }
                System.out.println(
                        "Generated " + chunks + " sections in " + ((System.nanoTime() - start) / 1_000_000) + " ms");
                FrameMetrics metrics =
                        new FrameMetrics(chunks, vertices, meshBytes, Boolean.getBoolean("voxel.debug"), window.time());
                new GameLoop().run(window, camera, renderer, metrics);
            }
        }
    }
}
