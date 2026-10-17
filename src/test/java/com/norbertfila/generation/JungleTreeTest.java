package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.TreeType;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class JungleTreeTest {
    @Test
    void mahoganyRegionsChangeSpeciesWithoutChangingGroundHeight() {
        var jungle = new JungleVegetation(42);
        int lowX = 0, lowZ = 0, highX = 0, highZ = 0;
        double low = 1, high = 0;
        for (int z = -1024; z <= 1024; z += 64)
            for (int x = -1024; x <= 1024; x += 64) {
                double value = jungle.mahogany(x, z);
                if (value < low) {
                    low = value;
                    lowX = x;
                    lowZ = z;
                }
                if (value > high) {
                    high = value;
                    highX = x;
                    highZ = z;
                }
            }
        assertEquals(0, low);
        assertEquals(1, high);
        GroundSampler ground = (x, z) -> new TerrainSummary(
                32,
                BlockId.GRASS,
                BlockId.DIRT,
                BlockId.STONE,
                TerrainSummary.NO_BEDROCK,
                TerrainSummary.NO_WATER,
                com.norbertfila.generation.biome.BiomeType.JUNGLE,
                new com.norbertfila.generation.biome.ClimateSample(.6f, .6f));
        var generator =
                new TreeGenerator(42, (x, z) -> 1, ground, com.norbertfila.generation.biome.BiomeRegistry.defaults());
        var ordinary = generator.trees(lowX, lowZ);
        var mahogany = generator.trees(highX, highZ);
        assertTrue(mahogany.stream().filter(t -> t.type() == TreeType.MAHOGANY).count()
                > ordinary.stream().filter(t -> t.type() == TreeType.MAHOGANY).count());
        assertTrue(mahogany.stream().allMatch(t -> t.baseY() == 33));
        assertTrue(mahogany.stream()
                .filter(t -> t.type() == TreeType.MAHOGANY)
                .allMatch(t -> t.plan().log() == BlockId.MAHOGANY_LOG));
    }

    @Test
    void plansHaveCorrectHeightsAndConnectedWood() {
        for (var type : List.of(TreeType.JUNGLE, TreeType.GIANT_JUNGLE, TreeType.MAHOGANY))
            for (int seed = 0; seed < 30; seed++) {
                var plan = JungleTrees.plan(15, 33, -1, seed, type);
                int height = plan.maxY() - 32;
                assertTrue(height >= (type == TreeType.JUNGLE ? 7 : type == TreeType.GIANT_JUNGLE ? 18 : 12));
                assertTrue(height <= (type == TreeType.JUNGLE ? 12 : type == TreeType.GIANT_JUNGLE ? 32 : 22));
                var wood = new HashSet<BlockPos>();
                plan.wood((x, y, z, b) -> wood.add(new BlockPos(x, y, z)));
                var connected = new HashSet<BlockPos>();
                var queue = new ArrayDeque<BlockPos>();
                queue.add(new BlockPos(15, 33, -1));
                while (!queue.isEmpty()) {
                    var p = queue.remove();
                    if (!wood.contains(p) || !connected.add(p)) continue;
                    for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}})
                        queue.add(new BlockPos(p.x() + d[0], p.y() + d[1], p.z() + d[2]));
                }
                assertEquals(wood, connected);
            }
    }

    @Test
    void verticalSectionsRemainDeterministicAndContainGiantCrowns() {
        var scene = new JungleScene(false);
        int leaves = 0;
        for (int y = 6; y >= 2; y--) {
            var p = new ChunkPos(0, y, 0);
            var a = scene.generate(p);
            var b = scene.generate(p);
            assertArrayEquals(a.blocks(), b.blocks());
            for (byte block : a.blocks()) if (block == BlockId.JUNGLE_LEAVES) leaves++;
        }
        assertTrue(leaves > 100);
    }
}
