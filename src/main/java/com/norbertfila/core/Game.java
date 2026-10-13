package com.norbertfila.core;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.generation.*;
import com.norbertfila.meshing.*;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.*;
import java.nio.file.Path;

public final class Game {
    private static final int TERRAIN_RADIUS = 4;

    public void run() {
        try (Window window = new Window(1280, 720, "Voxel Engine | M0")) {
            Camera camera = new Camera();
            TerrainConfig terrainConfig = TerrainConfig.defaults();
            boolean waterScene = Boolean.getBoolean("voxel.waterScene");
            ViewDemand viewDemand = waterScene
                    ? new ViewDemand(TERRAIN_RADIUS, 0, 1, ViewDemand.Shape.SQUARE)
                    : terrainDemand(terrainConfig);
            TerrainSource generator = waterScene ? new WaterTestScene() : new TerrainGenerator(0, terrainConfig);
            ChunkMesher mesher = new WaterMesher();
            GameSettings settings = new GameSettings();
            SettingsPanel settingsPanel = new SettingsPanel(settings);
            var blockTextures = new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures();
            try (BlockTextureArray textureArray = new BlockTextureArray(blockTextures);
                    WorldRuntime runtime = new WorldRuntime(
                            generator,
                            mesher,
                            viewDemand,
                            Path.of(System.getProperty(
                                    "voxel.save", waterScene ? "saves/water-test.vxl" : "saves/world.vxl")));
                    TerrainRenderer renderer = new TerrainRenderer(textureArray);
                    UiRenderer uiRenderer = new UiRenderer()) {
                FrameMetrics metrics = new FrameMetrics(0, 0, 0, Boolean.getBoolean("voxel.debug"), window.time());
                window.setTitle("Voxel Engine | Render distance: " + settings.renderDistance());
                new GameLoop().run(window, camera, renderer, uiRenderer, metrics, runtime, settingsPanel);
            }
        }
    }

    static ViewDemand terrainDemand(TerrainConfig config) {
        int minChunkY = Math.floorDiv(config.minHeight() - Chunk.SIZE, Chunk.SIZE);
        int maxChunkY = Math.floorDiv(Math.max(config.maxHeight(), config.waterLevel()) + Chunk.SIZE, Chunk.SIZE);
        return new ViewDemand(TERRAIN_RADIUS, minChunkY, maxChunkY, ViewDemand.Shape.SQUARE);
    }
}
