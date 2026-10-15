package com.norbertfila.items;

import com.norbertfila.player.Inventory;
import com.norbertfila.world.*;
import java.util.*;

public final class ItemSystem {
    public static final int PICKUP_DELAY_TICKS = 10;
    public static final int THROW_PICKUP_DELAY_TICKS = 40;
    public static final int DESPAWN_TICKS = 5 * 60 * 60;
    private static final double GRAVITY = 16;
    private static final double MERGE_RADIUS = .5;
    private final Map<Long, ItemEntity> entities = new LinkedHashMap<>();
    private final Map<ChunkPos, Set<Long>> buckets = new HashMap<>();
    private final VoxelCollision collision = new VoxelCollision();
    private long nextId;

    public ItemSystem() {
        this(ItemState.EMPTY);
    }

    public ItemSystem(ItemState state) {
        nextId = state.nextId();
        for (var item : state.entities()) {
            var entity = new ItemEntity(item);
            entities.put(entity.id, entity);
            index(entity);
        }
    }

    public boolean canSpawn(BlockPos block, List<ItemStack> drops) {
        if (drops.isEmpty()) return true;
        return hasCapacity(drops.size())
                && safeCoordinate(block.x())
                && safeCoordinate(block.y())
                && safeCoordinate(block.z());
    }

    public void spawn(BlockPos block, List<ItemStack> drops) {
        if (!canSpawn(block, drops)) throw new IllegalStateException("No room for block drops");
        for (var stack : drops) {
            var random = new SplittableRandom(nextId ^ block.hashCode());
            add(new ItemSnapshot(
                    nextId,
                    stack.itemId(),
                    stack.count(),
                    block.x() + .5,
                    block.y() + .5,
                    block.z() + .5,
                    random.nextDouble(-1, 1),
                    random.nextDouble(2, 3),
                    random.nextDouble(-1, 1),
                    PICKUP_DELAY_TICKS,
                    0,
                    false));
        }
    }

    public boolean trySpawn(
            ItemStack stack, double x, double y, double z, double vx, double vy, double vz, int pickupDelayTicks) {
        if (!hasCapacity(1)) return false;
        add(new ItemSnapshot(nextId, stack.itemId(), stack.count(), x, y, z, vx, vy, vz, pickupDelayTicks, 0, false));
        return true;
    }

    private boolean hasCapacity(int count) {
        return count <= ItemState.MAX_ENTITIES - entities.size() && nextId <= Long.MAX_VALUE - count;
    }

    private void add(ItemSnapshot state) {
        var entity = new ItemEntity(state);
        entities.put(entity.id, entity);
        index(entity);
        nextId++;
    }

    public void pause() {
        entities.values().forEach(ItemEntity::pause);
    }

    public void tick(World world, ChunkPos center, int radius, double dt, Aabb pickupBox, Inventory inventory) {
        if (radius < 0 || !Double.isFinite(dt) || dt <= 0 || dt > .1)
            throw new IllegalArgumentException("Invalid item tick");
        pause();
        var active = new ArrayList<Long>();
        for (var entry : buckets.entrySet())
            if (activeChunk(entry.getKey(), center, radius) && world.hasChunk(entry.getKey()))
                active.addAll(entry.getValue());
        active.sort(Long::compare);
        for (long id : active) {
            ItemEntity item = entities.get(id);
            if (!move(world, item, dt)) continue;
            if (++item.ageTicks >= DESPAWN_TICKS) {
                remove(item);
                continue;
            }
            if (item.pickupDelayTicks > 0) item.pickupDelayTicks--;
        }
        if (pickupBox != null) {
            for (long id : nearby(pickupBox)) {
                var item = entities.get(id);
                if (item.moving && item.pickupDelayTicks == 0 && pickupBox.intersects(item.body())) {
                    item.count = inventory.insert(item.itemId, item.count);
                    if (item.count == 0) remove(item);
                }
            }
        }
        for (long id : active) {
            var item = entities.get(id);
            if (item != null && item.moving && item.ageTicks % 10 == 0) merge(item);
        }
    }

    public ItemState snapshot() {
        return new ItemState(
                nextId, entities.values().stream().map(ItemEntity::snapshot).toList());
    }

    public List<ItemRenderState> renderSnapshot(World world) {
        return entities.values().stream()
                .filter(item -> world.hasChunk(chunk(item)))
                .map(item -> new ItemRenderState(
                        item.snapshot(), item.previousX, item.previousY, item.previousZ, item.moving))
                .toList();
    }

    public static Aabb pickupBox(Aabb player) {
        return new Aabb(
                player.minX() - .75,
                player.minY() - .25,
                player.minZ() - .75,
                player.maxX() + .75,
                player.maxY() + .25,
                player.maxZ() + .75);
    }

    private boolean move(World world, ItemEntity item, double dt) {
        if (!collision.available(world, item.body())) return false;
        if (!collision.spaceFree(world, item.body()) && !escape(world, item)) {
            item.moving = true;
            return true;
        }
        double vy = Math.max(-20, item.vy - GRAVITY * dt);
        double dx = boundedMove(item.x, item.vx * dt),
                dy = boundedMove(item.y, vy * dt),
                dz = boundedMove(item.z, item.vz * dt);
        var moved = collision.move(world, item.body(), dx, dy, dz);
        if (!moved.available()) return false;
        ChunkPos previous = chunk(item);
        item.x = (moved.body().minX() + moved.body().maxX()) * .5;
        item.y = (moved.body().minY() + moved.body().maxY()) * .5;
        item.z = (moved.body().minZ() + moved.body().maxZ()) * .5;
        item.vx = moved.blockedX() || dx != item.vx * dt ? 0 : item.vx * Math.exp(-(moved.grounded() ? 8 : .5) * dt);
        item.vy = moved.blockedY() || dy != vy * dt ? 0 : vy;
        item.vz = moved.blockedZ() || dz != item.vz * dt ? 0 : item.vz * Math.exp(-(moved.grounded() ? 8 : .5) * dt);
        item.onGround = moved.grounded();
        item.moving = true;
        if (!previous.equals(chunk(item))) {
            unindex(previous, item.id);
            index(item);
        }
        return true;
    }

    private boolean escape(World world, ItemEntity item) {
        var original = item.body();
        for (int step = 1; step <= 5; step++)
            for (int axis = 0; axis < 3; axis++)
                for (int sign : new int[] {1, -1}) {
                    double distance = step * .25 * sign;
                    double dx = axis == 0 ? distance : 0, dy = axis == 1 ? distance : 0, dz = axis == 2 ? distance : 0;
                    if (boundedMove(item.x, dx) != dx || boundedMove(item.y, dy) != dy || boundedMove(item.z, dz) != dz)
                        continue;
                    if (!collision.spaceFree(world, original.move(dx, dy, dz))) continue;
                    var previous = chunk(item);
                    item.x += dx;
                    item.y += dy;
                    item.z += dz;
                    item.pause();
                    if (!previous.equals(chunk(item))) {
                        unindex(previous, item.id);
                        index(item);
                    }
                    return true;
                }
        return false;
    }

    private static double boundedMove(double position, double delta) {
        double target = position + delta;
        if (target >= Integer.MIN_VALUE + 2d && target <= Integer.MAX_VALUE - 2d) return delta;
        return Math.clamp(target, Integer.MIN_VALUE + 2d, Integer.MAX_VALUE - 2d) - position;
    }

    private void merge(ItemEntity item) {
        var body = item.body();
        var range = new Aabb(
                body.minX() - MERGE_RADIUS,
                body.minY() - MERGE_RADIUS,
                body.minZ() - MERGE_RADIUS,
                body.maxX() + MERGE_RADIUS,
                body.maxY() + MERGE_RADIUS,
                body.maxZ() + MERGE_RADIUS);
        for (long id : nearby(range)) {
            var other = entities.get(id);
            if (id <= item.id
                    || !other.moving
                    || item.itemId != other.itemId
                    || item.count == Inventory.MAX_STACK
                    || !range.intersects(other.body())) continue;
            int move = Math.min(other.count, Inventory.MAX_STACK - item.count);
            item.count += move;
            other.count -= move;
            item.ageTicks = Math.max(item.ageTicks, other.ageTicks);
            item.pickupDelayTicks = Math.max(item.pickupDelayTicks, other.pickupDelayTicks);
            if (other.count == 0) remove(other);
        }
    }

    private List<Long> nearby(Aabb region) {
        var ids = new ArrayList<Long>();
        for (var chunk : VoxelCollision.requiredChunks(region)) {
            var bucket = buckets.get(chunk);
            if (bucket != null) ids.addAll(bucket);
        }
        ids.sort(Long::compare);
        return ids;
    }

    private void index(ItemEntity item) {
        buckets.computeIfAbsent(chunk(item), ignored -> new LinkedHashSet<>()).add(item.id);
    }

    private void unindex(ChunkPos chunk, long id) {
        var bucket = buckets.get(chunk);
        bucket.remove(id);
        if (bucket.isEmpty()) buckets.remove(chunk);
    }

    private void remove(ItemEntity item) {
        entities.remove(item.id);
        unindex(chunk(item), item.id);
    }

    private static ChunkPos chunk(ItemEntity item) {
        return Coordinates.worldToChunk(
                new BlockPos((int) Math.floor(item.x), (int) Math.floor(item.y), (int) Math.floor(item.z)));
    }

    private static boolean activeChunk(ChunkPos chunk, ChunkPos center, int radius) {
        return Math.abs((long) chunk.x() - center.x()) <= radius && Math.abs((long) chunk.z() - center.z()) <= radius;
    }

    private static boolean safeCoordinate(int coordinate) {
        return coordinate > Integer.MIN_VALUE + 2 && coordinate < Integer.MAX_VALUE - 2;
    }
}
