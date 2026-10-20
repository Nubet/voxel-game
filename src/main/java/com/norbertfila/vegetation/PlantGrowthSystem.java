package com.norbertfila.vegetation;

import com.norbertfila.world.*;
import java.util.*;

public final class PlantGrowthSystem {
    public static final int DEFAULT_BUDGET = 256;
    private static final Comparator<BlockPos> POSITION_ORDER =
            Comparator.comparingInt(BlockPos::x).thenComparingInt(BlockPos::y).thenComparingInt(BlockPos::z);
    private static final Comparator<Scheduled> SCHEDULE_ORDER =
            Comparator.comparingLong(Scheduled::tick).thenComparing(Scheduled::root, POSITION_ORDER);
    private final World world;
    private final WorldEdit edits;
    private final long seed;
    private final GrowthRule rule;
    private final int budget;
    private final Map<BlockPos, Entry> plants = new HashMap<>();
    private final Map<ChunkPos, Set<BlockPos>> dependents = new HashMap<>();
    private final TreeSet<Scheduled> scheduled = new TreeSet<>(SCHEDULE_ORDER);
    private final Set<ChunkPos> scans = new LinkedHashSet<>();
    private final Map<BlockPos, Long> candidates = new LinkedHashMap<>();
    private final Set<BlockPos> checks = new LinkedHashSet<>();
    private long tick;
    private boolean growing;

    public PlantGrowthSystem(World world, int farTileSize, long seed) {
        this(world, farTileSize, seed, new SugarCaneGrowthRule(), DEFAULT_BUDGET);
    }

    public PlantGrowthSystem(World world, int farTileSize, long seed, GrowthRule rule, int budget) {
        this.world = Objects.requireNonNull(world);
        edits = new WorldEdit(world, farTileSize);
        this.seed = seed;
        this.rule = Objects.requireNonNull(rule);
        if (budget < 1) throw new IllegalArgumentException("Growth budget must be positive");
        this.budget = budget;
    }

    public void advanceClock(long value) {
        world.assertOwner();
        if (value < tick) throw new IllegalArgumentException("Plant clock cannot go backwards");
        tick = value;
    }

    public void onBlockChanged(BlockMutation change) {
        world.assertOwner();
        if (growing || change.previous().orElse(-1) == Byte.toUnsignedInt(change.block())) return;
        var position = change.position();
        for (var root : List.copyOf(dependents.getOrDefault(Coordinates.worldToChunk(position), Set.of()))) {
            long dx = (long) position.x() - root.x(), dy = (long) position.y() - root.y();
            long dz = (long) position.z() - root.z();
            if (!(dx == 0 && dz == 0 && dy >= -1 && dy <= 2 || dy == -1 && Math.abs(dx) + Math.abs(dz) == 1)) continue;
            if (position.equals(root) && change.block() != rule.blockId()) remove(root);
            else {
                var entry = plants.get(root);
                cancel(entry);
                entry.state = next(root, tick, Math.incrementExact(entry.state.cycle()));
                checks.add(root);
            }
        }
        if (change.block() == rule.blockId()) discover(position, tick);
        addCandidate(position.x(), (long) position.y() + 1, position.z());
        if (change.block() == BlockId.WATER || change.previous().orElse(-1) == BlockId.WATER)
            for (int[] side : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}})
                addCandidate((long) position.x() + side[0], (long) position.y() + 1, (long) position.z() + side[1]);
    }

    private void addCandidate(long x, long y, long z) {
        if (x < Integer.MIN_VALUE
                || x > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE
                || z > Integer.MAX_VALUE
                || y < Integer.MIN_VALUE
                || y > Integer.MAX_VALUE
                || !WorldBounds.containsY((int) y)) return;
        var position = new BlockPos((int) x, (int) y, (int) z);
        if (world.hasChunk(Coordinates.worldToChunk(position))
                && world.getBlock(position).orElse(-1) == Byte.toUnsignedInt(rule.blockId()))
            candidates.putIfAbsent(position, tick);
    }

    public void onChunkChanged(ChunkPos chunk) {
        world.assertOwner();
        if (world.hasChunk(chunk)) {
            scans.add(chunk);
            var above = new ChunkPos(chunk.x(), chunk.y() + 1, chunk.z());
            if (world.hasChunk(above)) scans.add(above);
        } else scans.remove(chunk);
        for (var root : dependents.getOrDefault(chunk, Set.of())) {
            cancel(plants.get(root));
            checks.add(root);
        }
    }

    public TickResult update(long tick, boolean allowGrowth) {
        advanceClock(tick);
        for (int i = 0; i < 4 && !scans.isEmpty(); i++) scan(take(scans));
        for (int i = 0; i < budget && !candidates.isEmpty(); i++) discoverCandidate();
        for (int i = 0; i < budget && !checks.isEmpty(); i++) refresh(take(checks));
        var dirty = new LinkedHashSet<ChunkPos>();
        int processed = 0, changed = 0;
        while (allowGrowth
                && processed < budget
                && !scheduled.isEmpty()
                && scheduled.first().tick() <= tick) {
            var due = scheduled.pollFirst();
            var entry = plants.get(due.root());
            entry.scheduled = null;
            processed++;
            var site = refresh(due.root());
            if (site == null || site.status() != GrowthSite.Status.READY) continue;
            cancel(entry);
            BlockChangeResult result;
            growing = true;
            try {
                result = edits.setBlock(site.target(), rule.blockId());
            } finally {
                growing = false;
            }
            if (result.changed()) {
                changed++;
                dirty.addAll(result.dirtyChunks());
                // Anchor the next deadline to the previous one so catch-up and continuous simulation agree.
                entry.state = next(due.root(), due.tick(), Math.incrementExact(entry.state.cycle()));
            }
            refresh(due.root());
        }
        return new TickResult(processed, changed, List.copyOf(dirty));
    }

    private void scan(ChunkPos position) {
        var chunk = world.getChunk(position);
        if (chunk == null || chunk.uniformBlock() >= 0 && chunk.uniformBlock() != Byte.toUnsignedInt(rule.blockId()))
            return;
        for (int y = 0; y < Chunk.SIZE; y++)
            for (int z = 0; z < Chunk.SIZE; z++)
                for (int x = 0; x < Chunk.SIZE; x++) {
                    if (chunk.get(x, y, z) != rule.blockId() || y > 0 && chunk.get(x, y - 1, z) == rule.blockId())
                        continue;
                    discover(
                            new BlockPos(
                                    Math.toIntExact((long) position.x() * Chunk.SIZE + x),
                                    Math.toIntExact((long) position.y() * Chunk.SIZE + y),
                                    Math.toIntExact((long) position.z() * Chunk.SIZE + z)),
                            tick);
                }
    }

    private void discoverCandidate() {
        var root = candidates.keySet().iterator().next();
        long startedAt = candidates.remove(root);
        discover(root, startedAt);
    }

    private void discover(BlockPos root, long startedAt) {
        if (plants.containsKey(root)
                || !world.hasChunk(Coordinates.worldToChunk(root))
                || world.getBlock(root).orElse(-1) != Byte.toUnsignedInt(rule.blockId())) return;
        var below = new BlockPos(root.x(), root.y() - 1, root.z());
        if (!world.hasChunk(Coordinates.worldToChunk(below))
                || world.getBlock(below).orElse(-1) == Byte.toUnsignedInt(rule.blockId())) return;
        if (plants.size() >= PlantGrowthSnapshot.MAX_PLANTS) throw new IllegalStateException("Too many growing plants");
        var entry = new Entry(next(root, startedAt, 0));
        plants.put(root, entry);
        entry.dependencies = rule.dependencies(root);
        for (var chunk : entry.dependencies)
            dependents.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(root);
        checks.add(root);
    }

    private GrowthSite refresh(BlockPos root) {
        var entry = plants.get(root);
        if (entry == null) return null;
        cancel(entry);
        var site = rule.inspect(world, root);
        unindex(root, entry.dependencies);
        entry.dependencies = site.dependencies();
        for (var chunk : entry.dependencies)
            dependents.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(root);
        if (site.status() == GrowthSite.Status.INVALID) remove(root);
        else if (site.status() == GrowthSite.Status.READY) {
            entry.scheduled = new Scheduled(root, entry.state.nextGrowthTick());
            scheduled.add(entry.scheduled);
        }
        return site;
    }

    private PlantGrowthState next(BlockPos root, long startedAt, long cycle) {
        return new PlantGrowthState(
                root,
                rule.blockId(),
                startedAt,
                Math.addExact(startedAt, rule.delay(seed, root, startedAt, cycle)),
                cycle);
    }

    private void cancel(Entry entry) {
        if (entry.scheduled != null) {
            scheduled.remove(entry.scheduled);
            entry.scheduled = null;
        }
    }

    private void remove(BlockPos root) {
        var entry = plants.remove(root);
        if (entry == null) return;
        cancel(entry);
        checks.remove(root);
        unindex(root, entry.dependencies);
    }

    private void unindex(BlockPos root, Set<ChunkPos> dependencies) {
        for (var chunk : dependencies) {
            var roots = dependents.get(chunk);
            if (roots != null && roots.remove(root) && roots.isEmpty()) dependents.remove(chunk);
        }
    }

    public PlantGrowthSnapshot snapshot() {
        world.assertOwner();
        while (!candidates.isEmpty()) discoverCandidate();
        return new PlantGrowthSnapshot(plants.values().stream()
                .map(entry -> entry.state)
                .sorted(Comparator.comparing(PlantGrowthState::root, POSITION_ORDER))
                .toList());
    }

    public void restore(PlantGrowthSnapshot snapshot, long tick) {
        advanceClock(tick);
        if (!plants.isEmpty()) throw new IllegalStateException("Plant system is already populated");
        for (var state : snapshot.plants()) {
            if (state.blockId() != rule.blockId() || state.startedAt() > tick)
                throw new IllegalArgumentException("Plant state does not match the world");
            var entry = new Entry(state);
            entry.dependencies = rule.dependencies(state.root());
            plants.put(state.root(), entry);
            for (var chunk : entry.dependencies)
                dependents.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(state.root());
            if (world.hasChunk(Coordinates.worldToChunk(state.root()))) checks.add(state.root());
        }
    }

    public int activeUpdates() {
        world.assertOwner();
        return scheduled.size();
    }

    private static <T> T take(Set<T> values) {
        var iterator = values.iterator();
        var value = iterator.next();
        iterator.remove();
        return value;
    }

    private record Scheduled(BlockPos root, long tick) {}

    private static final class Entry {
        private PlantGrowthState state;
        private Set<ChunkPos> dependencies = Set.of();
        private Scheduled scheduled;

        private Entry(PlantGrowthState state) {
            this.state = state;
        }
    }

    public record TickResult(int processed, int changedBlocks, List<ChunkPos> dirtyChunks) {}
}
