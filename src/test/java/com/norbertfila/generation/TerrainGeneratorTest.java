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
    void samplesHeightOncePerColumnAndSkipsDisabledLayers() {
        TerrainConfig.Layer enabled = new TerrainConfig.Layer(.01, 1, .5, 2, 1);
        TerrainConfig.Layer disabled = new TerrainConfig.Layer(.01, 4, .5, 2, 0);
        int[] calls = {0};
        TerrainGenerator generator =
                new TerrainGenerator(0, new TerrainConfig(8, enabled, disabled, disabled), (x, z) -> {
                    calls[0]++;
                    return -.25;
                });
        ChunkData data = generator.generate(new ChunkPos(-1, 0, 1));
        assertEquals(256, calls[0]);
        assertEquals(BlockId.AIR, data.get(0, 8, 0));
        assertEquals(BlockId.GRASS, data.get(0, 7, 0));
        assertEquals(BlockId.DIRT, data.get(0, 4, 0));
        assertEquals(BlockId.STONE, data.get(0, 3, 0));
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
                            for (int y = 0; y < Chunk.SIZE; y++)
                                assertEquals(sample.materialAt(chunkY * Chunk.SIZE + y), data.get(x, y, z));
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
        assertEquals(3, generator.generatorVersion());
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
}
