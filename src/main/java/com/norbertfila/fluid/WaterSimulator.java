package com.norbertfila.fluid;

import com.norbertfila.world.BlockChangeResult;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldEdit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class WaterSimulator {
    private static final Comparator<ChunkPos> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z);
    private final World world;
    private final WorldEdit edits;
    private final FluidScheduler scheduler;
    private final boolean extended;
    private final Map<BlockPos, FluidSnapshot.Update> deferred = new LinkedHashMap<>();
    private final Map<ChunkPos, Set<BlockPos>> waiting = new HashMap<>();
    private long currentTick;

    public WaterSimulator(World world, int farTileSize) {
        this(world, farTileSize, FluidScheduler.DEFAULT_MAX_UPDATES_PER_TICK);
    }

    public WaterSimulator(World world, int farTileSize, int maxUpdatesPerTick) {
        this(world, farTileSize, maxUpdatesPerTick, false);
    }

    public WaterSimulator(World world, int farTileSize, int maxUpdatesPerTick, boolean extended) {
        this.world = Objects.requireNonNull(world);
        edits = new WorldEdit(world, farTileSize);
        scheduler = new FluidScheduler(maxUpdatesPerTick);
        this.extended = extended;
    }

    public void onBlockChanged(BlockPos position, long tick) {
        Objects.requireNonNull(position);
        advanceClock(tick);
        scheduleNeighborhood(position, nextTick(tick), true);
    }

    public TickResult tick(long tick) {
        long nextTick = nextTick(tick);
        advanceClock(tick);
        Set<ChunkPos> dirty = new TreeSet<>(CHUNK_ORDER);
        int[] changed = {0};
        int processed = scheduler.processDue(tick, position -> {
            Set<ChunkPos> missing = new LinkedHashSet<>();
            int next = WaterFlowRules.nextState(world, position, missing, extended);
            if (next == WaterFlowRules.UNAVAILABLE) {
                defer(new FluidSnapshot.Update(position, FluidScheduler.WATER_TICK_DELAY, List.copyOf(missing)));
                return;
            }
            if (next == WaterFlowRules.BLOCKED) return;
            byte block = next == WaterState.EMPTY ? BlockId.AIR : BlockId.WATER;
            BlockChangeResult result = edits.setBlock(position, block, (byte) next);
            if (!result.changed()) return;
            changed[0]++;
            dirty.addAll(result.dirtyChunks());
            scheduleNeighborhood(position, nextTick, true);
        });
        return new TickResult(processed, changed[0], List.copyOf(dirty));
    }

    public int pendingUpdates() {
        return scheduler.size() + deferred.size();
    }

    public int deferredUpdates() {
        return deferred.size();
    }

    public void onChunkChanged(ChunkPos chunk, long tick) {
        Objects.requireNonNull(chunk);
        advanceClock(tick);
        if (!world.hasChunk(chunk)) {
            for (var update : scheduler.pending()) {
                if (!Coordinates.worldToChunk(update.position()).equals(chunk)) continue;
                scheduler.cancel(update.position());
                defer(new FluidSnapshot.Update(
                        update.position(), Math.max(0, update.executeAtTick() - tick), List.of(chunk)));
            }
        } else {
            for (BlockPos position : List.copyOf(waiting.getOrDefault(chunk, Set.of()))) {
                var update = deferred.get(position);
                if (!update.missingChunks().stream().allMatch(world::hasChunk)) continue;
                removeDeferred(update);
                scheduler.schedule(position, Math.addExact(tick, update.remainingTicks()));
            }
        }
        // Include the edge halo: support changes can affect a donor across two chunk axes.
        int halo = extended ? 5 : 1;
        for (int y = -1; y <= Chunk.SIZE; y++) {
            for (int z = -halo; z < Chunk.SIZE + halo; z++) {
                for (int x = -halo; x < Chunk.SIZE + halo; x++) {
                    long wx = (long) chunk.x() * Chunk.SIZE + x;
                    long wy = (long) chunk.y() * Chunk.SIZE + y;
                    long wz = (long) chunk.z() * Chunk.SIZE + z;
                    if (wx < Integer.MIN_VALUE
                            || wx > Integer.MAX_VALUE
                            || wy < Integer.MIN_VALUE
                            || wy > Integer.MAX_VALUE
                            || wz < Integer.MIN_VALUE
                            || wz > Integer.MAX_VALUE) continue;
                    BlockPos position = new BlockPos((int) wx, (int) wy, (int) wz);
                    if (world.hasChunk(Coordinates.worldToChunk(position))
                            && world.getBlock(position).orElseThrow() == BlockId.WATER)
                        scheduleNeighborhood(position, nextTick(tick), false);
                }
            }
        }
    }

    public FluidSnapshot snapshot(long tick) {
        advanceClock(tick);
        var updates = new ArrayList<FluidSnapshot.Update>();
        for (var update : scheduler.pending())
            updates.add(
                    new FluidSnapshot.Update(update.position(), Math.max(0, update.executeAtTick() - tick), List.of()));
        updates.addAll(deferred.values());
        return new FluidSnapshot(tick, updates);
    }

    public void restore(FluidSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (pendingUpdates() != 0 || currentTick != 0)
            throw new IllegalStateException("Restore requires a fresh simulator");
        currentTick = snapshot.tick();
        for (var update : snapshot.updates()) {
            var missing = new LinkedHashSet<ChunkPos>();
            for (ChunkPos chunk : update.missingChunks()) if (!world.hasChunk(chunk)) missing.add(chunk);
            ChunkPos ownChunk = Coordinates.worldToChunk(update.position());
            if (!world.hasChunk(ownChunk)) {
                missing.clear();
                missing.add(ownChunk);
            }
            if (missing.isEmpty())
                scheduler.schedule(update.position(), Math.addExact(currentTick, update.remainingTicks()));
            else defer(new FluidSnapshot.Update(update.position(), update.remainingTicks(), List.copyOf(missing)));
        }
    }

    private void defer(FluidSnapshot.Update update) {
        deferred.put(update.position(), update);
        for (ChunkPos chunk : update.missingChunks())
            waiting.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(update.position());
    }

    private void removeDeferred(FluidSnapshot.Update update) {
        deferred.remove(update.position());
        for (ChunkPos chunk : update.missingChunks()) {
            Set<BlockPos> positions = waiting.get(chunk);
            positions.remove(update.position());
            if (positions.isEmpty()) waiting.remove(chunk);
        }
    }

    private void advanceClock(long tick) {
        nextTick(tick);
        if (tick < currentTick) throw new IllegalArgumentException("Fluid clock must not go backwards");
        currentTick = tick;
    }

    private void scheduleNeighborhood(BlockPos position, long tick, boolean dependenciesChanged) {
        schedule(position, tick, dependenciesChanged);
        schedule(WaterFlowRules.offset(position, 0, -1, 0), tick, dependenciesChanged);
        BlockPos above = WaterFlowRules.offset(position, 0, 1, 0);
        schedule(above, tick, dependenciesChanged);
        for (int direction = 0; direction < WaterFlowRules.DX.length; direction++) {
            int dx = WaterFlowRules.DX[direction], dz = WaterFlowRules.DZ[direction];
            schedule(WaterFlowRules.offset(position, dx, 0, dz), tick, dependenciesChanged);
            // Changing support changes the lateral feed from the block above, even if its state is unchanged.
            schedule(WaterFlowRules.offset(above, dx, 0, dz), tick, dependenciesChanged);
        }
        if (extended && dependenciesChanged) {
            // A terrain edit can change a donor's best route four steps away, and its recipients one step farther.
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 5) continue;
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos affected = WaterFlowRules.offset(position, dx, dy, dz);
                        if (affected != null
                                && world.hasChunk(Coordinates.worldToChunk(affected))
                                && world.getBlock(affected).orElseThrow() == BlockId.WATER) {
                            schedule(affected, tick, dependenciesChanged);
                            for (int direction = 0; direction < WaterFlowRules.DX.length; direction++)
                                schedule(
                                        WaterFlowRules.offset(
                                                affected,
                                                WaterFlowRules.DX[direction],
                                                0,
                                                WaterFlowRules.DZ[direction]),
                                        tick,
                                        dependenciesChanged);
                        }
                    }
                }
            }
        }
    }

    private void schedule(BlockPos position, long tick, boolean dependenciesChanged) {
        if (position == null) return;
        var parked = deferred.get(position);
        if (parked != null) {
            if (!dependenciesChanged || !world.hasChunk(Coordinates.worldToChunk(position))) return;
            // New local supply can make the previously missing dependency irrelevant.
            removeDeferred(parked);
        }
        scheduler.schedule(position, tick);
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
