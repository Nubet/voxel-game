package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.lod.FarTerrainGenerator;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import com.norbertfila.world.ChunkPos;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerrainGeneratorTest {
    @Test
    void highMountainSurfaceAndLodShareTheSameGlobalHeight() {
        TerrainGenerator generator = new TerrainGenerator(42, TerrainConfig.defaults());
        int height = generator.surfaceHeight(-272, 240);
        assertTrue(height >= 200);
        assertEquals(BlockId.SNOW, blockAt(generator, -272, height, 240));
        assertEquals(BlockId.AIR, blockAt(generator, -272, height + 1, 240));
        assertEquals(BlockId.DIRT, blockAt(generator, -272, height - 3, 240));
        assertEquals(BlockId.STONE, blockAt(generator, -272, height - 4, 240));
        var tile = new FarTerrainGenerator(generator, 64, 4, 0, 64).generate(-5, 3);
        assertEquals(height, tile.heightAt(12, 12));
    }

    @Test
    void terrainUsesGrassDirtSandAndSnowWithoutGeneratingGravel() {
        TerrainGenerator generator = new TerrainGenerator(42, TerrainConfig.defaults());
        boolean sand = false, dirt = false, grass = false, snow = false;
        for (int z = -1024; z < 1024; z += 16)
            for (int x = -1024; x < 1024; x += 16) {
                TerrainSummary sample = generator.sample(x, z);
                byte material = sample.surfaceMaterial();
                sand |= material == BlockId.SAND;
                grass |= material == BlockId.GRASS;
                snow |= material == BlockId.SNOW;
                dirt |= sample.subsurfaceMaterial() == BlockId.DIRT;
                assertNotEquals(BlockId.GRAVEL, material);
                assertNotEquals(BlockId.GRAVEL, sample.subsurfaceMaterial());
            }
        assertTrue(sand, "sand");
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
        for (int z = -4; z <= 4; z++)
            for (int x = -4; x <= 4; x++)
                for (int y = 0; y <= 4; y++) {
                    byte[] blocks = generator.generate(new ChunkPos(x, y, z)).blocks();
                    for (byte block : blocks) {
                        log |= block == BlockId.LOG;
                        leaves |= block == BlockId.LEAVES;
                    }
                }
        assertTrue(log);
        assertTrue(leaves);
    }

    @Test
    void classicPresetHasVersionSixReferenceChunks() {
        ChunkPos[] positions = {new ChunkPos(0, 2, 0), new ChunkPos(-2, 2, 3), new ChunkPos(-12, 4, 14)};
        int[][] expected = {{2079817867, 322607703, 1742602241}, {-1299730120, -1048288188, -1014507725}};
        long[] seeds = {0, 42};
        for (int i = 0; i < seeds.length; i++) {
            TerrainGenerator generator = new TerrainGenerator(seeds[i], TerrainConfig.classic());
            assertEquals(6, generator.generatorVersion());
            for (int j = 0; j < positions.length; j++)
                assertEquals(
                        expected[i][j],
                        Arrays.hashCode(generator.generate(positions[j]).blocks()));
        }
    }

    @Test
    void samplesBaseTerrainOncePerColumnAndSkipsDisabledLayers() {
        TerrainConfig.Layer enabled = new TerrainConfig.Layer(.01, 1, .5, 2, 1);
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 4, .5, 2, 0);
        int[] calls = {0};
        TerrainGenerator generator =
                new TerrainGenerator(0, new TerrainConfig(8, enabled, disabled, disabled), (x, z) -> {
                    calls[0]++;
                    return -.25;
                });
        ChunkData data = generator.generate(new ChunkPos(-1, 0, 1));
        assertEquals(265, calls[0]);
        assertEquals(BlockId.AIR, data.get(0, 8, 0));
        assertEquals(BlockId.SAND, data.get(0, 7, 0));
        assertEquals(BlockId.BEDROCK, data.get(0, 6, 0));
        assertEquals(BlockId.AIR, data.get(0, 4, 0));
        assertEquals(BlockId.AIR, data.get(0, 3, 0));
    }

    @Test
    void voxelsAgreeWithGlobalSamplesAcrossHorizontalAndVerticalBoundaries() {
        TerrainGenerator generator = new TerrainGenerator(42, TerrainConfig.defaults());
        for (int chunkZ : new int[] {-2, -1, 0, 1})
            for (int chunkX : new int[] {-2, -1, 0, 1}) {
                for (int chunkY : new int[] {-2, -1, 0, 1, 2, 3, 4}) {
                    ChunkData data = generator.generate(new ChunkPos(chunkX, chunkY, chunkZ));
                    for (int z = 0; z < Chunk.SIZE; z++)
                        for (int x = 0; x < Chunk.SIZE; x++) {
                            TerrainSummary sample = generator.sample(chunkX * Chunk.SIZE + x, chunkZ * Chunk.SIZE + z);
                            for (int y = 0; y < Chunk.SIZE; y++) {
                                int worldY = chunkY * Chunk.SIZE + y;
                                byte actual = data.get(x, y, z);
                                byte expected = sample.materialAt(worldY);
                                assertTrue(actual == expected
                                        || expected == BlockId.AIR
                                                && (actual == BlockId.LOG || actual == BlockId.LEAVES));
                            }
                        }
                }
            }
        var tile = new FarTerrainGenerator(generator, 64, 4, 0, 64).generate(-1, 0);
        for (int z = 0; z <= 64; z += 4)
            for (int x = 0; x <= 64; x += 4)
                assertEquals(generator.surfaceHeight(-64 + x, z), tile.heightAt(x / 4, z / 4));
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
        assertEquals(6, generator.generatorVersion());
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

    private static byte blockAt(TerrainGenerator generator, int x, int y, int z) {
        return generator
                .generate(new ChunkPos(
                        Math.floorDiv(x, Chunk.SIZE), Math.floorDiv(y, Chunk.SIZE), Math.floorDiv(z, Chunk.SIZE)))
                .get(Math.floorMod(x, Chunk.SIZE), Math.floorMod(y, Chunk.SIZE), Math.floorMod(z, Chunk.SIZE));
    }
}
