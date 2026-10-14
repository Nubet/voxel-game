package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.core.*;
import com.norbertfila.meshing.MeshData;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerState;
import com.norbertfila.player.WaterContact;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class UnderwaterVisibilitySmoke {
    private static final int WIDTH = 960, HEIGHT = 540;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        check(Files.isDirectory(output), "Output directory must exist");
        try (Window window = new Window(WIDTH, HEIGHT, "Underwater visibility acceptance");
                UiRenderer ui = new UiRenderer()) {
            System.out.println("scene=distance-cards/aquarium seed=0 resolution=960x540 GPU=" + glGetString(GL_RENDERER)
                    + " GL=" + glGetString(GL_VERSION) + " Java=" + Runtime.version());
            distanceCards(ui, output);
            aquarium(ui, output);
            check(glGetError() == GL_NO_ERROR, "Final GL error");
            System.out.println(
                    "PASS underwater contrast, exact dry reset, HUD, real surfaces and unchanged GPU mesh memory");
        }
    }

    private static void distanceCards(UiRenderer ui, Path output) throws Exception {
        var eye = new Vector3f(0, 3, 0);
        var vp = projection(eye, new Vector3f(0, 3, -1));
        try (TerrainRenderer renderer = new TerrainRenderer()) {
            float[] depths = {3, 12, 28}, x = {-1.2f, 0, 11.2f};
            for (int i = 0; i < 3; i++) {
                float halfWidth = depths[i] * .13f, halfHeight = depths[i] * .18f;
                float[] vertices = new float[36];
                int cursor = 0;
                for (int corner : new int[] {0, 1, 2, 2, 3, 0}) {
                    vertices[cursor++] = x[i] + (corner == 1 || corner == 2 ? halfWidth : -halfWidth);
                    vertices[cursor++] = 3 + (corner >= 2 ? halfHeight : -halfHeight);
                    vertices[cursor++] = -depths[i];
                    vertices[cursor++] = 1;
                    vertices[cursor++] = 1;
                    vertices[cursor++] = 1;
                }
                renderer.upload(
                        new ChunkPos(Math.floorDiv((int) Math.floor(x[i]), 16), 0, Math.floorDiv((int) -depths[i], 16)),
                        new MeshData(vertices));
            }
            var dry = new UnderwaterVisibility();
            var wet = new UnderwaterVisibility();
            wet.update(true, true, 1);
            byte[] baseline = capture(renderer, ui, vp, eye, dry, false);
            byte[] underwater = capture(renderer, ui, vp, eye, wet, false);
            byte[] restored = capture(renderer, ui, vp, eye, dry, true);
            check(Arrays.equals(baseline, restored), "Dry overload retains underwater uniforms");
            double previousContrast = Double.POSITIVE_INFINITY;
            for (int i = 0; i < 3; i++) {
                int px = (int) ((x[i] / depths[i] * (1 / Math.tan(Math.toRadians(30))) / (WIDTH / (double) HEIGHT) + 1)
                        * WIDTH
                        / 2);
                int py = HEIGHT / 2 + 25;
                check(channel(baseline, px, py, 0) == 255, "Card missing from dry frame");
                double contrast = 0;
                float[] fog = {
                    UnderwaterVisibility.FOG_RED, UnderwaterVisibility.FOG_GREEN, UnderwaterVisibility.FOG_BLUE
                };
                for (int c = 0; c < 3; c++) contrast += Math.abs(channel(underwater, px, py, c) - fog[c] * 255);
                check(contrast < previousContrast, "Fog is not stronger at greater distance");
                previousContrast = contrast;
                System.out.println("distance=" + depths[i] + " fogContrast=" + contrast);
            }
            check(
                    channel(underwater, WIDTH / 2, HEIGHT / 2, 0) == 255
                            && channel(underwater, WIDTH / 2, HEIGHT / 2, 1) == 255,
                    "Crosshair was tinted");
            check(
                    channel(underwater, 1, 1, 0) == Math.round(UnderwaterVisibility.FOG_RED * 255),
                    "Fog background seam");
            write(output.resolve("underwater-distance-dry.png"), baseline);
            write(output.resolve("underwater-distance-wet.png"), underwater);
            for (int i = 0; i < 50; i++) {
                draw(renderer, ui, vp, eye, dry, false);
                draw(renderer, ui, vp, eye, wet, false);
            }
            for (var visibility : new UnderwaterVisibility[] {dry, wet}) {
                glFinish();
                long started = System.nanoTime();
                for (int i = 0; i < 200; i++) draw(renderer, ui, vp, eye, visibility, false);
                glFinish();
                System.out.printf(
                        "cards synchronous render+HUD meanMs=%.4f fog=%.1f draws=3 warmup=50 frames=200%n",
                        (System.nanoTime() - started) / 200e6, visibility.amount());
            }
        }
    }

    private static void aquarium(UiRenderer ui, Path output) throws Exception {
        World world = new World();
        for (int x = -1; x <= 0; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -3; z <= 0; z++) world.putChunk(new ChunkPos(x, y, z), new Chunk());
        for (int x = -8; x <= 8; x++)
            for (int z = -40; z <= 6; z++) {
                world.setBlock(new BlockPos(x, 0, z), BlockId.SAND, 64);
                for (int y = 1; y <= 5; y++) world.setBlock(new BlockPos(x, y, z), BlockId.WATER, 64);
            }
        for (int z : new int[] {0, -8, -20, -32})
            for (int y = 1; y <= 3; y++) {
                world.setBlock(new BlockPos(-3, y, z), BlockId.BRICKS, 64);
                world.setBlock(new BlockPos(3, y, z), BlockId.STONE, 64);
            }
        var player = PlayerState.at(new PlayerState.Position(.5, 1, 4.5));
        var contact = WaterContact.sample(world, player);
        check(contact.available() && contact.eyesSubmerged(), "Aquarium contact missing");
        var wet = new UnderwaterVisibility();
        wet.update(contact.eyesSubmerged(), contact.available(), 1);
        var dry = new UnderwaterVisibility();
        try (BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                TerrainRenderer renderer = new TerrainRenderer(textures)) {
            var mesher = new WaterMesher();
            for (var p : world.residentPositions()) {
                var n = world.blockNeighborhood(p);
                renderer.upload(p, mesher.mesh(n.center(), n, p.x(), p.y(), p.z()));
            }
            long allocated = renderer.memoryStats().allocated();
            var eye = new Vector3f(.5f, 2.62f, 4.5f);
            var vp = projection(eye, new Vector3f(.5f, 1.4f, -12));
            write(output.resolve("underwater-aquarium-baseline.png"), capture(renderer, ui, vp, eye, dry, false));
            write(output.resolve("underwater-aquarium-wet.png"), capture(renderer, ui, vp, eye, wet, false));
            write(
                    output.resolve("underwater-aquarium-surface.png"),
                    capture(renderer, ui, projection(eye, new Vector3f(.5f, 7, -5)), eye, wet, false));
            eye.set(.5f, 8, 4.5f);
            write(
                    output.resolve("underwater-aquarium-above.png"),
                    capture(renderer, ui, projection(eye, new Vector3f(.5f, 1, -8)), eye, dry, false));
            check(renderer.memoryStats().allocated() == allocated, "View effect changed GPU geometry allocations");
            System.out.println("aquarium meshes=" + world.residentCount() + " GPUBytes=" + allocated);
        }
    }

    private static Matrix4f projection(Vector3f eye, Vector3f target) {
        return new Matrix4f()
                .perspective((float) Math.toRadians(60), WIDTH / (float) HEIGHT, .1f, 100)
                .lookAt(eye, target, new Vector3f(0, 1, 0));
    }

    private static void draw(
            TerrainRenderer renderer,
            UiRenderer ui,
            Matrix4f vp,
            Vector3f eye,
            UnderwaterVisibility visibility,
            boolean legacy) {
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(visibility.backgroundRed(), visibility.backgroundGreen(), visibility.backgroundBlue(), 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        var stats = legacy
                ? renderer.render(vp, Frustum.from(vp), eye)
                : renderer.render(vp, Frustum.from(vp), eye, visibility.amount());
        check(stats.drawCalls() > 0, "No terrain draws");
        ui.render(new SettingsPanel(new GameSettings()), new Hotbar(), new UiViewport(WIDTH, HEIGHT, WIDTH, HEIGHT));
        check(glGetError() == GL_NO_ERROR, "Draw GL error");
    }

    private static byte[] capture(
            TerrainRenderer renderer,
            UiRenderer ui,
            Matrix4f vp,
            Vector3f eye,
            UnderwaterVisibility visibility,
            boolean legacy) {
        draw(renderer, ui, vp, eye, visibility, legacy);
        var pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] data = new byte[pixels.remaining()];
        pixels.get(data);
        return data;
    }

    private static int channel(byte[] pixels, int x, int y, int channel) {
        return Byte.toUnsignedInt(pixels[(y * WIDTH + x) * 4 + channel]);
    }

    private static void write(Path path, byte[] pixels) throws Exception {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++)
                image.setRGB(
                        x,
                        HEIGHT - 1 - y,
                        0xff000000
                                | channel(pixels, x, y, 0) << 16
                                | channel(pixels, x, y, 1) << 8
                                | channel(pixels, x, y, 2));
        ImageIO.write(image, "png", path.toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
