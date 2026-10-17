package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.diagnostics.FrameProfiler;
import com.norbertfila.generation.WaterTestScene;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.world.*;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.player.visual.model.ModelVariant;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ViewDemand;
import com.norbertfila.world.BlockRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.lwjgl.BufferUtils;

public final class PlayerGameHookSmoke {
    public static void run(Window window, SkinManager skins, Path output) throws Exception {
        var root = Files.createTempDirectory(output, "game-hook-");
        var catalog = new WorldCatalog(root, "default", 1);
        var id = UUID.randomUUID();
        catalog.create(new WorldMetadata(new WorldIdentity(id, 0, "default", 1), "Player hook", Instant.now()));
        var settings = new GameSettings();
        settings.setRenderDistance(2);
        settings.setClouds(CloudMode.OFF);
        try (var io = new WorldIo();
                var input = new Input(window.handle());
                var ui = new UiRenderer();
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var terrain = new TerrainRenderer(textures)) {
            var session = new GameSession(
                    io,
                    seed -> new WaterTestScene(),
                    new WaterMesher(),
                    new ViewDemand(2, 0, 2, ViewDemand.Shape.SQUARE));
            var loop = new GameLoop(skins);
            session.open(catalog, id);
            loop.awaitLoad(window, input, ui, settings, session);
            long deadline = System.nanoTime() + 15_000_000_000L;
            var camera = new Camera(settings);
            var driver = new GameLoop.FrameDriver() {
                int frames;
                int classicArmPixels;
                int switches;
                boolean hiddenChecked, displacedChecked;

                @Override
                public WorldRuntime.Controls controls(WorldRuntime.Controls controls) {
                    return new WorldRuntime.Controls(
                            PlayerInput.NONE, -90, 0, true, false, 2, 0, false, 0, 2, false, controls.appearance());
                }

                @Override
                public void update(WorldRuntime runtime, WorldRuntime.Frame state, Camera view) {
                    if (state == null || !state.ready()) return;
                    if (hiddenChecked) {
                        var feet = state.playerRenderState().current().feet();
                        view.setPosition(feet.x(), feet.y() + 1, feet.z() - 5);
                        view.restoreLook(90, 0);
                    }
                }

                @Override
                public boolean frameCompleted(
                        WorldRuntime runtime, WorldRuntime.Frame state, TerrainRenderer.RenderStats stats, int queue) {
                    if (System.nanoTime() > deadline) throw new AssertionError("Production player hook timed out");
                    if (runtime.state() != WorldRuntime.State.PLAYING || state == null || ++frames < 10) return false;
                    if (state.playerRenderState().appearance().model() != settings.modelVariant()) return false;
                    var pixels = BufferUtils.createByteBuffer(512 * 512 * 4);
                    glReadBuffer(GL_FRONT);
                    glReadPixels(0, 0, 512, 512, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                    glReadBuffer(GL_BACK);
                    int bodyPixels = 0;
                    for (int y = 200; y < 300; y++)
                        for (int x = 200; x < 300; x++) {
                            int p = (y * 512 + x) * 4;
                            if (Byte.toUnsignedInt(pixels.get(p)) == 30
                                    && Byte.toUnsignedInt(pixels.get(p + 1)) == 30
                                    && (Byte.toUnsignedInt(pixels.get(p + 2)) == 40
                                            || Byte.toUnsignedInt(pixels.get(p + 2)) == 120)) bodyPixels++;
                        }
                    if (!hiddenChecked) {
                        if (bodyPixels != 0) throw new AssertionError("Local body visible in first person");
                        hiddenChecked = true;
                        frames = 0;
                        return false;
                    }
                    if (bodyPixels < 100)
                        throw new AssertionError("Production hook did not draw the local body: " + bodyPixels);
                    int armPixels = 0;
                    for (int p = 0; p < pixels.capacity(); p += 4) {
                        int red = Byte.toUnsignedInt(pixels.get(p));
                        if ((red == 94 || red == 126) && Byte.toUnsignedInt(pixels.get(p + 1)) == 30) armPixels++;
                    }
                    if (switches == 0) {
                        if (armPixels < 100) throw new AssertionError("Production arms not visible");
                        classicArmPixels = armPixels;
                        settings.setModelVariant(ModelVariant.SLIM);
                    } else if (switches == 1) {
                        if (armPixels >= classicArmPixels * .9 || armPixels < classicArmPixels * .6)
                            throw new AssertionError(
                                    "Runtime Slim arms did not narrow: " + classicArmPixels + " -> " + armPixels);
                        settings.setModelVariant(ModelVariant.CLASSIC);
                    } else {
                        if (armPixels != classicArmPixels)
                            throw new AssertionError("Classic switch did not restore arms");
                        displacedChecked = true;
                        return true;
                    }
                    switches++;
                    frames = 0;
                    return false;
                }
            };
            try {
                loop.run(
                        window,
                        input,
                        camera,
                        terrain,
                        ui,
                        new FrameMetrics(0, 0, 0, false, window.time()),
                        session,
                        settings,
                        driver,
                        FrameProfiler.disabled());
                if (!driver.displacedChecked) throw new AssertionError("Production player hook was not exercised");
                if (glGetError() != GL_NO_ERROR) throw new AssertionError("Production player hook GL error");
                System.out.println(
                        "PASS production GameLoop: first-person hidden, snapshot Classic/Slim/Classic setting switching");
            } finally {
                session.saveAndQuit();
                loop.awaitStop(window, input, ui, settings, session);
            }
        }
    }
}
