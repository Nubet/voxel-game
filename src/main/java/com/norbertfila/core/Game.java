package com.norbertfila.core;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.generation.*;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.world.WorldCatalog;
import com.norbertfila.persistence.world.WorldIo;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.ui.Screen;
import com.norbertfila.ui.screens.MainMenuScreen;
import com.norbertfila.world.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Game {
    private static final int TERRAIN_RADIUS = 4;
    private static final Logger LOG = Logger.getLogger(Game.class.getName());

    public void run() {
        try (Window window = new Window(1280, 720, "Voxel Engine");
                Input input = new Input(window.handle());
                UiRenderer ui = new UiRenderer();
                WorldIo io = new WorldIo();
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            var settings = new GameSettings();
            var loop = new GameLoop();
            boolean failed = false;
            while (!window.shouldClose()) {
                window.setTitle("Voxel Engine");
                if (loop.runMenu(window, input, ui, settings, new MainMenuScreen(failed)) != Screen.Action.PLAY) break;
                try {
                    GameLoop.Exit exit = runSession(window, input, ui, textures, settings, loop, io);
                    failed = false;
                    if (exit == GameLoop.Exit.QUIT) window.requestClose();
                } catch (RuntimeException failure) {
                    failed = true;
                    LOG.log(Level.SEVERE, "Session failed; world may not have been saved", failure);
                }
            }
        }
    }

    private GameLoop.Exit runSession(
            Window window,
            Input input,
            UiRenderer ui,
            BlockTextureArray textures,
            GameSettings settings,
            GameLoop loop,
            WorldIo io) {
        TerrainConfig config = TerrainConfig.defaults();
        boolean waterScene = Boolean.getBoolean("voxel.waterScene");
        ViewDemand demand =
                waterScene ? new ViewDemand(TERRAIN_RADIUS, 0, 1, ViewDemand.Shape.SQUARE) : terrainDemand(config);
        String selected = System.getProperty("voxel.save");
        if (selected == null)
            throw new IllegalStateException(
                    "No catalog world selected; set voxel.save to its state.vxl until the world-selection UI is available");
        Path statePath = Path.of(selected).toAbsolutePath().normalize();
        if (!statePath.getFileName().toString().equals("state.vxl"))
            throw new IllegalArgumentException("Expected a catalog state.vxl path");
        Path directory = statePath.getParent();
        UUID id = UUID.fromString(directory.getFileName().toString());
        int version = waterScene
                ? new WaterTestScene().generatorVersion()
                : new TerrainGenerator(0, config).generatorVersion();
        var catalog = new WorldCatalog(directory.getParent(), "default", version);
        var session = new GameSession(
                io,
                seed -> waterScene ? new WaterTestScene() : new TerrainGenerator(seed, config),
                new WaterMesher(),
                demand);
        try (TerrainRenderer renderer = new TerrainRenderer(textures)) {
            session.open(catalog, id);
            loop.awaitLoad(window, input, ui, settings, session);
            if (session.runtime() == null) {
                if (session.failure() != null) throw new IllegalStateException("World load failed", session.failure());
                return window.shouldClose() ? GameLoop.Exit.QUIT : GameLoop.Exit.BACK_TO_MENU;
            }
            var camera = new Camera(settings);
            var metrics = new FrameMetrics(0, 0, 0, Boolean.getBoolean("voxel.debug"), window.time());
            window.setTitle("Voxel Engine | Render distance: " + settings.renderDistance());
            try {
                return loop.run(window, input, camera, renderer, ui, metrics, session, settings);
            } finally {
                if (session.state() == GameSession.State.PLAYING) session.saveAndQuit();
                else if (session.state() == GameSession.State.LOADING) session.cancelLoading();
                loop.awaitStop(window, input, ui, settings, session);
                if (session.failure() != null) LOG.log(Level.SEVERE, "Session failed", session.failure());
            }
        }
    }

    static ViewDemand terrainDemand(TerrainConfig config) {
        int minChunkY = Math.floorDiv(config.minHeight() - Chunk.SIZE, Chunk.SIZE);
        int maxChunkY = Math.floorDiv(Math.max(config.maxHeight(), config.waterLevel()) + Chunk.SIZE, Chunk.SIZE);
        return new ViewDemand(TERRAIN_RADIUS, minChunkY, maxChunkY, ViewDemand.Shape.SQUARE);
    }
}
