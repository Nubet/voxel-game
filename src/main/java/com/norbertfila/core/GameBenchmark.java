package com.norbertfila.core;

import static org.lwjgl.opengl.GL46C.*;
import static org.lwjgl.opengl.GL46C.glGetInteger;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.diagnostics.BenchmarkRecord;
import com.norbertfila.diagnostics.DurationHistogram;
import com.norbertfila.diagnostics.FrameMetrics;
import com.norbertfila.diagnostics.FrameProfiler;
import com.norbertfila.diagnostics.FrameProfiler.Distribution;
import com.norbertfila.diagnostics.FrameProfiler.Segment;
import com.norbertfila.diagnostics.SimulationMetrics;
import com.norbertfila.generation.TerrainConfig;
import com.norbertfila.generation.TerrainGenerator;
import com.norbertfila.lod.FarTerrainStreamer;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.world.WorldCatalog;
import com.norbertfila.persistence.world.WorldIdentity;
import com.norbertfila.persistence.world.WorldIo;
import com.norbertfila.persistence.world.WorldMetadata;
import com.norbertfila.platform.Input;
import com.norbertfila.platform.Window;
import com.norbertfila.player.PlayerInput;
import com.norbertfila.player.PlayerState;
import com.norbertfila.player.SpawnResolver;
import com.norbertfila.render.BlockTextureArray;
import com.norbertfila.render.CloudMode;
import com.norbertfila.render.TerrainRenderer;
import com.norbertfila.render.UiRenderer;
import com.norbertfila.streaming.ChunkManager;
import com.norbertfila.streaming.WorkerCounts;
import com.norbertfila.world.BlockRegistry;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

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
    // The soak scene alone may run longer, up to the 10 minute cap for one benchmark run.
    private static final long SOAK_TIME_LIMIT_NANOS = 600_000_000_000L;
    private static final long SOAK_REPORT_NANOS = 30_000_000_000L;
    private static final long SETTLE_STABLE_NANOS = 1_000_000_000L;
    private static final long SETTLE_TIMEOUT_NANOS = 60_000_000_000L;
    private static final long WARMUP_NANOS = 2_000_000_000L;
    private static final int PROFILER_CAPACITY = 1 << 17;
    private static final int GL_NVX_CURRENT_AVAILABLE_VIDMEM = 0x9049;
    private static final double NANO_TIME_COST_NS = nanoTimeCost();
    private static final String RUN_ID = UUID.randomUUID().toString();

    public static boolean requested() {
        return System.getProperty("voxel.benchmark") != null;
    }

    public void run() {
        List<BenchmarkScene> scenes = scenes(System.getProperty("voxel.benchmark"));
        int renderDistance = Integer.getInteger("voxel.benchmark.rd", 8);
        int farDistance = Integer.getInteger("voxel.benchmark.far", GameSettings.DEFAULT_FAR_DISTANCE);
        String screenshots = System.getProperty("voxel.benchmark.screenshots");
        String label = System.getProperty("voxel.benchmark.label", "unlabeled");
        Path out = Path.of(System.getProperty("voxel.benchmark.out", "benchmarks/opt/game-benchmark.csv"));
        String seconds = System.getProperty("voxel.benchmark.seconds");
        double measureOverride = seconds == null ? 0 : Double.parseDouble(seconds);
        if (measureOverride != 0 && (!Double.isFinite(measureOverride) || measureOverride <= 0))
            throw new IllegalArgumentException("Benchmark duration must be positive and finite");
        if (scenes.contains(BenchmarkScene.FLY_RETURN)
                && (scenes.indexOf(BenchmarkScene.FLY_RETURN) == 0
                        || scenes.get(scenes.indexOf(BenchmarkScene.FLY_RETURN) - 1) != BenchmarkScene.FLY_NEW
                        || (measureOverride != 0 && measureOverride != BenchmarkScene.FLIGHT_SECONDS)))
            throw new IllegalArgumentException(
                    "FLY_RETURN requires FLY_NEW immediately before it, both lasting 10 seconds");
        int width = Integer.getInteger("voxel.benchmark.width", 1280);
        int height = Integer.getInteger("voxel.benchmark.height", 720);
        boolean gpuTimers = Boolean.parseBoolean(System.getProperty("voxel.benchmark.gpuTimers", "true"));
        long seed = Long.getLong("voxel.benchmark.seed", SEED);
        var workers = new WorkerCounts(
                Integer.getInteger("voxel.benchmark.genWorkers", WorkerCounts.DEFAULT.generation()),
                Integer.getInteger("voxel.benchmark.meshWorkers", WorkerCounts.DEFAULT.meshing()),
                Integer.getInteger("voxel.benchmark.farWorkers", WorkerCounts.DEFAULT.far()));
        try (Window window = new Window(width, height, "Voxel Engine | Benchmark");
                Input input = new Input(window.handle());
                UiRenderer ui = new UiRenderer();
                WorldIo io = new WorldIo();
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            TerrainConfig config = TerrainConfig.defaults();
            var terrain = new TerrainGenerator(seed, config);
            var catalog = new WorldCatalog(WORLDS, "default", terrain.generatorVersion());
            var session = new GameSession(
                    io,
                    worldSeed -> new TerrainGenerator(worldSeed, config),
                    new WaterMesher(),
                    Game.terrainDemand(config),
                    workers);
            var spawn = new SpawnResolver(terrain)
                    .resolve(
                            Integer.getInteger("voxel.benchmark.siteX", 0),
                            Integer.getInteger("voxel.benchmark.siteZ", 0));
            var site = BenchmarkScene.Site.sample(
                    terrain,
                    (int) Math.floor(spawn.x()),
                    (int) Math.floor(spawn.z()),
                    BenchmarkScene.FLIGHT_SPEED * BenchmarkScene.FLIGHT_SECONDS);
            var settings = new GameSettings();
            settings.setRenderDistance(renderDistance);
            settings.setFarDistance(farDistance);
            settings.setClouds(CloudMode.valueOf(
                    System.getProperty("voxel.benchmark.clouds", "FAST").toUpperCase(Locale.ROOT)));
            var identity = new WorldIdentity(UUID.randomUUID(), seed, "default", terrain.generatorVersion());
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
                    var driver = new Driver(
                            scenes,
                            site,
                            profiler,
                            renderer,
                            measureOverride,
                            screenshots == null ? null : Path.of(screenshots),
                            window,
                            settings);
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
                    records = driver.records(label, renderDistance, farDistance, window, workers);
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
            return Arrays.stream(BenchmarkScene.values())
                    .filter(scene -> scene != BenchmarkScene.SOAK && scene != BenchmarkScene.COLLISION_FLIGHT)
                    .toList();
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
        private final Path screenshots;
        private final Window window;
        private final GameSettings settings;
        private final int baseRenderDistance;
        private final long deadline;
        private long lastSoakReport;
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
                double measureOverride,
                Path screenshots,
                Window window,
                GameSettings settings) {
            this.scenes = List.copyOf(scenes);
            this.screenshots = screenshots;
            this.window = window;
            this.settings = settings;
            baseRenderDistance = settings.renderDistance();
            deadline = System.nanoTime()
                    + (scenes.equals(List.of(BenchmarkScene.SOAK)) ? SOAK_TIME_LIMIT_NANOS : TIME_LIMIT_NANOS);
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
        public WorldRuntime.Controls controls(WorldRuntime.Controls ignored) {
            boolean moving =
                    phase == Phase.RUNNING && current.measureStarted != 0 && scene() == BenchmarkScene.COLLISION_FLIGHT;
            var pose = scene().pose(lastScriptTime, site);
            return new WorldRuntime.Controls(
                    new PlayerInput(moving, false, false, false, false, false, moving),
                    pose.yaw(),
                    pose.pitch(),
                    phase == Phase.LOADING,
                    false,
                    settings.renderDistance(),
                    0,
                    false,
                    settings.farDistance(),
                    settings.simulationDistance());
        }

        @Override
        public void update(WorldRuntime runtime, WorldRuntime.Frame state, Camera camera) {
            long now = System.nanoTime();
            if (phase == Phase.LOADING && runtime.state() == WorldRuntime.State.PLAYING) startScene(runtime, now);
            if (phase == Phase.LOADING) return;
            double t = phase == Phase.RUNNING ? (now - phaseStarted) / 1e9 : 0;
            var pose = scene().pose(t, site);
            if (scene() != BenchmarkScene.COLLISION_FLIGHT || phase != Phase.RUNNING)
                camera.setPosition(pose.x(), pose.y(), pose.z());
            camera.restoreLook((float) pose.yaw(), (float) pose.pitch());
            settings.setRenderDistance(scene().renderDistance(t, baseRenderDistance));
            if (phase != Phase.RUNNING) return;
            if (scene() != BenchmarkScene.COLLISION_FLIGHT && state != null && state.tick() != lastTeleportTick) {
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
                    boolean drained = drained(state.metrics(), uploadQueue)
                            && renderer.far().pendingUploads() == 0;
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
                    if (scene() == BenchmarkScene.SOAK && now - lastSoakReport >= SOAK_REPORT_NANOS) {
                        lastSoakReport = now;
                        soakReport(t, state, uploadQueue);
                    }
                    if (t >= scene().setupSeconds() + measureSeconds()) {
                        profiler.recording(false);
                        current.end(runtime, state, renderer, profiler, now);
                        completed.add(current);
                        if (screenshots != null) screenshot(scene());
                        if (++sceneIndex == scenes.size()) return true;
                        startScene(runtime, now);
                    }
                }
            }
            return false;
        }

        private static boolean drained(SimulationMetrics metrics, int uploadQueue) {
            var streaming = metrics.streaming();
            var far = metrics.far();
            return far.inFlight() == 0
                    && far.queued() == 0
                    && streaming.generationOutstanding() == 0
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

        /** One line of long-run health: memory after the last collection, queues and GPU memory. */
        private void soakReport(double t, WorldRuntime.Frame state, int uploadQueue) {
            long oldGen = 0;
            for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
                var usage = pool.getCollectionUsage();
                if (usage != null && pool.getType() == java.lang.management.MemoryType.HEAP) oldGen += usage.getUsed();
            }
            var metrics = state.metrics();
            var streaming = metrics.streaming();
            System.out.printf(
                    Locale.ROOT,
                    "[soak] t=%.0f rd=%d heapAfterGcMB=%.1f chunkMB=%.1f residents=%d farTiles=%d farQueued=%d"
                            + " genOutstanding=%d meshOutstanding=%d pendingRemesh=%d uploadQueue=%d acks=%d"
                            + " gpuMeshMB=%.1f gpuArenaMB=%.1f farGpuMB=%.1f threads=%d%n",
                    t,
                    settings.renderDistance(),
                    megabytes(oldGen),
                    megabytes(metrics.estimatedChunkBytes()),
                    state.residents().size(),
                    metrics.far().resident(),
                    metrics.far().queued(),
                    streaming.generationOutstanding(),
                    streaming.meshOutstanding(),
                    streaming.pendingRemesh(),
                    uploadQueue,
                    metrics.outstandingAcks(),
                    megabytes(renderer.memoryStats().allocated()),
                    megabytes(renderer.memoryStats().arenaBytes()),
                    megabytes(renderer.far().gpuBytes()),
                    Thread.activeCount());
        }

        /** Saves the last presented frame; diagnostic only, so a failure is logged and the run continues. */
        private void screenshot(BenchmarkScene scene) {
            int[] width = new int[1], height = new int[1];
            window.framebufferSize(width, height);
            ByteBuffer pixels = MemoryUtil.memAlloc(width[0] * height[0] * 4);
            try {
                glReadBuffer(GL_FRONT);
                glReadPixels(0, 0, width[0], height[0], GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                glReadBuffer(GL_BACK);
                var image = new BufferedImage(width[0], height[0], BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < height[0]; y++)
                    for (int x = 0; x < width[0]; x++) {
                        int offset = (y * width[0] + x) * 4;
                        image.setRGB(
                                x,
                                height[0] - y - 1,
                                Byte.toUnsignedInt(pixels.get(offset)) << 16
                                        | Byte.toUnsignedInt(pixels.get(offset + 1)) << 8
                                        | Byte.toUnsignedInt(pixels.get(offset + 2)));
                    }
                Files.createDirectories(screenshots);
                ImageIO.write(
                        image,
                        "png",
                        screenshots
                                .resolve(scene.name().toLowerCase(Locale.ROOT) + ".png")
                                .toFile());
            } catch (IOException failure) {
                LOG.log(Level.WARNING, "Benchmark screenshot failed", failure);
            } finally {
                MemoryUtil.memFree(pixels);
            }
        }

        List<BenchmarkRecord> records(
                String label, int renderDistance, int farDistance, Window window, WorkerCounts workers) {
            int[] width = new int[1], height = new int[1];
            window.framebufferSize(width, height);
            if (completed.size() < scenes.size())
                throw new IllegalStateException(
                        "Incomplete benchmark: " + completed.size() + " of " + scenes.size() + " scenes");
            return completed.stream()
                    .map(measurement ->
                            measurement.record(label, renderDistance, farDistance, width[0], height[0], workers))
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
        private long visibleSum, drawSum, meshDrawSum, cullNanos, frames;
        private int residentMax, uploadQueueMax, over16, over33;
        private Counters start, end;
        private double retainedHeapMb, profilerOverheadUs;
        private double frameMeanMs, frameMaxMs;
        private long reservoirReplacements, oldestAckMax, movementWaitingTicks;
        private long meshPipelineBytesMax;
        private double startX, startZ, travelledBlocks;
        private int over6944, genQueueMax, meshQueueMax;
        private long nvxStartKb = -1, nvxEndKb = -1;
        private Distribution frameTimes, gpuTerrain, gpuUi, gpuFar, gpuClouds, gpuTranslucent;

        Measurement(BenchmarkScene scene) {
            this.scene = scene;
        }

        void begin(WorldRuntime runtime, WorldRuntime.Frame state, TerrainRenderer renderer, long now) {
            measureStarted = now;
            start = Counters.sample(runtime, state, renderer);
            nvxStartKb = nvxAvailableKb();
            startX = state.playerState().position().x();
            startZ = state.playerState().position().z();
        }

        void sample(WorldRuntime.Frame state, TerrainRenderer.RenderStats stats, int uploadQueue) {
            frames++;
            visibleSum += stats.visible();
            drawSum += stats.drawCalls();
            meshDrawSum += stats.draws();
            cullNanos += stats.cullNanos();
            uploadQueueMax = Math.max(uploadQueueMax, uploadQueue);
            residentMax = Math.max(residentMax, state.residents().size());
            if (state.tick() == lastSampledTick) return;
            if (lastSampledTick >= 0) missedTicks += Math.max(0, state.tick() - lastSampledTick - 1);
            lastSampledTick = state.tick();
            var metrics = state.metrics();
            genQueueMax = Math.max(genQueueMax, metrics.streaming().generationQueued());
            meshQueueMax = Math.max(meshQueueMax, metrics.streaming().meshQueued());
            oldestAckMax = Math.max(oldestAckMax, metrics.oldestAckNanos());
            if (metrics.movementWaiting()) movementWaitingTicks++;
            meshPipelineBytesMax = Math.max(
                    meshPipelineBytesMax,
                    metrics.outstandingMeshBytes() + metrics.streaming().readyMeshBytes());
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
            gpuFar = profiler.gpuDistribution(FrameProfiler.GpuPass.FAR_TERRAIN);
            gpuClouds = profiler.gpuDistribution(FrameProfiler.GpuPass.CLOUDS);
            gpuTranslucent = profiler.gpuDistribution(FrameProfiler.GpuPass.TRANSLUCENT_AND_EFFECTS);
            over16 = profiler.framesOver(16_666_667);
            over6944 = profiler.framesOver(6_944_444);
            over33 = profiler.framesOver(33_333_333);
            long recorded = Math.max(1, profiler.totalFrames());
            frames = profiler.totalFrames();
            profilerOverheadUs =
                    (profiler.overheadNanos() / (double) recorded + Segment.values().length * NANO_TIME_COST_NS) / 1e3;
            retainedHeapMb = retainedHeapMb();
            frameMeanMs = profiler.meanMillis();
            frameMaxMs = profiler.maxMillis();
            reservoirReplacements = profiler.droppedFrames();
            travelledBlocks = Math.hypot(
                    state.playerState().position().x() - startX,
                    state.playerState().position().z() - startZ);
        }

        BenchmarkRecord record(
                String label, int renderDistance, int farDistance, int width, int height, WorkerCounts workers) {
            double seconds = measureNanos / 1e9;
            var record = new BenchmarkRecord()
                    .put("schema", 2)
                    .put("generationWorkers", workers.generation())
                    .put("meshingWorkers", workers.meshing())
                    .put("farWorkers", workers.far())
                    .put("resultBatch", ChunkManager.RESULTS_PER_UPDATE)
                    .put("schedulerReadyCapacity", 64)
                    .put("nearUploadBudgetMs", 2)
                    .put("farUploadBudgetMs", 1)
                    .put("runId", RUN_ID)
                    .put("revision", System.getProperty("voxel.benchmark.revision", "unknown"))
                    .put("dirty", System.getProperty("voxel.benchmark.dirty", "unknown"))
                    .put("seed", Long.getLong("voxel.benchmark.seed", SEED))
                    .put("siteX", Integer.getInteger("voxel.benchmark.siteX", 0))
                    .put("siteZ", Integer.getInteger("voxel.benchmark.siteZ", 0))
                    .put("clouds", System.getProperty("voxel.benchmark.clouds", "FAST"))
                    .put("gpuTimers", System.getProperty("voxel.benchmark.gpuTimers", "true"))
                    .put("gpu", glGetString(GL_RENDERER))
                    .put("glVersion", glGetString(GL_VERSION))
                    .put(
                            "jvmArgs",
                            String.join(
                                    " ", ManagementFactory.getRuntimeMXBean().getInputArguments()))
                    .put("label", label)
                    .put("scene", scene)
                    .put("rd", renderDistance)
                    .put("far", farDistance)
                    .put("width", width)
                    .put("height", height)
                    .put("settleS", settleNanos / 1e9)
                    .put("settleTimedOut", settleTimedOut)
                    .put("measuredS", seconds)
                    .put("frames", frames)
                    .put("frameSamples", frameTimes.samples())
                    .put("frameOverflowCount", reservoirReplacements)
                    .put("ticksComplete", missedTicks == 0)
                    .put("fps", frames / seconds)
                    .put("frame", frameTimes)
                    .put("frameMeanMs", frameMeanMs)
                    .put("frameExactMaxMs", frameMaxMs)
                    .put("over6944us", over6944)
                    .put("over16ms", over16)
                    .put("over33ms", over33);
            for (Segment segment : Segment.values()) {
                Distribution distribution = segments[segment.ordinal()];
                String name = segment.name().toLowerCase(Locale.ROOT);
                record.put(name + "P50Ms", distribution.p50Millis()).put(name + "P95Ms", distribution.p95Millis());
                if (segment == Segment.UPLOAD) record.put("uploadMaxMs", distribution.maxMillis());
            }
            record.put("gpuSamples", gpuTerrain.samples())
                    .put("gpuTerrainP50Ms", gpuTerrain.p50Millis())
                    .put("gpuTerrainP95Ms", gpuTerrain.p95Millis())
                    .put("gpuUiP50Ms", gpuUi.p50Millis())
                    .put("gpuFarP50Ms", gpuFar.p50Millis())
                    .put("gpuFarP95Ms", gpuFar.p95Millis())
                    .put("gpuCloudsP50Ms", gpuClouds.p50Millis())
                    .put("gpuCloudsP95Ms", gpuClouds.p95Millis())
                    .put("gpuTranslucentP50Ms", gpuTranslucent.p50Millis());
            var f0 = start.far();
            var f1 = end.far();
            long farBuilt = f1.built() - f0.built();
            record.put("farTiles", f1.resident())
                    .put("farBuiltPerS", farBuilt / seconds)
                    .put("farBuildAvgMs", average(f1.buildNanos() - f0.buildNanos(), farBuilt))
                    .put("farQueuedEnd", f1.queued())
                    .put("farGpuMB", megabytes(end.farGpuBytes()))
                    .put("farArenaMB", megabytes(end.farArenaBytes()));
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
            timing(
                    record,
                    "genExecution",
                    s0.generationTimings().execution(),
                    s1.generationTimings().execution());
            timing(
                    record,
                    "genQueue",
                    s0.generationTimings().queueWait(),
                    s1.generationTimings().queueWait());
            timing(
                    record,
                    "genIntake",
                    s0.generationTimings().intake(),
                    s1.generationTimings().intake());
            timing(
                    record,
                    "meshExecution",
                    s0.meshTimings().execution(),
                    s1.meshTimings().execution());
            timing(
                    record,
                    "meshQueue",
                    s0.meshTimings().queueWait(),
                    s1.meshTimings().queueWait());
            timing(
                    record,
                    "meshIntake",
                    s0.meshTimings().intake(),
                    s1.meshTimings().intake());
            timing(record, "requestToGenerated", s0.generationDistribution(), s1.generationDistribution());
            timing(record, "requestToUpload", s0.uploadDistribution(), s1.uploadDistribution());
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
                    .put("waterOnlyMeshesPerS", (s1.waterOnlyMeshes() - s0.waterOnlyMeshes()) / seconds)
                    .put("residentMax", residentMax)
                    .put("visibleAvg", frames == 0 ? 0.0 : (double) visibleSum / frames)
                    .put("drawsAvg", frames == 0 ? 0.0 : (double) drawSum / frames)
                    .put("meshDrawsAvg", frames == 0 ? 0.0 : (double) meshDrawSum / frames)
                    .put("cullAvgMs", frames == 0 ? 0.0 : cullNanos / 1e6 / frames)
                    .put("directDraw", Boolean.getBoolean("voxel.render.directDraw"))
                    .put("uploadQueueMax", uploadQueueMax)
                    .put("generationQueueMax", genQueueMax)
                    .put("meshQueueMax", meshQueueMax)
                    .put("meshPipelineMaxMB", megabytes(meshPipelineBytesMax))
                    .put("oldestAckMaxMs", oldestAckMax / 1e6)
                    .put("movementWaitingTicks", movementWaitingTicks)
                    .put("travelledBlocks", travelledBlocks)
                    .put("renderAllocMBps", megabytes(end.renderAllocated() - start.renderAllocated()) / seconds)
                    .put("worldAllocMBps", megabytes(end.worldAllocated() - start.worldAllocated()) / seconds)
                    .put("totalAllocMBps", megabytes(end.totalAllocated() - start.totalAllocated()) / seconds)
                    .put("renderCpuMsPerS", (end.renderCpuNanos() - start.renderCpuNanos()) / 1e6 / seconds)
                    .put("worldCpuMsPerS", (end.worldCpuNanos() - start.worldCpuNanos()) / 1e6 / seconds)
                    .put("processCpuMsPerS", (end.processCpuNanos() - start.processCpuNanos()) / 1e6 / seconds)
                    .put("gcCount", end.gcCount() - start.gcCount())
                    .put("gcMs", end.gcMillis() - start.gcMillis())
                    .put("retainedHeapMB", retainedHeapMb)
                    .put("chunkMemoryMB", megabytes(end.chunkBytes()))
                    .put("gpuMeshMB", megabytes(end.gpuMeshBytes()))
                    .put("gpuArenaMB", megabytes(end.gpuArenaBytes()))
                    .put("gpuArenas", end.gpuArenas())
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

        private static void timing(
                BenchmarkRecord record,
                String prefix,
                DurationHistogram.Snapshot before,
                DurationHistogram.Snapshot after) {
            record.put(prefix + "P50Ms", after.percentileMillisSince(before, .5))
                    .put(prefix + "P95Ms", after.percentileMillisSince(before, .95))
                    .put(prefix + "P99Ms", after.percentileMillisSince(before, .99));
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
            FarTerrainStreamer.Metrics far,
            long farGpuBytes,
            long farArenaBytes,
            long renderAllocated,
            long worldAllocated,
            long totalAllocated,
            long renderCpuNanos,
            long worldCpuNanos,
            long processCpuNanos,
            long gcCount,
            long gcMillis,
            long gpuMeshBytes,
            long gpuArenaBytes,
            int gpuArenas,
            long chunkBytes) {
        static Counters sample(WorldRuntime runtime, WorldRuntime.Frame state, TerrainRenderer renderer) {
            var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            long gcCount = 0, gcMillis = 0;
            for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += Math.max(0, collector.getCollectionCount());
                gcMillis += Math.max(0, collector.getCollectionTime());
            }
            return new Counters(
                    state.metrics().streaming(),
                    state.metrics().far(),
                    renderer.far().gpuBytes(),
                    renderer.far().arenaBytes(),
                    threads.getThreadAllocatedBytes(Thread.currentThread().threadId()),
                    threads.getThreadAllocatedBytes(runtime.threadId()),
                    threads.getTotalThreadAllocatedBytes(),
                    threads.getCurrentThreadCpuTime(),
                    threads.getThreadCpuTime(runtime.threadId()),
                    ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
                            .getProcessCpuTime(),
                    gcCount,
                    gcMillis,
                    renderer.memoryStats().allocated(),
                    renderer.memoryStats().arenaBytes(),
                    renderer.memoryStats().arenas(),
                    state.metrics().estimatedChunkBytes());
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
