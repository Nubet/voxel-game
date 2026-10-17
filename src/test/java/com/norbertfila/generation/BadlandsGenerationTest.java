package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.BiomeType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class BadlandsGenerationTest {
    @Test
    void cliffFixtureExposesAllSevenTerracottasAboveTheRedSandFloor() {
        var scene = new BadlandsCliffScene();
        var materials = new HashSet<Byte>();
        var cliff = scene.sample(-1, 0);
        for (int y = 49; y <= 80; y++) materials.add(cliff.materialAt(y));
        assertEquals(
                Set.of(
                        BlockId.TERRACOTTA,
                        BlockId.TERRACOTTA_ORANGE,
                        BlockId.TERRACOTTA_RED,
                        BlockId.TERRACOTTA_YELLOW,
                        BlockId.TERRACOTTA_BROWN,
                        BlockId.TERRACOTTA_WHITE,
                        BlockId.TERRACOTTA_LIGHT_GRAY),
                materials);
        assertEquals(BlockId.RED_SAND, scene.sample(0, 0).materialAt(48));
        assertEquals(BlockId.TERRACOTTA_ORANGE, scene.sample(0, 0).materialAt(47));
    }

    @Test
    void realBadlandsChunksMatchTheirSummariesApartFromCarvedAir() {
        var generator = new TerrainGenerator(42, TerrainConfig.defaults());
        for (int[] site : new int[][] {{-1408, -1440}, {-2016, -1472}}) {
            var center = generator.sample(site[0], site[1]);
            assertEquals(BiomeType.BADLANDS, center.biome());
            assertNotNull(center.badlands());
            assertEquals(site[0] == -1408, center.surfaceMaterial() == BlockId.RED_SAND);
            int compared = 0;
            for (int cy = Math.floorDiv(center.surfaceHeight() - 48, 16);
                    cy <= Math.floorDiv(center.surfaceHeight(), 16);
                    cy++) {
                var position = new ChunkPos(Math.floorDiv(site[0], 16), cy, Math.floorDiv(site[1], 16));
                var chunk = generator.generate(position);
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        var summary = generator.sample(position.x() * 16 + x, position.z() * 16 + z);
                        if (summary.biome() != BiomeType.BADLANDS) continue;
                        for (int y = 0; y < 16; y++) {
                            int worldY = cy * 16 + y;
                            if (worldY > summary.surfaceHeight() || worldY < summary.surfaceHeight() - 48) continue;
                            byte actual = chunk.get(x, y, z);
                            if (actual == BlockId.AIR) continue;
                            assertEquals(
                                    summary.materialAt(worldY),
                                    actual,
                                    () -> "Badlands mismatch in " + position + " at height " + worldY);
                            compared++;
                        }
                    }
            }
            assertTrue(compared > 1000);
        }
    }

    @Test
    void badlandsStayDeterministicWhenSectionsAreGeneratedConcurrentlyOutOfOrder() throws Exception {
        var config = TerrainConfig.defaults();
        var sequential = new TerrainGenerator(42, config);
        var parallel = new TerrainGenerator(42, config);
        var positions = new ArrayList<ChunkPos>();
        for (int x = -127; x <= -125; x++) for (int y = 2; y <= 6; y++) positions.add(new ChunkPos(x, y, -92));
        Collections.shuffle(positions, new Random(42));
        try (var workers = Executors.newFixedThreadPool(4)) {
            List<Future<ChunkData>> futures = new ArrayList<>();
            for (var position : positions) futures.add(workers.submit(() -> parallel.generate(position)));
            for (int i = 0; i < positions.size(); i++)
                assertArrayEquals(
                        sequential.generate(positions.get(i)).blocks(),
                        futures.get(i).get().blocks());
        }
    }
}
