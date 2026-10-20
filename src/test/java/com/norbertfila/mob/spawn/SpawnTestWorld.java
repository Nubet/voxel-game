package com.norbertfila.mob.spawn;

import com.norbertfila.light.LightProbe;
import com.norbertfila.light.SkyExposure;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.WeatherSample;
import com.norbertfila.world.*;
import java.util.random.RandomGenerator;

/** Flat stone below y = 0 with an optional sealed cave layer whose floor is at y = -20. */
final class SpawnTestWorld {
    static final int RADIUS = 5;
    static final int CAVE_FLOOR = -20;
    final World world = new World();
    final SkyExposure sky = new SkyExposure(world);
    final LightProbe light = new LightProbe(world, sky);

    SpawnTestWorld(boolean cave) {
        world.setBlockChangeListener(sky::onBlockChanged);
        for (int x = -RADIUS; x <= RADIUS; x++)
            for (int z = -RADIUS; z <= RADIUS; z++)
                for (int y = WorldBounds.MIN_CHUNK_Y; y <= WorldBounds.MAX_CHUNK_Y; y++)
                    world.putChunk(new ChunkPos(x, y, z), chunk(y, cave));
    }

    private static Chunk chunk(int chunkY, boolean cave) {
        if (chunkY >= 0) return new Chunk();
        if (!cave || chunkY != Math.floorDiv(CAVE_FLOOR, Chunk.SIZE)) return Chunk.uniform(BlockId.STONE);
        byte[] blocks = new byte[Chunk.BLOCK_BYTES];
        java.util.Arrays.fill(blocks, BlockId.STONE);
        int floor = Math.floorMod(CAVE_FLOOR, Chunk.SIZE);
        java.util.Arrays.fill(
                blocks, floor * LayerStorage.LAYER_BLOCKS, (floor + 2) * LayerStorage.LAYER_BLOCKS, BlockId.AIR);
        return Chunk.fromBlocks(blocks);
    }

    WorldSpawnEnvironment environment(int minecraftTime) {
        return new WorldSpawnEnvironment(
                world,
                sky,
                light,
                WorldTime.INITIAL.withMinecraftTime(minecraftTime),
                WeatherSample.CLEAR,
                new ChunkPos(0, 0, 0),
                RADIUS);
    }

    static SpawnSettings settings(int surfaceCap, int caveCap) {
        var d = SpawnSettings.DEFAULT;
        return new SpawnSettings(
                java.util.Map.of(
                        new com.norbertfila.mob.PopulationKey(
                                com.norbertfila.mob.MobCategory.HOSTILE, com.norbertfila.mob.SpawnZone.SURFACE),
                        surfaceCap,
                        new com.norbertfila.mob.PopulationKey(
                                com.norbertfila.mob.MobCategory.HOSTILE, com.norbertfila.mob.SpawnZone.CAVE),
                        caveCap),
                d.cycleTicks(),
                4,
                81,
                81,
                d.groupsPerColumn(),
                d.attemptsPerGroup(),
                d.groupSpread(),
                d.minPlayerDistance(),
                d.despawnDistance(),
                d.randomDespawnDistance(),
                d.despawnDelayTicks(),
                d.randomDespawnChance(),
                d.maxBlockLight(),
                d.maxSpawnLight(),
                d.nightStart(),
                d.nightEnd());
    }

    /** Always rolls the highest value, so the random light threshold is as permissive as it gets. */
    static RandomGenerator highestRoll() {
        return new RandomGenerator() {
            public long nextLong() {
                return Long.MAX_VALUE;
            }

            public int nextInt(int bound) {
                return bound - 1;
            }
        };
    }
}
