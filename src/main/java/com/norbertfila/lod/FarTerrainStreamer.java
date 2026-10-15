package com.norbertfila.lod;

import com.norbertfila.generation.TerrainSource;
import com.norbertfila.world.Chunk;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Far terrain around the camera, owned by the world thread. It picks the tiles outside the near chunk area and their
 * sample step by distance, builds them on low-priority workers and publishes an immutable {@link Residency}.
 *
 * <p>A result is accepted only when it still matches the request for its tile and the tile's edits have not changed
 * since the request; otherwise it is dropped and the tile is requested again. A published tile stays until its
 * replacement arrives, so changing detail never leaves a hole.
 */
public final class FarTerrainStreamer implements AutoCloseable {
    /** Sample step by distance from the camera to the tile center, nearest ring first. */
    private static final int[] RING_LIMITS = {256, 512, 1024};

    private static final int[] RING_STEPS = {1, 2, 4, 8};
    private static final int HYSTERESIS_BLOCKS = 24;
    private static final int MAX_IN_FLIGHT = 8;
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final Logger LOG = Logger.getLogger(FarTerrainStreamer.class.getName());

    private final FarTerrainGenerator generator;
    private final WorldChangeOverlay overlay;
    private final ExecutorService workers;
    private final ConcurrentLinkedQueue<Built> completed = new ConcurrentLinkedQueue<>();
    // Wanted sample step per tile key, and the published meshes.
    private final Map<FarTilePos, Integer> desired = new HashMap<>();
    private final Map<FarTilePos, FarTileMesh> resident = new HashMap<>();
    private final Map<FarTilePos, Long> inFlight = new HashMap<>();
    // Tiles to build, nearest first; entries may be outdated and are checked when taken.
    private final ArrayDeque<FarTilePos> queue = new ArrayDeque<>();
    private long nextToken;
    private int cameraTileX = Integer.MIN_VALUE, cameraTileZ, nearMinX, nearMinZ, nearMaxX, nearMaxZ, farRadius;
    private int cameraX, cameraZ;
    // Tile range [min, max) entirely inside the near area.
    private int coveredMinX, coveredMaxX, coveredMinZ, coveredMaxZ;
    private Residency residency = new Residency(0, Map.of(), 0, 0, 0, 0);
    private boolean changed;
    private long builtNanos;
    private int builtTiles;

    public FarTerrainStreamer(TerrainSource source, WorldChangeOverlay overlay, int workerThreads) {
        generator = new FarTerrainGenerator(Objects.requireNonNull(source));
        this.overlay = Objects.requireNonNull(overlay);
        if (workerThreads < 1) throw new IllegalArgumentException("At least one far terrain worker is needed");
        workers = new ThreadPoolExecutor(
                workerThreads, workerThreads, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "far-terrain-" + THREAD_IDS.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                });
    }

    /**
     * Plans tiles around the camera. The near area is the square of chunk columns {@code nearCenter +- nearRadius}
     * the chunk streamer covers; tiles entirely inside it are not needed. A {@code farRadiusChunks} not larger than
     * the near radius turns far terrain off.
     */
    public void update(
            double cameraBlockX,
            double cameraBlockZ,
            int nearCenterX,
            int nearCenterZ,
            int nearRadiusChunks,
            int farRadiusChunks) {
        int x = (int) Math.floor(cameraBlockX), z = (int) Math.floor(cameraBlockZ);
        int tileX = Math.floorDiv(x, FarTerrainTile.SIZE), tileZ = Math.floorDiv(z, FarTerrainTile.SIZE);
        int minX = (nearCenterX - nearRadiusChunks) * Chunk.SIZE,
                maxX = (nearCenterX + nearRadiusChunks + 1) * Chunk.SIZE;
        int minZ = (nearCenterZ - nearRadiusChunks) * Chunk.SIZE,
                maxZ = (nearCenterZ + nearRadiusChunks + 1) * Chunk.SIZE;
        int radius = farRadiusChunks > nearRadiusChunks ? farRadiusChunks * Chunk.SIZE : 0;
        cameraX = x;
        cameraZ = z;
        if (minX != nearMinX || maxX != nearMaxX || minZ != nearMinZ || maxZ != nearMaxZ) {
            nearMinX = minX;
            nearMaxX = maxX;
            nearMinZ = minZ;
            nearMaxZ = maxZ;
            changed = true;
        }
        // Tiles lying entirely inside the near area; the near center moves every chunk, this range far less often.
        int coveredMinX = Math.ceilDiv(minX, FarTerrainTile.SIZE),
                coveredMaxX = Math.floorDiv(maxX, FarTerrainTile.SIZE);
        int coveredMinZ = Math.ceilDiv(minZ, FarTerrainTile.SIZE),
                coveredMaxZ = Math.floorDiv(maxZ, FarTerrainTile.SIZE);
        if (tileX == cameraTileX
                && tileZ == cameraTileZ
                && coveredMinX == this.coveredMinX
                && coveredMaxX == this.coveredMaxX
                && coveredMinZ == this.coveredMinZ
                && coveredMaxZ == this.coveredMaxZ
                && radius == farRadius) return;
        cameraTileX = tileX;
        cameraTileZ = tileZ;
        this.coveredMinX = coveredMinX;
        this.coveredMaxX = coveredMaxX;
        this.coveredMinZ = coveredMinZ;
        this.coveredMaxZ = coveredMaxZ;
        farRadius = radius;
        plan();
    }

    private void plan() {
        Map<FarTilePos, Integer> previous = new HashMap<>(desired);
        desired.clear();
        List<Planned> order = new ArrayList<>();
        int tiles = farRadius / FarTerrainTile.SIZE + 1;
        for (int dz = -tiles; dz <= tiles; dz++)
            for (int dx = -tiles; dx <= tiles; dx++) {
                int tileX = cameraTileX + dx, tileZ = cameraTileZ + dz;
                if (tileX >= coveredMinX && tileX < coveredMaxX && tileZ >= coveredMinZ && tileZ < coveredMaxZ)
                    continue;
                long distance = distance(tileX, tileZ);
                if (distance > farRadius) continue;
                FarTilePos key = new FarTilePos(tileX, tileZ);
                desired.put(key, step(distance, previous.get(key)));
                order.add(new Planned(distance, key));
            }
        changed |= resident.keySet().removeIf(key -> !desired.containsKey(key));
        order.sort(Comparator.comparingLong(Planned::distance));
        queue.clear();
        for (Planned entry : order) if (needsBuild(entry.tile())) queue.add(entry.tile());
    }

    private long distance(int tileX, int tileZ) {
        double dx = tileX * (double) FarTerrainTile.SIZE + FarTerrainTile.SIZE / 2.0 - cameraX;
        double dz = tileZ * (double) FarTerrainTile.SIZE + FarTerrainTile.SIZE / 2.0 - cameraZ;
        return (long) Math.sqrt(dx * dx + dz * dz);
    }

    /** Ring step for a distance; a tile keeps its current step until it is a hysteresis margin past the limit. */
    static int step(long distance, Integer current) {
        int ring = 0;
        while (ring < RING_LIMITS.length && distance >= RING_LIMITS[ring]) ring++;
        if (current == null || current == RING_STEPS[ring]) return RING_STEPS[ring];
        for (int index = 0; index < RING_STEPS.length; index++) {
            if (RING_STEPS[index] != current) continue;
            long low = index == 0 ? Long.MIN_VALUE : RING_LIMITS[index - 1] - (long) HYSTERESIS_BLOCKS;
            long high = index == RING_LIMITS.length ? Long.MAX_VALUE : RING_LIMITS[index] + (long) HYSTERESIS_BLOCKS;
            if (distance >= low && distance < high) return current;
        }
        return RING_STEPS[ring];
    }

    private boolean needsBuild(FarTilePos key) {
        Integer step = desired.get(key);
        if (step == null) return false;
        FarTileMesh current = resident.get(key);
        return current == null
                || current.sampleStep() != step
                || current.editRevision() != overlay.areaRevision(key.originX(), key.originZ(), FarTerrainTile.SIZE);
    }

    /** Rebuilds tiles whose edits changed; {@code tiles} come from the overlay's dirty far tiles. */
    public void markEdited(List<FarTilePos> tiles) {
        for (FarTilePos tile : tiles) {
            if (desired.containsKey(tile)) queue.addFirst(tile);
        }
    }

    /** Accepts finished tiles and starts new builds up to the in-flight bound. */
    public void process() {
        Built built;
        while ((built = completed.poll()) != null) {
            FarTilePos key = built.key();
            Long token = inFlight.get(key);
            if (token == null || token != built.token()) continue;
            inFlight.remove(key);
            builtNanos += built.nanos();
            builtTiles++;
            Integer step = desired.get(key);
            // A failed build is not retried until the plan or the tile's edits change, so it cannot spin.
            if (step == null || built.mesh() == null) continue;
            long revision = overlay.areaRevision(key.originX(), key.originZ(), FarTerrainTile.SIZE);
            if (step != built.mesh().sampleStep() || revision != built.mesh().editRevision()) {
                queue.addFirst(key);
                continue;
            }
            resident.put(key, built.mesh());
            changed = true;
        }
        while (inFlight.size() < MAX_IN_FLIGHT && !queue.isEmpty()) {
            FarTilePos key = queue.poll();
            if (inFlight.containsKey(key) || !needsBuild(key)) continue;
            submit(key, desired.get(key));
        }
        if (changed) {
            residency = new Residency(
                    residency.revision() + 1, Map.copyOf(resident), nearMinX, nearMinZ, nearMaxX, nearMaxZ);
            changed = false;
        }
    }

    private void submit(FarTilePos key, int step) {
        int tileX = key.x(), tileZ = key.z();
        AreaEdits edits = overlay.edits(key.originX(), key.originZ(), FarTerrainTile.SIZE);
        long token = ++nextToken;
        inFlight.put(key, token);
        workers.execute(() -> {
            long started = System.nanoTime();
            FarTileMesh mesh = null;
            try {
                FarTerrainTile tile = generator.generate(tileX, tileZ, step, edits);
                mesh = FarTileMesh.of(tile, FarTileMesher.mesh(tile, generator.border(tileX, tileZ, step)));
            } catch (RuntimeException failure) {
                LOG.log(Level.SEVERE, "Far tile build failed x=" + tileX + " z=" + tileZ + " step=" + step, failure);
            }
            completed.add(new Built(key, token, mesh, System.nanoTime() - started));
        });
    }

    public Residency residency() {
        return residency;
    }

    public Metrics metrics() {
        return new Metrics(desired.size(), resident.size(), inFlight.size(), queue.size(), builtTiles, builtNanos);
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }

    /**
     * Published far tiles by position and the near area in world blocks, {@code [nearMinX, nearMaxX) x [nearMinZ,
     * nearMaxZ)}, where far terrain must not be drawn. The revision changes whenever either does.
     */
    public record Residency(
            long revision,
            Map<FarTilePos, FarTileMesh> tiles,
            int nearMinX,
            int nearMinZ,
            int nearMaxX,
            int nearMaxZ) {}

    public record Metrics(int desired, int resident, int inFlight, int queued, int built, long buildNanos) {}

    /** {@code mesh} is null when the build failed. */
    private record Built(FarTilePos key, long token, FarTileMesh mesh, long nanos) {}

    private record Planned(long distance, FarTilePos tile) {}
}
