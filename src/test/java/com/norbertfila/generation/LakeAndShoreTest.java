package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.BlockId;
import org.junit.jupiter.api.Test;

class LakeAndShoreTest {
    @Test
    void inlandDepressionsCollectTributariesIntoBoundedLakes() {
        var water = new RegionalHydrology(42, 28, (x, z) -> 120 + 30 * (Math.sin(x * .004) + Math.cos(z * .004)));
        int lakes = 0;
        for (int z = -2048; z < 2048; z += 16)
            for (int x = -2048; x < 2048; x += 16) {
                double ground = 120 + 30 * (Math.sin(x * .004) + Math.cos(z * .004));
                var sample = water.sample(x, z, ground);
                if (sample.kind() != WaterKind.LAKE || sample.height() >= sample.waterLevel()) continue;
                assertTrue(sample.waterLevel() > 28);
                assertTrue(sample.waterLevel() - sample.height() <= 12);
                lakes++;
            }
        assertTrue(lakes > 20, "Wet lake samples: " + lakes);
    }

    @Test
    void shoreMaterialsDependOnActualWaterAndItsKind() {
        var resolver = new SurfaceMaterialResolver(new SurfaceMaterialMapper());
        var profile = BiomeRegistry.defaults().profile(BiomeType.PLAINS);
        var warm = new ClimateSample(.2f, .3f);
        assertEquals(
                BlockId.GRASS,
                resolver.resolve(29, 0, 28, 32, warm, profile, WaterKind.OCEAN, false, 0)
                        .surface());
        assertEquals(
                BlockId.SAND,
                resolver.resolve(28, 0, 28, 32, warm, profile, WaterKind.OCEAN, true, 0)
                        .surface());
        assertEquals(
                BlockId.GRAVEL,
                resolver.resolve(28, 0, 28, 32, warm, profile, WaterKind.OCEAN, true, -.4)
                        .surface());
        assertEquals(
                BlockId.GRASS,
                resolver.resolve(28, 0, 28, 32, warm, profile, WaterKind.OCEAN, true, .4)
                        .surface());
        assertEquals(
                BlockId.MUD,
                resolver.resolve(28, 0, 28, 32, warm, profile, WaterKind.LAKE, true, 0)
                        .surface());
        assertEquals(
                BlockId.MUD,
                resolver.resolve(28, 0, 28, 32, warm, profile, WaterKind.RIVER, true, 0)
                        .surface());
        assertEquals(
                BlockId.STONE,
                resolver.resolve(28, 2, 28, 32, warm, profile, WaterKind.LAKE, true, 0)
                        .surface());
    }
}
