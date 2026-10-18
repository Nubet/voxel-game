package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.*;
import com.norbertfila.lod.*;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class RareClassicPipelineTest {
    @Test
    void regionalProfilesDoNotDisappearWhenTheLocalClimateChangesInsideThem() {
        var regions = new ClassicRegionSampler(42);
        var region = find(regions, BiomeType.HIDDEN_VALLEYS_CLASSIC);
        var features = new ClassicTerrainFeatures(
                42,
                28,
                (x, z) -> 40,
                (x, z) -> false,
                candidate -> candidate.biome() == BiomeType.HIDDEN_VALLEYS_CLASSIC);
        int x = (int) region.x(), z = (int) region.z();
        var warm = features.apply(x, z, base(40), 1);
        var coldBase = new TerrainSummary(
                40,
                BlockId.GRASS,
                BlockId.DIRT,
                BlockId.STONE,
                WorldBounds.MIN_Y,
                28,
                BiomeType.TUNDRA,
                new ClimateSample(-.8f, -.8f),
                WaterKind.OCEAN);
        var cold = features.apply(x, z, coldBase, 0);
        assertNotNull(warm.shape());
        assertEquals(warm.shape(), cold.shape());
        assertEquals(warm.surfaceHeight(), cold.surfaceHeight());
    }

    @Test
    void shapesSurviveColumnCachingChunkGenerationAndVolumetricLod() {
        var config = TerrainConfig.defaults();
        var off = new TerrainConfig.Layer(.001, 1, .5, 2, 0);
        config = config.withClimate(off, off);
        var first = new TerrainGenerator(42, config, LandscapeTestConfig.region(1));
        var second = new TerrainGenerator(42, config, LandscapeTestConfig.region(1));
        var regions = new ClassicRegionSampler(42);
        for (var type : List.of(
                BiomeType.ARCHLANDS_CLASSIC, BiomeType.FLOATING_ISLES_CLASSIC, BiomeType.OVERHANG_HIGHLANDS_CLASSIC)) {
            var region = find(regions, type);
            boolean checked = false;
            for (int z = (int) region.z() - 96; z <= region.z() + 96 && !checked; z += 8)
                for (int x = (int) region.x() - 96; x <= region.x() + 96; x += 8) {
                    var sample = first.sample(x, z);
                    int gap = gap(sample);
                    if (gap == Integer.MIN_VALUE || sample.biome() != type) continue;
                    assertEquals(sample, second.sample(x, z));
                    boolean stone = false;
                    for (int y = sample.shape().minY(); y <= sample.surfaceHeight(); y++)
                        stone |= sample.materialAt(y) == BlockId.STONE;
                    assertTrue(stone, "Rock formations must retain stone below their surface soil");
                    assertEquals(sample.surfaceHeight(), first.surfaceHeight(x, z));
                    var summaries = new TerrainSummary[256];
                    Arrays.fill(summaries, sample);
                    var column = TerrainColumn.of(summaries, List.of());
                    assertEquals(BlockId.AIR, column.materialAt(0, gap));
                    assertEquals(-1, column.uniformBlock(Math.floorDiv(gap, 16) * 16));
                    var position = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(gap, 16), Math.floorDiv(z, 16));
                    var near = first.generate(position);
                    assertFalse(BlockRegistry.DEFAULT.occludesFullFace(
                            near.get(Math.floorMod(x, 16), Math.floorMod(gap, 16), Math.floorMod(z, 16))));
                    assertArrayEquals(near.blocks(), second.generate(position).blocks());
                    int tx = Math.floorDiv(x, 64), tz = Math.floorDiv(z, 64);
                    var far = new FarTerrainGenerator(first);
                    var tile = far.generate(tx, tz, 4, AreaEdits.none(tx * 64, tz * 64, 64));
                    var mesh = FarTileMesher.mesh(tile, far.border(tx, tz, 4));
                    boolean underside = false;
                    for (int i = 0; i < mesh.records().length; i += 3) underside |= (mesh.records()[i] >>> 14 & 7) == 3;
                    assertTrue(underside, type + " must retain undersides in far geometry");
                    System.out.printf("Volume site %s seed=42 x=%d z=%d gap=%d%n", type, x, z, gap);
                    checked = true;
                    break;
                }
            assertTrue(checked, "No genuine 3D column in " + type);
        }
    }

    @Test
    void coastsRequireAnOceanAndDoNotReplaceInlandLakes() {
        var noOcean = new ClassicTerrainFeatures(42, 28, (x, z) -> 12, (x, z) -> false);
        var ocean = new ClassicTerrainFeatures(42, 28, (x, z) -> 12, (x, z) -> true);
        int cliffs = 0;
        for (int z = -512; z <= 512; z += 32)
            for (int x = -512; x <= 512; x += 32) {
                var base = base(34);
                var dry = noOcean.apply(x, z, base, 1);
                assertNotEquals(BiomeType.COASTAL_CLIFFS_CLASSIC, dry.biome());
                var wet = ocean.apply(x, z, base, 1);
                if (wet.biome() == BiomeType.COASTAL_CLIFFS_CLASSIC) {
                    if (wet.surfaceHeight() > 50) cliffs++;
                }
            }
        assertTrue(cliffs > 10);
    }

    @Test
    void valleysContainLakesAndWaterfallsHaveOneSourceAndFallingWaterBelow() {
        var features = new ClassicTerrainFeatures(42, 28, (x, z) -> 40, (x, z) -> false);
        var regions = new ClassicRegionSampler(42);
        var valley = find(regions, BiomeType.HIDDEN_VALLEYS_CLASSIC);
        boolean lake = false;
        for (int z = (int) valley.z() - 64; z <= valley.z() + 64; z += 8)
            for (int x = (int) valley.x() - 64; x <= valley.x() + 64; x += 8) {
                var sample = features.apply(x, z, base(40), 1);
                lake |= sample.waterKind() == WaterKind.LAKE && sample.flooded();
            }
        assertTrue(lake, "A hidden valley must contain a lake basin");
        for (var type : List.of(
                BiomeType.FLOATING_ISLES_CLASSIC, BiomeType.OVERHANG_HIGHLANDS_CLASSIC, BiomeType.ARCHLANDS_CLASSIC)) {
            var region = find(regions, type);
            for (int z = (int) region.z() - 160; z <= region.z() + 160; z++)
                for (int x = (int) region.x() - 160; x <= region.x() + 160; x++) {
                    var sample = features.apply(x, z, base(40), 1);
                    var shape = sample.shape();
                    if (shape == null) continue;
                    for (int y = shape.maxY(); y > shape.minY(); y--)
                        if (shape.waterState(y) == WaterState.FALLING) {
                            int top = y + 1;
                            assertEquals(WaterState.SOURCE, shape.waterState(top));
                            int ox = Math.floorDiv(x, 16) * 16, oz = Math.floorDiv(z, 16) * 16;
                            int oy = Math.floorDiv(y, 16) * 16;
                            byte[] blocks = new byte[4096];
                            int index = (Math.floorMod(y, 16) * 16 + z - oz) * 16 + x - ox;
                            blocks[index] = BlockId.WATER;
                            var falling = new BitSet(4096);
                            falling.set(index);
                            var chunk = new ChunkData(new ChunkPos(ox / 16, oy / 16, oz / 16), blocks)
                                    .withFallingWater(falling)
                                    .toChunk();
                            assertEquals(WaterState.FALLING, chunk.waterState(x - ox, y - oy, z - oz));
                            System.out.printf("Waterfall %s seed=42 x=%d y=%d z=%d%n", type, x, top, z);
                            return;
                        }
                }
        }
        fail("No waterfall generated at any 3D formation");
    }

    private static TerrainSummary base(int height) {
        return new TerrainSummary(
                height,
                BlockId.GRASS,
                BlockId.DIRT,
                BlockId.STONE,
                WorldBounds.MIN_Y,
                28,
                BiomeType.CLASSIC_PLAINS,
                new ClimateSample(0, 0),
                WaterKind.OCEAN);
    }

    private static int gap(TerrainSummary sample) {
        if (sample.shape() == null) return Integer.MIN_VALUE;
        boolean roof = false;
        for (int y = sample.surfaceHeight(); y > sample.shape().minY() + 5; y--) {
            if (BlockRegistry.DEFAULT.isCollidable(sample.materialAt(y))) roof = true;
            else if (roof && sample.materialAt(y) == BlockId.AIR && sample.materialAt(y - 3) == BlockId.AIR)
                return y - 2;
        }
        return Integer.MIN_VALUE;
    }

    private static ClassicRegionSampler.Region find(ClassicRegionSampler sampler, BiomeType type) {
        for (int z = -16; z <= 16; z++)
            for (int x = -16; x <= 16; x++) {
                var region = sampler.sample(x * 1024 + 512, z * 1024 + 512);
                if (region != null && region.biome() == type) return region;
            }
        throw new AssertionError("No region for " + type);
    }
}
