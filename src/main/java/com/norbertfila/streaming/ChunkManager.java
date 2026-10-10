package com.norbertfila.streaming;

import com.norbertfila.generation.ChunkData;
import com.norbertfila.generation.WorldGenerator;
import com.norbertfila.meshing.BlockNeighborhood;
import com.norbertfila.meshing.ChunkMesher;
import com.norbertfila.meshing.GreedyMesher;
import com.norbertfila.meshing.MeshData;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.World;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ChunkManager implements AutoCloseable {
    private static final int WORKER_COUNT = 2;
    private static final int TASK_QUEUE_CAPACITY = 64;
    private static final int READY_QUEUE_CAPACITY = 64;
    private static final int RESULTS_PER_UPDATE = 32;

    private final World world;
    private final WorldGenerator generator;
    private final ChunkMesher mesher;
    private final ViewDemand viewDemand;
    private final int hysteresisRadius;
    private final ChunkScheduler<ChunkData> generationScheduler =
            new ChunkScheduler<>(WORKER_COUNT, TASK_QUEUE_CAPACITY, READY_QUEUE_CAPACITY);
    private final ChunkScheduler<MeshData> meshingScheduler =
            new ChunkScheduler<>(WORKER_COUNT, TASK_QUEUE_CAPACITY, READY_QUEUE_CAPACITY);
    private final Map<ChunkPos, ChunkEntry> entries = new HashMap<>();
    private final Map<ChunkPos, Long> meshTokens = new HashMap<>();
    private final AtomicLong generationTokens = new AtomicLong();
    private final AtomicLong meshTokenSequence = new AtomicLong();

    public ChunkManager(World world, WorldGenerator generator, ViewDemand viewDemand) {
        this(world, generator, new GreedyMesher(), viewDemand);
    }

    public ChunkManager(World world, WorldGenerator generator, ChunkMesher mesher, ViewDemand viewDemand) {
        this(world, generator, mesher, viewDemand, viewDemand.horizontalRadius() + 1);
    }

    public ChunkManager(World world, WorldGenerator generator, ViewDemand viewDemand, int unloadRadius) {
        this(world, generator, new GreedyMesher(), viewDemand, unloadRadius);
    }

    public ChunkManager(
            World world, WorldGenerator generator, ChunkMesher mesher, ViewDemand viewDemand, int unloadRadius) {
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
        boolean changed = viewDemand.update(center);
        int scheduled = 0;
        for (ChunkPos position : viewDemand.requested()) {
            if (world.hasChunk(position) || entries.containsKey(position)) continue;
            ChunkEntry entry = new ChunkEntry(position);
            long token = generationTokens.getAndIncrement();
            entry.setGenerationToken(token);
            entry.beginGenerating();
            if (generationScheduler.submitLatest(new ChunkTask<>(
                    position, 0, token, ChunkPriority.unprioritized(), () -> generator.generate(position)))) {
                entries.put(position, entry);
                scheduled++;
            }
        }

        List<ChunkPos> evicted = new ArrayList<>();
        if (changed) {
            boolean square = viewDemand.shape() == ViewDemand.Shape.SQUARE;
            generationScheduler.cancelOutside(center, hysteresisRadius, square);
            meshingScheduler.cancelOutside(center, hysteresisRadius, square);
            generationScheduler.discardReadyOutside(center, hysteresisRadius, square);
            meshingScheduler.discardReadyOutside(center, hysteresisRadius, square);
            for (ChunkPos position : List.copyOf(entries.keySet())) {
                if (withinHysteresis(position, center)) continue;
                entries.remove(position);
                meshTokens.remove(position);
                world.removeChunk(position);
                evicted.add(position);
            }
        }
        return new UpdateResult(
                changed,
                scheduled,
                0,
                viewDemand.center(),
                viewDemand.requested().size(),
                List.copyOf(evicted));
    }

    public ProcessResult processResults() {
        int generated = 0;
        int discarded = 0;
        for (int index = 0; index < RESULTS_PER_UPDATE; index++) {
            ChunkTask.Outcome<ChunkData> outcome = generationScheduler.pollReady();
            if (outcome == null) break;
            ChunkEntry entry = entries.get(outcome.position());
            if (entry == null || entry.generationToken() != outcome.token() || !outcome.succeeded()) {
                outcome.discard();
                discarded++;
                continue;
            }
            ChunkData data = outcome.result();
            world.putChunk(outcome.position(), Chunk.fromBlocks(data.blocks()));
            entry.generated(0);
            scheduleMesh(outcome.position());
            for (ChunkPos neighbor : neighbors(outcome.position())) {
                if (world.hasChunk(neighbor)) scheduleMesh(neighbor);
            }
            generated++;
        }

        List<MeshResult> meshes = new ArrayList<>();
        for (int index = 0; index < RESULTS_PER_UPDATE; index++) {
            ChunkTask.Outcome<MeshData> outcome = meshingScheduler.pollReady();
            if (outcome == null) break;
            ChunkEntry entry = entries.get(outcome.position());
            Long expectedToken = meshTokens.get(outcome.position());
            if (entry == null
                    || expectedToken == null
                    || expectedToken != outcome.token()
                    || outcome.revision() != entry.chunkRevision()
                    || !outcome.succeeded()
                    || !entry.readyCpu(outcome.revision())) {
                outcome.discard();
                discarded++;
                continue;
            }
            meshes.add(new MeshResult(outcome.position(), outcome.result(), entry.meshRevision(), outcome.token()));
        }
        return new ProcessResult(generated, meshes, discarded);
    }

    public int awaitGeneration(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout);
        if (timeout.isNegative()) throw new IllegalArgumentException("Timeout must not be negative");
        long deadline = System.nanoTime() + timeout.toNanos();
        int generated = 0;
        while (generationScheduler.outstandingJobs() > 0) {
            generated += processResults().generated();
            if (System.nanoTime() >= deadline) throw new IllegalStateException("Chunk generation timed out");
            Thread.sleep(1);
        }
        generated += processResults().generated();
        return generated;
    }

    public void markUploaded(MeshResult result) {
        Objects.requireNonNull(result);
        ChunkEntry entry = entries.get(result.position());
        if (entry == null || !entry.uploaded(result.meshRevision())) {
            throw new IllegalStateException("Mesh result is no longer current: " + result.position());
        }
    }

    public boolean readyForPlayer(ChunkPos position) {
        return world.hasChunk(Objects.requireNonNull(position));
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

    public World world() {
        return world;
    }

    private void scheduleMesh(ChunkPos position) {
        ChunkEntry entry = entries.get(position);
        Chunk chunk = world.getChunk(position);
        if (entry == null || chunk == null || !entry.beginMeshing(chunk.revision())) return;
        long token = meshTokenSequence.getAndIncrement();
        meshTokens.put(position, token);
        BlockNeighborhood neighborhood = world.blockNeighborhood(position);
        meshingScheduler.submitLatest(new ChunkTask<>(
                position,
                chunk.revision(),
                token,
                ChunkPriority.unprioritized(),
                () -> mesher.mesh(neighborhood.center(), neighborhood, position.x(), position.y(), position.z())));
    }

    private List<ChunkPos> neighbors(ChunkPos position) {
        return List.of(
                new ChunkPos(position.x() + 1, position.y(), position.z()),
                new ChunkPos(position.x() - 1, position.y(), position.z()),
                new ChunkPos(position.x(), position.y() + 1, position.z()),
                new ChunkPos(position.x(), position.y() - 1, position.z()),
                new ChunkPos(position.x(), position.y(), position.z() + 1),
                new ChunkPos(position.x(), position.y(), position.z() - 1));
    }

    private boolean withinHysteresis(ChunkPos position, ChunkPos center) {
        if (position.y() < viewDemand.minChunkY() || position.y() > viewDemand.maxChunkY()) return false;
        int dx = position.x() - center.x();
        int dz = position.z() - center.z();
        return viewDemand.shape() == ViewDemand.Shape.SQUARE
                ? Math.abs(dx) <= hysteresisRadius && Math.abs(dz) <= hysteresisRadius
                : (long) dx * dx + (long) dz * dz <= (long) hysteresisRadius * hysteresisRadius;
    }

    @Override
    public void close() {
        generationScheduler.close();
        meshingScheduler.close();
    }

    public record UpdateResult(
            boolean changed, int scheduled, int generated, ChunkPos center, int requested, List<ChunkPos> evicted) {}

    public record ProcessResult(int generated, List<MeshResult> meshes, int discarded) {}

    public record MeshResult(ChunkPos position, MeshData mesh, long meshRevision, long token) {}
}
