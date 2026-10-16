package com.norbertfila.fluid;

import com.norbertfila.world.BlockChangeResult;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.WaterState;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldBounds;
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
import java.util.TreeMap;
import java.util.TreeSet;

public final class WaterSimulator {
    private static final TickResult IDLE = new TickResult(0, 0, List.of(), List.of());
    private static final Comparator<ChunkPos> CHUNK_ORDER =
            Comparator.comparingInt(ChunkPos::x).thenComparingInt(ChunkPos::y).thenComparingInt(ChunkPos::z);
    private final World world;
    private final WorldEdit edits;
    private final FluidScheduler scheduler;
    private final boolean extended;
    private final Map<BlockPos, FluidSnapshot.Update> deferred = new LinkedHashMap<>();
    private final Map<ChunkPos, Set<BlockPos>> waiting = new HashMap<>();
    // Deferred updates by the chunk they are in, as opposed to the chunks they wait for.
    private final Map<ChunkPos, Set<BlockPos>> deferredIn = new HashMap<>();
    // Updates outside the simulation area, frozen with their remaining delay. Sorted so that waking them schedules
    // same-tick updates in a repeatable order.
    private final Map<ChunkPos, Map<BlockPos, Long>> dormant = new TreeMap<>(CHUNK_ORDER);
    private int dormantCount;
    // Chunks loaded outside the simulation area, by column. Their water is scanned when the area reaches them.
    private final Map<Long, Set<ChunkPos>> unscanned = new HashMap<>();
    private ChunkPos areaCenter;
    private int areaRadius;
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

    /**
     * Limits updates to chunk columns within {@code radius} of {@code center}; updates elsewhere wait, keeping their
     * remaining delay. Until the first call every chunk is simulated.
     */
    public void setSimulationArea(ChunkPos center, int radius, long tick) {
        Objects.requireNonNull(center);
        if (radius < 0) throw new IllegalArgumentException("Simulation radius must not be negative");
        advanceClock(tick);
        if (center.equals(areaCenter) && radius == areaRadius) return;
        ChunkPos previousCenter = areaCenter;
        int previousRadius = areaRadius;
        areaCenter = center;
        areaRadius = radius;
        for (ChunkPos chunk : scheduler.chunks()) {
            if (simulated(chunk)) continue;
            for (var update : scheduler.pendingIn(chunk)) {
                scheduler.cancel(update.position());
                park(update.position(), update.executeAtTick() - tick);
            }
        }
        for (ChunkPos chunk : List.copyOf(dormant.keySet())) {
            if (!simulated(chunk)) continue;
            var updates = dormant.remove(chunk);
            dormantCount -= updates.size();
            updates.forEach((position, remaining) -> scheduler.schedule(position, tick + remaining));
        }
        if (unscanned.isEmpty()) return;
        for (int dz = -radius; dz <= radius; dz++)
            for (int dx = -radius; dx <= radius; dx++) {
                long x = (long) center.x() + dx, z = (long) center.z() + dz;
                if (previousCenter != null
                        && Math.abs(x - previousCenter.x()) <= previousRadius
                        && Math.abs(z - previousCenter.z()) <= previousRadius) continue;
                var chunks = unscanned.remove(column(x, z));
                if (chunks != null) for (ChunkPos chunk : chunks) scan(chunk, tick);
            }
    }

    public void onBlockChanged(BlockPos position, long tick) {
        Objects.requireNonNull(position);
        advanceClock(tick);
        scheduleNeighborhood(position, nextTick(tick), true);
    }

    public TickResult tick(long tick) {
        long nextTick = nextTick(tick);
        advanceClock(tick);
        if (!scheduler.hasDue(tick)) {
            scheduler.processDue(tick, ignored -> {});
            return IDLE;
        }
        Set<ChunkPos> dirty = new TreeSet<>(CHUNK_ORDER), levelDirty = new TreeSet<>(CHUNK_ORDER);
        int[] changed = {0};
        int processed = scheduler.processDue(tick, position -> {
            Set<ChunkPos> missing = new LinkedHashSet<>();
            int next = WaterFlowRules.nextState(world, position, missing, extended);
            if (next == WaterFlowRules.UNAVAILABLE) {
                ChunkPos own = Coordinates.worldToChunk(position);
                // An unloaded chunk without changes is woken by the scan when it loads; parking it would leak.
                if (!world.hasChunk(own) && !world.changes().hasChanges(own)) return;
                defer(new FluidSnapshot.Update(position, FluidScheduler.WATER_TICK_DELAY, List.copyOf(missing)));
                return;
            }
            if (next == WaterFlowRules.BLOCKED) return;
            if (next == WaterState.EMPTY && world.getBlock(position).orElseThrow() != BlockId.WATER) return;
            byte block = next == WaterState.EMPTY ? BlockId.AIR : BlockId.WATER;
            BlockChangeResult result = edits.setBlock(position, block, (byte) next);
            if (!result.changed()) return;
            changed[0]++;
            boolean levelOnly =
                    block == BlockId.WATER && result.previous().orElseThrow() == BlockId.toUnsignedInt(BlockId.WATER);
            (levelOnly ? levelDirty : dirty).addAll(result.dirtyChunks());
            scheduleNeighborhood(position, nextTick, true);
        });
        levelDirty.removeAll(dirty);
        return new TickResult(processed, changed[0], List.copyOf(dirty), List.copyOf(levelDirty));
    }

    public int pendingUpdates() {
        return scheduler.size() + deferred.size() + dormantCount;
    }

    /** Updates waiting for the player to bring their chunk back into the simulation area. */
    public int dormantUpdates() {
        return dormantCount;
    }

    public int deferredUpdates() {
        return deferred.size();
    }

    public Metrics metrics(long tick) {
        return new Metrics(
                tick,
                scheduler.size(),
                deferred.size(),
                waiting.size(),
                scheduler.dueUpdates(tick),
                scheduler.oldestDueAge(tick));
    }

    public record Metrics(long sampledTick, int active, int deferred, int waitingChunks, int due, long oldestDueAge) {}

    public void onChunkChanged(ChunkPos chunk, long tick) {
        Objects.requireNonNull(chunk);
        advanceClock(tick);
        if (!simulated(chunk)) {
            // Outside the area only bookkeeping happens; the scan below waits until the area reaches the chunk.
            if (world.hasChunk(chunk))
                unscanned
                        .computeIfAbsent(column(chunk.x(), chunk.z()), ignored -> new TreeSet<>(CHUNK_ORDER))
                        .add(chunk);
            else {
                Set<ChunkPos> pending = unscanned.get(column(chunk.x(), chunk.z()));
                if (pending != null && pending.remove(chunk) && pending.isEmpty())
                    unscanned.remove(column(chunk.x(), chunk.z()));
            }
        }
        if (!world.hasChunk(chunk)) {
            // Generated water without changes is woken again by the scan below when the chunk returns, so only
            // chunks holding authoritative changes keep their frozen updates. Otherwise exploring would retain
            // every shoreline update ever scheduled.
            boolean keep = world.changes().hasChanges(chunk);
            for (var update : scheduler.pendingIn(chunk)) {
                scheduler.cancel(update.position());
                if (keep)
                    defer(new FluidSnapshot.Update(
                            update.position(), Math.max(0, update.executeAtTick() - tick), List.of(chunk)));
            }
            if (!keep) {
                for (BlockPos position : List.copyOf(deferredIn.getOrDefault(chunk, Set.of())))
                    removeDeferred(deferred.get(position));
                var frozen = dormant.remove(chunk);
                if (frozen != null) dormantCount -= frozen.size();
            }
        } else {
            for (BlockPos position : List.copyOf(waiting.getOrDefault(chunk, Set.of()))) {
                var update = deferred.get(position);
                if (!update.missingChunks().stream().allMatch(world::hasChunk)) continue;
                removeDeferred(update);
                activate(position, Math.addExact(tick, update.remainingTicks()));
            }
        }
        if (simulated(chunk)) scan(chunk, tick);
    }

    /** Schedules every water block in and next to {@code chunk} that may still flow. */
    private void scan(ChunkPos chunk, long tick) {
        // Include the edge halo: support changes can affect a donor across two chunk axes.
        int halo = extended ? 5 : 1;
        // Scan in global y, z, x order because same-tick fluid updates run in scheduling order. Chunks without water
        // are looked up once, so the scan costs almost nothing away from lakes.
        Chunk[] sources = new Chunk[27];
        boolean anyWater = false;
        for (int dy = -1; dy <= 1; dy++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dx = -1; dx <= 1; dx++) {
                    long cx = (long) chunk.x() + dx, cy = (long) chunk.y() + dy, cz = (long) chunk.z() + dz;
                    if (cx < Integer.MIN_VALUE
                            || cx > Integer.MAX_VALUE
                            || cy < Integer.MIN_VALUE
                            || cy > Integer.MAX_VALUE
                            || cz < Integer.MIN_VALUE
                            || cz > Integer.MAX_VALUE) continue;
                    Chunk source = world.getChunk(new ChunkPos((int) cx, (int) cy, (int) cz));
                    if (source != null && source.containsWater()) {
                        sources[(dy + 1) * 9 + (dz + 1) * 3 + dx + 1] = source;
                        anyWater = true;
                    }
                }
        if (!anyWater) return;
        for (int y = -1; y <= Chunk.SIZE; y++) {
            for (int z = -halo; z < Chunk.SIZE + halo; z++) {
                for (int x = -halo; x < Chunk.SIZE + halo; x++) {
                    int dx = Math.floorDiv(x, Chunk.SIZE),
                            dy = Math.floorDiv(y, Chunk.SIZE),
                            dz = Math.floorDiv(z, Chunk.SIZE);
                    Chunk source = sources[(dy + 1) * 9 + (dz + 1) * 3 + dx + 1];
                    if (source == null
                            || source.get(
                                            Math.floorMod(x, Chunk.SIZE),
                                            Math.floorMod(y, Chunk.SIZE),
                                            Math.floorMod(z, Chunk.SIZE))
                                    != BlockId.WATER) continue;
                    long wx = (long) chunk.x() * Chunk.SIZE + x;
                    long wy = (long) chunk.y() * Chunk.SIZE + y;
                    long wz = (long) chunk.z() * Chunk.SIZE + z;
                    BlockPos water = new BlockPos((int) wx, (int) wy, (int) wz);
                    // Open water away from shores and falls would only queue updates that change nothing, enough of
                    // them to back up the update budget while flying over the sea.
                    if (WaterFlowRules.settledSource(world, water)) continue;
                    scheduleNeighborhood(water, nextTick(tick), false);
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
        dormant.values()
                .forEach(chunk -> chunk.forEach((position, remaining) ->
                        updates.add(new FluidSnapshot.Update(position, remaining, List.of()))));
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
            if (missing.isEmpty()) activate(update.position(), Math.addExact(currentTick, update.remainingTicks()));
            else defer(new FluidSnapshot.Update(update.position(), update.remainingTicks(), List.copyOf(missing)));
        }
    }

    private void defer(FluidSnapshot.Update update) {
        deferred.put(update.position(), update);
        deferredIn
                .computeIfAbsent(Coordinates.worldToChunk(update.position()), ignored -> new LinkedHashSet<>())
                .add(update.position());
        for (ChunkPos chunk : update.missingChunks())
            waiting.computeIfAbsent(chunk, ignored -> new LinkedHashSet<>()).add(update.position());
    }

    private void removeDeferred(FluidSnapshot.Update update) {
        deferred.remove(update.position());
        ChunkPos own = Coordinates.worldToChunk(update.position());
        Set<BlockPos> parked = deferredIn.get(own);
        if (parked != null && parked.remove(update.position()) && parked.isEmpty()) deferredIn.remove(own);
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
        if (position == null || !WorldBounds.containsY(position.y())) return;
        var parked = deferred.get(position);
        if (parked != null) {
            if (!dependenciesChanged || !world.hasChunk(Coordinates.worldToChunk(position))) return;
            // New local supply can make the previously missing dependency irrelevant.
            removeDeferred(parked);
        }
        activate(position, tick);
    }

    private void activate(BlockPos position, long executeAtTick) {
        ChunkPos chunk = Coordinates.worldToChunk(position);
        if (simulated(chunk)) scheduler.schedule(position, executeAtTick);
        else park(position, executeAtTick - currentTick);
    }

    private void park(BlockPos position, long remainingTicks) {
        var updates = dormant.computeIfAbsent(Coordinates.worldToChunk(position), ignored -> new LinkedHashMap<>());
        Long previous = updates.get(position);
        if (previous == null) dormantCount++;
        if (previous == null || remainingTicks < previous) updates.put(position, Math.max(0, remainingTicks));
    }

    private static long column(long x, long z) {
        return x << 32 | z & 0xFFFFFFFFL;
    }

    private boolean simulated(ChunkPos chunk) {
        return areaCenter == null
                || Math.abs((long) chunk.x() - areaCenter.x()) <= areaRadius
                        && Math.abs((long) chunk.z() - areaCenter.z()) <= areaRadius;
    }

    private static long nextTick(long tick) {
        if (tick < 0) throw new IllegalArgumentException("Tick must not be negative");
        return Math.addExact(tick, FluidScheduler.WATER_TICK_DELAY);
    }

    /**
     * {@code dirtyChunks} had a block type change and need a full remesh; {@code levelDirtyChunks} only had water
     * levels change, which affects water surfaces but no solid geometry.
     */
    public record TickResult(
            int processedUpdates, int changedBlocks, List<ChunkPos> dirtyChunks, List<ChunkPos> levelDirtyChunks) {
        public TickResult {
            if (processedUpdates < 0 || changedBlocks < 0 || changedBlocks > processedUpdates)
                throw new IllegalArgumentException("Invalid fluid update counts");
            dirtyChunks = List.copyOf(dirtyChunks);
            levelDirtyChunks = List.copyOf(levelDirtyChunks);
        }
    }
}
