package com.norbertfila.fluid;

import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class FluidScheduler {
    public static final int WATER_TICK_DELAY = 15;
    public static final int DEFAULT_MAX_UPDATES_PER_TICK = 2_000;
    private final int maxUpdatesPerTick;
    private static final Comparator<FluidTick> ORDER =
            Comparator.comparingLong(FluidTick::executeAtTick).thenComparingLong(FluidTick::sequence);
    private final Map<BlockPos, FluidTick> scheduled = new HashMap<>();
    // Remove accelerated entries in O(log n), without retaining stale heap nodes.
    private final NavigableSet<FluidTick> queue = new TreeSet<>(ORDER);
    // Scheduled positions per chunk, so unloading a chunk finds its updates without scanning the whole queue.
    private final Map<ChunkPos, Set<BlockPos>> byChunk = new HashMap<>();
    private long sequence;
    private long currentTick = -1;
    private int updatesThisTick;
    private boolean processing;

    public FluidScheduler() {
        this(DEFAULT_MAX_UPDATES_PER_TICK);
    }

    public FluidScheduler(int maxUpdatesPerTick) {
        if (maxUpdatesPerTick <= 0) throw new IllegalArgumentException("Fluid update budget must be positive");
        this.maxUpdatesPerTick = maxUpdatesPerTick;
    }

    public boolean scheduleWater(BlockPos position, long currentTick) {
        if (currentTick < 0) throw new IllegalArgumentException("Tick must not be negative");
        return schedule(position, Math.addExact(currentTick, WATER_TICK_DELAY));
    }

    public boolean schedule(BlockPos position, long executeAtTick) {
        Objects.requireNonNull(position);
        if (executeAtTick < 0) throw new IllegalArgumentException("Tick must not be negative");
        FluidTick previous = scheduled.get(position);
        if (previous != null && previous.executeAtTick() <= executeAtTick) return false;
        long nextSequence = Math.incrementExact(sequence);
        FluidTick tick = new FluidTick(position, executeAtTick, sequence);
        if (previous != null) queue.remove(previous);
        else index(position);
        scheduled.put(position, tick);
        queue.add(tick);
        sequence = nextSequence;
        return true;
    }

    public int processDue(long tick, Consumer<BlockPos> update) {
        Objects.requireNonNull(update);
        if (tick < 0 || tick < currentTick) throw new IllegalArgumentException("Fluid clock must not go backwards");
        if (processing) throw new IllegalStateException("Fluid updates must not be processed recursively");
        if (tick != currentTick) {
            currentTick = tick;
            updatesThisTick = 0;
        }
        int processed = 0;
        processing = true;
        try {
            while (updatesThisTick < maxUpdatesPerTick && !queue.isEmpty()) {
                FluidTick next = queue.first();
                if (next.executeAtTick() > tick) break;
                queue.pollFirst();
                scheduled.remove(next.position());
                unindex(next.position());
                updatesThisTick++;
                try {
                    update.accept(next.position());
                } catch (RuntimeException | Error failure) {
                    FluidTick replacement = scheduled.get(next.position());
                    if (replacement == null || replacement.executeAtTick() > next.executeAtTick()) {
                        if (replacement != null) queue.remove(replacement);
                        else index(next.position());
                        scheduled.put(next.position(), next);
                        queue.add(next);
                    }
                    throw failure;
                }
                processed++;
            }
        } finally {
            processing = false;
        }
        return processed;
    }

    public int size() {
        return scheduled.size();
    }

    public boolean hasDue(long tick) {
        return !queue.isEmpty() && queue.first().executeAtTick() <= tick;
    }

    public long oldestDueAge(long tick) {
        return queue.isEmpty() ? 0 : Math.max(0, tick - queue.first().executeAtTick());
    }

    public int dueUpdates(long tick) {
        int count = 0;
        for (var entry : queue) {
            if (entry.executeAtTick() > tick) break;
            count++;
        }
        return count;
    }

    public List<PendingUpdate> pending() {
        return queue.stream()
                .map(t -> new PendingUpdate(t.position(), t.executeAtTick()))
                .toList();
    }

    /** Chunks holding at least one pending update. */
    public List<ChunkPos> chunks() {
        return List.copyOf(byChunk.keySet());
    }

    /** Pending updates inside one chunk, in execution order. */
    public List<PendingUpdate> pendingIn(ChunkPos chunk) {
        Set<BlockPos> positions = byChunk.get(Objects.requireNonNull(chunk));
        if (positions == null) return List.of();
        List<FluidTick> ticks = new ArrayList<>(positions.size());
        for (BlockPos position : positions) ticks.add(scheduled.get(position));
        ticks.sort(ORDER);
        List<PendingUpdate> result = new ArrayList<>(ticks.size());
        for (FluidTick tick : ticks) result.add(new PendingUpdate(tick.position(), tick.executeAtTick()));
        return result;
    }

    public void cancel(BlockPos position) {
        FluidTick previous = scheduled.remove(position);
        if (previous == null) return;
        queue.remove(previous);
        unindex(position);
    }

    private void index(BlockPos position) {
        byChunk.computeIfAbsent(Coordinates.worldToChunk(position), ignored -> new HashSet<>())
                .add(position);
    }

    private void unindex(BlockPos position) {
        ChunkPos chunk = Coordinates.worldToChunk(position);
        Set<BlockPos> positions = byChunk.get(chunk);
        if (positions != null && positions.remove(position) && positions.isEmpty()) byChunk.remove(chunk);
    }

    public record PendingUpdate(BlockPos position, long executeAtTick) {}

    private record FluidTick(BlockPos position, long executeAtTick, long sequence) {}
}
