package com.norbertfila.core;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.generation.*;
import com.norbertfila.i18n.Translations;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.AppearancePreferences;
import com.norbertfila.persistence.CrosshairPreferences;
import com.norbertfila.persistence.LanguagePreferences;
import com.norbertfila.persistence.ViewDistancePreferences;
import com.norbertfila.persistence.world.WorldCatalog;
import com.norbertfila.persistence.world.WorldIo;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.ui.Screen;
import com.norbertfila.world.*;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Game {
    private static final int TERRAIN_RADIUS = 4;
    private static final Logger LOG = Logger.getLogger(Game.class.getName());

    public void run() {
        var languagePreferences = new LanguagePreferences();
        var settings = new GameSettings(new Translations(languagePreferences.load()));
        var crosshairPreferences = new CrosshairPreferences();
        settings.setCrosshair(crosshairPreferences.load());
        var distancePreferences = new ViewDistancePreferences();
        distancePreferences.loadInto(settings);
        var appearancePreferences = new AppearancePreferences();
        settings.setModelVariant(appearancePreferences.load());
        try (Window window = new Window(1280, 720, "Voxel Engine");
                Input input = new Input(window.handle());
                UiRenderer ui = new UiRenderer();
                SkinManager skins = new SkinManager();
                WorldIo io = new WorldIo();
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            var loop = new GameLoop(skins);
            TerrainConfig config = TerrainConfig.defaults();
            int version = new TerrainGenerator(0, config).generatorVersion();
            var catalog =
                    new WorldCatalog(Path.of(System.getProperty("voxel.worlds", "saves/worlds")), "default", version);
            var menu = new WorldMenu(io, catalog, "default", version, settings, skins);
            var session = new GameSession(
                    io, seed -> new TerrainGenerator(seed, config), new WaterMesher(), terrainDemand(config));
            while (!window.shouldClose()) {
                window.setTitle("Voxel Engine");
                var action = loop.runMenu(window, input, ui, settings, menu);
                if (window.shouldClose() || !(action instanceof Screen.Action.OpenWorld selected)) break;
                Throwable problem = null;
                try {
                    GameLoop.Exit exit =
                            runSession(window, input, ui, textures, settings, loop, session, catalog, selected.id());
                    problem = session.failure();
                    if (exit == GameLoop.Exit.QUIT) window.requestClose();
                } catch (RuntimeException failure) {
                    problem = failure;
                    LOG.log(Level.SEVERE, "Session failed; world may not have been saved", failure);
                }
                if (!window.shouldClose()) menu.sessionEnded(problem);
            }
        } finally {
            languagePreferences.save(settings.translations().language());
            crosshairPreferences.save(settings.crosshair());
            distancePreferences.save(settings);
            appearancePreferences.save(settings.modelVariant());
        }
    }

    GameLoop.Exit runSession(
            Window window,
            Input input,
            UiRenderer ui,
            BlockTextureArray textures,
            GameSettings settings,
            GameLoop loop,
            GameSession session,
            WorldCatalog catalog,
            UUID id) {
        try (TerrainRenderer renderer = new TerrainRenderer(textures)) {
            session.open(catalog, id);
            try {
                loop.awaitLoad(window, input, ui, settings, session);
                if (session.runtime() == null)
                    return window.shouldClose() ? GameLoop.Exit.QUIT : GameLoop.Exit.BACK_TO_MENU;
                var camera = new Camera(settings);
                var metrics = new FrameMetrics(0, 0, 0, Boolean.getBoolean("voxel.debug"), window.time());
                window.setTitle("Voxel Engine | Render distance: " + settings.renderDistance());
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
        int minChunkY = com.norbertfila.world.WorldBounds.MIN_CHUNK_Y;
        int maxChunkY = com.norbertfila.world.WorldBounds.MAX_CHUNK_Y;
        return new ViewDemand(TERRAIN_RADIUS, minChunkY, maxChunkY, ViewDemand.Shape.SQUARE);
    }
}
