package com.norbertfila.core;

import static org.lwjgl.glfw.GLFW.*;
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
                boolean lookInitialized;

                @Override
                public WorldRuntime.Controls controls(WorldRuntime.Controls controls) {
                    return new WorldRuntime.Controls(
                            PlayerInput.NONE,
                            controls.yaw(),
                            controls.pitch(),
                            true,
                            true,
                            2,
                            0,
                            false,
                            0,
                            2,
                            false,
                            controls.appearance());
                }

                @Override
                public void update(WorldRuntime runtime, WorldRuntime.Frame state, Camera view) {
                    if (state == null || !state.ready() || runtime.state() != WorldRuntime.State.PLAYING) return;
                    if (!lookInitialized) {
                        // Pixel fixtures below assume full daylight, independent of the world's initial dawn.
                        if (!runtime.offer(new WorldRuntime.ChatCommand("/time set noon")))
                            throw new AssertionError("Time command queue is full");
                        view.restoreLook(-90, 0);
                        lookInitialized = true;
                    }
                }

                @Override
                public boolean frameCompleted(
                        WorldRuntime runtime, WorldRuntime.Frame state, TerrainRenderer.RenderStats stats, int queue) {
                    if (System.nanoTime() > deadline)
                        throw new AssertionError("Production player hook timed out: stage=" + switches + " time="
                                + (state == null ? "unpublished" : state.worldTime()));
                    if (runtime.state() != WorldRuntime.State.PLAYING || state == null || ++frames < 10) return false;
                    if (state.worldTime().minecraftTime() != (switches == 7 ? 18000 : 6000)) return false;
                    if (state.playerRenderState().appearance().model() != settings.modelVariant()) return false;
                    var pixels = BufferUtils.createByteBuffer(512 * 512 * 4);
                    glReadBuffer(GL_FRONT);
                    glReadPixels(0, 0, 512, 512, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                    glReadBuffer(GL_BACK);
                    if (switches == 7) {
                        int p = (450 * 512 + 256) * 4;
                        if (Byte.toUnsignedInt(pixels.get(p + 2)) > 50)
                            throw new AssertionError("Production sky stayed blue after /time set midnight");
                        displacedChecked = true;
                        return true;
                    }
                    int bodyPixels = 0;
                    for (int y = 200; y < 300; y++)
                        for (int x = 200; x < 300; x++) {
                            int p = (y * 512 + x) * 4;
                            int red = Byte.toUnsignedInt(pixels.get(p));
                            int green = Byte.toUnsignedInt(pixels.get(p + 1));
                            int blue = Byte.toUnsignedInt(pixels.get(p + 2));
                            if (red >= 25
                                    && red <= 27
                                    && ((green >= 25 && green <= 27) || (green >= 52 && green <= 54))
                                    && blue > 30) bodyPixels++;
                        }
                    if (!hiddenChecked) {
                        if (bodyPixels != 0) throw new AssertionError("Local body visible in first person");
                        hiddenChecked = true;
                        key(window, GLFW_KEY_F5, GLFW_PRESS);
                        key(window, GLFW_KEY_F5, GLFW_REPEAT);
                        key(window, GLFW_KEY_F5, GLFW_RELEASE);
                        frames = 0;
                        return false;
                    }
                    if (switches == 6) {
                        if (camera.perspective() != Camera.Perspective.FIRST || bodyPixels != 0)
                            throw new AssertionError("F5 loop did not restore hidden first person");
                        if (!runtime.offer(new WorldRuntime.ChatCommand("/time set midnight")))
                            throw new AssertionError("Time command queue is full");
                        switches = 7;
                        frames = 0;
                        return false;
                    }
                    if (switches == 4) {
                        if (camera.perspective() != Camera.Perspective.FRONT)
                            throw new AssertionError("UI consumed F5 as gameplay");
                        key(window, GLFW_KEY_E, GLFW_PRESS);
                        switches++;
                        frames = 0;
                        return false;
                    }
                    if (switches == 5) {
                        if (camera.perspective() != Camera.Perspective.FRONT)
                            throw new AssertionError("Blocked F5 leaked after UI closed");
                        key(window, GLFW_KEY_F5, GLFW_PRESS);
                        key(window, GLFW_KEY_F5, GLFW_RELEASE);
                        switches++;
                        frames = 0;
                        return false;
                    }
                    if (bodyPixels < 100)
                        throw new AssertionError("Production hook did not draw the local body: " + bodyPixels
                                + " perspective=" + camera.perspective() + " stage=" + switches);
                    if (switches == 0 && camera.perspective() == Camera.Perspective.REAR) {
                        key(window, GLFW_KEY_F5, GLFW_PRESS);
                        key(window, GLFW_KEY_F5, GLFW_RELEASE);
                        frames = 0;
                        return false;
                    }
                    int armPixels = 0;
                    for (int p = 0; p < pixels.capacity(); p += 4) {
                        int red = Byte.toUnsignedInt(pixels.get(p));
                        int green = Byte.toUnsignedInt(pixels.get(p + 1));
                        if (green >= 17
                                && green <= 63
                                && (Math.abs(red - green * 94.0 / 62) < 1.5
                                        || Math.abs(red - green * 126.0 / 62) < 1.5
                                        || Math.abs(red - green * 94.0 / 30) < 1.5
                                        || Math.abs(red - green * 126.0 / 30) < 1.5)) armPixels++;
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
                    } else if (switches == 2) {
                        if (armPixels != classicArmPixels)
                            throw new AssertionError("Classic switch did not restore arms");
                    } else if (switches == 3) {
                        if (camera.perspective() != Camera.Perspective.FRONT)
                            throw new AssertionError("Second F5 did not select front view");
                        key(window, GLFW_KEY_E, GLFW_PRESS);
                        key(window, GLFW_KEY_F5, GLFW_PRESS);
                        key(window, GLFW_KEY_F5, GLFW_RELEASE);
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
                        "PASS production GameLoop: F5 first/rear/front/first, repeat suppression, UI blocking, Classic/Slim/Classic, paused time commands change sky");
            } finally {
                session.saveAndQuit();
                loop.awaitStop(window, input, ui, settings, session);
            }
        }
    }

    private static void key(Window window, int key, int action) {
        var callback = glfwSetKeyCallback(window.handle(), null);
        glfwSetKeyCallback(window.handle(), callback);
        if (callback == null) throw new AssertionError("Missing production input callback");
        callback.invoke(window.handle(), key, 0, action, 0);
    }
}
