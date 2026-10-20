package com.norbertfila.mob.hazard;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.HillsGenerator;
import com.norbertfila.light.SkyExposure;
import com.norbertfila.mob.MobType;
import com.norbertfila.simulation.WorldTime;
import com.norbertfila.weather.BiomeWeather;
import com.norbertfila.weather.WeatherSample;
import com.norbertfila.world.*;
import org.junit.jupiter.api.Test;

class WorldHazardEnvironmentTest {
    private final World world = new World();
    private final SkyExposure sky = new SkyExposure(world);

    WorldHazardEnvironmentTest() {
        world.setBlockChangeListener(sky::onBlockChanged);
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                for (int y = WorldBounds.MIN_CHUNK_Y; y <= WorldBounds.MAX_CHUNK_Y; y++)
                    world.putChunk(new ChunkPos(x, y, z), y < 0 ? Chunk.uniform(BlockId.STONE) : new Chunk());
    }

    @Test
    void daylightEndsAtDuskAndUnderStorms() {
        assertTrue(environment(6000, WeatherSample.CLEAR).daylight());
        assertFalse(environment(18000, WeatherSample.CLEAR).daylight());
        assertFalse(environment(6000, new WeatherSample(1, 1, 1, 0)).daylight());
    }

    @Test
    void roofsBlockTheSkyAndRainAndWaterIsDetected() {
        var clear = environment(6000, WeatherSample.CLEAR);
        var eye = new BlockPos(4, 1, 4);
        assertTrue(clear.seesSky(eye));
        assertFalse(clear.rainingAt(eye));
        world.setBlock(new BlockPos(4, 5, 4), BlockId.PLANKS, 64);
        assertFalse(clear.seesSky(eye));
        world.setBlock(new BlockPos(4, 5, 4), BlockId.AIR, 64);
        var body = MobType.ZOMBIE.body(4.5, 0, 4.5);
        assertFalse(clear.touchesWater(body));
        world.setBlock(new BlockPos(4, 1, 4), BlockId.WATER, 64);
        assertTrue(clear.touchesWater(body));
    }

    private WorldHazardEnvironment environment(int time, WeatherSample weather) {
        return new WorldHazardEnvironment(
                world, sky, new BiomeWeather(new HillsGenerator()), WorldTime.INITIAL.withMinecraftTime(time), weather);
    }
}
