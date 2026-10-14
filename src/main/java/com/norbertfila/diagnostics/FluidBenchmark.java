package com.norbertfila.diagnostics;

import com.norbertfila.fluid.*;
import com.norbertfila.persistence.world.*;
import com.norbertfila.player.*;
import com.norbertfila.player.InventorySnapshot;
import com.norbertfila.player.PlayerController.Mode;
import com.norbertfila.world.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class FluidBenchmark {
    public enum Scene {
        LAKE,
        SOURCE,
        WATERFALL,
        DRAIN,
        ROUTE,
        CONVERSION
    }

    public record Fixture(World world, WaterSimulator water, BlockPos source) {}

    public record Result(
            Scene scene,
            boolean extended,
            long nanos,
            long allocatedBytes,
            long processed,
            long changed,
            long dirty,
            int active,
            int deferred,
            long checksum,
            long chunkBytes) {}

    private FluidBenchmark() {}

    public static Fixture fixture(Scene scene, boolean extended) {
        World world = new World();
        int top = scene == Scene.WATERFALL ? 9 : 1;
        for (int x = -2; x <= 1; x++)
            for (int y = -1; y <= top; y++)
                for (int z = -2; z <= 1; z++) world.putChunk(new ChunkPos(x, y, z), new Chunk());
        for (int x = -24; x <= 24; x++)
            for (int z = -24; z <= 24; z++) set(world, x, 0, z, BlockId.STONE, WaterState.EMPTY);
        WaterSimulator water = new WaterSimulator(world, 64, 2000, extended);
        BlockPos source = new BlockPos(0, scene == Scene.WATERFALL ? 129 : 1, 0);
        if (scene == Scene.LAKE || scene == Scene.DRAIN) {
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 4; z++)
                    for (int y = 1; y <= 4; y++) {
                        boolean wall = Math.abs(x) == 4 || Math.abs(z) == 4;
                        set(
                                world,
                                x,
                                y,
                                z,
                                wall ? BlockId.STONE : BlockId.WATER,
                                wall
                                        ? WaterState.EMPTY
                                        : scene == Scene.LAKE ? WaterState.SOURCE : WaterState.flowing(3));
                    }
            for (int x = -3; x <= 3; x++)
                for (int z = -3; z <= 3; z++)
                    for (int y = 1; y <= 4; y++) water.onBlockChanged(new BlockPos(x, y, z), 0);
        } else {
            world.setBlock(source, BlockId.WATER, 64);
            water.onBlockChanged(source, 0);
            if (scene == Scene.ROUTE) {
                for (int x = -24; x <= 24; x++)
                    for (int z = -24; z <= 24; z++) set(world, x, -2, z, BlockId.STONE, WaterState.EMPTY);
                world.setBlock(new BlockPos(2, 0, 0), BlockId.AIR, 64);
                water.onBlockChanged(new BlockPos(2, 0, 0), 0);
            }
            if (scene == Scene.CONVERSION) {
                world.setBlock(new BlockPos(2, 1, 0), BlockId.WATER, 64);
                water.onBlockChanged(new BlockPos(2, 1, 0), 0);
            }
        }
        return new Fixture(world, water, source);
    }

    private static void set(World world, int x, int y, int z, byte block, byte state) {
        BlockPos p = new BlockPos(x, y, z), local = Coordinates.worldToLocal(p);
        world.getChunk(Coordinates.worldToChunk(p)).set(local.x(), local.y(), local.z(), block, state);
    }

    public static Result run(Scene scene, boolean extended) {
        Fixture fixture = fixture(scene, extended);
        ThreadMXBean bean = allocations();
        long beforeBytes = allocated(bean), started = System.nanoTime();
        long processed = 0, changed = 0, dirty = 0;
        for (int tick = 1; tick <= 10000; tick++) {
            if (tick == 5000 && (scene == Scene.SOURCE || scene == Scene.WATERFALL)) {
                fixture.world().setBlock(fixture.source(), BlockId.AIR, 64);
                fixture.water().onBlockChanged(fixture.source(), tick);
            }
            var result = fixture.water().tick(tick);
            processed += result.processedUpdates();
            changed += result.changedBlocks();
            dirty += result.dirtyChunks().size();
            if (tick > 9500 && scene != Scene.ROUTE && scene != Scene.CONVERSION)
                check(
                        result.processedUpdates() == 0
                                && result.changedBlocks() == 0
                                && result.dirtyChunks().isEmpty(),
                        "Unsettled scene " + scene);
        }
        long nanos = System.nanoTime() - started, bytes = allocated(bean);
        var metrics = fixture.water().metrics(10000);
        long checksum = fixture.world().changes().changes().hashCode();
        return new Result(
                scene,
                extended,
                nanos,
                beforeBytes < 0 ? -1 : bytes - beforeBytes,
                processed,
                changed,
                dirty,
                metrics.active(),
                metrics.deferred(),
                checksum,
                chunkBytes(fixture.world()));
    }

    public static long chunkBytes(World world) {
        return world.residentPositions().stream()
                .mapToLong(p -> world.getChunk(p).estimatedBytes())
                .sum();
    }

    public static void main(String[] args) throws Exception {
        metadata("CPU headless", "N/A", "N/A", 0);
        if (args.length > 0 && args[0].equals("soak")) {
            soak(Integer.parseInt(args[1]), Path.of(args[2]));
            return;
        }
        System.out.println(
                "warmup=2 fresh worlds per scene/variant repeats=5 ticks=10000 budget=2000 seed=0 version=1");
        for (Scene scene : Scene.values()) {
            for (int warm = 0; warm < 2; warm++) {
                run(scene, false);
                run(scene, true);
            }
            long[][] timings = new long[2][5];
            for (int repeat = 0; repeat < 5; repeat++) {
                Result base = run(scene, false), extended = run(scene, true);
                if (scene != Scene.ROUTE && scene != Scene.CONVERSION)
                    check(base.checksum() == extended.checksum(), "A/B final deltas differ: " + scene);
                System.out.println("repeat=" + repeat + " " + base);
                System.out.println("repeat=" + repeat + " " + extended);
                timings[0][repeat] = base.nanos();
                timings[1][repeat] = extended.nanos();
            }
            for (int variant = 0; variant < 2; variant++) {
                Arrays.sort(timings[variant]);
                System.out.printf(
                        "summary scene=%s extended=%s medianMs=%.3f minMs=%.3f maxMs=%.3f%n",
                        scene,
                        variant == 1,
                        timings[variant][2] / 1e6,
                        timings[variant][0] / 1e6,
                        timings[variant][4] / 1e6);
            }
        }
        contactBenchmark();
        System.out.println("PASS CPU A/B and stable-water assertions");
    }

    private static void contactBenchmark() {
        var fixture = fixture(Scene.LAKE, true);
        var player = PlayerState.at(new PlayerState.Position(.5, 1, .5));
        for (int i = 0; i < 10000; i++) WaterContact.sample(fixture.world(), player);
        ThreadMXBean bean = allocations();
        long before = allocated(bean), started = System.nanoTime();
        double sum = 0;
        for (int i = 0; i < 100000; i++)
            sum += WaterContact.sample(fixture.world(), player).submergedFraction();
        long nanos = System.nanoTime() - started, bytes = allocated(bean);
        check(sum == 100000, "Contact result changed");
        System.out.printf(
                "contact calls=100000 warmup=10000 nsPerCall=%.1f bytesPerCall=%.1f%n",
                nanos / 100000d, before < 0 ? -1 : (bytes - before) / 100000d);
    }

    private static void soak(int seconds, Path save) throws Exception {
        check(seconds > 0 && seconds <= 600, "Duration must be positive and at most 600 seconds");
        check(!Files.exists(save), "Use a fresh isolated save path");
        long started = System.nanoTime(), deadline = started + seconds * 1_000_000_000L;
        int cycles = 0, maxOverlay = 0, maxPending = 0;
        long minRetained = Long.MAX_VALUE, maxRetained = 0;
        while (System.nanoTime() < deadline) {
            var fixture = fixture(Scene.SOURCE, true);
            for (int tick = 1; tick <= 400; tick++) fixture.water().tick(tick);
            ChunkPos seam = new ChunkPos(0, 0, 0);
            Chunk base = fixture.world().getChunk(seam);
            fixture.world().removeChunk(seam);
            fixture.water().onChunkChanged(seam, 400);
            int parked = fixture.water().deferredUpdates();
            for (int tick = 401; tick <= 800; tick++) fixture.water().tick(tick);
            check(fixture.water().deferredUpdates() >= parked, "Deferred work disappeared");
            fixture.world().putChunk(seam, base);
            fixture.water().onChunkChanged(seam, 800);
            var identity = new WorldIdentity(new java.util.UUID(0, 1), 0, "default", 1);
            var snapshot = WorldSnapshots.capture(
                    identity,
                    1,
                    fixture.world(),
                    fixture.water().snapshot(800),
                    InventorySnapshot.EMPTY,
                    new SavedPlayerState(.5, 1, .5, -90, 0, Mode.SURVIVAL, false));
            WorldStateCodec.write(save, snapshot);
            var loaded = WorldStateCodec.read(save, identity);
            check(
                    snapshot.deltas().equals(loaded.deltas())
                            && snapshot.fluids().equals(loaded.fluids())
                            && snapshot.identity().equals(loaded.identity()),
                    "Save round trip differs");
            var resumed = fixture(Scene.SOURCE, true);
            // The restored overlay and task clock replace the fresh source simulation, not its generated floor.
            WorldSnapshots.restore(resumed.world(), loaded, 64);
            WaterSimulator water = new WaterSimulator(resumed.world(), 64, 2000, true);
            water.restore(loaded.fluids());
            resumed.world().setBlock(resumed.source(), BlockId.AIR, 64);
            water.onBlockChanged(resumed.source(), 801);
            for (int tick = 801; tick <= 1800; tick++) water.tick(tick);
            check(water.pendingUpdates() == 0, "Source did not recede after restart");
            maxOverlay = Math.max(maxOverlay, resumed.world().changes().size());
            maxPending = Math.max(maxPending, loaded.fluids().updates().size());
            cycles++;
            if (cycles % 100 == 0) {
                // Retained-heap diagnostics are outside timed benchmark runs.
                System.gc();
                long heap =
                        ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                minRetained = Math.min(minRetained, heap);
                maxRetained = Math.max(maxRetained, heap);
                System.out.println("soak cycles=" + cycles + " elapsedSeconds=" + (System.nanoTime() - started) / 1e9
                        + " retainedHeap=" + heap + " maxOverlay=" + maxOverlay + " maxTasks=" + maxPending);
            }
        }
        System.out.println("PASS headless soak seconds=" + seconds + " cycles=" + cycles + " retainedMin=" + minRetained
                + " retainedMax=" + maxRetained + " maxOverlay=" + maxOverlay + " maxTasks=" + maxPending);
    }

    public static void metadata(String scene, String resolution, String gpu, int radius) {
        System.out.println("commit=" + System.getProperty("voxel.commit", "unknown") + " scene=" + scene
                + " seed=0 generatorVersion=1 resolution=" + resolution + " RD=" + radius + " GPU=" + gpu);
        System.out.println("CPU=" + System.getenv("PROCESSOR_IDENTIFIER") + " Java=" + Runtime.version() + " OS="
                + System.getProperty("os.name") + " JVM="
                + ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    private static ThreadMXBean allocations() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof ThreadMXBean allocation) || !allocation.isThreadAllocatedMemorySupported()) return null;
        if (!allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
        return allocation;
    }

    private static long allocated(ThreadMXBean bean) {
        return bean == null
                ? -1
                : bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
