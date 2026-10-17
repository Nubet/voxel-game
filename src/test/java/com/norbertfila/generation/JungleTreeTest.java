package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.TreeType;
import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class JungleTreeTest {
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
