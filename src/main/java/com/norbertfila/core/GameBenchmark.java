package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.glGetInteger;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.diagnostics.BenchmarkRecord;
import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.diagnostics.FrameProfiler;
import com.norbertfila.diagnostics.FrameProfiler.Distribution;
import com.norbertfila.diagnostics.FrameProfiler.Segment;
import com.norbertfila.diagnostics.SimulationMetrics;
import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.world.WorldCatalog;
import com.norbertfila.persistence.world.WorldIdentity;
import com.norbertfila.persistence.world.WorldIo;
import com.norbertfila.persistence.world.WorldMetadata;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerState;
import com.norbertfila.player.SpawnResolver;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.render.UiRenderer;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.world.BlockRegistry;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.lwjgl.opengl.GL;

/**
 * Repeatable benchmark of the production game: real window, runtime, streaming, meshing and renderer driven by
 * deterministic {@link BenchmarkScene} scripts on a fresh world with a fixed seed. One run uses one session and one
 * render distance and is capped at five minutes.
 *
 * <p>Enabled with {@code -Dvoxel.benchmark=all} or a comma-separated scene list. Options: {@code
 * voxel.benchmark.rd}, {@code voxel.benchmark.seconds}, {@code voxel.benchmark.label}, {@code
 * voxel.benchmark.out}, {@code voxel.benchmark.width/height}, {@code voxel.benchmark.gpuTimers}.
 */
public final class GameBenchmark {
    static final long SEED = 7_340_215L;
    private static final Logger LOG = Logger.getLogger(GameBenchmark.class.getName());
    private static final Path WORLDS = Path.of("build/benchmark-worlds");
    private static final long TIME_LIMIT_NANOS = 300_000_000_000L;
    private static final long SETTLE_STABLE_NANOS = 1_000_000_000L;
    private static final long SETTLE_TIMEOUT_NANOS = 60_000_000_000L;
    private static final long WARMUP_NANOS = 2_000_000_000L;
    private static final int PROFILER_CAPACITY = 1 << 17;
    private static final int GL_NVX_CURRENT_AVAILABLE_VIDMEM = 0x9049;
    private static final double NANO_TIME_COST_NS = nanoTimeCost();

    public static boolean requested() {
        return System.getProperty("voxel.benchmark") != null;
    }

    public void run() {
        List<BenchmarkScene> scenes = scenes(System.getProperty("voxel.benchmark"));
        int renderDistance = Integer.getInteger("voxel.benchmark.rd", 8);
        String label = System.getProperty("voxel.benchmark.label", "unlabeled");
        Path out = Path.of(System.getProperty("voxel.benchmark.out", "benchmarks/opt/game-benchmark.csv"));
        String seconds = System.getProperty("voxel.benchmark.seconds");
        double measureOverride = seconds == null ? 0 : Double.parseDouble(seconds);
        int width = Integer.getInteger("voxel.benchmark.width", 1280);
        int height = Integer.getInteger("voxel.benchmark.height", 720);
        boolean gpuTimers = Boolean.parseBoolean(System.getProperty("voxel.benchmark.gpuTimers", "true"));
        try (Window window = new Window(width, height, "Voxel Engine | Benchmark");
                Input input = new Input(window.handle());
                UiRenderer ui = new UiRenderer();
                WorldIo io = new WorldIo();
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            TerrainConfig config = TerrainConfig.defaults();
            var terrain = new TerrainGenerator(SEED, config);
            var catalog = new WorldCatalog(WORLDS, "default", terrain.generatorVersion());
            var session = new GameSession(
                    io, seed -> new TerrainGenerator(seed, config), new WaterMesher(), Game.terrainDemand(config));
            var spawn = new SpawnResolver(terrain).resolve(0, 0);
            var site = BenchmarkScene.Site.sample(
                    terrain,
                    (int) Math.floor(spawn.x()),
                    (int) Math.floor(spawn.z()),
                    BenchmarkScene.FLIGHT_SPEED * BenchmarkScene.FLIGHT_SECONDS);
            var settings = new GameSettings();
            settings.setRenderDistance(renderDistance);
            var identity = new WorldIdentity(UUID.randomUUID(), SEED, "default", terrain.generatorVersion());
            var entry = io.create(catalog, new WorldMetadata(identity, "benchmark", Instant.now()))
                    .join();
            var loop = new GameLoop();
            var profiler = new FrameProfiler(PROFILER_CAPACITY, gpuTimers);
            List<BenchmarkRecord> records;
            try (TerrainRenderer renderer = new TerrainRenderer(textures)) {
                session.open(catalog, entry.id());
                try {
                    loop.awaitLoad(window, input, ui, settings, session);
                    if (session.runtime() == null)
                        throw new IllegalStateException("Benchmark world failed to load", session.failure());
                    var driver = new Driver(scenes, site, profiler, renderer, measureOverride);
                    loop.run(
                            window,
                            input,
                            new Camera(settings),
                            renderer,
                            ui,
                            new FrameMetrics(0, 0, 0, false, window.time()),
                            session,
                            settings,
                            driver,
                            profiler);
                    records = driver.records(label, renderDistance, window);
                } finally {
                    if (session.state() == GameSession.State.PLAYING) session.saveAndQuit();
                    else if (session.state() == GameSession.State.LOADING) session.cancelLoading();
                    loop.awaitStop(window, input, ui, settings, session);
                }
            } finally {
                deleteWorld(entry.id());
            }
            if (session.failure() != null)
                throw new IllegalStateException("Benchmark session failed", session.failure());
            for (BenchmarkRecord record : records) {
                record.appendTo(out);
                System.out.println("[benchmark] "
                        + record.summary(
                                "scene",
                                "rd",
                                "settleS",
                                "fps",
                                "frameP50Ms",
                                "frameP99Ms",
                                "over33ms",
                                "renderP95Ms",
                                "gpuTerrainP95Ms",
                                "tickP95Ms",
                                "tickMaxMs",
                                "genJobsPerS",
                                "visibleLatencyAvgMs",
                                "worldAllocMBps",
                                "retainedHeapMB"));
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write benchmark results", failure);
        }
    }

    private static List<BenchmarkScene> scenes(String value) {
        if (value.isBlank() || value.equalsIgnoreCase("all") || value.equalsIgnoreCase("true"))
            return List.of(BenchmarkScene.values());
        return Arrays.stream(value.split(","))
                .map(name -> BenchmarkScene.valueOf(name.strip().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private static void deleteWorld(UUID id) throws IOException {
        Path directory = WORLDS.toAbsolutePath().resolve(id.toString());
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }

    /**
     * Runs the scenes back to back in one session. Each scene teleports to its start pose, waits until streaming is
     * idle, warms up, then measures. Scenes that do not fit in {@link #TIME_LIMIT_NANOS} are skipped.
     */
    private static final class Driver implements GameLoop.FrameDriver {
        private enum Phase {
            LOADING,
            SETTLING,
            WARMUP,
            RUNNING
        }

        private final List<BenchmarkScene> scenes;
        private final BenchmarkScene.Site site;
        private final FrameProfiler profiler;
        private final TerrainRenderer renderer;
        private final double measureOverride;
        private final long deadline = System.nanoTime() + TIME_LIMIT_NANOS;
        private final List<Measurement> completed = new ArrayList<>();
        private int sceneIndex;
        private Phase phase = Phase.LOADING;
        private long phaseStarted, stableSince = -1;
        private double lastScriptTime;
        private long lastTeleportTick = -1;
        private Measurement current;

        Driver(
                List<BenchmarkScene> scenes,
                BenchmarkScene.Site site,
                FrameProfiler profiler,
                TerrainRenderer renderer,
                double measureOverride) {
            this.scenes = List.copyOf(scenes);
            this.site = site;
            this.profiler = profiler;
            this.renderer = renderer;
            this.measureOverride = measureOverride;
            profiler.recording(false);
        }

        private BenchmarkScene scene() {
            return scenes.get(sceneIndex);
        }

        private double measureSeconds() {
            return measureOverride > 0 ? measureOverride : scene().measureSeconds();
        }

        @Override
        public void update(WorldRuntime runtime, WorldRuntime.Frame state, Camera camera) {
            long now = System.nanoTime();
            if (phase == Phase.LOADING && runtime.state() == WorldRuntime.State.PLAYING) startScene(runtime, now);
            if (phase == Phase.LOADING) return;
            double t = phase == Phase.RUNNING ? (now - phaseStarted) / 1e9 : 0;
            var pose = scene().pose(t, site);
            camera.setPosition((float) pose.x(), (float) pose.y(), (float) pose.z());
            camera.restoreLook((float) pose.yaw(), (float) pose.pitch());
            if (phase != Phase.RUNNING) return;
            if (state != null && state.tick() != lastTeleportTick) {
                lastTeleportTick = state.tick();
                teleport(runtime, pose);
            }
            scene().commands(lastScriptTime, t, site, action -> {
                if (!runtime.offer(action)) current.droppedCommands++;
            });
            lastScriptTime = t;
        }

        private void startScene(WorldRuntime runtime, long now) {
            current = new Measurement(scene());
            teleport(runtime, scene().pose(0, site));
            stableSince = -1;
            enter(Phase.SETTLING, now);
        }

        private void teleport(WorldRuntime runtime, BenchmarkScene.Pose pose) {
            if (!runtime.offer(new WorldRuntime.Teleport(pose.x(), pose.y() - PlayerState.EYE_HEIGHT, pose.z())))
                current.droppedCommands++;
        }

        @Override
        public boolean frameCompleted(
                WorldRuntime runtime, WorldRuntime.Frame state, TerrainRenderer.RenderStats stats, int uploadQueue) {
            long now = System.nanoTime();
            if (now >= deadline) {
                profiler.recording(false);
                return true;
            }
            if (state == null) return false;
            switch (phase) {
                case LOADING -> {}
                case SETTLING -> {
                    boolean drained = drained(state.metrics(), uploadQueue);
                    if (!drained) stableSince = -1;
                    else if (stableSince < 0) stableSince = now;
                    if (drained && now - stableSince >= SETTLE_STABLE_NANOS) {
                        current.settleNanos = stableSince - phaseStarted;
                        enter(Phase.WARMUP, now);
                    } else if (now - phaseStarted >= SETTLE_TIMEOUT_NANOS) {
                        current.settleNanos = now - phaseStarted;
                        current.settleTimedOut = true;
                        enter(Phase.WARMUP, now);
                    }
                }
                case WARMUP -> {
                    if (now - phaseStarted >= WARMUP_NANOS) {
                        enter(Phase.RUNNING, now);
                        lastScriptTime = 0;
                    }
                }
                case RUNNING -> {
                    double t = (now - phaseStarted) / 1e9;
                    if (current.measureStarted == 0 && t >= scene().setupSeconds()) {
                        profiler.reset();
                        profiler.recording(true);
                        current.begin(runtime, state, renderer, now);
                    } else if (current.measureStarted != 0) current.sample(state, stats, uploadQueue);
                    if (t >= scene().setupSeconds() + measureSeconds()) {
                        profiler.recording(false);
                        current.end(runtime, state, renderer, profiler, now);
                        completed.add(current);
                        if (++sceneIndex == scenes.size()) return true;
                        startScene(runtime, now);
                    }
                }
            }
            return false;
        }

        private static boolean drained(SimulationMetrics metrics, int uploadQueue) {
            var streaming = metrics.streaming();
            return streaming.generationOutstanding() == 0
                    && streaming.meshOutstanding() == 0
                    && streaming.pendingRemesh() == 0
                    && metrics.outstandingAcks() == 0
                    && metrics.publishedMeshes() == 0
                    && uploadQueue == 0;
        }

        private void enter(Phase next, long now) {
            phase = next;
            phaseStarted = now;
        }

        List<BenchmarkRecord> records(String label, int renderDistance, Window window) {
            int[] width = new int[1], height = new int[1];
            window.framebufferSize(width, height);
            if (completed.size() < scenes.size())
                LOG.warning(
                        "Benchmark time limit reached after " + completed.size() + " of " + scenes.size() + " scenes");
            return completed.stream()
                    .map(measurement -> measurement.record(label, renderDistance, width[0], height[0]))
                    .toList();
        }
    }

    /** Metrics of one measured scene; profiler distributions are captured when the scene ends. */
    private static final class Measurement {
        private final BenchmarkScene scene;
        private final Samples ticks = new Samples(), streaming = new Samples(), publish = new Samples();
        private final Samples water = new Samples(), capture = new Samples();
        private final Distribution[] segments = new Distribution[Segment.values().length];
        private long settleNanos, measureStarted, measureNanos;
        private boolean settleTimedOut;
        private long lastSampledTick = -1, missedTicks, droppedCommands;
        private long visibleSum, drawSum, frames;
        private int residentMax, uploadQueueMax, over16, over33;
        private Counters start, end;
        private double retainedHeapMb, profilerOverheadUs;
        private long nvxStartKb = -1, nvxEndKb = -1;
        private Distribution frameTimes, gpuTerrain, gpuUi;

        Measurement(BenchmarkScene scene) {
            this.scene = scene;
        }

        void begin(WorldRuntime runtime, WorldRuntime.Frame state, TerrainRenderer renderer, long now) {
            measureStarted = now;
            start = Counters.sample(runtime, state, renderer);
            nvxStartKb = nvxAvailableKb();
        }

        void sample(WorldRuntime.Frame state, TerrainRenderer.RenderStats stats, int uploadQueue) {
            frames++;
            visibleSum += stats.visible();
            drawSum += stats.drawCalls();
            uploadQueueMax = Math.max(uploadQueueMax, uploadQueue);
            residentMax = Math.max(residentMax, state.residents().size());
            if (state.tick() == lastSampledTick) return;
            if (lastSampledTick >= 0) missedTicks += Math.max(0, state.tick() - lastSampledTick - 1);
            lastSampledTick = state.tick();
            var metrics = state.metrics();
            ticks.add(metrics.tickNanos());
            streaming.add(metrics.streamingNanos());
            publish.add(metrics.publishNanos());
            water.add(metrics.waterNanos());
            capture.add(metrics.captureNanos());
        }

        void end(
                WorldRuntime runtime,
                WorldRuntime.Frame state,
                TerrainRenderer renderer,
                FrameProfiler profiler,
                long now) {
            measureNanos = now - measureStarted;
            end = Counters.sample(runtime, state, renderer);
            nvxEndKb = nvxAvailableKb();
            frameTimes = profiler.frameDistribution();
            for (Segment segment : Segment.values())
                segments[segment.ordinal()] = profiler.segmentDistribution(segment);
            gpuTerrain = profiler.gpuDistribution(FrameProfiler.GpuPass.TERRAIN);
            gpuUi = profiler.gpuDistribution(FrameProfiler.GpuPass.UI);
            over16 = profiler.framesOver(16_666_667);
            over33 = profiler.framesOver(33_333_333);
            int recorded = Math.max(1, profiler.frames());
            profilerOverheadUs =
                    (profiler.overheadNanos() / (double) recorded + Segment.values().length * NANO_TIME_COST_NS) / 1e3;
            retainedHeapMb = retainedHeapMb();
        }

        BenchmarkRecord record(String label, int renderDistance, int width, int height) {
            double seconds = measureNanos / 1e9;
            var record = new BenchmarkRecord()
                    .put("label", label)
                    .put("scene", scene)
                    .put("rd", renderDistance)
                    .put("width", width)
                    .put("height", height)
                    .put("settleS", settleNanos / 1e9)
                    .put("settleTimedOut", settleTimedOut)
                    .put("measuredS", seconds)
                    .put("frames", frameTimes.samples())
                    .put("fps", frameTimes.samples() / seconds)
                    .put("frame", frameTimes)
                    .put("frameMeanMs", frameTimes.meanMillis())
                    .put("over16ms", over16)
                    .put("over33ms", over33);
            for (Segment segment : Segment.values()) {
                Distribution distribution = segments[segment.ordinal()];
                String name = segment.name().toLowerCase(Locale.ROOT);
                record.put(name + "P50Ms", distribution.p50Millis()).put(name + "P95Ms", distribution.p95Millis());
            }
            record.put("gpuSamples", gpuTerrain.samples())
                    .put("gpuTerrainP50Ms", gpuTerrain.p50Millis())
                    .put("gpuTerrainP95Ms", gpuTerrain.p95Millis())
                    .put("gpuUiP50Ms", gpuUi.p50Millis());
            var tickTimes = ticks.distribution();
            record.put("ticksSampled", tickTimes.samples())
                    .put("ticksMissed", missedTicks)
                    .put("tickP50Ms", tickTimes.p50Millis())
                    .put("tickP95Ms", tickTimes.p95Millis())
                    .put("tickMaxMs", tickTimes.maxMillis())
                    .put("streamingTickP50Ms", streaming.distribution().p50Millis())
                    .put("streamingTickP95Ms", streaming.distribution().p95Millis())
                    .put("publishTickP50Ms", publish.distribution().p50Millis())
                    .put("publishTickP95Ms", publish.distribution().p95Millis())
                    .put("waterTickP95Ms", water.distribution().p95Millis())
                    .put("captureMaxMs", capture.distribution().maxMillis());
            var s0 = start.streaming();
            var s1 = end.streaming();
            long generated = s1.generationCompleted() - s0.generationCompleted();
            long meshed = s1.meshCompleted() - s0.meshCompleted();
            record.put("genJobsPerS", generated / seconds)
                    .put("genAvgMs", average(s1.generationNanos() - s0.generationNanos(), generated))
                    .put("genWaitAvgMs", average(s1.generationWaitNanos() - s0.generationWaitNanos(), generated))
                    .put("meshJobsPerS", meshed / seconds)
                    .put("meshAvgMs", average(s1.meshNanos() - s0.meshNanos(), meshed))
                    .put("meshWaitAvgMs", average(s1.meshWaitNanos() - s0.meshWaitNanos(), meshed))
                    .put("genLatencyAvgMs", latency(s0.generationLatency(), s1.generationLatency()))
                    .put("visibleLatencyAvgMs", latency(s0.visibleLatency(), s1.visibleLatency()))
                    .put("discardedResults", s1.discardedResults() - s0.discardedResults())
                    .put("schedulerMetadata", s1.schedulerMetadata())
                    .put("skippedMeshInputsPerS", (s1.skippedMeshInputs() - s0.skippedMeshInputs()) / seconds)
                    .put("residentMax", residentMax)
                    .put("visibleAvg", frames == 0 ? 0.0 : (double) visibleSum / frames)
                    .put("drawsAvg", frames == 0 ? 0.0 : (double) drawSum / frames)
                    .put("uploadQueueMax", uploadQueueMax)
                    .put("renderAllocMBps", megabytes(end.renderAllocated() - start.renderAllocated()) / seconds)
                    .put("worldAllocMBps", megabytes(end.worldAllocated() - start.worldAllocated()) / seconds)
                    .put("totalAllocMBps", megabytes(end.totalAllocated() - start.totalAllocated()) / seconds)
                    .put("gcCount", end.gcCount() - start.gcCount())
                    .put("gcMs", end.gcMillis() - start.gcMillis())
                    .put("retainedHeapMB", retainedHeapMb)
                    .put("gpuMeshMB", megabytes(end.gpuMeshBytes()))
                    .put("vramDeltaMB", nvxStartKb < 0 ? Double.NaN : (nvxStartKb - nvxEndKb) / 1024.0)
                    .put("droppedCommands", droppedCommands)
                    .put("profilerOverheadUsPerFrame", profilerOverheadUs)
                    .put("java", Runtime.version())
                    .put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"))
                    .put("cpus", Runtime.getRuntime().availableProcessors())
                    .put("maxHeapMB", megabytes(Runtime.getRuntime().maxMemory()));
            return record;
        }

        private static double average(long nanos, long count) {
            return count == 0 ? 0.0 : nanos / 1e6 / count;
        }

        private static double latency(ChunkManager.LatencyTotals before, ChunkManager.LatencyTotals after) {
            return average(after.totalNanos() - before.totalNanos(), after.count() - before.count());
        }
    }

    private static double nanoTimeCost() {
        long sink = 0;
        int iterations = 200_000;
        long started = System.nanoTime();
        for (int index = 0; index < iterations; index++) sink += System.nanoTime();
        long elapsed = System.nanoTime() - started;
        return sink == 42 ? 0 : (double) elapsed / iterations;
    }

    private static double megabytes(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private static long nvxAvailableKb() {
        return GL.getCapabilities().GL_NVX_gpu_memory_info ? glGetInteger(GL_NVX_CURRENT_AVAILABLE_VIDMEM) : -1;
    }

    private static double retainedHeapMb() {
        System.gc();
        System.gc();
        return megabytes(
                ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }

    private record Counters(
            ChunkManager.Metrics streaming,
            long renderAllocated,
            long worldAllocated,
            long totalAllocated,
            long gcCount,
            long gcMillis,
            long gpuMeshBytes) {
        static Counters sample(WorldRuntime runtime, WorldRuntime.Frame state, TerrainRenderer renderer) {
            var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long gcCount = 0, gcMillis = 0;
            for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += Math.max(0, collector.getCollectionCount());
                gcMillis += Math.max(0, collector.getCollectionTime());
            }
            return new Counters(
                    state.metrics().streaming(),
                    threads.getThreadAllocatedBytes(Thread.currentThread().threadId()),
                    threads.getThreadAllocatedBytes(runtime.threadId()),
                    threads.getTotalThreadAllocatedBytes(),
                    gcCount,
                    gcMillis,
                    renderer.memoryStats().allocated());
        }
    }

    /** Fixed-capacity nanosecond samples; samples beyond capacity are ignored. */
    private static final class Samples {
        private final long[] values = new long[1 << 16];
        private int count;

        void add(long nanos) {
            if (count < values.length) values[count++] = nanos;
        }

        Distribution distribution() {
            return Distribution.of(values, count);
        }
    }
}
