package com.norbertfila.render;

import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.platform.Window;
import com.norbertfila.weather.*;
import com.norbertfila.world.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

public final class WeatherSmoke {
    private static final int SIZE = 512;
    private static final Path OUTPUT = Path.of("build/weather-smoke");
    private static final WeatherSample RAIN = new WeatherSample(.95f, 1, 0, 0);
    private static final WeatherSample STORM = new WeatherSample(1, 1, 1, 0);

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        try (var window = new Window(SIZE, SIZE, "Weather regression");
                var sky = new SkyRenderer();
                var precipitation = new PrecipitationRenderer()) {
            glViewport(0, 0, SIZE, SIZE);
            var view = new Matrix4f()
                    .perspective((float) Math.toRadians(70), 1, .1f, 128)
                    .lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
            var columns = columns(new LocalWeather(1, 0), 0);
            clear();
            check(
                    precipitation.render(view, columns, 0, 8, 0, 1, WeatherSample.CLEAR, 0, SkyModel.DAY) == 0,
                    "Clear weather drew precipitation");
            check(total(capture()) == 0, "Clear weather changed pixels");
            precipitation.render(view, columns, 0, 8, 0, 1, RAIN, 0, SkyModel.DAY);
            var rain = capture();
            check(total(rain) > 1000, "Rain is not visible");
            write(rain, "rain");
            check(
                    glGetBoolean(GL_DEPTH_WRITEMASK)
                            && glIsEnabled(GL_DEPTH_TEST)
                            && glIsEnabled(GL_CULL_FACE)
                            && !glIsEnabled(GL_BLEND),
                    "Precipitation leaked terrain state");
            clear();
            precipitation.render(view, columns, 0, 8, 0, 1.1, RAIN, 0, SkyModel.DAY);
            check(!rain.equals(capture()), "Rain did not animate");
            clear();
            precipitation.render(view, columns, 0, 8, 0, 257, RAIN, 0, SkyModel.DAY);
            check(rain.equals(capture()), "Time wrapping jumped precipitation");
            for (int translation : new int[] {1 << 24, -(1 << 24)}) {
                var distant = columns.stream()
                        .map(c -> new PrecipitationColumn(
                                c.x() + translation, c.z() + translation, c.surface(), c.weather()))
                        .toList();
                clear();
                precipitation.render(view, distant, translation, 8, translation, 1, RAIN, 0, SkyModel.DAY);
                check(rain.equals(capture()), "Distant world coordinates lost precipitation precision");
            }
            clear();
            precipitation.render(view, columns, 0, 15.999, 0, 1, RAIN, 0, SkyModel.DAY);
            long belowBoundary = total(capture());
            clear();
            precipitation.render(view, columns, 0, 16.001, 0, 1, RAIN, 0, SkyModel.DAY);
            long aboveBoundary = total(capture());
            check(
                    Math.abs(aboveBoundary - belowBoundary) < belowBoundary * .1,
                    "Vertical section boundary jumped precipitation");
            clear();
            precipitation.render(view, columns(new LocalWeather(0, 1), 0), 0, 8, 0, 1, RAIN, 0, SkyModel.DAY);
            var snow = capture();
            check(total(snow) > 1000 && !snow.equals(rain), "Snow is not distinct and visible");
            write(snow, "snow");
            clear();
            precipitation.render(view, columns(new LocalWeather(1, 0), 40), 0, 8, 0, 1, RAIN, 0, SkyModel.DAY);
            check(total(capture()) == 0, "Rain leaked under a roof");
            clear();
            precipitation.render(view, columns, 0, 8, 0, 1, RAIN, 1, SkyModel.DAY);
            check(total(capture()) == 0, "Rain visible underwater");
            clear();
            precipitation.render(view, columns(new LocalWeather(0, 0), 0), 0, 8, 0, 1, RAIN, 0, SkyModel.DAY);
            check(total(capture()) == 0, "Desert drew precipitation");
            var overhead = new Matrix4f()
                    .perspective((float) Math.toRadians(70), 1, .1f, 128)
                    .lookAt(0, 0, 0, 0, 1, -.001f, 0, 1, 0);
            long clearSky = 0, stormSky = 0;
            for (var weather : List.of(
                    WeatherSample.CLEAR, new WeatherSample(.8f, 0, 0, 0), RAIN, STORM, new WeatherSample(1, 1, 1, 1))) {
                clear();
                sky.render(overhead, SkyModel.weather(SkyModel.DAY, weather), 0);
                var pixels = capture();
                write(pixels, "sky-" + weather.clouds() + "-" + weather.storm() + "-" + weather.flash());
                if (weather == WeatherSample.CLEAR) clearSky = total(pixels);
                if (weather == STORM) stormSky = total(pixels);
                if (weather.flash() > 0) check(total(pixels) > stormSky, "Storm flash missing");
            }
            check(clearSky > stormSky * 2, "Storm did not darken the sky");
            roomAndTimings(view, sky, precipitation);
            check(glGetError() == GL_NO_ERROR, "Weather OpenGL error");
            System.out.println(
                    "PASS rain/snow, roofs, desert, underwater, time wrap, atmosphere, flashes and GL state; GPU="
                            + glGetString(GL_RENDERER));
        }
    }

    private static void roomAndTimings(Matrix4f view, SkyRenderer sky, PrecipitationRenderer precipitation)
            throws Exception {
        var world = new World();
        for (int cz = -2; cz <= 1; cz++)
            for (int cx = -2; cx <= 1; cx++)
                for (int cy = WorldBounds.MIN_CHUNK_Y; cy <= WorldBounds.MAX_CHUNK_Y; cy++)
                    world.putChunk(new ChunkPos(cx, cy, cz), new Chunk());
        for (int z = -32; z < 32; z++)
            for (int x = -32; x < 32; x++) world.setBlock(new BlockPos(x, 0, z), BlockId.GRASS, 64);
        for (int z = -5; z <= 4; z++)
            for (int x = -4; x <= 4; x++) world.setBlock(new BlockPos(x, 8, z), BlockId.PLANKS, 64);
        for (int y = 1; y < 8; y++)
            for (int x = -4; x <= 4; x++)
                world.setBlock(
                        new BlockPos(x, y, -5),
                        Math.abs(x) <= 1 && y >= 3 && y <= 5 ? BlockId.GLASS : BlockId.PLANKS,
                        64);
        var field = new PrecipitationField(world, new BiomeWeather(new HillsGenerator()));
        world.setBlockChangeListener(field::onBlockChanged);
        for (int i = 0; i < 40; i++) field.update(.5, .5, true);
        check(field.snapshot().size() > 1500, "Loaded room lost precipitation columns");
        check(
                field.snapshot().stream()
                                .filter(c -> c.x() == 0 && c.z() == 0)
                                .findFirst()
                                .orElseThrow()
                                .surface()
                        == 9,
                "Room roof missing from precipitation snapshot");
        try (var textures = new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures());
                var terrain = new TerrainRenderer(textures)) {
            for (int z = -2; z <= 1; z++)
                for (int x = -2; x <= 1; x++) {
                    var position = new ChunkPos(x, 0, z);
                    var neighborhood = world.blockNeighborhood(position);
                    terrain.upload(
                            position,
                            new WaterMesher()
                                    .mesh(
                                            neighborhood.center(),
                                            neighborhood,
                                            x * Chunk.SIZE,
                                            0,
                                            z * Chunk.SIZE,
                                            ChunkMesh.ALL));
                }
            var atmosphere = SkyModel.weather(SkyModel.DAY, STORM);
            clear();
            sky.render(view, atmosphere, 0);
            terrain.environment(atmosphere);
            terrain.render(view, .5, 4, .5, 0);
            var baseline = capture();
            precipitation.render(view, field.snapshot(), .5, 4, .5, 1, STORM, 0, atmosphere);
            var rain = capture();
            write(rain, "room-rain");
            check(differences(baseline, rain, 170, 190, 330, 320) > 10, "Rain is not visible through the glass window");
            check(differences(baseline, rain, 20, 20, 100, 100) == 0, "Rain leaked through an opaque wall");
            clear();
            terrain.render(view, .5, 4, .5, 0);
            var depth = BufferUtils.createFloatBuffer(1);
            glReadPixels(40, 40, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, depth);
            check(depth.get(0) < 1, "Room test did not draw opaque terrain");

            long[] moving = new long[120];
            for (int i = 0; i < moving.length; i++) {
                long start = System.nanoTime();
                check(
                        field.update(i % 4 + .5, .5, true) <= PrecipitationField.BUDGET,
                        "Moving weather exceeded budget");
                moving[i] = System.nanoTime() - start;
            }
            Arrays.sort(moving);
            System.out.printf(
                    java.util.Locale.ROOT,
                    "Weather field CPU moving: p50=%.3f ms p95=%.3f ms%n",
                    moving[60] / 1e6,
                    moving[114] / 1e6);
            field.update(.5, .5, true);
            for (int i = 0; i < 40; i++) field.update(.5, .5, true);
            for (var weather : List.of(WeatherSample.CLEAR, STORM)) {
                long[] cpu = new long[60], gpu = new long[60];
                int query = glGenQueries();
                try {
                    for (int frame = -20; frame < 60; frame++) {
                        var environment = SkyModel.weather(SkyModel.DAY, weather);
                        terrain.environment(environment);
                        glBeginQuery(GL_TIME_ELAPSED, query);
                        long start = System.nanoTime();
                        clear();
                        sky.render(view, environment, 0);
                        terrain.render(view, .5, 4, .5, 0);
                        precipitation.render(
                                view, field.snapshot(), .5, 4, .5, frame / 60d + 10, weather, 0, environment);
                        long submitted = System.nanoTime() - start;
                        glEndQuery(GL_TIME_ELAPSED);
                        long elapsed = glGetQueryObjectui64(query, GL_QUERY_RESULT);
                        if (frame >= 0) {
                            cpu[frame] = submitted;
                            gpu[frame] = elapsed;
                        }
                    }
                } finally {
                    glDeleteQueries(query);
                }
                Arrays.sort(cpu);
                Arrays.sort(gpu);
                System.out.printf(
                        java.util.Locale.ROOT,
                        "Weather scene 512x512 %s: CPU submit p50=%.3f p95=%.3f ms; GPU p50=%.3f p95=%.3f ms%n",
                        weather == STORM ? "storm" : "clear",
                        cpu[30] / 1e6,
                        cpu[57] / 1e6,
                        gpu[30] / 1e6,
                        gpu[57] / 1e6);
            }
        }
    }

    private static int differences(ByteBuffer a, ByteBuffer b, int x0, int y0, int x1, int y1) {
        int changed = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int i = (y * SIZE + x) * 4;
                if (a.get(i) != b.get(i) || a.get(i + 1) != b.get(i + 1) || a.get(i + 2) != b.get(i + 2)) changed++;
            }
        return changed;
    }

    private static List<PrecipitationColumn> columns(LocalWeather weather, float surface) {
        var columns = new ArrayList<PrecipitationColumn>();
        for (int z = -20; z <= 4; z++)
            for (int x = -16; x <= 16; x++) columns.add(new PrecipitationColumn(x, z, surface, weather));
        return List.copyOf(columns);
    }

    private static void clear() {
        glClearColor(0, 0, 0, 1);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    private static ByteBuffer capture() {
        var pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return pixels;
    }

    private static long total(ByteBuffer pixels) {
        long result = 0;
        for (int i = 0; i < pixels.capacity(); i += 4)
            result += Byte.toUnsignedInt(pixels.get(i))
                    + Byte.toUnsignedInt(pixels.get(i + 1))
                    + Byte.toUnsignedInt(pixels.get(i + 2));
        return result;
    }

    private static void write(ByteBuffer pixels, String name) throws Exception {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int offset = ((SIZE - 1 - y) * SIZE + x) * 4;
                image.setRGB(
                        x,
                        y,
                        Byte.toUnsignedInt(pixels.get(offset)) << 16
                                | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(offset + 2)));
            }
        ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
