package com.norbertfila.fluid;

import com.norbertfila.world.BlockChangeResult;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class WaterSimulator {
    private static final Comparator<ChunkPos> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z);
    private final World world;
    private final WorldEdit edits;
    private final FluidScheduler scheduler;

    public WaterSimulator(World world, int farTileSize) {
        this(world, farTileSize, FluidScheduler.DEFAULT_MAX_UPDATES_PER_TICK);
    }

    public WaterSimulator(World world, int farTileSize, int maxUpdatesPerTick) {
        this.world = Objects.requireNonNull(world);
        edits = new WorldEdit(world, farTileSize);
        scheduler = new FluidScheduler(maxUpdatesPerTick);
    }

    public void onBlockChanged(BlockPos position, long tick) {
        Objects.requireNonNull(position);
        scheduleNeighborhood(position, nextTick(tick));
    }

    public TickResult tick(long tick) {
        long nextTick = nextTick(tick);
        Set<ChunkPos> dirty = new TreeSet<>(CHUNK_ORDER);
        int[] changed = {0};
        int processed = scheduler.processDue(tick, position -> {
            int next = WaterFlowRules.nextState(world, position);
            if (next == WaterFlowRules.UNAVAILABLE) {
                // shortcut: retry unknown dependencies; phase04 replaces polling with chunk-lifecycle wakeups.
                scheduler.schedule(position, nextTick);
                return;
            }
            if (next == WaterFlowRules.BLOCKED) return;
            byte block = next == WaterState.EMPTY ? BlockId.AIR : BlockId.WATER;
            BlockChangeResult result = edits.setBlock(position, block, (byte) next);
            if (!result.changed()) return;
            changed[0]++;
            dirty.addAll(result.dirtyChunks());
            scheduleNeighborhood(position, nextTick);
        });
        return new TickResult(processed, changed[0], List.copyOf(dirty));
    }

    public int pendingUpdates() {
        return scheduler.size();
    }

    private void scheduleNeighborhood(BlockPos position, long tick) {
        schedule(position, tick);
        schedule(WaterFlowRules.offset(position, 0, -1, 0), tick);
        BlockPos above = WaterFlowRules.offset(position, 0, 1, 0);
        schedule(above, tick);
        for (int direction = 0; direction < WaterFlowRules.DX.length; direction++) {
            int dx = WaterFlowRules.DX[direction], dz = WaterFlowRules.DZ[direction];
            schedule(WaterFlowRules.offset(position, dx, 0, dz), tick);
            // Changing support changes the lateral feed from the block above, even if its state is unchanged.
            schedule(WaterFlowRules.offset(above, dx, 0, dz), tick);
        }
    }

    private void schedule(BlockPos position, long tick) {
        if (position != null) scheduler.schedule(position, tick);
    }

    private static long nextTick(long tick) {
        if (tick < 0) throw new IllegalArgumentException("Tick must not be negative");
        return Math.addExact(tick, FluidScheduler.WATER_TICK_DELAY);
    }

    public record TickResult(int processedUpdates, int changedBlocks, List<ChunkPos> dirtyChunks) {
        public TickResult {
            if (processedUpdates < 0 || changedBlocks < 0 || changedBlocks > processedUpdates)
                throw new IllegalArgumentException("Invalid fluid update counts");
            dirtyChunks = List.copyOf(dirtyChunks);
        }
    }
}
