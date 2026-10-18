package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.generation.biome.TreeType;
import com.norbertfila.world.BlockId;
import com.norbertfila.world.Chunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassicTreeCrownTest {
    @Test
    void classicOakAndBirchHaveSolidSquareLowerLayersAndSmallCrossShapedTops() {
        for (var type : List.of(TreeType.BROADLEAF, TreeType.BIRCH))
            for (long seed = 0; seed < 16; seed++) {
                var tree = new TreeGenerator.Tree(8, 8, 1, 5, 2, seed, type, null, true);
                byte leaf = type == TreeType.BIRCH ? BlockId.BIRCH_LEAVES : BlockId.LEAVES;
                byte log = type == TreeType.BIRCH ? BlockId.BIRCH_LOG : BlockId.LOG;
                byte[] blocks = new byte[Chunk.BLOCK_BYTES];
                TreeGenerator.place(List.of(tree), 0, 0, 0, blocks);
                for (int layer = 0; layer < 4; layer++) {
                    int y = 3 + layer, radius = layer < 2 ? 2 : 1;
                    for (int z = -3; z <= 3; z++)
                        for (int x = -3; x <= 3; x++) {
                            byte block = blocks[(y * 16 + z + 8) * 16 + x + 8];
                            if (Math.abs(x) > radius || Math.abs(z) > radius) assertEquals(BlockId.AIR, block);
                            else if (x == 0 && z == 0 && layer < 3) assertEquals(log, block);
                            else if (Math.abs(x) == radius && Math.abs(z) == radius) {
                                if (layer == 3) assertEquals(BlockId.AIR, block);
                                else assertTrue(block == BlockId.AIR || block == leaf);
                            } else assertEquals(leaf, block);
                        }
                }
                assertEquals(6, tree.maxY());
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        long top = Long.MIN_VALUE, highestLog = Long.MIN_VALUE;
                        for (int y = 0; y < 16; y++) {
                            byte block = blocks[(y * 16 + z) * 16 + x];
                            if (block == leaf) top = y;
                            if (block == log) highestLog = y;
                        }
                        assertEquals(
                                top > highestLog ? top : Long.MIN_VALUE,
                                TreeGenerator.canopyHeight(List.of(tree), x, z));
                    }
            }
    }

    @Test
    void classicCrownMatchesAcrossHorizontalAndVerticalChunkSeams() {
        var tree = new TreeGenerator.Tree(15, 15, 14, 5, 2, 42, TreeType.BROADLEAF, null, true);
        byte[] reference = new byte[Chunk.BLOCK_BYTES];
        TreeGenerator.place(List.of(tree), 7, 13, 7, reference);
        byte[][] sections = new byte[8][Chunk.BLOCK_BYTES];
        for (int y = 0; y < 2; y++)
            for (int z = 0; z < 2; z++)
                for (int x = 0; x < 2; x++)
                    TreeGenerator.place(List.of(tree), x * 16, y * 16, z * 16, sections[(y * 2 + z) * 2 + x]);
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int wx = x + 7, wy = y + 13, wz = z + 7;
                    byte[] section = sections[((wy / 16) * 2 + wz / 16) * 2 + wx / 16];
                    assertEquals(reference[(y * 16 + z) * 16 + x], section[((wy % 16) * 16 + wz % 16) * 16 + wx % 16]);
                }
    }
}
