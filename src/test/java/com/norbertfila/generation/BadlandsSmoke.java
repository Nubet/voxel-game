package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.lod.*;
import com.norbertfila.platform.Window;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.FarTerrainRenderer;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class BadlandsSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        var source = new TerrainGenerator(42, TerrainConfig.defaults());
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Badlands acceptance");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            scene(source, textures, -1408, -1440, output, "lowlands");
            scene(source, textures, -2016, -1472, output, "highlands");
            scene(new BadlandsCliffScene(), textures, 0, 0, output, "cliff-fixture");
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Badlands OpenGL error");
            System.out.println("PASS Badlands near/far rendering, GPU=" + glGetString(GL_RENDERER));
        }
    }

    private static void scene(TerrainSource source, BlockTextureArray textures, int x, int z, Path output, String name)
            throws Exception {
        int originTileX = Math.floorDiv(x, 64) - 1, originTileZ = Math.floorDiv(z, 64) - 1;
        var world = new World();
        long start = System.nanoTime();
        for (int cz = originTileZ * 4; cz < (originTileZ + 3) * 4; cz++)
            for (int cx = originTileX * 4; cx < (originTileX + 3) * 4; cx++) {
                int maxY = 0;
                for (int lz = 0; lz < 16; lz++)
                    for (int lx = 0; lx < 16; lx++)
                        maxY = Math.max(maxY, source.surfaceHeight(cx * 16 + lx, cz * 16 + lz) + 5);
                for (int cy = WorldBounds.MIN_CHUNK_Y; cy <= Math.floorDiv(maxY, 16); cy++) {
                    var position = new ChunkPos(cx, cy, cz);
                    world.putChunk(position, source.generate(position).toChunk());
                }
            }
        System.out.printf(
                "%s seed=42 x=%d z=%d chunks=%d generationMs=%.1f%n",
                name, x, z, world.residentCount(), (System.nanoTime() - start) / 1e6);
        int height = source.surfaceHeight(x, z);
        var eye = new Vector3f(x + 90, height + 60, z + 100);
        var vp = new Matrix4f()
                .perspective(
                        (float) Math.toRadians(65), VegetationSmoke.WIDTH / (float) VegetationSmoke.HEIGHT, .1f, 1000)
                .lookAt(eye, new Vector3f(x, height - 12, z), new Vector3f(0, 1, 0));
        try (var near = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(near, world);
            byte[] pixels = VegetationSmoke.draw(near, vp, eye);
            checkVisible(pixels);
            VegetationSmoke.write(output.resolve(name + "-near.png"), pixels);
        }
        var generator = new FarTerrainGenerator(source);
        for (int step : new int[] {1, 4, 8, 16}) {
            start = System.nanoTime();
            Map<FarTilePos, FarTileMesh> tiles = new HashMap<>();
            long quads = 0;
            for (int tz = originTileZ; tz < originTileZ + 3; tz++)
                for (int tx = originTileX; tx < originTileX + 3; tx++) {
                    var tile = generator.generate(tx, tz, step, AreaEdits.none(tx * 64, tz * 64, 64));
                    var mesh = new FarTileMesh(
                            tx,
                            tz,
                            step,
                            tile.editRevision(),
                            FarTileMesher.mesh(tile, generator.border(tx, tz, step)),
                            WorldBounds.MIN_Y - FarTileMesher.SKIRT,
                            WorldBounds.MAX_Y_EXCLUSIVE);
                    tiles.put(mesh.position(), mesh);
                    quads += mesh.mesh().quadCount();
                }
            System.out.printf(
                    "%s step=%d tiles=%d quads=%d buildMs=%.1f%n",
                    name, step, tiles.size(), quads, (System.nanoTime() - start) / 1e6);
            try (var far = new FarTerrainRenderer(textures)) {
                far.reconcile(new FarTerrainStreamer.Residency(1, tiles), eye.x, eye.z);
                while (far.pendingUploads() > 0) far.upload(Long.MAX_VALUE, Long.MAX_VALUE);
                glViewport(0, 0, VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT);
                glClearColor(32 / 255f, 64 / 255f, 96 / 255f, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                if (far.render(new Matrix4f(vp).translate(eye), eye.x, eye.y, eye.z, 0, ChunkManager.Coverage.EMPTY)
                        == 0) throw new AssertionError("No far Badlands draws");
                var buffer = BufferUtils.createByteBuffer(VegetationSmoke.WIDTH * VegetationSmoke.HEIGHT * 4);
                glReadPixels(0, 0, VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
                byte[] pixels = new byte[buffer.remaining()];
                buffer.get(pixels);
                checkVisible(pixels);
                VegetationSmoke.write(output.resolve(name + "-far-" + step + ".png"), pixels);
            }
        }
    }

    private static void checkVisible(byte[] pixels) {
        int visible = 0;
        for (int i = 0; i < pixels.length; i += 4)
            if ((pixels[i] & 255) != 32 || (pixels[i + 1] & 255) != 64 || (pixels[i + 2] & 255) != 96) visible++;
        if (visible < 5000) throw new AssertionError("Missing Badlands surface");
    }
}
