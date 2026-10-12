package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.lod.FarTerrainGenerator;
import com.norbertfila.world.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WaterGenerationTest {
    @ParameterizedTest
    @CsvSource({"15,17", "-18,-15", "17,17", "18,17"})
    void fillsOnlyAboveGroundThroughTheHighestWaterVoxelAcrossSignedBoundaries(int height, int level) {
        TerrainGenerator generator = flat(height, level, -1);
        for (int x : new int[] {-17, -1, 0, 15, 16})
            for (int z : new int[] {-17, -1, 0, 15, 16}) {
                TerrainSummary sample = generator.sample(x, z);
                assertEquals(height, sample.surfaceHeight());
                assertEquals(level, sample.waterLevel());
                assertEquals(height < level, sample.flooded());
                assertEquals(BlockId.SAND, block(generator, x, height, z));
                assertEquals(BlockId.BEDROCK, block(generator, x, height - 1, z));
                assertEquals(BlockId.AIR, block(generator, x, height - 2, z));
                for (int y = height + 1; y <= Math.max(height, level) + 1; y++) {
                    byte expected = y <= level ? BlockId.WATER : BlockId.AIR;
                    assertEquals(expected, block(generator, x, y, z));
                    assertEquals(expected, sample.materialAt(y));
                }
            }
    }

    @Test
    void floodedGroundRejectsTreesWhileDryGrassStillGeneratesThem() {
        TerrainGenerator flooded = flat(15, 17, 1);
        for (int y = 0; y <= 2; y++) {
            byte[] blocks = flooded.generate(new ChunkPos(-1, y, -1)).blocks();
            for (byte value : blocks) assertTrue(value != BlockId.LOG && value != BlockId.LEAVES);
        }
        TerrainGenerator dry = flat(15, 12, 1);
        boolean log = false;
        for (byte value : dry.generate(new ChunkPos(-1, 1, -1)).blocks()) log |= value == BlockId.LOG;
        assertTrue(log);
    }

    @Test
    void groundAndLodSamplesDoNotBecomeTheWaterSurface() {
        TerrainGenerator generator = flat(15, 17, -1);
        var tile = new FarTerrainGenerator(generator, 16, 4, 0, 64).generate(-1, -1);
        for (int z = 0; z < tile.sampleCount(); z++)
            for (int x = 0; x < tile.sampleCount(); x++) {
                assertEquals(15, tile.heightAt(x, z));
                assertEquals(BlockId.SAND, tile.materialAt(x, z));
            }
        TerrainSummary dry = new TerrainSummary(15, BlockId.GRASS, BlockId.DIRT, BlockId.STONE);
        assertFalse(dry.flooded());
        assertEquals(BlockId.AIR, dry.materialAt(16));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainSummary(15, BlockId.WATER, BlockId.DIRT, BlockId.STONE));
    }

    @Test
    void wetReferenceChunksAreDeterministicAcrossOrderAndConcurrency() {
        long[] seeds = {0, 42, -7, 1234};
        ChunkPos[] positions = {
            new ChunkPos(-3, 1, -9), new ChunkPos(-1, 1, -2), new ChunkPos(-2, 1, 5), new ChunkPos(2, 1, 7)
        };
        int[] hashes = {1833740391, 1805324019, 502885598, 2081593966};
        for (int i = 0; i < seeds.length; i++) {
            TerrainGenerator g = new TerrainGenerator(seeds[i], TerrainConfig.defaults());
            byte[] reference = g.generate(positions[i]).blocks();
            assertEquals(hashes[i], Arrays.hashCode(reference));
            boolean water = false;
            for (byte b : reference) water |= b == BlockId.WATER;
            assertTrue(water);
            List<ChunkPos> batch = List.of(positions[i], new ChunkPos(-1, 0, -1), new ChunkPos(0, 2, 0));
            var expected = batch.stream()
                    .map(p -> Arrays.hashCode(g.generate(p).blocks()))
                    .toList();
            assertEquals(
                    expected,
                    batch.parallelStream()
                            .map(p -> Arrays.hashCode(g.generate(p).blocks()))
                            .toList());
            for (int j = batch.size() - 1; j >= 0; j--)
                assertEquals(
                        expected.get(j).intValue(),
                        Arrays.hashCode(g.generate(batch.get(j)).blocks()));
        }
    }

    static TerrainGenerator flat(int height, int level, double forestDensity) {
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 1, .5, 2, 0);
        TerrainConfig base = new TerrainConfig(height, disabled, disabled, disabled);
        TerrainConfig config =
                new TerrainConfig(height, disabled, disabled, disabled, base.mountains(), base.warp(), level);
        return new TerrainGenerator(42, config, (x, z) -> 0, (x, z) -> forestDensity);
    }

    private static byte block(TerrainGenerator g, int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        BlockPos local = Coordinates.worldToLocal(position);
        return g.generate(Coordinates.worldToChunk(position)).get(local.x(), local.y(), local.z());
    }
}
