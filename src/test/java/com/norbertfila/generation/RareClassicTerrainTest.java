package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RareClassicTerrainTest {
    @Test
    void noiseIsThreeDimensionalDeterministicBoundedAndContinuous() {
        var first = new PerlinNoise3D(42);
        var second = new PerlinNoise3D(42);
        for (int i = -100; i <= 100; i++) {
            double x = i * .173, y = i * .071 + .3, z = i * .137 - .2;
            double value = first.sample(x, y, z);
            assertEquals(value, second.sample(x, y, z));
            assertTrue(value >= -1 && value <= 1);
            assertEquals(value, first.sample(x + 1e-7, y, z), .00001);
            assertEquals(value, first.sample(x, y + 1e-7, z), .00001);
            assertEquals(value, first.sample(x, y, z + 1e-7), .00001);
        }
        assertNotEquals(first.sample(.2, .3, .4), first.sample(.2, 1.3, .4));
        assertThrows(IllegalArgumentException.class, () -> first.sample(0, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> first.sample(Double.POSITIVE_INFINITY, 0, 0));
    }

    @Test
    void densityCreatesAirUnderStoneWhileFloatingIslandsKeepTheOriginalGround() {
        var features = new ClassicTerrainFeatures(42, 28, (x, z) -> 40, (x, z) -> true);
        for (var type : List.of(
                BiomeType.ARCHLANDS_CLASSIC,
                BiomeType.FLOATING_ISLES_CLASSIC,
                BiomeType.OVERHANG_HIGHLANDS_CLASSIC,
                BiomeType.COASTAL_CLIFFS_CLASSIC)) {
            var region = new ClassicRegionSampler.Region(type, 0, 0, 360, 1, 42);
            int gaps = 0, connected = 0;
            for (int z = -96; z <= 96; z += 8)
                for (int x = -96; x <= 96; x += 8) {
                    var field = features.new Field(region, x, z, 40, 1);
                    int runs = 0;
                    boolean previous = true;
                    for (int y = 20; y <= 180; y++) {
                        boolean solid = field.density(y) >= 0;
                        if (solid && !previous) runs++;
                        previous = solid;
                    }
                    if (runs > 0) gaps++;
                    else connected++;
                    if (type == BiomeType.FLOATING_ISLES_CLASSIC) assertTrue(field.density(39) >= 0);
                    double atEdge = field.density(75.25);
                    assertEquals(atEdge, features.new Field(region, x + 1e-7, z, 40, 1).density(75.25), .0001);
                }
            assertTrue(gaps > 5, type + " must contain genuine 3D air gaps; found=" + gaps);
            assertTrue(connected > 5, type + " must also contain ordinary ground");
        }
    }

    @Test
    void hiddenValleysHaveGentleFloorsHighWallsAndOpenPassages() {
        var features = new ClassicTerrainFeatures(42, 28, (x, z) -> 40, (x, z) -> false);
        var region = new ClassicRegionSampler.Region(BiomeType.HIDDEN_VALLEYS_CLASSIC, 0, 0, 360, 1, 42);
        double center = top(features.new Field(region, 0, 0, 40, 1));
        double high = -1000, low = 1000;
        for (int angle = 0; angle < 360; angle += 10) {
            double radians = Math.toRadians(angle);
            double height = top(features.new Field(region, Math.cos(radians) * 220, Math.sin(radians) * 220, 40, 1));
            high = Math.max(high, height);
            low = Math.min(low, height);
        }
        assertTrue(high > center + 35, "center=" + center + " highest wall=" + high);
        assertTrue(low < center + 15, "lowest passage=" + low + " center=" + center);
        assertTrue(Math.abs(top(features.new Field(region, 1, 0, 40, 1)) - center) <= 1);
        int dryFloor = 0, lake = 0;
        for (int z = -96; z <= 96; z += 8)
            for (int x = -96; x <= 96; x += 8) {
                int height = top(features.new Field(region, x, z, 40, 1));
                if (height < 35) lake++;
                else if (height < 53) dryFloor++;
            }
        assertTrue(lake > 0 && dryFloor > lake, "dry valley floor=" + dryFloor + " lake=" + lake);
    }

    @Test
    void densityBlendsAtRegionEdgesRatherThanClippingGeneratedBlocks() {
        var features = new ClassicTerrainFeatures(42, 28, (x, z) -> 40, (x, z) -> false);
        for (var type : BiomeType.values())
            if (type.rareClassic()) {
                var region = new ClassicRegionSampler.Region(type, 0, 0, 360, 0, 42);
                for (int y = 10; y < 180; y++) {
                    assertEquals(40 - y, features.new Field(region, 359.9, 0, 40, 0).density(y));
                    assertEquals(40 - y, features.new Field(region, 359.9, 0, 40, 1e-8).density(y), .00001);
                }
            }
    }

    @Test
    void newBiomesReallyOccurButOccupyOnlyASmallPartOfTheDefaultWorld() {
        var counts = new EnumMap<BiomeType, Integer>(BiomeType.class);
        int total = 0;
        for (long seed : new long[] {0, 7340215}) {
            var terrain = new TerrainGenerator(seed, TerrainConfig.defaults());
            var sites = EnumSet.noneOf(BiomeType.class);
            var volumeSites = EnumSet.noneOf(BiomeType.class);
            for (int z = -32768; z <= 32768; z += 256)
                for (int x = -32768; x <= 32768; x += 256) {
                    var sample = terrain.sample(x, z);
                    counts.merge(sample.biome(), 1, Integer::sum);
                    total++;
                    if (sample.biome().rareClassic()) {
                        assertNotNull(sample.shape());
                        if (sites.add(sample.biome()))
                            System.out.printf(
                                    "%s seed=%d x=%d y=%d z=%d%n", sample.biome(), seed, x, sample.surfaceHeight(), z);
                        if (!volumeSites.contains(sample.biome())) {
                            boolean roof = false;
                            for (int y = sample.surfaceHeight();
                                    y >= sample.shape().minY();
                                    y--)
                                if (sample.materialAt(y) == BlockId.STONE) roof = true;
                                else if (roof && sample.materialAt(y) == BlockId.AIR) {
                                    volumeSites.add(sample.biome());
                                    System.out.printf(
                                            "3D site %s seed=%d x=%d y=%d z=%d%n",
                                            sample.biome(), seed, x, sample.surfaceHeight(), z);
                                    break;
                                }
                        }
                    }
                }
        }
        int rare = 0;
        for (var type : BiomeType.values())
            if (type.rareClassic()) {
                int count = counts.getOrDefault(type, 0);
                assertTrue(count > 0 && count < total / 50, type + " count=" + count + " total=" + total);
                rare += count;
            }
        assertTrue(rare < total / 20, counts.toString());
        assertTrue(
                counts.get(BiomeType.FLOATING_ISLES_CLASSIC) < counts.get(BiomeType.ARCHLANDS_CLASSIC),
                counts.toString());
        System.out.println("Rare classic distribution: " + counts + " total=" + total);
    }

    private static int top(ClassicTerrainFeatures.Field field) {
        for (int y = 200; y >= -20; y--) if (field.density(y) >= 0) return y;
        throw new AssertionError("Missing terrain floor");
    }
}
