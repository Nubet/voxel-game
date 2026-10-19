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
                search:
                for (int radius = 0; radius <= 8; radius++)
                    for (int rz = -radius; rz <= radius; rz++)
                        for (int rx = -radius; rx <= radius; rx++) {
                            if (Math.max(Math.abs(rx), Math.abs(rz)) != radius) continue;
                            var plan = planner.plan(rx, rz);
                            for (int cz = rz * 8; cz < rz * 8 + 8; cz++)
                                for (int cx = rx * 8; cx < rx * 8 + 8; cx++) {
                                    var segments = plan.segments(cx, cz);
                                    for (var segment : segments) {
                                        int x = (int) Math.floor(segment.x()),
                                                y = (int) Math.floor(segment.y()),
                                                z = (int) Math.floor(segment.z());
                                        if ((chamber == null || segment.radiusX() > chamber.radiusX())
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
                                                            wx,
                                                            base.surfaceHeight(),
                                                            wz,
                                                            base.surfaceMaterial(),
                                                            segments)
                                                    && openSky(terrain, wx, base.surfaceHeight() + 1, wz))
                                                entrance = new Vector3f(wx + .5f, base.surfaceHeight() + .5f, wz + .5f);
                                        }
                                }
                            if (chamber != null && entrance != null) break search;
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
            captureLava(textures, output.resolve("lava.png"));
            captureNaturalLava(textures, output.resolve("natural-lava.png"));
            captureProfiles(textures, output);
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Cave rendering GL error");
        }
    }

    private static void captureProfiles(BlockTextureArray textures, Path output) throws Exception {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        var planner = new CavePlanner(42, com.norbertfila.world.WorldBounds.MIN_Y, terrain::surfaceHeight);
        var captured = java.util.EnumSet.noneOf(CaveProfile.class);
        var atlases = java.util.EnumSet.noneOf(CaveProfile.class);
        for (int rz = -3; rz <= 3; rz++)
            for (int rx = -3; rx <= 3; rx++) {
                var plan = planner.plan(rx, rz);
                for (var system : plan.systems()) {
                    boolean underground = system.junctions().stream()
                            .allMatch(p -> p.y()
                                    < terrain.surfaceHeight((int) Math.floor(p.x()), (int) Math.floor(p.z())) - 8);
                    if (!underground) continue;
                    if (atlases.add(system.character())) {
                        CaveGenerationAtlas.write(
                                terrain,
                                plan,
                                output.resolve("atlas-"
                                        + system.character().name().toLowerCase(java.util.Locale.ROOT) + ".png"));
                    }
                    for (var route : system.routes()) {
                        if (route.profile() == CaveProfile.FLOODED || captured.contains(route.profile())) continue;
                        var points = route.points();
                        var p = points.get(points.size() / 4);
                        var target = points.get(Math.min(points.size() - 1, points.size() / 4 + 10));
                        capture(
                                terrain,
                                textures,
                                vector(p).add(0, .25f, 0),
                                vector(target),
                                output.resolve("profile-"
                                        + route.profile().name().toLowerCase(java.util.Locale.ROOT) + ".png"));
                        captured.add(route.profile());
                        System.out.println("PASS profile=" + route.profile() + " seed=42 eye=" + vector(p));
                    }
                }
                if (!captured.contains(CaveProfile.CHAMBER))
                    for (var segment : plan.allSegments()) {
                        if (segment.profile() != CaveProfile.CHAMBER
                                || segment.y() > terrain.surfaceHeight((int) segment.x(), (int) segment.z()) - 8)
                            continue;
                        var target = new Vector3f((float) segment.x(), (float) segment.y(), (float) segment.z());
                        capture(
                                terrain,
                                textures,
                                new Vector3f(target).add((float) (-segment.radiusX() * .5), .2f, 0),
                                new Vector3f(target).add(2, 0, 1),
                                output.resolve("profile-chamber.png"));
                        captured.add(CaveProfile.CHAMBER);
                        System.out.println("PASS profile=CHAMBER seed=42 center=" + target);
                        break;
                    }
                if (!captured.contains(CaveProfile.FLOODED))
                    for (int z = rz * 8; z < rz * 8 + 8; z++)
                        for (int x = rx * 8; x < rx * 8 + 8; x++)
                            for (var feature : terrain.caveFeatures(x, z)) {
                                if (captured.contains(CaveProfile.FLOODED)
                                        || feature.type() != CaveFeatures.Type.FLOODED) continue;
                                var target = new Vector3f(feature.x() + .5f, feature.y() + .5f, feature.z() + .5f);
                                capture(
                                        terrain,
                                        textures,
                                        new Vector3f(target).add(-3, 1.5f, 0),
                                        target,
                                        output.resolve("profile-flooded.png"));
                                captured.add(CaveProfile.FLOODED);
                                System.out.println("PASS profile=FLOODED seed=42 water=" + target);
                            }
                if (captured.size() == CaveProfile.values().length && atlases.size() == 3) return;
            }
        if (!captured.contains(CaveProfile.SINKHOLE))
            for (long seed = 0; seed < 64 && !captured.contains(CaveProfile.SINKHOLE); seed++) {
                var candidateTerrain = new TerrainGenerator(seed, TerrainConfig.defaults());
                var candidatePlanner =
                        new CavePlanner(seed, com.norbertfila.world.WorldBounds.MIN_Y, candidateTerrain::surfaceHeight);
                for (int rz = -4; rz <= 4 && !captured.contains(CaveProfile.SINKHOLE); rz++)
                    for (int rx = -4; rx <= 4 && !captured.contains(CaveProfile.SINKHOLE); rx++)
                        for (var system : candidatePlanner.plan(rx, rz).systems())
                            for (var route : system.routes())
                                if (route.profile() == CaveProfile.SINKHOLE) {
                                    var point =
                                            route.points().get(route.points().size() / 2);
                                    var target = route.points().getLast();
                                    capture(
                                            candidateTerrain,
                                            textures,
                                            vector(point).add(0, .5f, 0),
                                            vector(target),
                                            output.resolve("profile-sinkhole.png"));
                                    captured.add(CaveProfile.SINKHOLE);
                                    System.out.println(
                                            "PASS profile=SINKHOLE seed=" + seed + " surface=" + vector(target));
                                }
            }
        if (captured.size() == CaveProfile.values().length) return;
        throw new AssertionError("Missing cave acceptance profiles: " + captured);
    }

    private static Vector3f vector(CaveSystem.Point point) {
        return new Vector3f((float) point.x(), (float) point.y(), (float) point.z());
    }

    private static void captureLava(BlockTextureArray textures, Path file) throws Exception {
        var world = new World();
        var chunk = new com.norbertfila.world.Chunk();
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                chunk.set(x, 0, z, BlockId.STONE);
                if (x >= 3 && x <= 12 && z >= 3 && z <= 12) chunk.set(x, 1, z, BlockId.LAVA);
            }
        world.putChunk(new ChunkPos(0, 0, 0), chunk);
        try (var renderer = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(renderer, world);
            var eye = new Vector3f(8, 10, 18);
            var vp = new Matrix4f()
                    .perspective(
                            (float) Math.toRadians(65),
                            VegetationSmoke.WIDTH / (float) VegetationSmoke.HEIGHT,
                            .1f,
                            160)
                    .lookAt(eye, new Vector3f(8, 1, 8), new Vector3f(0, 1, 0));
            byte[] pixels = VegetationSmoke.draw(renderer, vp, eye);
            int orange = 0;
            for (int i = 0; i < pixels.length; i += 4)
                if (Byte.toUnsignedInt(pixels[i]) > 180
                        && Byte.toUnsignedInt(pixels[i + 1]) < 180
                        && Byte.toUnsignedInt(pixels[i + 2]) < 80) orange++;
            if (orange < 1000) throw new AssertionError("Missing opaque lava surface");
            VegetationSmoke.write(file, pixels);
            System.out.println("PASS lava pixels=" + orange);
        }
    }

    private static void captureNaturalLava(BlockTextureArray textures, Path file) throws Exception {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        for (int z = -16; z < 16; z++)
            for (int x = -16; x < 16; x++)
                for (var feature : terrain.caveFeatures(x, z)) {
                    if (feature.material() != BlockId.LAVA) continue;
                    var data = terrain.generate(new ChunkPos(
                            Math.floorDiv(feature.x(), 16),
                            Math.floorDiv(feature.y(), 16),
                            Math.floorDiv(feature.z(), 16)));
                    if (data.get(
                                    Math.floorMod(feature.x(), 16),
                                    Math.floorMod(feature.y(), 16),
                                    Math.floorMod(feature.z(), 16))
                            != BlockId.LAVA) continue;
                    var target = new Vector3f(feature.x() + .5f, feature.y() + .5f, feature.z() + .5f);
                    capture(terrain, textures, new Vector3f(target).add(0, 2.5f, 0), target, file);
                    System.out.println("PASS natural lava=" + target);
                    return;
                }
        throw new AssertionError("Missing naturally generated lava in acceptance area");
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
