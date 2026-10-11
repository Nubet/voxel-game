package com.norbertfila.core;

import com.norbertfila.assets.AssetLoader;
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

    public void run() {
        try (Window window = new Window(1280, 720, "Voxel Engine | M0")) {
            Camera camera = new Camera();
            TerrainConfig terrainConfig = TerrainConfig.defaults();
            ViewDemand viewDemand = terrainDemand(terrainConfig);
            TerrainSource generator = new TerrainGenerator(0, terrainConfig);
            PlayerController player = new PlayerController();
            player.setPosition(new SpawnResolver(generator).resolve(0, 0));
            ChunkMesher mesher = new GreedyMesher();
            GameSettings settings = new GameSettings();
            SettingsPanel settingsPanel = new SettingsPanel(settings);
            var blockTextures = new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures();
            try (BlockTextureArray textureArray = new BlockTextureArray(blockTextures);
                    ChunkManager chunkManager = new ChunkManager(new World(), generator, mesher, viewDemand);
                    TerrainRenderer renderer = new TerrainRenderer();
                    UiRenderer uiRenderer = new UiRenderer()) {
                textureArray.bind(0);
                FrameMetrics metrics = new FrameMetrics(0, 0, 0, Boolean.getBoolean("voxel.debug"), window.time());
                window.setTitle("Voxel Engine | Render distance: " + settings.renderDistance());
                new GameLoop().run(window, camera, renderer, uiRenderer, metrics, chunkManager, player, settingsPanel);
            }
        }
    }

    static ViewDemand terrainDemand(TerrainConfig config) {
        int minChunkY = Math.floorDiv(config.minHeight() - Chunk.SIZE, Chunk.SIZE);
        int maxChunkY = Math.floorDiv(config.maxHeight() + Chunk.SIZE, Chunk.SIZE);
        return new ViewDemand(TERRAIN_RADIUS, minChunkY, maxChunkY, ViewDemand.Shape.SQUARE);
    }
}
