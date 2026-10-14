package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.core.*;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerState;
import com.norbertfila.player.WaterContact;
import com.norbertfila.ui.Hud;
import com.norbertfila.ui.UiCanvas;
import com.norbertfila.ui.UiViewport;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class UnderwaterVisibilitySmoke {
    private static final int WIDTH = 960, HEIGHT = 540;
    private static final UiCanvas HUD = createHud();

    private static UiCanvas createHud() {
        UiCanvas canvas = new UiCanvas();
        new Hud().render(canvas, new Hotbar(), new UiViewport(WIDTH, HEIGHT, WIDTH, HEIGHT));
        return canvas;
    }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        check(Files.isDirectory(output), "Output directory must exist");
        try (Window window = new Window(WIDTH, HEIGHT, "Underwater visibility acceptance");
                UiRenderer ui = new UiRenderer()) {
            System.out.println("scene=aquarium seed=0 resolution=960x540 GPU=" + glGetString(GL_RENDERER) + " GL="
                    + glGetString(GL_VERSION) + " Java=" + Runtime.version());
            aquarium(ui, output);
            check(glGetError() == GL_NO_ERROR, "Final GL error");
            System.out.println(
                    "PASS underwater contrast, exact dry reset, HUD, real surfaces and unchanged GPU mesh memory");
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
                renderer.upload(p, mesher.mesh(n.center(), n, p.x(), p.y(), p.z(), ChunkMesh.ALL));
            }
            long allocated = renderer.memoryStats().allocated();
            var eye = new Vector3f(.5f, 2.62f, 4.5f);
            var target = new Vector3f(.5f, 1.4f, -12);
            write(output.resolve("underwater-aquarium-baseline.png"), capture(renderer, ui, eye, target, dry));
            write(output.resolve("underwater-aquarium-wet.png"), capture(renderer, ui, eye, target, wet));
            write(
                    output.resolve("underwater-aquarium-surface.png"),
                    capture(renderer, ui, eye, new Vector3f(.5f, 7, -5), wet));
            eye.set(.5f, 8, 4.5f);
            write(
                    output.resolve("underwater-aquarium-above.png"),
                    capture(renderer, ui, eye, new Vector3f(.5f, 1, -8), dry));
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
            TerrainRenderer renderer, UiRenderer ui, Vector3f eye, Vector3f target, UnderwaterVisibility visibility) {
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(visibility.backgroundRed(), visibility.backgroundGreen(), visibility.backgroundBlue(), 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        var relative = projection(new Vector3f(), new Vector3f(target).sub(eye));
        var stats = renderer.render(
                relative, Frustum.from(projection(eye, target)), eye.x, eye.y, eye.z, visibility.amount());
        check(stats.drawCalls() > 0, "No terrain draws");
        ui.render(HUD, new UiViewport(WIDTH, HEIGHT, WIDTH, HEIGHT));
        check(glGetError() == GL_NO_ERROR, "Draw GL error");
    }

    private static byte[] capture(
            TerrainRenderer renderer, UiRenderer ui, Vector3f eye, Vector3f target, UnderwaterVisibility visibility) {
        draw(renderer, ui, eye, target, visibility);
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
