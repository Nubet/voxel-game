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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.CRC32;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;

public final class RiftGenerationSmoke {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        try (var window = new Window(VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, "Rift acceptance");
                var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            for (long seed : new long[] {0, 42, -123456789}) {
                var terrain = new TerrainGenerator(seed, TerrainConfig.defaults());
                distribution(terrain);
                scene(terrain, RiftGenerationTest.find(terrain), textures, output, "rift-" + seed);
            }
            var bank = new WaterBankScene();
            scene(bank, WaterBankScene.PLAN, textures, output, "dry-rift-near-water");
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("Rift OpenGL error");
            System.out.println("PASS rift acceptance GPU=" + glGetString(GL_RENDERER));
        }
    }

    private static void distribution(TerrainGenerator terrain) {
        var planner = new RiftPlanner(terrain.seed());
        int candidates = 0, accepted = 0;
        long area = 0;
        for (int z = -16; z <= 16; z++)
            for (int x = -16; x <= 16; x++) {
                var p = planner.plan(x, z);
                if (p.isEmpty()) continue;
                candidates++;
                var center = p.get().nodes().get(3);
                var plan = terrain.riftAt((int) center.x(), (int) center.z());
                if (plan == null) continue;
                accepted++;
                for (int wz = plan.minZ(); wz <= plan.maxZ(); wz++)
                    for (int wx = plan.minX(); wx <= plan.maxX(); wx++)
                        if (plan.section(wx + .5, wz + .5) != null) area++;
            }
        double worldArea = 33.0 * 33 * RiftPlanner.REGION_SIZE * RiftPlanner.REGION_SIZE;
        if (accepted == 0 || accepted > 108 || area / worldArea > .0001)
            throw new AssertionError("Rift distribution outside rare-landmark limits");
        System.out.printf(
                "seed=%d regions=1089 candidates=%d accepted=%d footprintPct=%.6f blocksPerRift=%.0f%n",
                terrain.seed(), candidates, accepted, 100 * area / worldArea, worldArea / accepted);
    }

    private static void scene(
            TerrainSource source, RiftPlanner.Plan plan, BlockTextureArray textures, Path output, String name)
            throws Exception {
        var center = plan.nodes().get(plan.nodes().size() / 2);
        int x = (int) center.x(), z = (int) center.z();
        int floor = source.surfaceHeight(x, z);
        var a = plan.nodes().getFirst();
        var b = plan.nodes().getLast();
        var direction = new Vector3f((float) (b.x() - a.x()), 0, (float) (b.z() - a.z())).normalize();
        var normal = new Vector3f(-direction.z, 0, direction.x);
        var target = new Vector3f(x + .5f, floor + 12, z + .5f);
        var overview = new Vector3f(target).fma(30, normal).fma(-30, direction).add(0, 100, 0);
        var rim = new Vector3f(x + .5f, 0, z + .5f).fma((float) center.halfWidth() * .9f, normal);
        rim.y = source.surfaceHeight((int) Math.floor(rim.x), (int) Math.floor(rim.z)) + 1.7f;
        var positions = new ArrayList<ChunkPos>();
        for (int cz = Math.floorDiv(plan.minZ() - 24, 16); cz <= Math.floorDiv(plan.maxZ() + 24, 16); cz++)
            for (int cx = Math.floorDiv(plan.minX() - 24, 16); cx <= Math.floorDiv(plan.maxX() + 24, 16); cx++)
                for (int cy = Math.floorDiv(floor - 16, 16); cy <= Math.floorDiv(floor + 72, 16); cy++)
                    positions.add(new ChunkPos(cx, cy, cz));
        var world = new World();
        long cold = generate(source, positions, world, name + " cold");
        long warm = generate(source, positions, null, name + " warm");
        if (cold != warm) throw new AssertionError("Rift cache changed terrain");
        System.out.printf(
                "%s x=%d y=%d z=%d biome=%s%n",
                name, x, floor, z, source.sample(x, z).biome());
        try (var renderer = new TerrainRenderer(textures)) {
            VegetationSmoke.upload(renderer, world);
            capture(renderer, overview, target, output.resolve(name + "-overview.png"));
            capture(renderer, rim, target, output.resolve(name + "-rim.png"));
            var eye = new Vector3f(x + .5f, floor + 1.7f, z + .5f);
            capture(
                    renderer,
                    eye,
                    new Vector3f(eye).fma(25, direction).add(0, 2, 0),
                    output.resolve(name + "-floor.png"));
        }
        var far = new FarTerrainGenerator(source);
        for (int step : new int[] {1, 4, 8, 16}) {
            var tiles = new HashMap<FarTilePos, FarTileMesh>();
            long start = System.nanoTime();
            for (int tz = Math.floorDiv(plan.minZ() - 24, 64); tz <= Math.floorDiv(plan.maxZ() + 24, 64); tz++)
                for (int tx = Math.floorDiv(plan.minX() - 24, 64); tx <= Math.floorDiv(plan.maxX() + 24, 64); tx++) {
                    var tile = far.generate(tx, tz, step, AreaEdits.none(tx * 64, tz * 64, 64));
                    var mesh = new FarTileMesh(
                            tx,
                            tz,
                            step,
                            0,
                            FarTileMesher.mesh(tile, far.border(tx, tz, step)),
                            WorldBounds.MIN_Y - FarTileMesher.SKIRT,
                            WorldBounds.MAX_Y_EXCLUSIVE);
                    tiles.put(mesh.position(), mesh);
                }
            System.out.printf(
                    "%s LOD step=%d tiles=%d ms=%.1f%n", name, step, tiles.size(), (System.nanoTime() - start) / 1e6);
            try (var renderer = new FarTerrainRenderer(textures)) {
                renderer.reconcile(new FarTerrainStreamer.Residency(1, tiles), overview.x, overview.z);
                while (renderer.pendingUploads() > 0) renderer.upload(Long.MAX_VALUE, Long.MAX_VALUE);
                glViewport(0, 0, VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT);
                glClearColor(32 / 255f, 64 / 255f, 96 / 255f, 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                if (renderer.render(
                                new Matrix4f(view(overview, target)).translate(overview),
                                overview.x,
                                overview.y,
                                overview.z,
                                0,
                                ChunkManager.Coverage.EMPTY)
                        == 0) throw new AssertionError("No far rift draws");
                var buffer = BufferUtils.createByteBuffer(VegetationSmoke.WIDTH * VegetationSmoke.HEIGHT * 4);
                glReadPixels(0, 0, VegetationSmoke.WIDTH, VegetationSmoke.HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
                byte[] pixels = new byte[buffer.remaining()];
                buffer.get(pixels);
                write(output.resolve(name + "-far-" + step + ".png"), pixels);
            }
        }
    }

    private static long generate(TerrainSource source, List<ChunkPos> positions, World world, String label) {
        var crc = new CRC32();
        long[] times = new long[positions.size()];
        for (int i = 0; i < positions.size(); i++) {
            long start = System.nanoTime();
            var data = source.generate(positions.get(i));
            times[i] = System.nanoTime() - start;
            crc.update(data.blocks());
            if (world != null) world.putChunk(positions.get(i), data.toChunk());
        }
        Arrays.sort(times);
        System.out.printf(
                "%s chunks=%d p50us=%.1f p95us=%.1f crc=%x%n",
                label,
                times.length,
                times[times.length / 2] / 1e3,
                times[(int) (times.length * .95)] / 1e3,
                crc.getValue());
        return crc.getValue();
    }

    private static Matrix4f view(Vector3f eye, Vector3f target) {
        return new Matrix4f()
                .perspective(
                        (float) Math.toRadians(65), VegetationSmoke.WIDTH / (float) VegetationSmoke.HEIGHT, .1f, 1000)
                .lookAt(eye, target, new Vector3f(0, 1, 0));
    }

    private static void capture(TerrainRenderer renderer, Vector3f eye, Vector3f target, Path file) throws Exception {
        write(file, VegetationSmoke.draw(renderer, view(eye, target), eye));
    }

    private static void write(Path file, byte[] pixels) throws Exception {
        int visible = 0;
        for (int i = 0; i < pixels.length; i += 4)
            if ((pixels[i] & 255) != 32 || (pixels[i + 1] & 255) != 64 || (pixels[i + 2] & 255) != 96) visible++;
        if (visible < 5000) throw new AssertionError("Missing rift geometry: " + file);
        VegetationSmoke.write(file, pixels);
    }

    private static final class WaterBankScene implements TerrainSource {
        static final RiftPlanner.Plan PLAN = new RiftPlanner.Plan(List.of(
                new RiftPlanner.Node(-48, .5, 5, 28),
                new RiftPlanner.Node(0, .5, 6, 30),
                new RiftPlanner.Node(48, .5, 5, 28)));

        @Override
        public TerrainSummary sample(int x, int z) {
            var base = new TerrainSummary(
                    z >= 18 ? 60 : 80,
                    z >= 18 ? BlockId.GRAVEL : BlockId.GRASS,
                    BlockId.DIRT,
                    BlockId.STONE,
                    WorldBounds.MIN_Y,
                    62);
            return RiftTerrainFeature.carve(PLAN, x, z, base);
        }

        @Override
        public int surfaceHeight(int x, int z) {
            return sample(x, z).surfaceHeight();
        }

        @Override
        public SurfaceSample sampleArea(int ox, int oz, int size) {
            var center = TerrainSource.super.sampleArea(ox, oz, size);
            var selected = new SurfaceSample(center.x(), center.z(), center.terrain(), true);
            for (int z = oz; z < oz + size; z++)
                for (int x = ox; x < ox + size; x++) {
                    var terrain = sample(x, z);
                    if (PLAN.section(x + .5, z + .5) != null
                            && terrain.surfaceHeight() < selected.terrain().surfaceHeight())
                        selected = new SurfaceSample(x, z, terrain, true);
                }
            return selected;
        }

        @Override
        public long seed() {
            return 0;
        }

        @Override
        public int generatorVersion() {
            return 47;
        }

        @Override
        public ChunkData generate(ChunkPos p) {
            var blocks = new byte[ChunkData.BLOCK_COUNT];
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    var column = sample(p.x() * 16 + x, p.z() * 16 + z);
                    for (int y = 0; y < 16; y++) blocks[(y * 16 + z) * 16 + x] = column.materialAt(p.y() * 16 + y);
                }
            return new ChunkData(p, blocks);
        }
    }
}
