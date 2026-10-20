package com.norbertfila.mob.spawn;

import com.norbertfila.light.LightProbe;
import com.norbertfila.light.SkyExposure;
import com.norbertfila.mob.MobPopulation;
import com.norbertfila.mob.PlayerPosition;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.WeatherSample;
import com.norbertfila.world.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Times full spawn and despawn cycles with the default settings around one player, on stone riddled with dark
 * tunnels and lit by scattered torches. Populations are cleared regularly so that caps never stop the work.
 */
public final class MobSpawnBenchmark {
    private static final int RADIUS = 9;

    public static void main(String[] args) {
        var world = world();
        var sky = new SkyExposure(world);
        var light = new LightProbe(world, sky);
        var spawner = NaturalSpawner.overworld();
        var despawner = new Despawner(spawner.settings());
        var players = List.of(new PlayerPosition(8, 0, 8));
        var random = new SplittableRandom(1);
        for (String phase : new String[] {"warmup", "measure"}) {
            int cycles = 2000;
            long[] nanos = new long[cycles];
            int spawned = 0;
            for (int i = 0; i < cycles; i++) {
                var population = new MobPopulation();
                long started = System.nanoTime();
                var environment = new WorldSpawnEnvironment(
                        world,
                        sky,
                        light,
                        WorldTime.INITIAL.withMinecraftTime(18000),
                        WeatherSample.CLEAR,
                        new ChunkPos(0, 0, 0),
                        RADIUS);
                despawner.despawn(population, players, random);
                spawned += spawner.spawn(environment, population, players, random);
                nanos[i] = System.nanoTime() - started;
            }
            Arrays.sort(nanos);
            System.out.printf(
                    Locale.ROOT,
                    "%s: mean %.3f ms, p50 %.3f ms, p95 %.3f ms, max %.3f ms, %.1f mobs per cycle%n",
                    phase,
                    Arrays.stream(nanos).average().orElseThrow() / 1e6,
                    nanos[cycles / 2] / 1e6,
                    nanos[cycles * 95 / 100] / 1e6,
                    nanos[cycles - 1] / 1e6,
                    spawned / (double) cycles);
        }
    }

    private static World world() {
        var world = new World();
        var random = new SplittableRandom(7);
        for (int x = -RADIUS; x <= RADIUS; x++)
            for (int z = -RADIUS; z <= RADIUS; z++)
                for (int y = WorldBounds.MIN_CHUNK_Y; y <= WorldBounds.MAX_CHUNK_Y; y++) {
                    if (y >= 0) {
                        world.putChunk(new ChunkPos(x, y, z), new Chunk());
                        continue;
                    }
                    byte[] blocks = new byte[Chunk.BLOCK_BYTES];
                    Arrays.fill(blocks, BlockId.STONE);
                    for (int tunnel = 0; tunnel < 3; tunnel++) {
                        int ly = 1 + random.nextInt(13), lz = random.nextInt(16);
                        for (int lx = 0; lx < 16; lx++)
                            for (int dy = 0; dy < 2; dy++) blocks[((ly + dy) * 16 + lz) * 16 + lx] = BlockId.AIR;
                        if (random.nextInt(4) == 0) blocks[(ly * 16 + lz) * 16 + random.nextInt(16)] = BlockId.TORCH;
                    }
                    world.putChunk(new ChunkPos(x, y, z), Chunk.fromBlocks(blocks));
                }
        return world;
    }
}
