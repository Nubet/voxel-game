package com.norbertfila.core;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.meshing.*;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.*;

public final class Game {
    private static final int TERRAIN_RADIUS = 4;
    private static final int TERRAIN_MIN_CHUNK_Y = -1;
    private static final int TERRAIN_MAX_CHUNK_Y = 1;

    public void run() {
        try (Window window = new Window(1280, 720, "Voxel Engine | M0")) {
            Camera camera = new Camera();
            ViewDemand viewDemand =
                    new ViewDemand(TERRAIN_RADIUS, TERRAIN_MIN_CHUNK_Y, TERRAIN_MAX_CHUNK_Y, ViewDemand.Shape.SQUARE);
            WorldGenerator generator = new HillsGenerator();
            ChunkMesher mesher = new VisibleFaceMesher();
            try (TerrainRenderer renderer = new TerrainRenderer()) {
                long start = System.nanoTime();
                World world = new World();
                ChunkManager chunkManager = new ChunkManager(world, generator, viewDemand);
                ChunkManager.UpdateResult initial = chunkManager.update(camera.chunkPosition());
                int chunks = initial.requested();
                int vertices = 0;
                long meshBytes = 0;
                for (ChunkPos position : chunkManager.residentPositions()) {
                    BlockNeighborhood neighborhood = world.blockNeighborhood(position);
                    MeshData mesh =
                            mesher.mesh(neighborhood.center(), neighborhood, position.x(), position.y(), position.z());
                    renderer.upload(position, mesh);
                    vertices += mesh.vertexCount();
                    meshBytes += (long) mesh.vertexCount() * MeshData.VERTEX_STRIDE * Float.BYTES;
                }
                System.out.println(
                        "Generated " + chunks + " sections in " + ((System.nanoTime() - start) / 1_000_000) + " ms");
                FrameMetrics metrics =
                        new FrameMetrics(chunks, vertices, meshBytes, Boolean.getBoolean("voxel.debug"), window.time());
                new GameLoop().run(window, camera, renderer, metrics, chunkManager);
            }
        }
    }
}
