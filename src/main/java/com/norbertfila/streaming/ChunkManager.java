package com.norbertfila.streaming;

import com.norbertfila.diagnostics.DurationHistogram;
import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.WorldGenerator;
import com.norbertfila.meshing.BlockNeighborhood;
import com.norbertfila.meshing.ChunkMesh;
import com.norbertfila.meshing.LayeredMesher;
import com.norbertfila.meshing.WaterMesher;
import com.norbertfila.world.BlockChangeResult;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockRegistry;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.ChunkSnapshot;
import com.norbertfila.world.World;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ChunkManager implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ChunkManager.class.getName());
    private static final int WORKER_COUNT = 2;
    private static final int TASK_QUEUE_CAPACITY = 64;
    private static final int READY_QUEUE_CAPACITY = 64;
    public static final int RESULTS_PER_UPDATE = 32;
    /**
     * Chunks stay loaded this many columns past the render distance, so walking back and forth across the edge does
     * not load and evict the same chunks over and over.
     */
    public static final int UNLOAD_MARGIN = 2;

    private static final long GENERATION_INTAKE_NANOS = 2_000_000;
    private static final int[][] FACE_OFFSETS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final ChunkSnapshot EMPTY_SECTION = new ChunkSnapshot(new byte[Chunk.BLOCK_BYTES], 0);
    private static final BlockNeighborhood EMPTY_NEIGHBORHOOD = BlockNeighborhood.centerOnly(EMPTY_SECTION);

    private final World world;
    private final WorldGenerator generator;
    private final LayeredMesher mesher;
    private final ViewDemand viewDemand;
    private int hysteresisRadius;
    private final ChunkScheduler<ChunkData> generationScheduler =
            new ChunkScheduler<>(WORKER_COUNT, TASK_QUEUE_CAPACITY, READY_QUEUE_CAPACITY);
    private final ChunkScheduler<ChunkMesh> meshingScheduler =
            new ChunkScheduler<>(WORKER_COUNT, TASK_QUEUE_CAPACITY, READY_QUEUE_CAPACITY);
    private final Map<ChunkPos, ChunkEntry> entries = new HashMap<>();
    private final Map<ChunkPos, Long> meshTokens = new HashMap<>();
    // Mesh parts (ChunkMesh.SOLID, ChunkMesh.WATER) still to rebuild per chunk, and the parts of the task in flight.
    private final Map<ChunkPos, Integer> pendingRemesh = new HashMap<>();
    private final Map<ChunkPos, Integer> meshingParts = new HashMap<>();
    private final Set<ChunkPos> pendingEdits = new HashSet<>();
    private Set<ChunkPos> collisionChunks = Set.of();
    private boolean collisionDemandChanged;
    // Demanded sections that have no entry yet. Admission walks backlogOrder, a near-first snapshot of this set that is
    // re-sorted only after the set or the center changes, so idle ticks do no demand work.
    private final Set<ChunkPos> generationBacklog = new HashSet<>();
    private List<ChunkPos> backlogOrder = List.of();
    private int backlogCursor;
    private boolean backlogUnsorted, backlogSeeded;
    private final Set<ChunkPos> releasedCollisionChunks = new HashSet<>();
    private ChunkPos evictionCenter;
    private long residencyRevision;
    private Residency residency = Residency.EMPTY;
    // Sections with an uploaded mesh per chunk column, counted over the demanded height range; a column is drawn by
    // near terrain once every section of it is.
    private final Map<Long, Integer> drawnSections = new HashMap<>();
    private long coverageRevision;
    private Coverage coverage = Coverage.EMPTY;
    private final AtomicLong generationTokens = new AtomicLong();
    private final AtomicLong meshTokenSequence = new AtomicLong();
    private Consumer<ChunkPos> chunkLifecycleListener = ignored -> {};
    private long acceptedRemeshes;
    private long generatedResults, meshResults, discardedResults, skippedMeshInputs, waterOnlyMeshes;
    private final Latency generationLatency = new Latency(), visibleLatency = new Latency();

    public Metrics metrics() {
        world.assertOwner();
        return new Metrics(
                generationScheduler.queuedTasks(),
                generationScheduler.activeTasks(),
                generationScheduler.outstandingJobs(),
                meshingScheduler.queuedTasks(),
                meshingScheduler.activeTasks(),
                meshingScheduler.outstandingJobs(),
                pendingRemesh.size(),
                acceptedRemeshes,
                generationScheduler.workNanos(),
                generationScheduler.completedJobs(),
                meshingScheduler.workNanos(),
                meshingScheduler.completedJobs(),
                generatedResults,
                meshResults,
                discardedResults,
                generationScheduler.queueWaitNanos(),
                meshingScheduler.queueWaitNanos(),
                generationLatency.snapshot(),
                visibleLatency.snapshot(),
                generationScheduler.metadataEntries() + meshingScheduler.metadataEntries(),
                skippedMeshInputs,
                waterOnlyMeshes,
                generationScheduler.timings(),
                meshingScheduler.timings(),
                generationLatency.histogram.snapshot(),
                visibleLatency.histogram.snapshot());
    }

    public record Metrics(
            int generationQueued,
            int generationActive,
            int generationOutstanding,
            int meshQueued,
            int meshActive,
            int meshOutstanding,
            int pendingRemesh,
            long acceptedRemeshes,
            long generationNanos,
            long generationCompleted,
            long meshNanos,
            long meshCompleted,
            long generatedResults,
            long meshResults,
            long discardedResults,
            long generationWaitNanos,
            long meshWaitNanos,
            LatencyTotals generationLatency,
            LatencyTotals visibleLatency,
            int schedulerMetadata,
            long skippedMeshInputs,
            long waterOnlyMeshes,
            ChunkScheduler.Timings generationTimings,
            ChunkScheduler.Timings meshTimings,
            DurationHistogram.Snapshot generationDistribution,
            DurationHistogram.Snapshot uploadDistribution) {}

    /** Cumulative request-to-event latency: request to generated data, or request to first GPU upload. */
    public record LatencyTotals(long count, long totalNanos, long maxNanos) {}

    private static final class Latency {
        private final DurationHistogram histogram = new DurationHistogram();
        private long count, total, max;

        void record(long nanos) {
            histogram.record(nanos);
            count++;
            total += nanos;
            max = Math.max(max, nanos);
        }

        LatencyTotals snapshot() {
            return new LatencyTotals(count, total, max);
        }
    }

    public void setChunkLifecycleListener(Consumer<ChunkPos> listener) {
        world.assertOwner();
        chunkLifecycleListener = Objects.requireNonNull(listener);
    }

    public ChunkManager(World world, WorldGenerator generator, ViewDemand viewDemand) {
        this(world, generator, new WaterMesher(), viewDemand);
    }

    public ChunkManager(World world, WorldGenerator generator, LayeredMesher mesher, ViewDemand viewDemand) {
        this(world, generator, mesher, viewDemand, viewDemand.horizontalRadius() + UNLOAD_MARGIN);
    }

    public ChunkManager(World world, WorldGenerator generator, ViewDemand viewDemand, int unloadRadius) {
        this(world, generator, new WaterMesher(), viewDemand, unloadRadius);
    }

    public ChunkManager(
            World world, WorldGenerator generator, LayeredMesher mesher, ViewDemand viewDemand, int unloadRadius) {
        this.world = Objects.requireNonNull(world);
        this.generator = Objects.requireNonNull(generator);
        this.mesher = Objects.requireNonNull(mesher);
        this.viewDemand = Objects.requireNonNull(viewDemand);
        if (unloadRadius < viewDemand.horizontalRadius()) {
            throw new IllegalArgumentException("Unload radius must not be smaller than load radius");
        }
        this.hysteresisRadius = unloadRadius;
    }

    public UpdateResult update(ChunkPos center) {
        world.assertOwner();
        boolean moved = viewDemand.update(center);
        if (!backlogSeeded) {
            viewDemand.requested().forEach(this::addToBacklog);
            backlogSeeded = true;
        } else if (moved) {
            viewDemand.removed().forEach(generationBacklog::remove);
            viewDemand.added().forEach(this::addToBacklog);
        }
        if (moved) backlogUnsorted = true;
        boolean changed = moved || collisionDemandChanged;
        collisionDemandChanged = false;
        int scheduled = 0;
        for (ChunkPos position : collisionChunks) {
            if (scheduleGeneration(position, ChunkPriority.collision())) scheduled++;
        }
        scheduled += admitGeneration();

        List<ChunkPos> evicted = List.of();
        if (changed) {
            boolean square = viewDemand.shape() == ViewDemand.Shape.SQUARE;
            generationScheduler.cancelOutside(center, hysteresisRadius, square, collisionChunks);
            meshingScheduler.cancelOutside(center, hysteresisRadius, square, collisionChunks);
            generationScheduler.discardReadyOutside(center, hysteresisRadius, square, collisionChunks);
            meshingScheduler.discardReadyOutside(center, hysteresisRadius, square, collisionChunks);
            evicted = evictOutsideHysteresis(center);
        }
        processPendingRemeshes();
        return new UpdateResult(changed, scheduled, 0, viewDemand.center(), viewDemand.size(), evicted);
    }

    private void addToBacklog(ChunkPos position) {
        if (!entries.containsKey(position) && !world.hasChunk(position)) generationBacklog.add(position);
    }

    private int admitGeneration() {
        if (generationBacklog.isEmpty() || !generationScheduler.hasCapacity()) return 0;
        if (backlogUnsorted || backlogCursor == backlogOrder.size()) {
            ChunkPos center = viewDemand.center();
            List<ChunkPos> order = new ArrayList<>(generationBacklog);
            order.sort(Comparator.comparingLong(position -> ChunkPriority.distanceSquared(position, center)));
            backlogOrder = order;
            backlogCursor = 0;
            backlogUnsorted = false;
        }
        int admitted = 0;
        while (backlogCursor < backlogOrder.size()) {
            ChunkPos position = backlogOrder.get(backlogCursor);
            if (!generationBacklog.contains(position)) {
                backlogCursor++;
                continue;
            }
            if (scheduleGeneration(position, ChunkPriority.visual(position, viewDemand.center()))) admitted++;
            else if (!entries.containsKey(position) && !world.hasChunk(position)) break;
            generationBacklog.remove(position);
            backlogCursor++;
        }
        return admitted;
    }

    /**
     * Evicts entries that left the hysteresis region. After a move only the columns that left it, and chunks no
     * longer pinned for collision, are examined; after a configuration change every entry is.
     */
    private List<ChunkPos> evictOutsideHysteresis(ChunkPos center) {
        List<ChunkPos> candidates = new ArrayList<>(releasedCollisionChunks);
        releasedCollisionChunks.clear();
        if (evictionCenter == null) candidates.addAll(entries.keySet());
        else if (!evictionCenter.equals(center)) addLeavingColumns(evictionCenter, center, candidates);
        evictionCenter = center;
        List<ChunkPos> evicted = new ArrayList<>();
        for (ChunkPos position : candidates) {
            if (!entries.containsKey(position) || withinHysteresis(position, center)) continue;
            sectionUndrawn(entries.remove(position));
            meshTokens.remove(position);
            meshingParts.remove(position);
            pendingRemesh.remove(position);
            pendingEdits.remove(position);
            generationBacklog.remove(position);
            if (world.removeChunk(position) != null) {
                residencyRevision++;
                chunkLifecycleListener.accept(position);
            }
            evicted.add(position);
        }
        for (ChunkPos position : evicted) {
            for (ChunkPos neighbor : neighbors(position)) {
                if (world.hasChunk(neighbor)) requestRemesh(neighbor);
            }
        }
        return List.copyOf(evicted);
    }

    private void addLeavingColumns(ChunkPos from, ChunkPos to, List<ChunkPos> candidates) {
        for (int dz = -hysteresisRadius; dz <= hysteresisRadius; dz++)
            for (int dx = -hysteresisRadius; dx <= hysteresisRadius; dx++) {
                long x = (long) from.x() + dx, z = (long) from.z() + dz;
                if (!inHysteresisColumn(dx, dz) || inHysteresisColumn(x - to.x(), z - to.z())) continue;
                for (int y = viewDemand.minChunkY(); y <= viewDemand.maxChunkY(); y++)
                    candidates.add(new ChunkPos((int) x, y, (int) z));
            }
    }

    public ProcessResult processResults() {
        return processResults(RESULTS_PER_UPDATE, RESULTS_PER_UPDATE);
    }

    public ProcessResult processResults(int generationBudget, int meshBudget) {
        world.assertOwner();
        if (generationBudget < 0
                || generationBudget > RESULTS_PER_UPDATE
                || meshBudget < 0
                || meshBudget > RESULTS_PER_UPDATE)
            throw new IllegalArgumentException("Result budget is outside the batch limit");
        int generated = 0;
        int discarded = 0;
        long intakeStarted = System.nanoTime();
        for (int index = 0;
                index < generationBudget && (index == 0 || System.nanoTime() - intakeStarted < GENERATION_INTAKE_NANOS);
                index++) {
            ChunkTask.Outcome<ChunkData> outcome = generationScheduler.pollReady();
            if (outcome == null) break;
            ChunkEntry entry = entries.get(outcome.position());
            if (entry == null || entry.generationToken() != outcome.token() || !outcome.succeeded()) {
                if (entry != null && entry.generationToken() == outcome.token() && outcome.failure() != null) {
                    LOG.log(
                            Level.SEVERE,
                            "Chunk generation failed position=" + outcome.position() + " token=" + outcome.token(),
                            outcome.failure());
                    entries.remove(outcome.position());
                    if (viewDemand.contains(outcome.position())) {
                        generationBacklog.add(outcome.position());
                        backlogUnsorted = true;
                    }
                }
                outcome.discard();
                discarded++;
                continue;
            }
            ChunkData data = outcome.result();
            Chunk chunk = data.toChunk();
            world.putChunk(outcome.position(), chunk);
            residencyRevision++;
            chunkLifecycleListener.accept(outcome.position());
            entry.generated(chunk.revision());
            generationLatency.record(System.nanoTime() - entry.requestedAt());
            requestRemesh(outcome.position());
            for (ChunkPos neighbor : neighbors(outcome.position())) {
                if (world.hasChunk(neighbor)) requestRemesh(neighbor);
            }
            generated++;
        }

        List<MeshResult> meshes = new ArrayList<>();
        for (int index = 0; index < meshBudget; index++) {
            ChunkTask.Outcome<ChunkMesh> outcome = meshingScheduler.pollReady();
            if (outcome == null) break;
            ChunkEntry entry = entries.get(outcome.position());
            Long expectedToken = meshTokens.get(outcome.position());
            Chunk chunk = world.getChunk(outcome.position());
            if (entry == null
                    || expectedToken == null
                    || expectedToken != outcome.token()
                    || outcome.revision() != entry.chunkRevision()
                    || chunk == null
                    || outcome.revision() != chunk.revision()
                    || outcome.meshDependencyRevision() != chunk.meshDependencyRevision()
                    || pendingEdits.contains(outcome.position())
                    || !outcome.succeeded()
                    || !entry.readyCpu(outcome.revision())) {
                if (entry != null
                        && expectedToken != null
                        && expectedToken == outcome.token()
                        && outcome.revision() == entry.chunkRevision()) {
                    if (outcome.failure() != null)
                        LOG.log(
                                Level.SEVERE,
                                "Chunk meshing failed position=" + outcome.position() + " revision="
                                        + outcome.revision() + " token=" + outcome.token(),
                                outcome.failure());
                    entry.meshFailed();
                    retryMeshParts(outcome.position());
                }
                outcome.discard();
                discarded++;
                continue;
            }
            if (outcome.result().parts() == ChunkMesh.WATER) waterOnlyMeshes++;
            meshes.add(new MeshResult(
                    outcome.position(),
                    outcome.result(),
                    outcome.revision(),
                    outcome.meshDependencyRevision(),
                    entry.meshRevision(),
                    outcome.token()));
        }
        processPendingRemeshes();
        generatedResults += generated;
        meshResults += meshes.size();
        discardedResults += discarded;
        return new ProcessResult(generated, meshes, discarded);
    }

    public int awaitGeneration(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout);
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        long deadline = System.nanoTime() + timeout.toNanos();
        int generated = 0;
        while (generationScheduler.outstandingJobs() > 0) {
            generated += processResults(RESULTS_PER_UPDATE, 0).generated();
            if (System.nanoTime() >= deadline) throw new IllegalStateException("Chunk generation timed out");
            Thread.sleep(1);
        }
        generated += processResults(RESULTS_PER_UPDATE, 0).generated();
        return generated;
    }

    public boolean markUploaded(MeshResult result) {
        world.assertOwner();
        if (!isCurrentMesh(result)) return false;
        ChunkEntry entry = entries.get(result.position());
        if (!entry.uploaded(result.meshRevision())) return false;
        meshingParts.remove(result.position());
        if (entry.firstUpload()) {
            visibleLatency.record(System.nanoTime() - entry.requestedAt());
            sectionDrawn(result.position());
        }
        processPendingRemesh(result.position());
        return true;
    }

    public boolean rejectUpload(MeshResult result) {
        world.assertOwner();
        if (!matchesPendingUpload(result)) return false;
        ChunkEntry entry = entries.get(result.position());
        if (!entry.meshFailed()) return false;
        retryMeshParts(result.position());
        processPendingRemesh(result.position());
        return true;
    }

    public boolean isCurrentMesh(MeshResult result) {
        world.assertOwner();
        if (!matchesPendingUpload(result)) return false;
        Chunk chunk = world.getChunk(result.position());
        return chunk != null
                && chunk.revision() == result.dataRevision()
                && chunk.meshDependencyRevision() == result.meshDependencyRevision()
                && !pendingEdits.contains(result.position());
    }

    private boolean matchesPendingUpload(MeshResult result) {
        Objects.requireNonNull(result);
        ChunkEntry entry = entries.get(result.position());
        Long expectedToken = meshTokens.get(result.position());
        return entry != null
                && expectedToken != null
                && expectedToken == result.token()
                && entry.state() == ChunkState.READY_CPU
                && entry.meshRevision() == result.meshRevision()
                && entry.chunkRevision() == result.dataRevision();
    }

    public boolean readyForPlayer(ChunkPos position) {
        world.assertOwner();
        return world.hasChunk(Objects.requireNonNull(position));
    }

    public boolean ensureCollisionChunks(List<ChunkPos> required) {
        world.assertOwner();
        if (required.size() > 512) throw new IllegalArgumentException("Collision demand exceeds chunk budget");
        Set<ChunkPos> next = Set.copyOf(required);
        if (!next.equals(collisionChunks)) {
            for (ChunkPos position : collisionChunks)
                if (!next.contains(position)) releasedCollisionChunks.add(position);
            releasedCollisionChunks.removeAll(next);
            collisionChunks = next;
            collisionDemandChanged = true;
            // A chunk already queued for display must not wait behind other visual work once the player needs it.
            for (ChunkPos position : next) {
                ChunkEntry entry = entries.get(position);
                if (entry != null && entry.state() == ChunkState.GENERATING)
                    generationScheduler.promote(position, ChunkPriority.collision());
            }
        }
        for (ChunkPos position : required) scheduleGeneration(position, ChunkPriority.collision());
        return required.stream().allMatch(world::hasChunk);
    }

    private boolean scheduleGeneration(ChunkPos position, ChunkPriority priority) {
        if (world.hasChunk(position) || entries.containsKey(position) || !generationScheduler.hasCapacity())
            return false;
        ChunkEntry entry = new ChunkEntry(position);
        long token = generationTokens.getAndIncrement();
        entry.setGenerationToken(token);
        entry.beginGenerating();
        if (!generationScheduler.submitLatest(
                new ChunkTask<>(position, 0, token, priority, () -> generator.generate(position)))) return false;
        entries.put(position, entry);
        return true;
    }

    public List<ChunkPos> residentPositions() {
        return world.residentPositions();
    }

    public int residentCount() {
        return world.residentCount();
    }

    public int loadRadius() {
        return viewDemand.horizontalRadius();
    }

    public int unloadRadius() {
        return hysteresisRadius;
    }

    public boolean setRenderDistance(int radius) {
        world.assertOwner();
        if (radius < 0) throw new IllegalArgumentException("Render distance must not be negative");
        boolean changed =
                viewDemand.configure(radius, viewDemand.minChunkY(), viewDemand.maxChunkY(), viewDemand.shape());
        if (changed) {
            hysteresisRadius = radius + UNLOAD_MARGIN;
            generationBacklog.clear();
            backlogSeeded = false;
            evictionCenter = null;
        }
        return changed;
    }

    public World world() {
        return world;
    }

    public void requestRemesh(BlockChangeResult change) {
        Objects.requireNonNull(change);
        if (!change.changed()) return;
        requestRemesh(change.dirtyChunks());
    }

    private void sectionDrawn(ChunkPos position) {
        if (position.y() < viewDemand.minChunkY() || position.y() > viewDemand.maxChunkY()) return;
        drawnSections.merge(columnKey(position.x(), position.z()), 1, Integer::sum);
        coverageRevision++;
    }

    private void sectionUndrawn(ChunkEntry entry) {
        ChunkPos position = entry.position();
        if (!entry.hasMesh() || position.y() < viewDemand.minChunkY() || position.y() > viewDemand.maxChunkY()) return;
        drawnSections.computeIfPresent(
                columnKey(position.x(), position.z()), (key, count) -> count > 1 ? count - 1 : null);
        coverageRevision++;
    }

    // Multiplying by an odd constant is a bijection, so keys stay unique while x and z bits spread over the hash.
    private static long columnKey(int x, int z) {
        return ((long) x << 32 | Integer.toUnsignedLong(z)) * 0x9E3779B97F4A7C15L;
    }

    /**
     * Chunk columns around the view center whose every demanded section has an uploaded mesh, so near terrain fully
     * draws them. The same instance is returned until coverage or the center changes.
     */
    public Coverage coverage() {
        world.assertOwner();
        ChunkPos center = viewDemand.center();
        int radius = hysteresisRadius;
        int minX = center.x() - radius, minZ = center.z() - radius, size = 2 * radius + 1;
        if (coverage.revision() == coverageRevision
                && coverage.minChunkX() == minX
                && coverage.minChunkZ() == minZ
                && coverage.size() == size) return coverage;
        int sections = viewDemand.maxChunkY() - viewDemand.minChunkY() + 1;
        long[] bits = new long[(size * size + 63) / 64];
        for (int z = 0; z < size; z++)
            for (int x = 0; x < size; x++) {
                Integer drawn = drawnSections.get(columnKey(minX + x, minZ + z));
                if (drawn != null && drawn >= sections) bits[(z * size + x) >>> 6] |= 1L << (z * size + x);
            }
        coverage = new Coverage(coverageRevision, minX, minZ, size, bits);
        return coverage;
    }

    /**
     * Columns {@code [minChunkX, minChunkX + size) x [minChunkZ, minChunkZ + size)} as bits indexed {@code z * size +
     * x}; a set bit means near terrain draws that whole column. The array is never modified after construction.
     */
    public record Coverage(long revision, int minChunkX, int minChunkZ, int size, long[] bits) {
        public static final Coverage EMPTY = new Coverage(-1, 0, 0, 0, new long[0]);

        public boolean drawn(int chunkX, int chunkZ) {
            long x = (long) chunkX - minChunkX, z = (long) chunkZ - minChunkZ;
            if (x < 0 || z < 0 || x >= size || z >= size) return false;
            int index = (int) (z * size + x);
            return (bits[index >>> 6] & 1L << index) != 0;
        }
    }

    /** The resident chunks and their generation tokens; the same instance is returned until residency changes. */
    public Residency residency() {
        world.assertOwner();
        if (residency.revision() != residencyRevision) {
            Map<ChunkPos, Long> tokens = new HashMap<>();
            entries.forEach((position, entry) -> {
                if (world.hasChunk(position)) tokens.put(position, entry.generationToken());
            });
            residency = new Residency(residencyRevision, Map.copyOf(tokens));
        }
        return residency;
    }

    /**
     * Immutable residency snapshot. The revision changes whenever a chunk is loaded or evicted, and a reload of the
     * same position gets a new token, so a consumer that saw an older snapshot can tell the incarnations apart.
     */
    public record Residency(long revision, Map<ChunkPos, Long> tokens) {
        public static final Residency EMPTY = new Residency(0, Map.of());

        public Residency {
            tokens = Map.copyOf(tokens);
        }
    }

    public void requestRemesh(List<ChunkPos> dirtyChunks) {
        world.assertOwner();
        requestRemesh(dirtyChunks, ChunkMesh.ALL);
    }

    /** Rebuilds only water surfaces, for changes that alter water levels but not block types. */
    public void requestWaterRemesh(List<ChunkPos> dirtyChunks) {
        world.assertOwner();
        requestRemesh(dirtyChunks, ChunkMesh.WATER);
    }

    private void requestRemesh(List<ChunkPos> dirtyChunks, int parts) {
        for (ChunkPos position : dirtyChunks) {
            if (!entries.containsKey(position) || !world.hasChunk(position)) continue;
            pendingRemesh.merge(position, parts, (left, right) -> left | right);
            pendingEdits.add(position);
        }
    }

    private void scheduleMesh(ChunkPos position) {
        ChunkEntry entry = entries.get(position);
        Chunk chunk = world.getChunk(position);
        if (entry == null || chunk == null) return;
        if (!meshingScheduler.hasCapacity()) return;
        ChunkPriority priority = meshPriority(position);
        if (!entry.beginMeshing(chunk.revision())) return;
        // A chunk without any uploaded mesh needs every part, whatever change triggered the request.
        Integer pending = pendingRemesh.remove(position);
        int parts = entry.hasMesh() && pending != null ? pending : ChunkMesh.ALL;
        meshingParts.put(position, parts);
        long token = meshTokenSequence.getAndIncrement();
        meshTokens.put(position, token);
        Callable<ChunkMesh> work;
        if (producesNoFaces(position, chunk)) {
            work = () ->
                    mesher.mesh(EMPTY_SECTION, EMPTY_NEIGHBORHOOD, position.x(), position.y(), position.z(), parts);
            skippedMeshInputs++;
        } else {
            BlockNeighborhood neighborhood = world.blockNeighborhood(position);
            work = () ->
                    mesher.mesh(neighborhood.center(), neighborhood, position.x(), position.y(), position.z(), parts);
        }
        boolean submitted = meshingScheduler.submitLatest(
                new ChunkTask<>(position, chunk.revision(), chunk.meshDependencyRevision(), token, priority, work));
        if (!submitted) {
            meshTokens.remove(position);
            entry.meshFailed();
            retryMeshParts(position);
            LOG.warning("Meshing task rejected position=" + position + " revision=" + chunk.revision());
        } else {
            pendingEdits.remove(position);
            acceptedRemeshes++;
        }
    }

    /**
     * True when the section's mesh is certainly empty: it is all air, or one opaque block enclosed on all six sides by
     * resident sections of opaque blocks. Missing neighbors do not qualify because the mesher draws provisional faces
     * toward them.
     */
    private boolean producesNoFaces(ChunkPos position, Chunk chunk) {
        int block = chunk.uniformBlock();
        if (block == Byte.toUnsignedInt(BlockId.AIR)) return true;
        if (block < 0 || !BlockRegistry.DEFAULT.occludesFullFace((byte) block)) return false;
        for (int[] offset : FACE_OFFSETS) {
            Chunk neighbor = world.getChunk(
                    new ChunkPos(position.x() + offset[0], position.y() + offset[1], position.z() + offset[2]));
            if (neighbor == null) return false;
            int neighborBlock = neighbor.uniformBlock();
            if (neighborBlock < 0 || !BlockRegistry.DEFAULT.occludesFullFace((byte) neighborBlock)) return false;
        }
        return true;
    }

    private ChunkPriority meshPriority(ChunkPos position) {
        ChunkPos center = viewDemand.center();
        if (pendingEdits.contains(position)) return ChunkPriority.edit(position, center);
        ChunkEntry entry = entries.get(position);
        return entry != null && entry.hasMesh()
                ? ChunkPriority.refresh(position, center)
                : ChunkPriority.visual(position, center);
    }

    private void requestRemesh(ChunkPos position) {
        if (entries.containsKey(position) && world.hasChunk(position))
            pendingRemesh.merge(position, ChunkMesh.ALL, (left, right) -> left | right);
    }

    /** Re-queues the parts of a task whose result was discarded, so they are not lost. */
    private void retryMeshParts(ChunkPos position) {
        Integer parts = meshingParts.remove(position);
        if (entries.containsKey(position) && world.hasChunk(position))
            pendingRemesh.merge(position, parts == null ? ChunkMesh.ALL : parts, (left, right) -> left | right);
    }

    private void processPendingRemeshes() {
        if (pendingRemesh.isEmpty() || !meshingScheduler.hasCapacity()) return;
        ChunkPos[] pending = pendingRemesh.keySet().toArray(ChunkPos[]::new);
        // Category, distance and array index packed into one long give the scheduling order with a primitive sort.
        long[] keys = new long[pending.length];
        for (int index = 0; index < pending.length; index++) {
            ChunkPriority priority = meshPriority(pending[index]);
            long distance = Math.min(priority.distanceSquared(), (1L << 34) - 1);
            keys[index] = (long) priority.category().ordinal() << 58 | distance << 24 | index;
        }
        Arrays.sort(keys);
        for (long key : keys) {
            if (!meshingScheduler.hasCapacity()) break;
            processPendingRemesh(pending[(int) (key & 0xFFFFFF)]);
        }
    }

    private void processPendingRemesh(ChunkPos position) {
        if (!pendingRemesh.containsKey(position)) return;
        ChunkEntry entry = entries.get(position);
        Chunk chunk = world.getChunk(position);
        if (entry == null || chunk == null) {
            pendingRemesh.remove(position);
            pendingEdits.remove(position);
            return;
        }
        if (entry.state() == ChunkState.GENERATED || entry.state() == ChunkState.RESIDENT_GPU) scheduleMesh(position);
    }

    private List<ChunkPos> neighbors(ChunkPos position) {
        return World.meshNeighbors(position);
    }

    private boolean withinHysteresis(ChunkPos position, ChunkPos center) {
        if (collisionChunks.contains(position)) return true;
        if (position.y() < viewDemand.minChunkY() || position.y() > viewDemand.maxChunkY()) return false;
        return inHysteresisColumn((long) position.x() - center.x(), (long) position.z() - center.z());
    }

    private boolean inHysteresisColumn(long dx, long dz) {
        return viewDemand.shape() == ViewDemand.Shape.SQUARE
                ? Math.abs(dx) <= hysteresisRadius && Math.abs(dz) <= hysteresisRadius
                : dx * dx + dz * dz <= (long) hysteresisRadius * hysteresisRadius;
    }

    @Override
    public void close() {
        generationScheduler.close();
        meshingScheduler.close();
    }

    public record UpdateResult(
            boolean changed, int scheduled, int generated, ChunkPos center, int requested, List<ChunkPos> evicted) {}

    public record ProcessResult(int generated, List<MeshResult> meshes, int discarded) {}

    public record MeshResult(
            ChunkPos position,
            ChunkMesh mesh,
            long dataRevision,
            long meshDependencyRevision,
            long meshRevision,
            long token) {
        public MeshResult {
            Objects.requireNonNull(position);
            Objects.requireNonNull(mesh);
        }

        public long bytes() {
            return mesh.bytes();
        }
    }
}
