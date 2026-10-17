package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.lod.*;
import com.norbertfila.meshing.*;
import com.norbertfila.platform.Window;
import com.norbertfila.render.*;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.world.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class JungleSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Jungle acceptance");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            scene(new JungleScene(false), textures, output, "flat");
            scene(new JungleScene(true), textures, output, "slope");
            scene(new JungleScene(false, false), textures, output, "forest-reference");
            var source = new TerrainGenerator(42, TerrainConfig.defaults());
            var main = JungleSites.find(source, false);
            var mahogany = JungleSites.find(source, true);
            scene(source, textures, output, "natural-jungle", main.x(), main.z());
            scene(source, textures, output, "natural-mahogany", mahogany.x(), mahogany.z());
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Jungle OpenGL error");
        }
    }

    static void scene(TerrainSource source, BlockTextureArray textures, Path output, String name) throws Exception {
        scene(source, textures, output, name, 0, 0);
    }

    static void scene(TerrainSource source, BlockTextureArray textures, Path output, String name, int ox, int oz)
            throws Exception {
        var world = new World();
        var times = new long[3];
        for (int round = -1; round < times.length; round++) {
            long start = System.nanoTime();
            for (int z = -2; z <= 2; z++)
                for (int x = -2; x <= 2; x++)
                    // Include all possible crowns, not only sections containing ground.
                    for (int y = 0; y < 7; y++) {
                        var p = new ChunkPos(Math.floorDiv(ox, 16) + x, y, Math.floorDiv(oz, 16) + z);
                        world.putChunk(p, source.generate(p).toChunk());
                    }
            if (round >= 0) times[round] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        var mesher = new WaterMesher();
        long quads = 0, bytes = 0;
        var meshTimes = new long[5];
        for (int round = -3; round < meshTimes.length; round++) {
            long start = System.nanoTime();
            quads = 0;
            bytes = 0;
            for (var p : world.residentPositions()) {
                var n = world.blockNeighborhood(p);
                var mesh = mesher.mesh(n.center(), n, p.x(), p.y(), p.z(), ChunkMesh.ALL);
                quads += mesh.quadCount();
                bytes += mesh.bytes();
            }
            if (round >= 0) meshTimes[round] = System.nanoTime() - start;
        }
        Arrays.sort(meshTimes);
        String report =
                "%s seed=%d x=%d z=%d sections=%d generationMedianMs=%.2f meshMedianMs=%.2f quads=%d meshBytes=%d%n"
                        .formatted(
                                name,
                                source.seed(),
                                ox,
                                oz,
                                world.residentCount(),
                                times[1] / 1e6,
                                meshTimes[2] / 1e6,
                                quads,
                                bytes);
        System.out.print(report);
        Files.writeString(output.resolve(name + "-metrics.txt"), report);
        int largest = JungleWalkability.largest(world, source, ox - 32, oz - 32, 64);
        Files.writeString(
                output.resolve(name + "-metrics.txt"),
                "largestConnectedWalkable=" + largest + " area=4096\n",
                StandardOpenOption.APPEND);
        try (var renderer = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(renderer, world);
            for (String view : List.of("ground", "canopy")) {
                int height = source.surfaceHeight(ox, oz);
                var eye = view.equals("ground")
                        ? new Vector3f(ox, source.surfaceHeight(ox, oz + 25) + 2.7f, oz + 25)
                        : new Vector3f(ox + 65, height + 45, oz + 65);
                var target = new Vector3f(ox, height + (view.equals("ground") ? 3 : 8), oz);
                var vp = new Matrix4f()
                        .perspective((float) Math.toRadians(65), 960f / 540, .1f, 500)
                        .lookAt(eye, target, new Vector3f(0, 1, 0));
                VegetationSmoke.write(
                        output.resolve(name + "-" + view + ".png"), VegetationSmoke.draw(renderer, vp, eye));
                if (view.equals("canopy")) {
                    for (int i = 0; i < 20; i++)
                        renderer.render(new Matrix4f(vp).translate(eye), eye.x, eye.y, eye.z, 0);
                    glFinish();
                    long start = System.nanoTime();
                    for (int i = 0; i < 50; i++)
                        renderer.render(new Matrix4f(vp).translate(eye), eye.x, eye.y, eye.z, 0);
                    glFinish();
                    String gpu = "gpuBytes=%d synchronousRenderMeanMs=%.3f warmupFrames=20 measuredFrames=50%n"
                            .formatted(renderer.memoryStats().allocated(), (System.nanoTime() - start) / 50e6);
                    System.out.print(gpu);
                    Files.writeString(output.resolve(name + "-metrics.txt"), gpu, StandardOpenOption.APPEND);
                }
            }
        }
        var generator = new FarTerrainGenerator(source);
        for (int step : new int[] {1, 4, 8, 16}) {
            var tiles = new HashMap<FarTilePos, FarTileMesh>();
            for (int z = -1; z <= 0; z++)
                for (int x = -1; x <= 0; x++) {
                    int tx = Math.floorDiv(ox, 64) + x, tz = Math.floorDiv(oz, 64) + z;
                    var tile = generator.generate(tx, tz, step, AreaEdits.none(tx * 64, tz * 64, 64));
                    var mesh = new FarTileMesh(
                            tx,
                            tz,
                            step,
                            0,
                            FarTileMesher.mesh(tile, generator.border(tx, tz, step)),
                            tile.minHeight(),
                            tile.maxHeight() + 1);
                    tiles.put(mesh.position(), mesh);
                }
            try (var renderer = new FarTerrainRenderer(textures)) {
                var eye = new Vector3f(ox + 90, source.surfaceHeight(ox, oz) + 68, oz + 90);
                renderer.reconcile(new FarTerrainStreamer.Residency(1, tiles), eye.x, eye.z);
                while (renderer.pendingUploads() > 0) renderer.upload(Long.MAX_VALUE, Long.MAX_VALUE);
                var vp = new Matrix4f()
                        .perspective((float) Math.toRadians(65), 960f / 540, .1f, 500)
                        .lookAt(eye, new Vector3f(ox, source.surfaceHeight(ox, oz) + 11, oz), new Vector3f(0, 1, 0));
                glClearColor(32 / 255f, 64 / 255f, 96 / 255f, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                if (renderer.render(
                                new Matrix4f(vp).translate(eye), eye.x, eye.y, eye.z, 0, ChunkManager.Coverage.EMPTY)
                        == 0) throw new AssertionError("Missing Jungle far draws");
                var pixels = org.lwjgl.BufferUtils.createByteBuffer(960 * 540 * 4);
                glReadPixels(0, 0, 960, 540, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                byte[] image = new byte[pixels.remaining()];
                pixels.get(image);
                VegetationSmoke.write(output.resolve(name + "-far-" + step + ".png"), image);
            }
        }
    }
}
