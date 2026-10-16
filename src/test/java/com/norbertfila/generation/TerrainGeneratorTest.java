package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.lod.AreaEdits;
import com.norbertfila.lod.FarTerrainGenerator;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerrainGeneratorTest {
    @Test
    void highMountainSurfaceAndLodShareTheSameGlobalHeight() {
        TerrainGenerator generator = new TerrainGenerator(42, TerrainConfig.defaults());
        int wx = 0, wz = 0;
        search:
        for (int z = -2048; z <= 2048; z += 16)
            for (int x = -2048; x <= 2048; x += 16)
                if (generator.surfaceHeight(x, z) >= 200) {
                    wx = x;
                    wz = z;
                    break search;
                }
        int height = generator.surfaceHeight(wx, wz);
        assertTrue(height >= 200);
        assertEquals(generator.sample(wx, wz).surfaceMaterial(), blockAt(generator, wx, height, wz));
        assertEquals(BlockId.AIR, blockAt(generator, wx, height + 1, wz));
        assertEquals(generator.sample(wx, wz).subsurfaceMaterial(), blockAt(generator, wx, height - 3, wz));
        assertEquals(BlockId.STONE, blockAt(generator, wx, height - 4, wz));
        int tx = Math.floorDiv(wx, 64), tz = Math.floorDiv(wz, 64);
        var tile = new FarTerrainGenerator(generator).generate(tx, tz, 1, AreaEdits.none(tx * 64, tz * 64, 64));
        assertEquals(height, tile.heightAt(Math.floorMod(wx, 64), Math.floorMod(wz, 64)));
    }

    @Test
    void terrainUsesGrassDirtSandGravelAndSnow() {
        TerrainGenerator generator = new TerrainGenerator(42, TerrainConfig.defaults());
        boolean sand = false, gravel = false, dirt = false, grass = false, stone = false, snow = false;
        for (int z = -1024; z < 1024; z += 16)
            for (int x = -1024; x < 1024; x += 16) {
                TerrainSummary sample = generator.sample(x, z);
                byte material = sample.surfaceMaterial();
                sand |= material == BlockId.SAND;
                gravel |= material == BlockId.GRAVEL;
                stone |= material == BlockId.STONE;
                grass |= material == BlockId.GRASS;
                snow |= material == BlockId.SNOW;
                dirt |= sample.subsurfaceMaterial() == BlockId.DIRT;
                if (material == BlockId.SAND) assertTrue(sample.surfaceHeight() <= sample.waterLevel() + 2);
            }
        assertTrue(sand, "sand");
        assertTrue(gravel, "gravel");
        assertTrue(stone, "stone");
        assertTrue(grass, "grass");
        assertTrue(dirt, "dirt");
        assertTrue(snow, "snow");
        var column = generator.sample(-272, 240);
        assertEquals(BlockId.BEDROCK, column.materialAt(column.bedrockHeight()));
        assertEquals(BlockId.AIR, column.materialAt(column.bedrockHeight() - 1));
    }

    @Test
    void deterministicTreesUseLogAndLeafBlocksAcrossChunkGeneration() {
        TerrainGenerator generator = new TerrainGenerator(0, TerrainConfig.defaults());
        boolean log = false, leaves = false;
        search:
        for (int z = -32; z <= 32; z += 4)
            for (int x = -32; x <= 32; x += 4) {
                int surfaceSection = Math.floorDiv(generator.surfaceHeight(x * Chunk.SIZE, z * Chunk.SIZE), Chunk.SIZE);
                for (int y = surfaceSection - 1; y <= surfaceSection + 2; y++) {
                    byte[] blocks = generator.generate(new ChunkPos(x, y, z)).blocks();
                    for (byte block : blocks) {
                        log |= block == BlockId.LOG;
                        leaves |= block == BlockId.LEAVES;
                    }
                    if (log && leaves) break search;
                }
            }
        assertTrue(log);
        assertTrue(leaves);
    }

    @Test
    void classicTerrainAndDecorationsAreDeterministic() {
        ChunkPos[] positions = {new ChunkPos(0, 2, 0), new ChunkPos(-2, 2, 3), new ChunkPos(-12, 4, 14)};
        long[] seeds = {0, 42};
        for (long seed : seeds) {
            TerrainGenerator generator = new TerrainGenerator(seed, TerrainConfig.classic());
            assertEquals(20, generator.generatorVersion());
            for (ChunkPos position : positions) {
                byte[] blocks = generator.generate(position).blocks();
                byte[] repeat = generator.generate(position).blocks();
                for (int k = 0; k < blocks.length; k++)
                    if (com.norbertfila.world.PlantRules.isPlant(blocks[k])) blocks[k] = BlockId.AIR;
                for (int k = 0; k < repeat.length; k++)
                    if (com.norbertfila.world.PlantRules.isPlant(repeat[k])) repeat[k] = BlockId.AIR;
                assertArrayEquals(blocks, repeat);
            }
        }
    }

    @Test
    void samplesBaseTerrainOncePerColumnAndSkipsDisabledLayers() {
        TerrainConfig.Layer enabled = new TerrainConfig.Layer(.01, 1, .5, 2, 1);
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 4, .5, 2, 0);
        int[] calls = {0};
        TerrainGenerator generator = new TerrainGenerator(
                0,
                new TerrainConfig(8, enabled, disabled, disabled),
                (x, z) -> {
                    calls[0]++;
                    return -.25;
                },
                (x, z) -> -1);
        ChunkData data = generator.generate(new ChunkPos(-1, 0, 1));
        assertTrue(calls[0] >= 256);
        int sampled = calls[0];
        generator.generate(new ChunkPos(-1, -1, 1));
        generator.generate(new ChunkPos(-1, 1, 1));
        assertEquals(sampled, calls[0]);
        assertTrue(data.get(0, 8, 0) == BlockId.AIR || com.norbertfila.world.PlantRules.isPlant(data.get(0, 8, 0)));
        assertEquals(BlockId.GRASS, data.get(0, 7, 0));
        assertEquals(BlockId.BEDROCK, data.get(0, 6, 0));
        assertEquals(BlockId.AIR, data.get(0, 4, 0));
        assertEquals(BlockId.AIR, data.get(0, 3, 0));
    }

    @Test
    void voxelsAgreeWithBaseSamplesAndPointCarvingAcrossAllChunkBoundaries() {
        TerrainGenerator generator = new TerrainGenerator(42, TerrainConfig.defaults());
        var carver = new CaveCarver(generator);
        for (int chunkZ : new int[] {-2, -1, 0, 1})
            for (int chunkX : new int[] {-2, -1, 0, 1}) {
                var segments = plannedSegments(generator, chunkX, chunkZ);
                for (int chunkY : new int[] {-2, -1, 0, 1, 2, 3, 4}) {
                    ChunkData data = generator.generate(new ChunkPos(chunkX, chunkY, chunkZ));
                    for (int z = 0; z < Chunk.SIZE; z++)
                        for (int x = 0; x < Chunk.SIZE; x++) {
                            TerrainSummary sample = generator.sample(chunkX * Chunk.SIZE + x, chunkZ * Chunk.SIZE + z);
                            for (int y = 0; y < Chunk.SIZE; y++) {
                                int worldY = chunkY * Chunk.SIZE + y;
                                byte actual = data.get(x, y, z);
                                byte expected = sample.materialAt(worldY);
                                if (carver.carves(chunkX * 16 + x, worldY, chunkZ * 16 + z, expected, segments))
                                    expected = BlockId.AIR;
                                assertTrue(actual == expected
                                        || expected == BlockId.AIR
                                                && (actual == BlockId.LOG
                                                        || actual == BlockId.LEAVES
                                                        || com.norbertfila.world.PlantRules.isPlant(actual)));
                            }
                        }
                }
            }
        var tile = new FarTerrainGenerator(generator).generate(-1, 0, 4, AreaEdits.none(-64, 0, 64));
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++)
                assertEquals(generator.surfaceHeight(-62 + x * 4, z * 4 + 2), tile.heightAt(x, z));
    }

    @Test
    void sameInstanceCanGenerateInDifferentOrdersAndConcurrently() {
        TerrainGenerator generator = new TerrainGenerator(-7, TerrainConfig.defaults());
        List<ChunkPos> positions = List.of(new ChunkPos(-17, 2, 31), new ChunkPos(4, -1, -9), new ChunkPos(0, 1, 0));
        List<Integer> expected = positions.stream()
                .map(p -> Arrays.hashCode(generator.generate(p).blocks()))
                .toList();
        assertEquals(
                expected,
                positions.parallelStream()
                        .map(p -> Arrays.hashCode(generator.generate(p).blocks()))
                        .toList());
        TerrainGenerator same = new TerrainGenerator(-7, TerrainConfig.defaults());
        for (int i = positions.size() - 1; i >= 0; i--)
            assertEquals(
                    expected.get(i).intValue(),
                    Arrays.hashCode(same.generate(positions.get(i)).blocks()));
        assertEquals(20, generator.generatorVersion());
    }

    @Test
    void rejectsOverflowingPositionsAndInvalidNoiseOutput() {
        TerrainGenerator generator = new TerrainGenerator(0, TerrainConfig.defaults());
        for (ChunkPos invalid : List.of(
                new ChunkPos(Integer.MAX_VALUE, 0, 0),
                new ChunkPos(0, Integer.MIN_VALUE, 0),
                new ChunkPos(0, 0, Integer.MAX_VALUE)))
            assertThrows(ArithmeticException.class, () -> generator.generate(invalid));
        assertTrue(Double.isFinite(generator.surfaceHeight(Integer.MIN_VALUE, Integer.MAX_VALUE)));
        assertThrows(
                IllegalStateException.class,
                () -> new TerrainGenerator(0, TerrainConfig.defaults(), (x, z) -> Double.NaN).surfaceHeight(0, 0));
        assertThrows(
                IllegalStateException.class,
                () -> new TerrainGenerator(0, TerrainConfig.defaults(), (x, z) -> 2).surfaceHeight(0, 0));
    }

    private static List<CavePlan.Segment> plannedSegments(TerrainGenerator generator, int chunkX, int chunkZ) {
        var planner =
                new CavePlanner(generator.seed(), TerrainConfig.defaults().minHeight() - 1, generator::surfaceHeight);
        var segments = new ArrayList<CavePlan.Segment>();
        int rx = Math.floorDiv(chunkX, 8), rz = Math.floorDiv(chunkZ, 8);
        for (int z = rz - 1; z <= rz + 1; z++)
            for (int x = rx - 1; x <= rx + 1; x++)
                segments.addAll(planner.plan(x, z).segments(chunkX, chunkZ));
        return segments;
    }

    private static byte blockAt(TerrainGenerator generator, int x, int y, int z) {
        return generator
                .generate(new ChunkPos(
                        Math.floorDiv(x, Chunk.SIZE), Math.floorDiv(y, Chunk.SIZE), Math.floorDiv(z, Chunk.SIZE)))
                .get(Math.floorMod(x, Chunk.SIZE), Math.floorMod(y, Chunk.SIZE), Math.floorMod(z, Chunk.SIZE));
    }
}
