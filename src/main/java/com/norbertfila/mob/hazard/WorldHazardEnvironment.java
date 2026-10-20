package com.norbertfila.mob.hazard;

import com.norbertfila.light.SkyDarkness;
import com.norbertfila.light.SkyExposure;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.BiomeWeather;
import com.norbertfila.weather.WeatherSample;
import com.norbertfila.world.Aabb;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.BlockPos;
import com.norbertfila.world.World;
import java.util.Objects;

/** Hazard view of a live world for one tick. */
public final class WorldHazardEnvironment implements HazardEnvironment {
    /** Minecraft treats the sky as day while it darkens sky light by fewer than four levels. */
    private static final int DAY_DARKENING_LIMIT = 4;

    private final World world;
    private final SkyExposure sky;
    private final BiomeWeather biomes;
    private final WeatherSample weather;
    private final boolean daylight;

    public WorldHazardEnvironment(
            World world, SkyExposure sky, BiomeWeather biomes, WorldTime time, WeatherSample weather) {
        this.world = Objects.requireNonNull(world);
        this.sky = Objects.requireNonNull(sky);
        this.biomes = Objects.requireNonNull(biomes);
        this.weather = Objects.requireNonNull(weather);
        daylight = SkyDarkness.of(time, weather) < DAY_DARKENING_LIMIT;
    }

    @Override
    public boolean daylight() {
        return daylight;
    }

    @Override
    public boolean seesSky(BlockPos position) {
        var top = sky.top(position.x(), position.z());
        return top.isPresent() && position.y() > top.getAsInt();
    }

    @Override
    public boolean rainingAt(BlockPos position) {
        if (weather.precipitation() <= 0 || !seesSky(position)) return false;
        var local = biomes.sample(position.x() + .5, position.z() + .5);
        return local.rain() > local.snow();
    }

    @Override
    public boolean touchesWater(Aabb body) {
        for (int x = (int) Math.floor(body.minX()); x < Math.ceil(body.maxX()); x++)
            for (int y = (int) Math.floor(body.minY()); y < Math.ceil(body.maxY()); y++)
                for (int z = (int) Math.floor(body.minZ()); z < Math.ceil(body.maxZ()); z++)
                    if (world.getBlock(new BlockPos(x, y, z)).orElse(BlockId.AIR) == BlockId.WATER) return true;
        return false;
    }
}
