package com.norbertfila.lod;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.platform.Window;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.FarTerrainRenderer;
import com.norbertfila.world.BlockRegistry;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

/** Renders far terrain of the benchmark seed from a few viewpoints into PNG files for visual checks. */
public final class FarTerrainSmoke {
    private static final int WIDTH = 1280, HEIGHT = 720;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        if (!Files.isDirectory(output)) throw new IllegalArgumentException("Output directory must exist");
        var terrain = new TerrainGenerator(7340215L, TerrainConfig.defaults());
        var generator = new FarTerrainGenerator(terrain);
        int radius = 16;
        Map<FarTilePos, FarTileMesh> tiles = new HashMap<>();
        long started = System.nanoTime();
        for (int z = -radius; z < radius; z++)
            for (int x = -radius; x < radius; x++) {
                double distance = Math.hypot((x + .5) * 64, (z + .5) * 64);
                int step = FarTerrainStreamer.step((long) distance, null);
                AreaEdits edits = AreaEdits.none(x * 64, z * 64, 64);
                FarTerrainTile tile = generator.generate(x, z, step, edits);
                FarTileMesh mesh = FarTileMesh.of(tile, FarTileMesher.mesh(tile, generator.border(x, z, step)));
                tiles.put(mesh.position(), mesh);
            }
        System.out.printf("tiles=%d buildMs=%.1f%n", tiles.size(), (System.nanoTime() - started) / 1e6);
        var residency = new FarTerrainStreamer.Residency(1, tiles, 0, 0, 0, 0);
        try (Window window = new Window(WIDTH, HEIGHT, "Far terrain smoke");
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                FarTerrainRenderer renderer = new FarTerrainRenderer(textures)) {
            renderer.reconcile(residency, 0, 0);
            while (renderer.pendingUploads() > 0) renderer.upload(Long.MAX_VALUE, Long.MAX_VALUE);
            int surface = terrain.surfaceHeight(0, 0);
            shot(renderer, output.resolve("far-high.png"), new Vector3f(0, surface + 120, 0), 45, -20);
            shot(renderer, output.resolve("far-low.png"), new Vector3f(0, surface + 3, 0), 135, -2);
            shot(renderer, output.resolve("far-top.png"), new Vector3f(0, surface + 600, 0), 45, -89);
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("GL error");
        }
    }

    private static void shot(FarTerrainRenderer renderer, Path file, Vector3f eye, float yaw, float pitch)
            throws Exception {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vector3f forward = new Vector3f(
                (float) (Math.cos(yr) * Math.cos(pr)), (float) Math.sin(pr), (float) (Math.sin(yr) * Math.cos(pr)));
        Matrix4f vp = new Matrix4f()
                .perspective((float) Math.toRadians(70), WIDTH / (float) HEIGHT, 1f, 4000f)
                .lookAt(new Vector3f(), forward, new Vector3f(0, 1, 0));
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(.55f, .75f, .95f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        int calls = renderer.render(vp, eye.x, eye.y, eye.z, 0, 0, 0, 0, 0);
        var pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) {
                int offset = (y * WIDTH + x) * 4;
                image.setRGB(
                        x,
                        HEIGHT - y - 1,
                        Byte.toUnsignedInt(pixels.get(offset)) << 16
                                | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(offset + 2)));
            }
        ImageIO.write(image, "png", file.toFile());
        System.out.println(file.getFileName() + " calls=" + calls);
    }
}
