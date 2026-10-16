package com.norbertfila.lod;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.core.GameSettings;
import com.norbertfila.i18n.Language;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.ui.*;
import com.norbertfila.ui.screens.SettingsScreen;
import com.norbertfila.ui.screens.ViewDistanceScreen;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class ViewDistanceSmoke {
    private static final int WIDTH = 640, HEIGHT = 480;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        try (var window = new Window(WIDTH, HEIGHT, "View distance acceptance");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var ui = new UiRenderer()) {
            textures.bind(0);
            var white = BufferUtils.createByteBuffer(4).putInt(-1).flip();
            glClearTexImage(glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY), 0, GL_RGBA, GL_UNSIGNED_BYTE, white);
            glViewport(0, 0, WIDTH, HEIGHT);
            for (boolean far : new boolean[] {false, true})
                for (boolean water : new boolean[] {false, true}) verifyFog(textures, output, far, water);
            verifyUi(ui, output);
            check(glGetError() == GL_NO_ERROR, "OpenGL error");
            System.out.println(
                    "PASS view distances: solid/water/LOD fog, underwater, PL/EN UI; GPU=" + glGetString(GL_RENDERER));
        }
    }

    private static void verifyFog(BlockTextureArray textures, Path output, boolean far, boolean water)
            throws Exception {
        String name = (far ? "far" : "near") + (water ? "-water" : "-solid");
        try (var renderer = new TerrainRenderer(textures)) {
            if (far) {
                int[] heights = new int[16 * 16], waterTops = new int[heights.length];
                byte[] materials = new byte[heights.length];
                Arrays.fill(heights, water ? FarTerrainTile.NO_SURFACE : 0);
                Arrays.fill(waterTops, water ? 0 : FarTerrainTile.NO_WATER);
                Arrays.fill(materials, BlockId.STONE);
                var tile = new FarTerrainTile(0, 0, 4, 0, heights, materials, waterTops);
                var mesh = FarTileMesh.of(tile, FarTileMesher.mesh(tile, new int[64]));
                renderer.far().reconcile(new FarTerrainStreamer.Residency(1, Map.of(mesh.position(), mesh)), 0, 0);
                while (renderer.far().pendingUploads() > 0) renderer.far().upload(Long.MAX_VALUE, Long.MAX_VALUE);
            } else {
                var world = new World();
                var chunk = new Chunk();
                for (int z = 0; z < Chunk.SIZE; z++)
                    for (int x = 0; x < Chunk.SIZE; x++) chunk.set(x, 0, z, water ? BlockId.WATER : BlockId.STONE);
                var position = new ChunkPos(0, 0, 0);
                world.putChunk(position, chunk);
                var n = world.blockNeighborhood(position);
                renderer.upload(position, new WaterMesher().mesh(n.center(), n, 0, 0, 0, ChunkMesh.ALL));
            }
            int[] sky = {140, 194, 245};
            renderer.viewDistances(4, 0);
            int base = distance(pixel(draw(renderer, far, 4, 0)), sky);
            check(base > 30, name + ": no visible reference surface");
            var middle = draw(renderer, far, 50, 0);
            int mid = distance(pixel(middle), sky);
            check(mid > 5 && mid < base - 5, name + ": missing gradual fog: " + base + " -> " + mid);
            write(output.resolve(name + "-fog.png"), middle);
            var hidden = draw(renderer, far, 80, 0);
            check(distance(pixel(hidden), sky) <= 3, name + ": outer edge did not blend into sky");
            renderer.viewDistances(4, 32);
            check(
                    distance(pixel(draw(renderer, far, 80, 0)), sky) > 30,
                    name + ": LOD range was limited to detail range");
            var submerged = draw(renderer, far, 80, 1);
            renderer.viewDistances(4, 0);
            check(submerged.equals(draw(renderer, far, 80, 1)), name + ": distance fog changed full underwater fog");
            check(glGetBoolean(GL_DEPTH_WRITEMASK) && !glIsEnabled(GL_BLEND), name + ": leaked render state");
            System.out.println(
                    "PASS " + name + " fog distance-to-sky=" + base + "/" + mid + "/" + distance(pixel(hidden), sky));
        }
    }

    private static ByteBuffer draw(TerrainRenderer renderer, boolean far, float distance, float underwater) {
        var eye = new Vector3f(8, 4, 8 + distance);
        var forward = new Vector3f(8, 1, 8).sub(eye);
        var vp = new Matrix4f()
                .ortho(-2, 2, -1.5f, 1.5f, .1f, 300)
                .lookAt(new Vector3f(), forward, new Vector3f(0, 1, 0));
        clear();
        int calls;
        if (far) calls = renderer.far().render(vp, eye.x, eye.y, eye.z, underwater, ChunkManager.Coverage.EMPTY);
        else calls = renderer.render(vp, eye.x, eye.y, eye.z, underwater).drawCalls();
        check(calls > 0, "No draws");
        return capture();
    }

    private static void verifyUi(UiRenderer ui, Path output) throws Exception {
        var settings = new GameSettings();
        settings.setRenderDistance(4);
        for (Language language : Language.values()) {
            settings.translations().setLanguage(language);
            for (int scale : new int[] {1, 2}) {
                var viewport = new UiViewport(WIDTH, HEIGHT, WIDTH, HEIGHT, scale);
                for (int far : new int[] {0, 4, 32, 128}) {
                    settings.setFarDistance(far);
                    var screen = new ViewDistanceScreen(settings);
                    var canvas = new UiCanvas();
                    clear();
                    screen.render(canvas, viewport);
                    ui.render(canvas, viewport);
                    write(
                            output.resolve("distances-" + language + "-scale" + scale + "-far" + far + ".png"),
                            capture());
                }
                var canvas = new UiCanvas();
                clear();
                new SettingsScreen(settings).render(canvas, viewport);
                ui.render(canvas, viewport);
                write(output.resolve("settings-" + language + "-scale" + scale + ".png"), capture());
            }
        }
    }

    private static int[] pixel(ByteBuffer pixels) {
        int offset = ((HEIGHT / 2) * WIDTH + WIDTH / 2) * 4;
        return new int[] {pixels.get(offset) & 255, pixels.get(offset + 1) & 255, pixels.get(offset + 2) & 255};
    }

    private static int distance(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
    }

    private static void clear() {
        glClearColor(.55f, .76f, .96f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static void write(Path file, ByteBuffer pixels) throws Exception {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) {
                int i = (y * WIDTH + x) * 4;
                image.setRGB(
                        x,
                        HEIGHT - y - 1,
                        (pixels.get(i) & 255) << 16 | (pixels.get(i + 1) & 255) << 8 | pixels.get(i + 2) & 255);
            }
        ImageIO.write(image, "png", file.toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
