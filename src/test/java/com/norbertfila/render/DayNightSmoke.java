package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.interaction.BlockBreakEvent;
import com.norbertfila.items.ItemRenderState;
import com.norbertfila.items.ItemSnapshot;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class DayNightSmoke {
    private static final int SIZE = 512;
    private static final Path OUTPUT = Path.of("build/day-night-smoke");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        try (Window window = new Window(SIZE, SIZE, "Day/night regression");
                SkyRenderer sky = new SkyRenderer();
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                TerrainRenderer terrain = new TerrainRenderer(textures);
                CloudRenderer clouds = new CloudRenderer();
                ItemEntityRenderer items = new ItemEntityRenderer(textures);
                BlockEffectsRenderer effects = new BlockEffectsRenderer(textures)) {
            glViewport(0, 0, SIZE, SIZE);
            Matrix4f view = view(0, -.2f, -1);
            var day = SkyModel.DAY;
            var night = SkyModel.at(54000);
            for (int i = 0; i < 4; i++) {
                clear();
                sky.render(view(1, .15f, 0), SkyModel.at(i * 18000), 0);
                write(capture(), new String[] {"sunrise", "noon", "sunset", "midnight"}[i]);
            }
            clear();
            sky.render(view(0, 1, -.001f), day, 0);
            ByteBuffer sun = capture();
            check(brightness(sun, SIZE / 2, SIZE / 2) > 600, "Sun texture missing overhead at noon");
            write(sun, "sun");
            clear();
            sky.render(view(0, 1, -.001f), night, 0);
            ByteBuffer moon = capture();
            check(brightness(moon, SIZE / 2, SIZE / 2) > 350, "Moon texture missing overhead at midnight");
            write(moon, "moon");
            var depth = BufferUtils.createFloatBuffer(1);
            glReadPixels(SIZE / 2, SIZE / 2, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, depth);
            check(depth.get(0) == 1, "Sky wrote world depth");
            check(
                    glIsEnabled(GL_DEPTH_TEST)
                            && glGetBoolean(GL_DEPTH_WRITEMASK)
                            && glIsEnabled(GL_CULL_FACE)
                            && !glIsEnabled(GL_BLEND),
                    "Sky leaked terrain state");

            clear();
            sky.render(view, night, 1);
            var submerged = capture();
            check(
                    brightness(submerged, 64, 64) == brightness(submerged, 256, 256),
                    "Celestial bodies visible underwater");
            write(submerged, "underwater-night");

            World world = new World();
            var position = new ChunkPos(0, 0, 0);
            world.putChunk(position, new Chunk());
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    world.setBlock(new BlockPos(x, 0, z), BlockId.GRASS, 64);
                    if (x > 10) world.setBlock(new BlockPos(x, 1, z), BlockId.WATER, 64);
                }
            }
            var n = world.blockNeighborhood(position);
            terrain.upload(position, new WaterMesher().mesh(n.center(), n, 0, 0, 0, ChunkMesh.ALL));
            ByteBuffer[] scenes = new ByteBuffer[2];
            int index = 0;
            for (SkyState state : new SkyState[] {day, night}) {
                clear();
                sky.render(view, state, 0);
                terrain.environment(state);
                check(terrain.render(view, 8, 4, 22, 0).drawCalls() > 0, "Terrain not drawn");
                scenes[index] = capture();
                write(scenes[index], index == 0 ? "world-day" : "world-night");
                index++;
            }
            check(
                    brightness(scenes[0], 256, 256) > brightness(scenes[1], 256, 256) * 3,
                    "Terrain stayed bright at night");
            glReadPixels(256, 256, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, depth);
            check(depth.get(0) < 1, "Terrain failed to occlude sky");

            var item = new ItemSnapshot(1, BlockId.COBBLE, 1, .5, .5, .5, 0, 0, 0, 0, 0, true);
            var event = new BlockBreakEvent(0, new BlockPos(0, 0, 0), BlockId.COBBLE);
            var objectView = view(0, 0, -1);
            long[][] objectLight = new long[2][2];
            index = 0;
            for (SkyState state : new SkyState[] {day, night}) {
                clear();
                items.environment(state);
                check(
                        items.render(objectView, List.of(new ItemRenderState(item, .5, .5, .5, false)), 0, .5, .5, 3)
                                == 1,
                        "Item was not drawn");
                objectLight[0][index] = totalLight(capture());
                clear();
                effects.environment(state);
                effects.render(objectView, Optional.empty(), List.of(event), .1, .5, .5, 3);
                objectLight[1][index] = totalLight(capture());
                index++;
            }
            for (long[] light : objectLight)
                check(light[0] > light[1] * 3 && light[1] > 0, "Item or debris stayed bright at night");

            for (CloudMode mode : new CloudMode[] {CloudMode.FAST, CloudMode.FANCY}) {
                var cloudView = view(0, 1, -.001f);
                long[] light = new long[2];
                index = 0;
                for (SkyState state : new SkyState[] {day, night}) {
                    clear();
                    clouds.environment(state);
                    clouds.render(cloudView, 0, 64, 0, 0, 0, mode);
                    var image = capture();
                    light[index] = totalLight(image);
                    index++;
                }
                check(light[0] > light[1] * 3 && light[1] > 0, "Clouds stayed bright at night: " + mode);
            }
            check(glGetError() == GL_NO_ERROR, "Day/night OpenGL error");
            System.out.println(
                    "PASS textured sun/moon, sky phases, underwater, terrain lighting/occlusion, items, debris, cloud modes and GL state; GPU="
                            + glGetString(GL_RENDERER));
        }
    }

    private static Matrix4f view(float x, float y, float z) {
        return new Matrix4f()
                .perspective((float) Math.toRadians(70), 1, .1f, 1600)
                .lookAt(0, 0, 0, x, y, z, 0, 1, 0);
    }

    private static void clear() {
        glClearColor(0, 0, 0, 1);
        glClearDepth(1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static int brightness(ByteBuffer pixels, int x, int y) {
        int p = (y * SIZE + x) * 4;
        return Byte.toUnsignedInt(pixels.get(p))
                + Byte.toUnsignedInt(pixels.get(p + 1))
                + Byte.toUnsignedInt(pixels.get(p + 2));
    }

    private static long totalLight(ByteBuffer pixels) {
        long total = 0;
        for (int p = 0; p < pixels.capacity(); p += 4) {
            total += Byte.toUnsignedInt(pixels.get(p))
                    + Byte.toUnsignedInt(pixels.get(p + 1))
                    + Byte.toUnsignedInt(pixels.get(p + 2));
        }
        return total;
    }

    private static void write(ByteBuffer pixels, String name) throws Exception {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int p = (y * SIZE + x) * 4;
                image.setRGB(
                        x,
                        SIZE - y - 1,
                        Byte.toUnsignedInt(pixels.get(p)) << 16
                                | Byte.toUnsignedInt(pixels.get(p + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(p + 2)));
            }
        }
        ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
