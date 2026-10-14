package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.meshing.*;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class VegetationSmoke {
    private static final int WIDTH = 960, HEIGHT = 540;
    private static final WaterMesher MESHER = new WaterMesher();
    private static volatile int sink;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        check(Files.isDirectory(output), "Output directory must exist");
        System.out.println("seed=42 scene=flat-grass CPU=" + System.getenv("PROCESSOR_IDENTIFIER") + " Java="
                + Runtime.version() + " OS=" + System.getProperty("os.name"));
        var terrain = new TerrainSummary(8, BlockId.GRASS, BlockId.DIRT, BlockId.STONE);
        TerrainSummary[] columns = new TerrainSummary[256];
        Arrays.fill(columns, terrain);
        var vegetation = new VegetationGenerator(42, new PerlinNoise(42), (x, z) -> .5);
        var baseline = new World();
        var decorated = new World();
        long decorationNanos = 0;
        int plants = 0;
        for (int z = -3; z <= 3; z++)
            for (int x = -3; x <= 3; x++) {
                byte[] blocks = new byte[4096];
                for (int y = 0; y < 16; y++)
                    for (int lz = 0; lz < 16; lz++)
                        for (int lx = 0; lx < 16; lx++) blocks[(y * 16 + lz) * 16 + lx] = terrain.materialAt(y);
                var pos = new ChunkPos(x, 0, z);
                baseline.putChunk(pos, Chunk.fromBlocks(blocks));
                long started = System.nanoTime();
                vegetation.generate(x * 16, 0, z * 16, blocks, TerrainColumn.of(columns, java.util.List.of()));
                decorationNanos += System.nanoTime() - started;
                for (byte block : blocks) if (PlantRules.isPlant(block)) plants++;
                decorated.putChunk(pos, Chunk.fromBlocks(blocks));
            }
        System.out.printf("sections=49 plants=%d coldDecorationPassMs=%.3f%n", plants, decorationNanos / 1e6);
        measureMeshes("baseline", baseline);
        measureMeshes("vegetation", decorated);
        try (Window window = new Window(WIDTH, HEIGHT, "Vegetation acceptance");
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            System.out.println("resolution=960x540 GPU=" + glGetString(GL_RENDERER) + " GL=" + glGetString(GL_VERSION));
            species(textures, output);
            var eye = new Vector3f(35, 30, 45);
            var vp = new Matrix4f()
                    .perspective((float) java.lang.Math.toRadians(65), WIDTH / (float) HEIGHT, .1f, 300)
                    .lookAt(eye, new Vector3f(0, 8, 0), new Vector3f(0, 1, 0));
            for (boolean enabled : new boolean[] {false, true}) {
                try (TerrainRenderer renderer = new TerrainRenderer(textures)) {
                    upload(renderer, enabled ? decorated : baseline);
                    write(
                            output.resolve(enabled ? "vegetation-field.png" : "vegetation-baseline.png"),
                            draw(renderer, vp, eye));
                    for (int i = 0; i < 50; i++) render(renderer, vp, eye);
                    glFinish();
                    long start = System.nanoTime();
                    for (int i = 0; i < 200; i++) render(renderer, vp, eye);
                    glFinish();
                    System.out.printf(
                            "vegetation=%s GPUBytes=%d synchronousRenderMeanMs=%.4f warmup=50 frames=200%n",
                            enabled, renderer.memoryStats().allocated(), (System.nanoTime() - start) / 200e6);
                }
            }
            check(glGetError() == GL_NO_ERROR, "GL error");
        }
        System.out.println("PASS five colored cutouts front/back, field rendering and GL lifecycle");
    }

    private static void measureMeshes(String label, World world) {
        List<ChunkPos> positions = world.residentPositions();
        var neighborhoods = positions.stream().map(world::blockNeighborhood).toList();
        long[] batches = new long[5];
        long bytes = 0;
        for (int round = -5; round < batches.length; round++) {
            long started = System.nanoTime();
            long batchBytes = 0;
            for (int i = 0; i < positions.size(); i++) {
                var p = positions.get(i);
                var n = neighborhoods.get(i);
                ChunkMesh mesh = MESHER.mesh(n.center(), n, p.x(), p.y(), p.z(), ChunkMesh.ALL);
                batchBytes += mesh.bytes();
                sink = mesh.vertexCount();
            }
            if (round >= 0) batches[round] = System.nanoTime() - started;
            bytes = batchBytes;
        }
        Arrays.sort(batches);
        System.out.printf(
                "%s meshMedianMsPerSection=%.4f meshBytes=%d warmup=5 batches=5%n", label, batches[2] / (49e6), bytes);
    }

    private static void species(BlockTextureArray textures, Path output) throws Exception {
        World world = new World();
        Chunk chunk = new Chunk();
        byte[] species = {
            BlockId.SHORT_GRASS, BlockId.FERN, BlockId.DANDELION_YELLOW, BlockId.DANDELION_WHITE, BlockId.POPPY
        };
        for (int i = 0; i < species.length; i++) chunk.set(1 + i * 3, 1, 4, species[i]);
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        try (TerrainRenderer renderer = new TerrainRenderer(textures)) {
            upload(renderer, world);
            for (int side : new int[] {1, -1}) {
                Vector3f eye = new Vector3f(7.5f, 1.5f, 4.5f + side * 12);
                Matrix4f vp = new Matrix4f()
                        .ortho(-8, 8, -4.5f, 4.5f, .1f, 100)
                        .lookAt(eye, new Vector3f(7.5f, 1.5f, 4.5f), new Vector3f(0, 1, 0));
                byte[] pixels = draw(renderer, vp, eye);
                for (int i = 0; i < species.length; i++) {
                    Vector3f center = vp.transformProject(new Vector3f(1.5f + i * 3, 1.5f, 4.5f));
                    int px = java.lang.Math.round((center.x + 1) * WIDTH / 2);
                    int visible = 0, background = 0;
                    for (int y = HEIGHT / 2 - 30; y < HEIGHT / 2 + 30; y++)
                        for (int x = px - 30; x < px + 30; x++) {
                            int offset = (y * WIDTH + x) * 4;
                            if (Byte.toUnsignedInt(pixels[offset]) == 32
                                    && Byte.toUnsignedInt(pixels[offset + 1]) == 64
                                    && Byte.toUnsignedInt(pixels[offset + 2]) == 96) background++;
                            else visible++;
                        }
                    check(visible > 20 && background > 200, "Missing or opaque plant " + species[i] + " side=" + side);
                }
                write(
                        output.resolve(side == 1 ? "vegetation-species-front.png" : "vegetation-species-back.png"),
                        pixels);
            }
        }
    }

    private static void upload(TerrainRenderer renderer, World world) {
        for (var p : world.residentPositions()) {
            var n = world.blockNeighborhood(p);
            renderer.upload(p, MESHER.mesh(n.center(), n, p.x(), p.y(), p.z(), ChunkMesh.ALL));
        }
    }

    private static void render(TerrainRenderer renderer, Matrix4f vp, Vector3f eye) {
        glViewport(0, 0, WIDTH, HEIGHT);
        glClearColor(32 / 255f, 64 / 255f, 96 / 255f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        check(renderer.render(vp, Frustum.from(vp), eye).drawCalls() > 0, "No draws");
        check(glGetError() == GL_NO_ERROR, "Render GL error");
    }

    private static byte[] draw(TerrainRenderer renderer, Matrix4f vp, Vector3f eye) {
        render(renderer, vp, eye);
        var pixels = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
        glReadPixels(0, 0, WIDTH, HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] bytes = new byte[pixels.remaining()];
        pixels.get(bytes);
        return bytes;
    }

    private static void write(Path file, byte[] pixels) throws Exception {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++) {
                int offset = (y * WIDTH + x) * 4;
                image.setRGB(
                        x,
                        HEIGHT - y - 1,
                        0xff000000
                                | Byte.toUnsignedInt(pixels[offset]) << 16
                                | Byte.toUnsignedInt(pixels[offset + 1]) << 8
                                | Byte.toUnsignedInt(pixels[offset + 2]));
            }
        ImageIO.write(image, "png", file.toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
