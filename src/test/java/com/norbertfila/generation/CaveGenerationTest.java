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
        int carved = 0, uniform = 0;
        for (int z = -3; z <= 3; z++)
            for (int x = -3; x <= 3; x++) {
                var data = generator.generate(new ChunkPos(x, 0, z));
                if (data.isUniform()) uniform++;
                for (int lz = 0; lz < 16; lz++)
                    for (int lx = 0; lx < 16; lx++) {
                        var base = generator.sample(x * 16 + lx, z * 16 + lz);
                        for (int y = 0; y < 16; y++)
                            if (base.materialAt(y) == BlockId.STONE && data.get(lx, y, lz) == BlockId.AIR) carved++;
                    }
            }
        assertTrue(carved > 100, "Caves must also cut sections formerly returned as uniform stone");
        assertTrue(uniform > 0, "Unaffected deep sections should remain compact");
        assertTrue(
                generator.caveCacheStats().hits() > generator.caveCacheStats().misses());
        assertTrue(generator.caveCacheStats().size() <= 128);
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
