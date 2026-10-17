package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class JungleVegetationTest {
    @Test
    void fruitClustersAndFlowersStayRareWhileShortGrassIsAbundant() {
        var scene = new JungleScene(false);
        var jungle = new JungleVegetation(42);
        var vegetation = new VegetationGenerator(42, (x, z) -> .5, (x, z) -> .5);
        int[] counts = new int[256];
        Map<BlockPos, Integer> fruitCells = new HashMap<>();
        for (int cz = -8; cz < 8; cz++)
            for (int cx = -8; cx < 8; cx++) {
                var summaries = new TerrainSummary[256];
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) summaries[z * 16 + x] = scene.sample(cx * 16 + x, cz * 16 + z);
                var column = TerrainColumn.of(summaries, List.of());
                byte[] blocks = new byte[4096];
                JungleDecorations.ground(42, cx * 16, 32, cz * 16, blocks, column, jungle);
                vegetation.generate(cx * 16, 32, cz * 16, blocks, column);
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        byte block = blocks[(16 + z) * 16 + x];
                        counts[Byte.toUnsignedInt(block)]++;
                        if (block == BlockId.MELON || block == BlockId.PUMPKIN)
                            fruitCells.merge(
                                    new BlockPos(Math.floorDiv(cx * 16 + x, 32), 0, Math.floorDiv(cz * 16 + z, 32)),
                                    1,
                                    Integer::sum);
                    }
            }
        int flowers = counts[Byte.toUnsignedInt(BlockId.POPPY)]
                + counts[Byte.toUnsignedInt(BlockId.DANDELION_YELLOW)]
                + counts[Byte.toUnsignedInt(BlockId.DANDELION_WHITE)];
        int grass = counts[Byte.toUnsignedInt(BlockId.SHORT_GRASS)];
        int melons = counts[Byte.toUnsignedInt(BlockId.MELON)], pumpkins = counts[Byte.toUnsignedInt(BlockId.PUMPKIN)];
        assertTrue(melons > 0 && pumpkins > 0);
        assertTrue(melons + pumpkins < 64, "Fruit blocks should cover less than 0.1% of ground");
        assertTrue(fruitCells.values().stream().allMatch(n -> n <= 3));
        assertTrue(fruitCells.values().stream().anyMatch(n -> n > 1));
        assertTrue(flowers > 0 && flowers < 130, "Flowers should cover less than 0.2% of ground");
        assertTrue(grass > 5242 && grass < 16384, "Short grass should cover 8–25% of unobstructed ground");
        System.out.printf(
                "Jungle 256x256: shortGrass=%d flowers=%d melons=%d pumpkins=%d%n", grass, flowers, melons, pumpkins);
    }

    @Test
    void bushPatchesLeaveConnectedOpenGround() {
        var ground = new JungleScene(false);
        var jungle = new JungleVegetation(42);
        Set<BlockPos> obstacles = new HashSet<>();
        int dense = 0, open = 0;
        for (int z = -96; z < 96; z++)
            for (int x = -96; x < 96; x++) {
                if (jungle.understory(x, z) > .1) dense++;
                if (jungle.understory(x, z) < 0) open++;
            }
        assertTrue(dense > 1000 && open > 1000);
        for (int cz = -2; cz < 2; cz++)
            for (int cx = -2; cx < 2; cx++) {
                byte[] blocks = new byte[4096];
                jungle.generate(cx * 16, 32, cz * 16, blocks, ground::sample, (x, z) -> true);
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (blocks[(1 * 16 + z) * 16 + x] != BlockId.AIR
                                || blocks[(2 * 16 + z) * 16 + x] != BlockId.AIR)
                            obstacles.add(new BlockPos(cx * 16 + x, 0, cz * 16 + z));
            }
        assertFalse(obstacles.isEmpty());
        int largest = 0;
        Set<BlockPos> visited = new HashSet<>();
        for (int z = -31; z < 31; z++)
            for (int x = -31; x < 31; x++) {
                var queue = new ArrayDeque<BlockPos>();
                queue.add(new BlockPos(x, 0, z));
                int size = 0;
                while (!queue.isEmpty()) {
                    var p = queue.remove();
                    if (Math.abs(p.x()) > 30 || Math.abs(p.z()) > 30 || obstacles.contains(p) || !visited.add(p))
                        continue;
                    size++;
                    queue.add(new BlockPos(p.x() + 1, 0, p.z()));
                    queue.add(new BlockPos(p.x() - 1, 0, p.z()));
                    queue.add(new BlockPos(p.x(), 0, p.z() + 1));
                    queue.add(new BlockPos(p.x(), 0, p.z() - 1));
                }
                largest = Math.max(largest, size);
            }
        assertTrue(largest > 2000, "Understory must retain a connected walkable area");
    }
}
