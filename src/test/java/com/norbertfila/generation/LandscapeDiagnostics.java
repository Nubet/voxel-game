package com.norbertfila.generation;

import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

public final class LandscapeDiagnostics {
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final long[] SEEDS = {0, 1, 42, 7340215, -1, -918273, 987654321, Long.MAX_VALUE};
    private static final Window[] WINDOWS = {
        new Window("overview", -4096, -4096, 256, 32),
        new Window("detail", -512, -512, 256, 4),
        new Window("remote", -36864, 28672, 256, 32)
    };

    private LandscapeDiagnostics() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("Expected output directory and optional baseline directory");
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        StringBuilder summary = new StringBuilder(
                "seed,version,window,origin_x,origin_z,size,step,min,p10,p50,p90,max,mean,mean_slope,mean_relief_32,water_fraction,shore_fraction,fingerprint\n");
        StringBuilder distribution = new StringBuilder("seed,window,kind,value,count\n");
        StringBuilder timings = new StringBuilder("seed,window,samples,sample_ms,chunks,cold_chunk_ms,warm_chunk_ms\n");
        StringBuilder sites = new StringBuilder("seed,window,x,z,water_level\n");
        StringBuilder biomeSites = new StringBuilder("seed,window,biome,x,z,height\n");
        StringBuilder blocks = new StringBuilder("seed,window,block,count\n");
        for (long seed : SEEDS) {
            TerrainGenerator terrain = new TerrainGenerator(seed, TerrainConfig.defaults());
            for (Window window : WINDOWS) {
                long start = System.nanoTime();
                Survey survey = survey(terrain, window);
                double sampleMs = elapsed(start);
                String name = seed + "-" + window.name();
                writeMaps(output.resolve(name + ".png"), survey);
                writeTransect(output.resolve(name + "-transect.csv"), terrain, window);
                summary.append(survey.summary(seed, terrain.generatorVersion()));
                survey.distributions(seed, distribution);
                var seenBiomes = java.util.EnumSet.noneOf(com.norbertfila.generation.biome.BiomeType.class);
                for (int index = 0; index < survey.samples().length; index++) {
                    var sample = survey.samples()[index];
                    if (!sample.flooded() && seenBiomes.add(sample.biome()))
                        biomeSites
                                .append(seed)
                                .append(',')
                                .append(window.name())
                                .append(',')
                                .append(sample.biome())
                                .append(',')
                                .append(window.x(index % window.size()))
                                .append(',')
                                .append(window.z(index / window.size()))
                                .append(',')
                                .append(sample.surfaceHeight())
                                .append('\n');
                }
                for (int index = 0; index < survey.samples().length; index++) {
                    var sample = survey.samples()[index];
                    if (sample.waterKind() == WaterKind.RIVER
                            && sample.flooded()
                            && sample.waterLevel() > TerrainConfig.defaults().waterLevel() + 3) {
                        sites.append(seed)
                                .append(',')
                                .append(window.name())
                                .append(',')
                                .append(window.x(index % window.size()))
                                .append(',')
                                .append(window.z(index / window.size()))
                                .append(',')
                                .append(sample.waterLevel())
                                .append('\n');
                        break;
                    }
                }
                int[] heights = survey.heights();
                int center = window.size() / 2;
                int x = Math.floorDiv(window.x(center), Chunk.SIZE);
                int z = Math.floorDiv(window.z(center), Chunk.SIZE);
                int y = Math.floorDiv(heights[center * window.size() + center], Chunk.SIZE);
                TerrainGenerator chunkTerrain = new TerrainGenerator(seed, TerrainConfig.defaults());
                start = System.nanoTime();
                int[] blockCounts = new int[256];
                long fingerprint = chunks(chunkTerrain, x, y, z, blockCounts);
                double coldMs = elapsed(start);
                start = System.nanoTime();
                if (fingerprint != chunks(chunkTerrain, x, y, z, null))
                    throw new IllegalStateException("Chunk cache changed generated blocks");
                for (int id = 0; id < blockCounts.length; id++)
                    if (blockCounts[id] > 0)
                        blocks.append(seed)
                                .append(',')
                                .append(window.name())
                                .append(',')
                                .append(BlockRegistry.DEFAULT
                                        .definition((byte) id)
                                        .name())
                                .append(',')
                                .append(blockCounts[id])
                                .append('\n');
                timings.append(String.format(
                        Locale.ROOT,
                        "%d,%s,%d,%.3f,27,%.3f,%.3f%n",
                        seed,
                        window.name(),
                        heights.length,
                        sampleMs,
                        coldMs,
                        elapsed(start)));
                System.out.println(name + ": " + survey.heightDistribution() + ", sampling " + (int) sampleMs + " ms");
            }
        }
        Files.writeString(output.resolve("summary.csv"), summary);
        Files.writeString(output.resolve("distributions.csv"), distribution);
        Files.writeString(output.resolve("timings.csv"), timings);
        Files.writeString(output.resolve("river-sites.csv"), sites);
        Files.writeString(output.resolve("biome-sites.csv"), biomeSites);
        Files.writeString(output.resolve("generated-blocks.csv"), blocks);
        if (args.length == 2)
            compare(
                    Path.of(args[1]).resolve("summary.csv"),
                    output.resolve("summary.csv"),
                    output.resolve("comparison.csv"));
    }

    static Survey survey(TerrainSource terrain, Window window) {
        int count = Math.multiplyExact(window.size(), window.size());
        int[] heights = new int[count];
        double[] slopes = new double[count];
        int[] relief = new int[count];
        boolean[] shoreMask = new boolean[count];
        TerrainSummary[] samples = new TerrainSummary[count];
        Map<String, Integer> distribution = new TreeMap<>();
        long fingerprint = 0xcbf29ce484222325L;
        int water = 0, shores = 0;
        for (int z = 0; z < window.size(); z++)
            for (int x = 0; x < window.size(); x++) {
                int index = z * window.size() + x;
                int wx = window.x(x), wz = window.z(z);
                TerrainSummary sample = terrain.sample(wx, wz);
                samples[index] = sample;
                heights[index] = sample.surfaceHeight();
                int min = sample.surfaceHeight(), max = min;
                boolean shore = false;
                for (int[] direction : DIRECTIONS) {
                    TerrainSummary neighbor = terrain.sample(wx + direction[0], wz + direction[1]);
                    slopes[index] =
                            Math.max(slopes[index], Math.abs((double) neighbor.surfaceHeight() - heights[index]));
                    shore |= sample.flooded() != neighbor.flooded();
                    int distant = terrain.surfaceHeight(wx + 32 * direction[0], wz + 32 * direction[1]);
                    min = Math.min(min, distant);
                    max = Math.max(max, distant);
                }
                relief[index] = max - min;
                shoreMask[index] = shore;
                if (sample.flooded()) water++;
                if (shore) shores++;
                distribution.merge("biome," + sample.biome(), 1, Integer::sum);
                distribution.merge(
                        "water_kind," + (sample.flooded() ? sample.waterKind() : WaterKind.NONE), 1, Integer::sum);
                distribution.merge(
                        "material,"
                                + BlockRegistry.DEFAULT
                                        .definition(sample.surfaceMaterial())
                                        .name(),
                        1,
                        Integer::sum);
                distribution.merge("height_bin," + Math.floorDiv(heights[index], 16) * 16, 1, Integer::sum);
                fingerprint = (fingerprint ^ heights[index]) * 0x100000001b3L;
                fingerprint = (fingerprint ^ sample.waterLevel()) * 0x100000001b3L;
                fingerprint = (fingerprint ^ sample.surfaceMaterial()) * 0x100000001b3L;
                fingerprint = (fingerprint ^ sample.biome().ordinal()) * 0x100000001b3L;
                fingerprint = (fingerprint ^ sample.waterKind().ordinal()) * 0x100000001b3L;
            }
        components(samples, window.size(), distribution, true);
        components(samples, window.size(), distribution, false);
        return new Survey(
                window, samples, heights, slopes, relief, shoreMask, water, shores, fingerprint, distribution);
    }

    private static void components(
            TerrainSummary[] samples, int size, Map<String, Integer> distribution, boolean water) {
        boolean[] seen = new boolean[samples.length];
        int[] queue = new int[samples.length];
        for (int start = 0; start < samples.length; start++) {
            if (seen[start] || water && !samples[start].flooded()) continue;
            int head = 0, tail = 1;
            queue[0] = start;
            seen[start] = true;
            while (head < tail) {
                int index = queue[head++], x = index % size, z = index / size;
                for (int[] d : DIRECTIONS) {
                    int nx = x + d[0], nz = z + d[1];
                    if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                    int next = nz * size + nx;
                    if (seen[next]) continue;
                    boolean same = water
                            ? samples[next].flooded() && samples[next].waterKind() == samples[start].waterKind()
                            : samples[next].biome() == samples[start].biome();
                    if (!same) continue;
                    seen[next] = true;
                    queue[tail++] = next;
                }
            }
            String type = water
                    ? samples[start].waterKind().name()
                    : samples[start].biome().name();
            distribution.merge(
                    (water ? "sampled_water_component," : "sampled_biome_component,") + type + ":" + tail,
                    1,
                    Integer::sum);
        }
    }

    private static long chunks(TerrainGenerator terrain, int cx, int cy, int cz, int[] counts) {
        long fingerprint = 1;
        for (int z = cz - 1; z <= cz + 1; z++)
            for (int x = cx - 1; x <= cx + 1; x++)
                for (int y = cy - 1; y <= cy + 1; y++) {
                    ChunkData chunk = terrain.generate(new ChunkPos(x, y, z));
                    for (int bz = 0; bz < Chunk.SIZE; bz++)
                        for (int bx = 0; bx < Chunk.SIZE; bx++)
                            for (int by = 0; by < Chunk.SIZE; by++) {
                                byte block = chunk.get(bx, by, bz);
                                fingerprint = fingerprint * 31 + block;
                                if (counts != null) counts[Byte.toUnsignedInt(block)]++;
                            }
                }
        return fingerprint;
    }

    static void compare(Path baseline, Path current, Path output) throws IOException {
        Map<String, String[]> previous = new TreeMap<>();
        var before = Files.readAllLines(baseline);
        var after = Files.readAllLines(current);
        if (before.isEmpty() || after.isEmpty() || !before.getFirst().equals(after.getFirst()))
            throw new IOException("Landscape report schemas differ");
        String[] columns = before.getFirst().split(",");
        for (int i = 1; i < before.size(); i++) {
            String[] row = before.get(i).split(",");
            previous.put(row[0] + "," + row[2], row);
        }
        StringBuilder result = new StringBuilder("seed,window,metric,baseline,current,delta\n");
        for (int i = 1; i < after.size(); i++) {
            String[] row = after.get(i).split(",");
            String key = row[0] + "," + row[2];
            String[] old = previous.get(key);
            if (old == null || !Arrays.equals(Arrays.copyOfRange(old, 3, 7), Arrays.copyOfRange(row, 3, 7)))
                throw new IOException("Landscape windows differ: " + key);
            for (int column = 7; column < columns.length - 1; column++)
                result.append(String.format(
                        Locale.ROOT,
                        "%s,%s,%s,%s,%.6f%n",
                        key,
                        columns[column],
                        old[column],
                        row[column],
                        Double.parseDouble(row[column]) - Double.parseDouble(old[column])));
        }
        Files.writeString(output, result);
    }

    private static double elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private static void writeTransect(Path output, TerrainSource terrain, Window window) throws IOException {
        StringBuilder csv = new StringBuilder("x,z,height,water_level,flooded,biome,material\n");
        for (int x = 0; x < window.size() * window.step(); x++) {
            int wx = window.x(0) + x, wz = window.z(window.size() / 2);
            TerrainSummary sample = terrain.sample(wx, wz);
            csv.append(wx)
                    .append(',')
                    .append(wz)
                    .append(',')
                    .append(sample.surfaceHeight())
                    .append(',')
                    .append(sample.waterLevel())
                    .append(',')
                    .append(sample.flooded())
                    .append(',')
                    .append(sample.biome())
                    .append(',')
                    .append(BlockRegistry.DEFAULT
                            .definition(sample.surfaceMaterial())
                            .name())
                    .append('\n');
        }
        Files.writeString(output, csv);
    }

    static void writeMaps(Path output, Survey survey) throws IOException {
        int size = survey.window().size();
        BufferedImage image = new BufferedImage(size * 3, size * 2 + 40, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        String[] labels = {
            "Height [-32, 320]",
            "Slope [0, 16]",
            "Relief at 32 blocks [0, 128]",
            "Biome",
            "Surface material",
            "Water depth / dry shore"
        };
        for (int panel = 0; panel < labels.length; panel++)
            graphics.drawString(labels[panel], panel % 3 * size + 4, panel / 3 * (size + 20) + 15);
        graphics.dispose();
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++) {
                int index = z * size + x;
                TerrainSummary sample = survey.samples()[index];
                int material = Byte.toUnsignedInt(sample.surfaceMaterial());
                int[] colors = {
                    gray((survey.heights()[index] + 32) / 352.0),
                    gray(survey.slopes()[index] / 16),
                    gray(survey.relief()[index] / 128.0),
                    BiomeDebugMap.color(sample.biome()).getRGB(),
                    Color.HSBtoRGB((material * .618034f) % 1, .6f, .9f),
                    sample.flooded()
                            ? new Color(
                                            20,
                                            70,
                                            140
                                                    + (int) Math.clamp(
                                                            (sample.waterLevel() - (long) sample.surfaceHeight()) * 3,
                                                            0,
                                                            115))
                                    .getRGB()
                            : survey.shoreMask()[index] ? 0xf0d090 : 0x384030
                };
                for (int panel = 0; panel < colors.length; panel++)
                    image.setRGB(panel % 3 * size + x, panel / 3 * (size + 20) + 20 + z, colors[panel]);
            }
        if (!ImageIO.write(image, "png", output.toFile())) throw new IOException("PNG writer is unavailable");
    }

    private static int gray(double value) {
        int level = (int) (Math.clamp(value, 0, 1) * 255);
        return level * 0x010101;
    }

    record Window(String name, int originX, int originZ, int size, int step) {
        Window {
            if (size < 1 || step < 1) throw new IllegalArgumentException("Window size and step must be positive");
            int extent = Math.addExact(Math.multiplyExact(size, step), 32);
            Math.addExact(originX, extent);
            Math.addExact(originZ, extent);
            Math.subtractExact(originX, 32);
            Math.subtractExact(originZ, 32);
        }

        int x(int index) {
            return originX + index * step;
        }

        int z(int index) {
            return originZ + index * step;
        }
    }

    record Survey(
            Window window,
            TerrainSummary[] samples,
            int[] heights,
            double[] slopes,
            int[] relief,
            boolean[] shoreMask,
            int water,
            int shores,
            long fingerprint,
            Map<String, Integer> distribution) {
        String heightDistribution() {
            int[] sorted = heights.clone();
            Arrays.sort(sorted);
            return sorted[0] + "," + sorted[sorted.length / 10] + "," + sorted[sorted.length / 2] + ","
                    + sorted[sorted.length * 9 / 10] + "," + sorted[sorted.length - 1];
        }

        String summary(long seed, int version) {
            return String.format(
                    Locale.ROOT,
                    "%d,%d,%s,%d,%d,%d,%d,%s,%.4f,%.4f,%.4f,%.6f,%.6f,%016x%n",
                    seed,
                    version,
                    window.name(),
                    window.originX(),
                    window.originZ(),
                    window.size(),
                    window.step(),
                    heightDistribution(),
                    Arrays.stream(heights).average().orElseThrow(),
                    Arrays.stream(slopes).average().orElseThrow(),
                    Arrays.stream(relief).average().orElseThrow(),
                    (double) water / heights.length,
                    (double) shores / heights.length,
                    fingerprint);
        }

        void distributions(long seed, StringBuilder output) {
            distribution.forEach((key, count) -> output.append(seed)
                    .append(',')
                    .append(window.name())
                    .append(',')
                    .append(key)
                    .append(',')
                    .append(count)
                    .append('\n'));
        }
    }
}
