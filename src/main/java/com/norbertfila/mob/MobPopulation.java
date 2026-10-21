package com.norbertfila.mob;

import com.norbertfila.world.Aabb;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.VoxelCollision;
import com.norbertfila.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MobPopulation {
    /** Hard safety limit across all categories, independent of the tunable spawn caps. */
    public static final int MAX_MOBS = 1024;

    private final Map<Long, Mob> mobs = new LinkedHashMap<>();
    private final Map<ChunkPos, Set<Mob>> buckets = new HashMap<>();
    private final Map<PopulationKey, Integer> counts = new HashMap<>();
    private final MobPhysics physics = new MobPhysics();
    private long nextId;

    public Optional<Mob> spawn(MobType type, SpawnZone zone, double x, double y, double z, float yaw) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(zone);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw))
            throw new IllegalArgumentException("Mob position must be finite");
        if (mobs.size() >= MAX_MOBS) return Optional.empty();
        var mob = new Mob(nextId++, type, zone, x, y, z, yaw);
        mobs.put(mob.id(), mob);
        buckets.computeIfAbsent(mob.chunk(), ignored -> new LinkedHashSet<>()).add(mob);
        counts.merge(mob.populationKey(), 1, Integer::sum);
        return Optional.of(mob);
    }

    public void remove(Mob mob) {
        if (mobs.remove(mob.id()) == null) return;
        unindex(mob.chunk(), mob);
        counts.computeIfPresent(mob.populationKey(), (key, count) -> count == 1 ? null : count - 1);
    }

    public void clear() {
        mobs.clear();
        buckets.clear();
        counts.clear();
    }

    public int count(PopulationKey key) {
        return counts.getOrDefault(key, 0);
    }

    public int size() {
        return mobs.size();
    }

    /** A copy, so callers may remove mobs while iterating. */
    public List<Mob> mobs() {
        return List.copyOf(mobs.values());
    }

    public List<Mob> near(Aabb region) {
        var found = new ArrayList<Mob>();
        for (var chunk : VoxelCollision.requiredChunks(region)) {
            var bucket = buckets.get(chunk);
            if (bucket != null) for (var mob : bucket) if (region.intersects(mob.body())) found.add(mob);
        }
        return found;
    }

    /** Mobs are not saved, so a mob whose chunk unloaded is dropped instead of frozen. */
    public void tick(World world, double dt) {
        if (!Double.isFinite(dt) || dt <= 0 || dt > .1) throw new IllegalArgumentException("Invalid mob tick");
        for (var mob : mobs()) {
            mob.previousX = mob.x;
            mob.previousY = mob.y;
            mob.previousZ = mob.z;
            mob.previousYaw = mob.yaw;
            mob.previousWalkDistance = mob.walkDistance;
            ChunkPos before = mob.chunk();
            if (!world.hasChunk(before)) {
                remove(mob);
                continue;
            }
            physics.step(world, mob, dt);
            mob.ticksAwayFromPlayers++;
            mob.ageHurt();
            ChunkPos after = mob.chunk();
            if (!after.equals(before)) {
                unindex(before, mob);
                buckets.computeIfAbsent(after, ignored -> new LinkedHashSet<>()).add(mob);
            }
        }
    }

    public List<MobRenderState> renderSnapshot() {
        return mobs.values().stream().map(Mob::renderState).toList();
    }

    private void unindex(ChunkPos chunk, Mob mob) {
        var bucket = buckets.get(chunk);
        bucket.remove(mob);
        if (bucket.isEmpty()) buckets.remove(chunk);
    }
}
