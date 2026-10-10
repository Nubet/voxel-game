package com.norbertfila.diagnostics;

import com.norbertfila.generation.*;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.ChunkSnapshot;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TerrainBenchmark {
    private static volatile long checksum;
    private static volatile ChunkData dataSink;
    private static volatile ChunkSnapshot snapshotSink;

    private TerrainBenchmark() {}

    public static Result run(
            String name,
            TerrainSource source,
            List<ChunkPos> positions,
            int warmup,
            int iterations,
            double noiseSamplesPerChunk) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(source);
        List<ChunkPos> inputs = List.copyOf(positions);
        if (inputs.isEmpty() || warmup < 0 || iterations < 1)
            throw new IllegalArgumentException("Invalid benchmark inputs");
        ThreadMXBean allocations = allocationBean();
        Measurement heights = measure(() -> columns(source, inputs), allocations, warmup, iterations);
        Measurement generate = measure(() -> chunks(source, inputs, false), allocations, warmup, iterations);
        Measurement snapshot = measure(() -> chunks(source, inputs, true), allocations, warmup, iterations);
        return new Result(
                name,
                source.seed(),
                source.generatorVersion(),
                inputs.size(),
                warmup,
                iterations,
                heights,
                generate,
                snapshot,
                noiseSamplesPerChunk);
    }

    public static double countNoiseSamples(long seed, TerrainConfig config, List<ChunkPos> positions) {
        if (positions.isEmpty()) throw new IllegalArgumentException("Positions must not be empty");
        CountingNoise noise = new CountingNoise(new PerlinNoise(seed));
        TerrainGenerator generator = new TerrainGenerator(seed, config, noise);
        for (ChunkPos position : positions) dataSink = generator.generate(position);
        return noise.calls / (double) positions.size();
    }

    public static String toCsv(List<Result> results) {
        StringBuilder out = new StringBuilder(
                "variant,seed,version,chunks,warmup,iterations,nsPerColumn,heightBytesPerColumn,nsPerChunk,bytesPerChunk,nsPerChunkWithSnapshot,bytesPerChunkWithSnapshot,noiseSamplesPerChunk\n");
        for (Result result : results) {
            double chunks = (double) result.chunks() * result.iterations();
            double columns = chunks * Chunk.SIZE * Chunk.SIZE;
            out.append(result.name())
                    .append(',')
                    .append(result.seed())
                    .append(',')
                    .append(result.version())
                    .append(',')
                    .append(result.chunks())
                    .append(',')
                    .append(result.warmup())
                    .append(',')
                    .append(result.iterations())
                    .append(',')
                    .append(result.heights().nanos() / columns)
                    .append(',')
                    .append(perItem(result.heights().bytes(), columns))
                    .append(',')
                    .append(result.generate().nanos() / chunks)
                    .append(',')
                    .append(perItem(result.generate().bytes(), chunks))
                    .append(',')
                    .append(result.snapshot().nanos() / chunks)
                    .append(',')
                    .append(perItem(result.snapshot().bytes(), chunks))
                    .append(',')
                    .append(result.noiseSamplesPerChunk())
                    .append('\n');
        }
        return out.toString();
    }

    public static void main(String[] args) {
        System.out.println("commit=" + System.getProperty("voxel.commit", "unknown") + " cpu="
                + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("runtime=" + Runtime.version() + " os=" + System.getProperty("os.name") + " arch="
                + System.getProperty("os.arch") + " logicalProcessors="
                + Runtime.getRuntime().availableProcessors());
        System.out.println(
                "scene: chunk X/Z [-128,112] step 16, Y=0/2/5; 256 height samples per chunk; allocation -1 means unavailable");
        List<ChunkPos> positions = new ArrayList<>();
        for (int z = -128; z < 128; z += 16)
            for (int x = -128; x < 128; x += 16) for (int y : new int[] {0, 2, 5}) positions.add(new ChunkPos(x, y, z));
        TerrainConfig defaults = TerrainConfig.defaults();
        TerrainConfig fbm =
                new TerrainConfig(defaults.baseHeight(), defaults.continental(), defaults.hills(), defaults.details());
        System.out.println("config=" + defaults);
        List<Result> results = new ArrayList<>();
        for (long seed : new long[] {0, 42, -1}) {
            for (int repetition = 0; repetition < 3; repetition++) {
                results.add(run("hills-v1-r" + repetition, new HillsGenerator(seed), positions, 3, 3, -1));
                results.add(run(
                        "fbm-default-r" + repetition,
                        new TerrainGenerator(seed, fbm),
                        positions,
                        3,
                        3,
                        countNoiseSamples(seed, fbm, positions)));
                for (int octaves : new int[] {1, 2, 4, 8}) {
                    TerrainConfig config = new TerrainConfig(
                            defaults.baseHeight(),
                            octaves(defaults.continental(), octaves),
                            octaves(defaults.hills(), octaves),
                            octaves(defaults.details(), octaves));
                    results.add(run(
                            "fbm-" + octaves + "-r" + repetition,
                            new TerrainGenerator(seed, config),
                            positions,
                            3,
                            3,
                            countNoiseSamples(seed, config, positions)));
                }
                results.add(run(
                        "mountains-r" + repetition,
                        new TerrainGenerator(seed, defaults),
                        positions,
                        3,
                        3,
                        countNoiseSamples(seed, defaults, positions)));
            }
            printLandscape(seed, defaults);
        }
        System.out.print(toCsv(results));
    }

    private static TerrainConfig.Layer octaves(TerrainConfig.Layer layer, int octaves) {
        return new TerrainConfig.Layer(
                layer.frequency(), octaves, layer.persistence(), layer.lacunarity(), layer.amplitude());
    }

    private static void printLandscape(long seed, TerrainConfig config) {
        TerrainGenerator mountain = new TerrainGenerator(seed, config);
        TerrainGenerator plains = new TerrainGenerator(
                seed, new TerrainConfig(config.baseHeight(), config.continental(), config.hills(), config.details()));
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, affected = 0, count = 0, maxStep = 0;
        int nearestX = 0, nearestZ = 0;
        long nearestDistance = Long.MAX_VALUE;
        for (int z = -4096; z <= 4096; z += 32)
            for (int x = -4096; x <= 4096; x += 32) {
                int height = mountain.surfaceHeight(x, z);
                min = Math.min(min, height);
                max = Math.max(max, height);
                if (height > plains.surfaceHeight(x, z)) affected++;
                maxStep = Math.max(maxStep, Math.abs(height - mountain.surfaceHeight(x + 1, z)));
                maxStep = Math.max(maxStep, Math.abs(height - mountain.surfaceHeight(x, z + 1)));
                long distance = (long) x * x + (long) z * z;
                if (height >= config.baseHeight() + 40 && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestX = x;
                    nearestZ = z;
                }
                count++;
            }
        System.out.println("landscape seed=" + seed + " min=" + min + " max=" + max + " mountainFraction="
                + affected / (double) count
                + " maxAdjacentStep=" + maxStep + " nearestHigh=" + nearestX + "," + nearestZ
                + " height=" + mountain.surfaceHeight(nearestX, nearestZ));
    }

    private static void columns(TerrainSource source, List<ChunkPos> positions) {
        long sum = 0;
        for (int index = 0; index < positions.size(); index++) {
            ChunkPos position = positions.get(index);
            int originX = Math.multiplyExact(position.x(), Chunk.SIZE);
            int originZ = Math.multiplyExact(position.z(), Chunk.SIZE);
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) sum += source.surfaceHeight(originX + x, originZ + z);
        }
        checksum = sum;
    }

    private static void chunks(TerrainSource source, List<ChunkPos> positions, boolean snapshot) {
        long sum = 0;
        for (int index = 0; index < positions.size(); index++) {
            ChunkPos position = positions.get(index);
            ChunkData data = source.generate(position);
            dataSink = data;
            if (snapshot) snapshotSink = new ChunkSnapshot(data.blocks(), 0);
            sum += data.get(0, 0, 0);
        }
        checksum = sum;
    }

    private static Measurement measure(Runnable work, ThreadMXBean allocations, int warmup, int iterations) {
        for (int i = 0; i < warmup; i++) work.run();
        long bytes = allocated(allocations);
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) work.run();
        long nanos = System.nanoTime() - start;
        long allocatedBytes = bytes < 0 ? -1 : allocated(allocations) - bytes;
        return new Measurement(nanos, allocatedBytes);
    }

    private static ThreadMXBean allocationBean() {
        if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) return null;
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }

    private static long allocated(ThreadMXBean bean) {
        return bean == null ? -1 : bean.getCurrentThreadAllocatedBytes();
    }

    private static double perItem(long bytes, double items) {
        return bytes < 0 ? -1 : bytes / items;
    }

    private static final class CountingNoise implements Noise2D {
        private final Noise2D delegate;
        private long calls;

        private CountingNoise(Noise2D delegate) {
            this.delegate = delegate;
        }

        @Override
        public double sample(double x, double z) {
            calls++;
            return delegate.sample(x, z);
        }
    }

    public record Measurement(long nanos, long bytes) {}

    public record Result(
            String name,
            long seed,
            int version,
            int chunks,
            int warmup,
            int iterations,
            Measurement heights,
            Measurement generate,
            Measurement snapshot,
            double noiseSamplesPerChunk) {}
}
