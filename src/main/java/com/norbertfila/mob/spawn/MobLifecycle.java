package com.norbertfila.mob.spawn;

import com.norbertfila.combat.AttackTarget;
import com.norbertfila.explosion.BlockSink;
import com.norbertfila.explosion.Explosion;
import com.norbertfila.light.LightLevels;
import com.norbertfila.light.LightProbe;
import com.norbertfila.light.SkyExposure;
import com.norbertfila.mob.Mob;
import com.norbertfila.mob.MobCategory;
import com.norbertfila.mob.MobCensus;
import com.norbertfila.mob.MobDeathEvent;
import com.norbertfila.mob.MobPopulation;
import com.norbertfila.mob.MobRenderState;
import com.norbertfila.mob.MobType;
import com.norbertfila.mob.PlayerPosition;
import com.norbertfila.mob.PopulationKey;
import com.norbertfila.mob.SpawnZone;
import com.norbertfila.mob.ai.MobAi;
import com.norbertfila.mob.ai.WorldMobWorld;
import com.norbertfila.mob.hazard.MobHazards;
import com.norbertfila.mob.hazard.WorldHazardEnvironment;
import com.norbertfila.projectile.ArrowSystem;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.BiomeWeather;
import com.norbertfila.weather.WeatherSample;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockMutation;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.Coordinates;
import com.norbertfila.world.World;
import com.norbertfila.world.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;

/** Owns the mobs of one world: their movement, natural spawning and despawning. Runs on the world owner thread. */
public final class MobLifecycle {
    private final World world;
    private final MobPopulation population = new MobPopulation();
    private final SkyExposure sky;
    private final LightProbe light;
    private final NaturalSpawner spawner;
    private final Despawner despawner;
    private final MobHazards hazards = new MobHazards();
    private final MobAi ai = MobAi.standard();
    private final Explosion explosion;
    private final ArrowSystem arrows;
    private final BiomeWeather biomes;
    private final SplittableRandom random;
    private static final int MAX_DEATHS = 32;
    private final Deque<MobDeathEvent> deaths = new ArrayDeque<>();
    private boolean naturalSpawning = true;
    private int activeColumns;

    public MobLifecycle(
            World world, BiomeWeather biomes, NaturalSpawner spawner, long seed, BlockSink blocks, ArrowSystem arrows) {
        this.arrows = Objects.requireNonNull(arrows);
        explosion = new Explosion(world, blocks);
        this.world = Objects.requireNonNull(world);
        this.biomes = Objects.requireNonNull(biomes);
        this.spawner = Objects.requireNonNull(spawner);
        despawner = new Despawner(spawner.settings());
        sky = new SkyExposure(world);
        light = new LightProbe(world, sky);
        random = new SplittableRandom(seed);
    }

    public void onBlockChanged(BlockMutation mutation) {
        sky.onBlockChanged(mutation);
    }

    public void onChunkChanged(ChunkPos chunk) {
        sky.onChunkChanged(chunk);
    }

    public void tick(
            long tick,
            double dt,
            List<PlayerPosition> players,
            List<AttackTarget> targets,
            WorldTime time,
            WeatherSample weather,
            ChunkPos center,
            int simulationDistance) {
        world.assertOwner();
        ai.tick(population, new WorldMobWorld(world, population, targets, explosion, arrows, random), random);
        for (var mob : population.tick(world, dt)) {
            deaths.addLast(new MobDeathEvent(tick, mob.body()));
            if (deaths.size() > MAX_DEATHS) deaths.removeFirst();
        }
        var victims = new ArrayList<AttackTarget>(targets);
        victims.addAll(population.mobs());
        arrows.tick(world, victims, dt);
        hazards.tick(population, new WorldHazardEnvironment(world, sky, biomes, time, weather), random);
        if (tick % spawner.settings().cycleTicks() != 0) return;
        despawner.despawn(population, players, random);
        var environment = new WorldSpawnEnvironment(world, sky, light, time, weather, center, simulationDistance);
        activeColumns = spawner.activeColumnCount(environment, players);
        if (naturalSpawning) spawner.spawn(environment, population, players, random);
    }

    /** Places a mob regardless of spawn rules; empty when the position is outside the world or not loaded. */
    public Optional<Mob> summon(MobType type, double x, double y, double z) {
        world.assertOwner();
        var feet = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (!WorldBounds.containsY(feet.y()) || !world.hasChunk(Coordinates.worldToChunk(feet)))
            return Optional.empty();
        var zone = SpawnZone.fromSkyLight(light.skyLight(feet).orElse(LightLevels.MAX_LEVEL));
        return population.spawn(type, zone, x, y, z, random.nextFloat() * 360);
    }

    public int removeAll() {
        int count = population.size();
        population.clear();
        return count;
    }

    public boolean naturalSpawning() {
        return naturalSpawning;
    }

    public void setNaturalSpawning(boolean enabled) {
        naturalSpawning = enabled;
    }

    public MobCensus census() {
        var zones = new ArrayList<MobCensus.Zone>();
        for (var category : MobCategory.values())
            for (var zone : SpawnZone.values()) {
                var key = new PopulationKey(category, zone);
                zones.add(new MobCensus.Zone(
                        key, population.count(key), spawner.settings().scaledCap(key, activeColumns)));
            }
        return new MobCensus(zones, naturalSpawning);
    }

    public int size() {
        return population.size();
    }

    public List<Mob> near(Aabb region) {
        return population.near(region);
    }

    /** The latest mobs that finished dying, oldest first. */
    public List<MobDeathEvent> deaths() {
        return List.copyOf(deaths);
    }

    public List<MobRenderState> renderSnapshot() {
        return population.renderSnapshot();
    }

    public LightLevels light() {
        return light;
    }
}
