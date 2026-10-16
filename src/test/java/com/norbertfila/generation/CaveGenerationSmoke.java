package com.norbertfila.generation;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.platform.Window;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CaveGenerationSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Cave generation acceptance");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            System.out.println("GPU=" + glGetString(GL_RENDERER) + " GL=" + glGetString(GL_VERSION));
            for (long seed : new long[] {0, 42, -123456789}) {
                var config = TerrainConfig.defaults();
                var terrain = new TerrainGenerator(seed, config);
                var planner = new CavePlanner(seed, com.norbertfila.world.WorldBounds.MIN_Y, terrain::surfaceHeight);
                var carver = new CaveCarver(terrain);
                CavePlan.Segment chamber = null;
                Vector3f entrance = null;
                for (int rz = -1; rz <= 1; rz++)
                    for (int rx = -1; rx <= 1; rx++) {
                        var plan = planner.plan(rx, rz);
                        for (int cz = rz * 8; cz < rz * 8 + 8; cz++)
                            for (int cx = rx * 8; cx < rx * 8 + 8; cx++) {
                                var segments = plan.segments(cx, cz);
                                for (var segment : segments) {
                                    int x = (int) Math.floor(segment.x()),
                                            y = (int) Math.floor(segment.y()),
                                            z = (int) Math.floor(segment.z());
                                    if ((chamber == null || segment.radiusXZ() > chamber.radiusXZ())
                                            && carver.carves(
                                                    x,
                                                    y,
                                                    z,
                                                    terrain.sample(x, z).materialAt(y),
                                                    segments)) chamber = segment;
                                }
                                if (entrance != null) continue;
                                for (int z = 0; z < 16; z++)
                                    for (int x = 0; x < 16; x++) {
                                        int wx = cx * 16 + x, wz = cz * 16 + z;
                                        var base = terrain.sample(wx, wz);
                                        if (carver.carves(
                                                        wx, base.surfaceHeight(), wz, base.surfaceMaterial(), segments)
                                                && openSky(terrain, wx, base.surfaceHeight() + 1, wz))
                                            entrance = new Vector3f(wx + .5f, base.surfaceHeight() + .5f, wz + .5f);
                                    }
                            }
                    }
                if (chamber == null || entrance == null)
                    throw new AssertionError("Missing caves/entrances seed=" + seed);
                var interior = new Vector3f((float) chamber.x(), (float) chamber.y(), (float) chamber.z());
                capture(
                        terrain,
                        textures,
                        interior,
                        new Vector3f(interior).add(12, -1, 3),
                        output.resolve("cave-" + seed + "-interior.png"));
                capture(
                        terrain,
                        textures,
                        new Vector3f(entrance).add(0, 12, 0),
                        entrance,
                        output.resolve("cave-" + seed + "-entrance.png"));
                System.out.println("PASS seed=" + seed + " interior=" + interior + " entrance=" + entrance);
            }
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Cave rendering GL error");
        }
    }

    private static void capture(
            TerrainGenerator terrain, BlockTextureArray textures, Vector3f eye, Vector3f target, Path file)
            throws Exception {
        int cx = Math.floorDiv((int) Math.floor(eye.x), 16),
                cy = Math.floorDiv((int) Math.floor(eye.y), 16),
                cz = Math.floorDiv((int) Math.floor(eye.z), 16);
        var world = new World();
        for (int z = cz - 2; z <= cz + 2; z++)
            for (int x = cx - 2; x <= cx + 2; x++)
                for (int y = cy - 2; y <= cy + 2; y++) {
                    var position = new ChunkPos(x, y, z);
                    world.putChunk(position, terrain.generate(position).toChunk());
                }
        try (var renderer = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(renderer, world);
            var vp = new Matrix4f()
                    .perspective(
                            (float) Math.toRadians(65),
                            VegetationSmoke.WIDTH / (float) VegetationSmoke.HEIGHT,
                            .1f,
                            160)
                    .lookAt(
                            eye,
                            target,
                            eye.x == target.x && eye.z == target.z ? new Vector3f(0, 0, -1) : new Vector3f(0, 1, 0));
            byte[] pixels = VegetationSmoke.draw(renderer, vp, eye);
            int visible = 0;
            for (int i = 0; i < pixels.length; i += 4)
                if (Byte.toUnsignedInt(pixels[i]) != 32
                        || Byte.toUnsignedInt(pixels[i + 1]) != 64
                        || Byte.toUnsignedInt(pixels[i + 2]) != 96) visible++;
            if (visible < 1000) throw new AssertionError("Missing cave geometry: " + file);
            VegetationSmoke.write(file, pixels);
        }
    }

    private static boolean openSky(TerrainGenerator terrain, int x, int y, int z) {
        for (int height = y; height < y + 16; height++)
            if (terrain.generate(new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(height, 16), Math.floorDiv(z, 16)))
                            .get(Math.floorMod(x, 16), Math.floorMod(height, 16), Math.floorMod(z, 16))
                    != BlockId.AIR) return false;
        return true;
    }
}
