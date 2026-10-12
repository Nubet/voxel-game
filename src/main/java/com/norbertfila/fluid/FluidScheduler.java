package com.norbertfila.fluid;

import com.norbertfila.world.BlockPos;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;

public final class FluidScheduler {
    public static final int WATER_TICK_DELAY = 15;
    public static final int DEFAULT_MAX_UPDATES_PER_TICK = 2_000;
    private final int maxUpdatesPerTick;
    private final Map<BlockPos, FluidTick> scheduled = new HashMap<>();
    // Remove accelerated entries in O(log n), without retaining stale heap nodes.
    private final NavigableSet<FluidTick> queue =
            new TreeSet<>(Comparator.comparingLong(FluidTick::executeAtTick).thenComparingLong(FluidTick::sequence));
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
                updatesThisTick++;
                try {
                    update.accept(next.position());
                } catch (RuntimeException | Error failure) {
                    FluidTick replacement = scheduled.get(next.position());
                    if (replacement == null || replacement.executeAtTick() > next.executeAtTick()) {
                        if (replacement != null) queue.remove(replacement);
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

    private record FluidTick(BlockPos position, long executeAtTick, long sequence) {}
}
