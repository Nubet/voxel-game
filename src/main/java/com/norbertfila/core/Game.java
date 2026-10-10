package com.norbertfila.core;

import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.generation.*;
import com.norbertfila.meshing.*;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerController;
import com.norbertfila.player.SpawnResolver;
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
            HillsGenerator generator = new HillsGenerator();
            PlayerController player = new PlayerController();
            player.setMode(PlayerController.Mode.SURVIVAL);
            player.setPosition(new SpawnResolver(generator).resolve(0, 0));
            ChunkMesher mesher = new GreedyMesher();
            try (ChunkManager chunkManager = new ChunkManager(new World(), generator, mesher, viewDemand);
                    TerrainRenderer renderer = new TerrainRenderer()) {
                FrameMetrics metrics = new FrameMetrics(0, 0, 0, Boolean.getBoolean("voxel.debug"), window.time());
                new GameLoop().run(window, camera, renderer, metrics, chunkManager, player);
            }
        }
    }
}
