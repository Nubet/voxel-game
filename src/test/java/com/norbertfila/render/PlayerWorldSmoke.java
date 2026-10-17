package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.*;
import com.norbertfila.meshing.*;
import com.norbertfila.player.visual.PlayerAppearance;
import com.norbertfila.player.visual.model.PlayerPose;
import com.norbertfila.world.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Executors;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

final class PlayerWorldSmoke {
    static void run() throws Exception {
        byte[] white = new byte[64 * 64 * 4];
        Arrays.fill(white, (byte) 255);
        try (var skins = new SkinManager(
                        Executors.newSingleThreadExecutor(),
                        () -> new SkinLoader.Loaded(new PlayerSkin(white), "white", ""));
                var player = new PlayerRenderer(skins);
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var terrain = new TerrainRenderer(textures)) {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (skins.texture() == 0 && System.nanoTime() < deadline) {
                skins.update();
                Thread.sleep(1);
            }
            check(skins.texture() != 0, "White skin failed to load");
            var projection = new Matrix4f().ortho(-1.3f, 1.3f, -1.3f, 1.3f, .1f, 300);
            var fog = new DistanceFog(10, 20);
            var vp = new Matrix4f(projection).lookAt(0, 0, 0, 0, 0, 1, 0, 1, 0);
            draw(player, vp, new Matrix4f().translation(0, -1, 5), fog, 0);
            near(pixel(), new float[] {.86f, .86f, .86f}, "Z directional shading");
            var side = new Matrix4f(projection).lookAt(0, 0, 0, 1, 0, 0, 0, 1, 0);
            draw(player, side, new Matrix4f().translation(5, -1, 0), fog, 0);
            near(pixel(), new float[] {.77f, .77f, .77f}, "X directional shading");
            draw(player, vp, new Matrix4f().translation(0, -1, 80), fog, 0);
            near(
                    pixel(),
                    new float[] {
                        UnderwaterVisibility.SKY_RED, UnderwaterVisibility.SKY_GREEN, UnderwaterVisibility.SKY_BLUE
                    },
                    "Player distance fog");
            draw(player, vp, new Matrix4f().translation(0, -1, 80), fog, 1);
            near(
                    pixel(),
                    new float[] {
                        UnderwaterVisibility.FOG_RED, UnderwaterVisibility.FOG_GREEN, UnderwaterVisibility.FOG_BLUE
                    },
                    "Player underwater fog");
            var underwater = capture();
            draw(player, vp, new Matrix4f().translation(0, -1, 80), new DistanceFog(100, 200), 1);
            check(underwater.equals(capture()), "Distance fog changed submerged player");

            terrain.viewDistances(16, 0);
            var clearFog = new DistanceFog(200, 300);
            var model = new Matrix4f().translation(0, -.5f, 5);
            draw(player, vp, model, clearFog, 0);
            var reference = capture();
            var position = new ChunkPos(0, 0, 0);
            for (int waterZ : new int[] {10, 6}) {
                var chunk = new Chunk();
                chunk.set(8, 1, waterZ, BlockId.WATER);
                var world = new World();
                world.putChunk(position, chunk);
                var n = world.blockNeighborhood(position);
                terrain.upload(position, new WaterMesher().mesh(n.center(), n, 0, 0, 0, ChunkMesh.ALL));
                draw(player, vp, model, clearFog, 0);
                terrain.renderOpaque(vp, 8.5, 1.5, 3, 0);
                int[] body = pixel();
                check(terrain.renderWater(vp, 8.5, 1.5, 3, 0) > 0, "No water draw");
                if (waterZ == 10) {
                    near(
                            pixel(),
                            new float[] {body[0] / 255f, body[1] / 255f, body[2] / 255f},
                            "Water behind player ignored depth");
                } else {
                    check(!Arrays.equals(body, pixel()), "Water in front did not blend over player");
                    check(!reference.equals(capture()), "Water pass did not change image");
                }
                check(glGetBoolean(GL_DEPTH_WRITEMASK) && !glIsEnabled(GL_BLEND), "Water state leaked");
            }
            check(glGetError() == GL_NO_ERROR, "Player world shading GL error");
            System.out.println(
                    "PASS player world shading: directional normals, distance/underwater fog, water before/behind depth");
        }
    }

    private static void draw(PlayerRenderer player, Matrix4f vp, Matrix4f model, DistanceFog fog, float water) {
        glDepthMask(true);
        glDepthFunc(GL_LESS);
        glClearDepth(1);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        player.render(vp, model, PlayerPose.NEUTRAL, PlayerAppearance.DEFAULT, fog, water);
    }

    private static int[] pixel() {
        var pixels = BufferUtils.createByteBuffer(4);
        glReadPixels(256, 256, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return new int[] {pixels.get(0) & 255, pixels.get(1) & 255, pixels.get(2) & 255};
    }

    private static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(512 * 512 * 4);
        glReadPixels(0, 0, 512, 512, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void near(int[] actual, float[] expected, String message) {
        for (int i = 0; i < 3; i++)
            check(Math.abs(actual[i] - expected[i] * 255) <= 3, message + ": " + Arrays.toString(actual));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
