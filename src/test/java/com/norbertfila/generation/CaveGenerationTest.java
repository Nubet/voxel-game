package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class CaveGenerationTest {
    @Test
    void cavesCutDeepStoneWithoutDiscardingUniformSections() {
        var generator = new TerrainGenerator(42, TerrainConfig.defaults());
        int carved = 0;
        for (int cy = -4; cy <= -2; cy++)
            for (int z = -3; z <= 3; z++)
                for (int x = -3; x <= 3; x++) {
                    var data = generator.generate(new ChunkPos(x, cy, z));
                    for (int lz = 0; lz < 16; lz++)
                        for (int lx = 0; lx < 16; lx++) {
                            var base = generator.sample(x * 16 + lx, z * 16 + lz);
                            for (int y = 0; y < 16; y++)
                                if (base.materialAt(y + cy * 16) == BlockId.STONE && data.get(lx, y, lz) == BlockId.AIR)
                                    carved++;
                        }
                }
        assertTrue(carved > 100, "Caves must also cut sections formerly returned as uniform stone");
        assertTrue(generator.generate(new ChunkPos(0, 15, 0)).isUniform(), "Empty sections should remain compact");
        assertTrue(
                generator.caveCacheStats().hits() > generator.caveCacheStats().misses());
        assertTrue(generator.caveCacheStats().size() <= 128);
    }

    @Test
    void supportedWorldEdgesRemainDeterministicWithoutCoordinateOverflow() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        for (int x : new int[] {Integer.MIN_VALUE / 16, Integer.MAX_VALUE / 16})
            for (int z : new int[] {Integer.MIN_VALUE / 16, Integer.MAX_VALUE / 16}) {
                var position = new ChunkPos(x, 0, z);
                assertArrayEquals(
                        terrain.generate(position).blocks(),
                        new TerrainGenerator(42, TerrainConfig.defaults())
                                .generate(position)
                                .blocks());
            }
    }

    @Test
    void evictedRegionPlansRecomputeWithoutChangingUnseenChunks() {
        var terrain = new TerrainGenerator(42, TerrainConfig.defaults());
        terrain.generate(new ChunkPos(0, 0, 0));
        for (int region = 1; region <= 24; region++) terrain.generate(new ChunkPos(region * 24, 0, region * 24));
        assertEquals(128, terrain.caveCacheStats().size());
        long misses = terrain.caveCacheStats().misses();
        var target = new ChunkPos(1, 0, 0);
        assertArrayEquals(
                new TerrainGenerator(42, TerrainConfig.defaults())
                        .generate(target)
                        .blocks(),
                terrain.generate(target).blocks());
        assertTrue(terrain.caveCacheStats().misses() >= misses + 9);
    }

    @Test
    void generationOrderAndWorkersDoNotChangeTheWorld() throws Exception {
        List<ChunkPos> positions = List.of(
                new ChunkPos(-9, 0, -1),
                new ChunkPos(-8, 1, 0),
                new ChunkPos(-1, -1, 7),
                new ChunkPos(0, 0, 8),
                new ChunkPos(7, 1, -8),
                new ChunkPos(8, 2, -9));
        var sequential = new TerrainGenerator(42, TerrainConfig.defaults());
        var expected =
                positions.stream().map(p -> sequential.generate(p).blocks()).toList();
        var parallel = new TerrainGenerator(42, TerrainConfig.defaults());
        try (var workers = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<ChunkData>>();
            for (int i = positions.size() - 1; i >= 0; i--) {
                var position = positions.get(i);
                futures.add(workers.submit(() -> parallel.generate(position)));
            }
            for (int i = 0; i < futures.size(); i++)
                assertArrayEquals(
                        expected.get(positions.size() - 1 - i),
                        futures.get(i).get().blocks());
        }
    }
}
