package com.norbertfila.mob.spawn;

import com.norbertfila.light.LightLevels;
import com.norbertfila.light.SkyDarkness;
import com.norbertfila.light.SkyExposure;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.WeatherSample;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.ChunkPos;
import com.norbertfila.world.VoxelCollision;
import com.norbertfila.world.World;
import java.util.Objects;
import java.util.OptionalInt;

/** Spawn view of a live world for one cycle; columns are active within simulation distance of the given chunk. */
public final class WorldSpawnEnvironment implements SpawnEnvironment {
    private final World world;
    private final SkyExposure sky;
    private final LightLevels light;
    private final VoxelCollision collision = new VoxelCollision();
    private final WorldTime time;
    private final int skyDarkening;
    private final ChunkPos center;
    private final int simulationDistance;

    public WorldSpawnEnvironment(
            World world,
            SkyExposure sky,
            LightLevels light,
            WorldTime time,
            WeatherSample weather,
            ChunkPos center,
            int simulationDistance) {
        this.world = Objects.requireNonNull(world);
        this.sky = Objects.requireNonNull(sky);
        this.light = Objects.requireNonNull(light);
        this.time = Objects.requireNonNull(time);
        this.skyDarkening = SkyDarkness.of(time, weather);
        this.center = Objects.requireNonNull(center);
        if (simulationDistance < 0) throw new IllegalArgumentException("Simulation distance is negative");
        this.simulationDistance = simulationDistance;
    }

    @Override
    public OptionalInt block(BlockPos position) {
        return world.getBlock(position);
    }

    @Override
    public boolean spaceFree(Aabb body) {
        return collision.available(world, body) && collision.spaceFree(world, body);
    }

    @Override
    public OptionalInt surfaceTop(int x, int z) {
        return sky.top(x, z);
    }

    @Override
    public boolean columnActive(int chunkX, int chunkZ) {
        return Math.abs((long) chunkX - center.x()) <= simulationDistance
                && Math.abs((long) chunkZ - center.z()) <= simulationDistance
                && world.hasChunk(new ChunkPos(chunkX, center.y(), chunkZ));
    }

    @Override
    public LightLevels light() {
        return light;
    }

    @Override
    public int skyDarkening() {
        return skyDarkening;
    }

    @Override
    public WorldTime time() {
        return time;
    }
}
