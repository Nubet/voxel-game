package com.norbertfila.diagnostics;

import static com.norbertfila.diagnostics.FluidBenchmark.check;
import static org.lwjgl.opengl.GL46C.*;

import com.norbertfila.assets.AssetLoader;
import com.norbertfila.core.*;
import com.norbertfila.generation.*;
import com.norbertfila.generation.ChunkData;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.persistence.world.*;
import com.norbertfila.platform.Window;
import com.norbertfila.player.*;
import com.norbertfila.render.*;
import com.norbertfila.streaming.*;
import com.norbertfila.world.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class FluidRuntimeBenchmark {
    private FluidRuntimeBenchmark() {}

    public static final class Terrain implements TerrainSource {
        public long seed() {
            return 0;
        }

        public int generatorVersion() {
            return 1;
        }

        public int surfaceHeight(int x, int z) {
            return 0;
        }

        public TerrainSummary sample(int x, int z) {
            return new TerrainSummary(0, BlockId.STONE, BlockId.STONE, BlockId.STONE);
        }

        public ChunkData generate(ChunkPos p) {
            byte[] blocks = new byte[Chunk.BLOCK_BYTES];
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        long wx = (long) p.x() * 16 + x, wy = (long) p.y() * 16 + y, wz = (long) p.z() * 16 + z;
                        byte block = wy <= 0 ? BlockId.STONE : BlockId.AIR;
                        if (wx >= 8 && wx < 48 && Math.abs(wz) <= 3 && wy >= 1 && wy <= 3) block = BlockId.WATER;
                        if (wx >= 8 && wx <= 48 && Math.abs(wz) == 4 && wy >= 1 && wy <= 3) block = BlockId.STONE;
                        if (wx == 32 && wz == 2 && wy == 17) block = BlockId.WATER;
                        blocks[(y * 16 + z) * 16 + x] = block;
                    }
            return new ChunkData(p, blocks);
        }
    }

    public static void main(String[] args) throws Exception {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        Path save = Path.of(args.length > 1 ? args[1] : "fluid-runtime-benchmark.vxl");
        check(
                seconds > 0 && seconds <= 600 && !Files.exists(save),
                "Use a duration of at most 600 seconds and fresh save path");
        int repeats = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        check(repeats > 0, "Repetitions must be positive");
        for (int repeat = 0; repeat < repeats; repeat++) {
            Path isolated = save.resolveSibling(save.getFileName() + "-r" + repeat);
            check(!Files.exists(isolated), "Repetition save already exists");
            run(seconds, isolated, repeat);
        }
    }

    private static void run(int seconds, Path save, int repeat) throws Exception {
        Samples frames = new Samples(), ticks = new Samples(), water = new Samples(), contacts = new Samples();
        Samples prediction = new Samples(), player = new Samples(), gaps = new Samples(), captures = new Samples();
        long uploads = 0, evictions = 0, stalls = 0, waiting = 0, movementSamples = 0, submergedSamples = 0;
        long maxAck = 0, lastTick = -1, lastPublication = 0, firstTick = -1, maxFluid = 0;
        long maxResident = 0, maxGpu = 0, retainedMin = Long.MAX_VALUE, retainedMax = 0;
        int direction = 1;
        double lastX = Double.NaN;
        long lastMinute = -1, lastRestart = -1;
        Set<Long> stages = new HashSet<>();
        boolean sawFlight = false, sawSpectator = false, sawDry = false;
        WorldRuntime runtime = null;
        TerrainRenderer renderer = null;
        try (Window window = new Window(1280, 720, "FLUID reproducible runtime soak");
                BlockTextureArray textures =
                        new BlockTextureArray(new AssetLoader(BlockRegistry.DEFAULT).loadBlockTextures())) {
            FluidBenchmark.metadata(
                    "finite-pool-land-flight-swim-r" + repeat,
                    "1280x720",
                    glGetString(GL_RENDERER) + " " + glGetString(GL_VERSION),
                    2);
            System.out.println("warmup=10s measurement=" + seconds + "s VSync=off cap=240 variant=extended PID="
                    + ProcessHandle.current().pid() + " save=" + save);
            renderer = new TerrainRenderer(textures);
            runtime = start(save);
            Camera camera = new Camera();
            UnderwaterVisibility visibility = new UnderwaterVisibility();
            Map<ChunkPos, Long> residents = Map.of();
            Map<ChunkPos, WorldRuntime.SessionMesh> pending = new HashMap<>();
            long start = System.nanoTime(), measureStart = start + 10_000_000_000L;
            long deadline = measureStart + seconds * 1_000_000_000L, previousFrame = start;
            long previousVisibility = start;
            while (System.nanoTime() < deadline) {
                long frameStarted = System.nanoTime();
                window.pollEvents();
                var frame = runtime.frame();
                if (frame == null) {
                    Thread.sleep(1);
                    continue;
                }
                if (firstTick < 0) firstTick = frame.tick();
                long elapsed = (frameStarted - start) / 1_000_000_000L;
                boolean measuring = frameStarted >= measureStart;
                boolean restart = elapsed > 0 && elapsed % 300 == 0 && lastRestart != elapsed;
                if (restart) {
                    lastRestart = elapsed;
                    long oldTick = frame.tick();
                    runtime.close();
                    var saved = WorldStateCodec.read(
                            save, WorldStateCodec.readHeader(save).identity());
                    check(saved.fluids().tick() >= oldTick, "Shutdown did not flush save");
                    renderer.close();
                    check(renderer.memoryStats().allocated() == 0, "GPU allocations survive close");
                    renderer = new TerrainRenderer(textures);
                    residents = Map.of();
                    pending.clear();
                    runtime = start(save);
                    lastTick = -1;
                    lastX = Double.NaN;
                    lastPublication = 0;
                    direction = 1;
                    previousFrame = System.nanoTime();
                    continue;
                }
                double x = frame.playerState().position().x();
                boolean turned = false;
                if (direction > 0 && x > 112 || direction < 0 && x < 1) {
                    direction = -direction;
                    turned = true;
                }
                int phase = (int) (elapsed % 300);
                long cycle = elapsed / 300 * 300;
                if (seconds >= 300
                        && phase >= 180
                        && phase < 200
                        && frame.mode() == PlayerController.Mode.SURVIVAL
                        && stages.add(cycle + 180))
                    check(runtime.offer(new WorldRuntime.NextMode()), "Creative command rejected");
                if (seconds >= 300
                        && phase >= 185
                        && phase < 200
                        && frame.mode() == PlayerController.Mode.CREATIVE
                        && !frame.flying()
                        && !frame.modePending()
                        && stages.add(cycle + 185)) {
                    check(runtime.offer(new WorldRuntime.Jump(elapsed)), "Jump command rejected");
                    check(runtime.offer(new WorldRuntime.Jump(elapsed + .1)), "Flight command rejected");
                }
                if (seconds >= 300
                        && phase >= 205
                        && phase < 215
                        && frame.mode() == PlayerController.Mode.CREATIVE
                        && stages.add(cycle + 205))
                    check(runtime.offer(new WorldRuntime.NextMode()), "Spectator command rejected");
                if (seconds >= 300
                        && phase >= 230
                        && frame.mode() == PlayerController.Mode.SPECTATOR
                        && stages.add(cycle + 230))
                    check(runtime.offer(new WorldRuntime.NextMode()), "Survival command rejected");
                boolean ascend =
                        frame.flying() && frame.playerState().position().y() < 24;
                sawFlight |= frame.flying();
                sawSpectator |= frame.mode() == PlayerController.Mode.SPECTATOR;
                sawDry |=
                        frame.waterContact().available() && frame.waterContact().submergedFraction() == 0;
                boolean descend = frame.mode() == PlayerController.Mode.SPECTATOR
                        && frame.playerState().position().y() > 2;
                runtime.controls(new WorldRuntime.Controls(
                        new PlayerInput(true, false, false, false, ascend, descend, false),
                        direction > 0 ? 0 : 180,
                        0,
                        false,
                        false,
                        2));
                boolean ackBlocked = elapsed % 60 >= 30 && elapsed % 60 < 35;
                if (!ackBlocked) {
                    for (var old : residents.entrySet()) {
                        if (old.getValue().equals(frame.residents().get(old.getKey()))) continue;
                        var discarded = pending.remove(old.getKey());
                        if (discarded != null) runtime.acknowledge(discarded, false);
                        renderer.remove(old.getKey());
                        evictions++;
                    }
                    residents = frame.residents();
                    WorldRuntime.SessionMesh mesh;
                    while ((mesh = runtime.pollMesh()) != null) {
                        var old = pending.put(mesh.mesh().position(), mesh);
                        if (old != null) runtime.acknowledge(old, false);
                        renderer.enqueueUpload(mesh.mesh());
                    }
                    WorldRuntime owner = runtime;
                    var upload = renderer.processUploads(
                            4L * 1024 * 1024, 2_000_000, m -> owner.frame().isCurrent(m));
                    check(upload.failed().isEmpty(), "GPU upload failed");
                    check(upload.queueDepth() <= 64, "Upload queue exceeds limit");
                    for (var m : upload.uploaded()) {
                        var issued = pending.remove(m.position());
                        if (issued != null) runtime.acknowledge(issued, true);
                        uploads++;
                    }
                    for (var m : upload.discarded()) {
                        var issued = pending.remove(m.position());
                        if (issued != null) runtime.acknowledge(issued, false);
                    }
                }
                if (frame.ready()) runtime.activate();
                var eye = frame.interpolatedEye(frameStarted);
                camera.setPosition(eye.x(), eye.y(), eye.z());
                visibility.update(
                        frame.waterContact().eyesSubmerged(),
                        frame.waterContact().available(),
                        Math.min(.1, (frameStarted - previousVisibility) / 1e9));
                previousVisibility = frameStarted;
                glViewport(0, 0, 1280, 720);
                glClearColor(visibility.backgroundRed(), visibility.backgroundGreen(), visibility.backgroundBlue(), 1);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                renderer.render(
                        camera.relativeViewProjection(1280, 720),
                        Frustum.from(camera.viewProjection(1280, 720)),
                        camera.x(),
                        camera.y(),
                        camera.z(),
                        visibility.amount());
                check(glGetError() == GL_NO_ERROR, "GL error");
                window.swapBuffers();
                var metrics = frame.metrics();
                check(
                        metrics.outstandingAcks() <= 64
                                && metrics.publishedMeshes() <= 64
                                && metrics.queuedActions() <= 64,
                        "Owner queue exceeds limit");
                check(
                        metrics.streaming().generationOutstanding() <= 64
                                && metrics.streaming().meshOutstanding() <= 64,
                        "Worker queue exceeds limit");
                check(!metrics.save().failed(), "Save worker failed");
                maxAck = Math.max(maxAck, metrics.outstandingAcks());
                maxFluid = Math.max(maxFluid, frame.pendingWater());
                maxResident = Math.max(maxResident, frame.residents().size());
                maxGpu = Math.max(maxGpu, renderer.memoryStats().allocated());
                if (frame.tick() != lastTick) {
                    if (measuring) {
                        ticks.add(metrics.tickNanos());
                        water.add(metrics.waterNanos());
                        contacts.add(metrics.contactNanos());
                        prediction.add(metrics.predictionNanos());
                        player.add(metrics.playerNanos());
                        if (metrics.captureNanos() > 0) captures.add(metrics.captureNanos());
                        if (lastPublication > 0) gaps.add(frame.publishedAt() - lastPublication);
                        if (metrics.movementWaiting()) waiting++;
                        if (frame.waterContact().submergedFraction() > .5) submergedSamples++;
                        if (!Double.isNaN(lastX)
                                && !turned
                                && Math.abs(frame.playerState().velocityX()) > .1) {
                            movementSamples++;
                            if (Math.abs(x - lastX) < 1e-9 && !metrics.movementWaiting()) stalls++;
                        }
                    }
                    lastTick = frame.tick();
                    lastX = x;
                    lastPublication = frame.publishedAt();
                }
                long minute = elapsed / 60;
                if (minute != lastMinute) {
                    lastMinute = minute;
                    long heap = ManagementFactory.getMemoryMXBean()
                            .getHeapMemoryUsage()
                            .getUsed();
                    long direct =
                            ManagementFactory.getPlatformMXBeans(java.lang.management.BufferPoolMXBean.class).stream()
                                    .mapToLong(java.lang.management.BufferPoolMXBean::getMemoryUsed)
                                    .sum();
                    System.out.println("checkpoint elapsed=" + elapsed + " heapUsed=" + heap + " direct=" + direct
                            + " chunkEstimate=" + metrics.estimatedChunkBytes() + " GPU=" + renderer.memoryStats()
                            + " fluid=" + metrics.fluid() + " streaming=" + metrics.streaming() + " save="
                            + metrics.save());
                    retainedMin = Math.min(retainedMin, heap);
                    retainedMax = Math.max(retainedMax, heap);
                }
                long remaining = 4_166_667 - (System.nanoTime() - frameStarted);
                if (remaining > 0) java.util.concurrent.locks.LockSupport.parkNanos(remaining);
                long now = System.nanoTime();
                if (measuring) frames.add(now - previousFrame);
                previousFrame = now;
            }
            runtime.close();
            runtime = null;
            renderer.close();
            check(renderer.memoryStats().allocated() == 0, "GPU memory did not release");
            var saved =
                    WorldStateCodec.read(save, WorldStateCodec.readHeader(save).identity());
            check(saved.fluids().tick() > firstTick, "Clock did not survive save");
            check(
                    uploads > 0 && submergedSamples > 0 && movementSamples > 0 && evictions > 0,
                    "Scene coverage incomplete");
            check(stalls == 0, "Unexplained movement pauses: " + stalls);
            check(sawDry && (seconds < 300 || sawFlight && sawSpectator), "Mode/dry scene coverage incomplete");
            System.out.println("frames " + frames.summary() + " FPS=" + 1e9 / frames.mean());
            System.out.println(
                    "logic " + ticks.summary() + " water " + water.summary() + " contact " + contacts.summary());
            System.out.println("prediction " + prediction.summary() + " player " + player.summary() + " publicationGap "
                    + gaps.summary() + " capture " + captures.summary());
            System.out.println("PASS runtime seconds=" + seconds + " repeat=" + repeat + " uploads=" + uploads
                    + " evictions=" + evictions + " maxResidents=" + maxResident + " maxACK=" + maxAck
                    + " maxFluid=" + maxFluid + " maxGPU=" + maxGpu + " heapSampleMin=" + retainedMin
                    + " heapSampleMax=" + retainedMax + " movementSamples=" + movementSamples + " stalls=" + stalls
                    + " waiting=" + waiting + " swimSamples=" + submergedSamples + " savedTick="
                    + saved.fluids().tick());
        } finally {
            if (runtime != null) runtime.close();
            if (renderer != null) renderer.close();
        }
    }

    private static WorldRuntime start(Path save) throws Exception {
        if (!java.nio.file.Files.exists(save)) {
            var terrain = new Terrain();
            WorldStateCodec.write(
                    save,
                    WorldSnapshot.initial(
                            new WorldIdentity(
                                    java.util.UUID.randomUUID(), terrain.seed(), "default", terrain.generatorVersion()),
                            java.time.Instant.now()));
        }
        return new WorldRuntime(
                new Terrain(), new WaterMesher(), new ViewDemand(2, 0, 1, ViewDemand.Shape.SQUARE), save);
    }

    private static final class Samples {
        private final long[] values = new long[1_000_000];
        private int size;
        private long count, sum, max;

        void add(long value) {
            values[size++ % values.length] = value;
            count++;
            sum += value;
            max = Math.max(max, value);
        }

        double mean() {
            return count == 0 ? 0 : sum / (double) count;
        }

        String summary() {
            long[] sorted = Arrays.copyOf(values, Math.min(size, values.length));
            Arrays.sort(sorted);
            return String.format(
                    Locale.ROOT,
                    "samples=%d meanMs=%.4f p50=%.4f p95=%.4f p99=%.4f max=%.4f",
                    count,
                    mean() / 1e6,
                    percentile(sorted, .5) / 1e6,
                    percentile(sorted, .95) / 1e6,
                    percentile(sorted, .99) / 1e6,
                    max / 1e6);
        }

        private static long percentile(long[] values, double p) {
            return values.length == 0 ? 0 : values[(int) Math.ceil(values.length * p) - 1];
        }
    }
}
