package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class JungleGenerationTest {
    @Test
    void naturalJungleContainsRootedDirtAndCocoaWithValidSupport() {
        var source = new TerrainGenerator(42, TerrainConfig.defaults());
        var site = JungleSites.find(source, false);
        var world = new World();
        int roots = 0;
        for (int z = -4; z < 4; z++)
            for (int x = -4; x < 4; x++)
                for (int y = 1; y <= 8; y++) {
                    var pos = new ChunkPos(Math.floorDiv(site.x(), 16) + x, y, Math.floorDiv(site.z(), 16) + z);
                    var data = source.generate(pos);
                    for (byte b : data.blocks()) if (b == BlockId.ROOTED_DIRT) roots++;
                    world.putChunk(pos, Chunk.fromBlocks(data.blocks()));
                }
        int cocoa = 0;
        for (int z = site.z() - 48; z < site.z() + 48; z++)
            for (int x = site.x() - 48; x < site.x() + 48; x++)
                for (int y = 16; y < 128; y++) {
                    var pos = new BlockPos(x, y, z);
                    int id = world.getBlock(pos).orElse(0);
                    if (id >= Byte.toUnsignedInt(BlockId.COCOA_EAST) && id <= Byte.toUnsignedInt(BlockId.COCOA_NORTH)) {
                        assertTrue(
                                PlantRules.canPlace(world, pos, (byte) id),
                                "Generated cocoa must attach to Jungle wood");
                        cocoa++;
                    }
                }
        assertTrue(roots > 0 && roots < 16384 / 10, "Rooted dirt should remain a minor ground detail: " + roots);
        assertTrue(cocoa > 0, "Natural Jungle must actually generate cocoa");
        System.out.printf("Natural Jungle 128x128: rootedDirt=%d cocoaInInner96x96=%d%n", roots, cocoa);
    }

    @Test
    void naturalJungleRemainsDeterministicAcrossParallelSectionsAndNegativeCoordinates() throws Exception {
        var source = new TerrainGenerator(42, TerrainConfig.defaults());
        var site = JungleSites.find(source, false);
        System.out.println("Jungle seed=42 x=" + site.x() + " z=" + site.z());
        var positions = new ArrayList<ChunkPos>();
        for (int z = -1; z <= 0; z++)
            for (int x = -1; x <= 0; x++)
                for (int y = 2; y <= 5; y++)
                    positions.add(new ChunkPos(Math.floorDiv(site.x(), 16) + x, y, Math.floorDiv(site.z(), 16) + z));
        var expected = positions.stream().map(p -> source.generate(p).blocks()).toList();
        var fresh = new TerrainGenerator(42, TerrainConfig.defaults());
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<byte[]>>();
            for (int i = positions.size() - 1; i >= 0; i--) {
                var p = positions.get(i);
                futures.add(executor.submit(() -> fresh.generate(p).blocks()));
            }
            for (int i = 0; i < futures.size(); i++)
                assertArrayEquals(
                        expected.get(expected.size() - 1 - i), futures.get(i).get(30, TimeUnit.SECONDS));
        }
        int leaves = 0, logs = 0;
        for (var blocks : expected)
            for (byte b : blocks) {
                if (b == BlockId.JUNGLE_LEAVES) leaves++;
                if (b == BlockId.JUNGLE_LOG) logs++;
            }
        assertTrue(leaves > 100 && logs > 20);
        assertEquals(
                source.canopies(site.x() - 32, site.z() - 32, 64), fresh.canopies(site.x() - 32, site.z() - 32, 64));
        var world = new World();
        for (int i = 0; i < positions.size(); i++) world.putChunk(positions.get(i), Chunk.fromBlocks(expected.get(i)));
        int largest = JungleWalkability.largest(world, source, site.x() - 16, site.z() - 16, 32);
        System.out.println("largestConnectedWalkable=" + largest + " area=1024");
        assertTrue(largest > 300, "Jungle must retain connected space with feet and head clearance");
    }
}
