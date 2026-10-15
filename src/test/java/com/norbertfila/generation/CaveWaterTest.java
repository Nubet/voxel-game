package com.norbertfila.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.norbertfila.world.BlockId;
import com.norbertfila.world.ChunkPos;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class CaveWaterTest {
    @Test
    void seabedsKeepThreeLayersWhileDrySurfacesCanOpen() {
        var flooded = source((x, z) -> 12);
        var dry = source((x, z) -> 24);
        var segments = List.of(new CavePlan.Segment(8, 12, 8, 8, 8));
        byte[] blocks = base(flooded, new ChunkPos(0, 0, 0));
        new CaveCarver(flooded).carve(new ChunkPos(0, 0, 0), blocks, segments);
        for (int y = 10; y <= 12; y++) assertNotEquals(BlockId.AIR, blocks[(y * 16 + 8) * 16 + 8]);
        assertEquals(BlockId.AIR, blocks[(9 * 16 + 8) * 16 + 8]);
        var dryCarver = new CaveCarver(dry);
        assertTrue(dryCarver.carves(8, 24, 8, BlockId.GRASS, List.of(new CavePlan.Segment(8, 24, 8, 3, 3))));
    }

    @Test
    void waterBeyondHorizontalAndVerticalChunkEdgesCannotBeExposed() {
        for (boolean xAxis : new boolean[] {true, false}) {
            var terrain = source((x, z) -> (xAxis ? x : z) >= 16 ? 8 : 24);
            var carver = new CaveCarver(terrain);
            var segments = List.of(new CavePlan.Segment(15.5, 16, 15.5, 7, 7));
            int carved = 0;
            for (int cy = 0; cy <= 1; cy++)
                for (int cz = 0; cz <= 1; cz++)
                    for (int cx = 0; cx <= 1; cx++) {
                        var position = new ChunkPos(cx, cy, cz);
                        byte[] blocks = base(terrain, position);
                        carver.carve(position, blocks, segments);
                        for (int y = 0; y < 16; y++)
                            for (int z = 0; z < 16; z++)
                                for (int x = 0; x < 16; x++) {
                                    int wx = cx * 16 + x, wy = cy * 16 + y, wz = cz * 16 + z;
                                    byte original = terrain.sample(wx, wz).materialAt(wy);
                                    boolean removed =
                                            original != BlockId.AIR && blocks[(y * 16 + z) * 16 + x] == BlockId.AIR;
                                    assertEquals(carver.carves(wx, wy, wz, original, segments), removed);
                                    if (!removed) continue;
                                    carved++;
                                    for (int[] d : new int[][] {
                                        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
                                    })
                                        assertNotEquals(
                                                BlockId.WATER,
                                                terrain.sample(wx + d[0], wz + d[2])
                                                        .materialAt(wy + d[1]));
                                }
                    }
            assertTrue(carved > 0);
        }
    }

    private static TerrainSource source(IntBinaryOperator height) {
        return new TerrainSource() {
            public long seed() {
                return 0;
            }

            public int generatorVersion() {
                return 1;
            }

            public int surfaceHeight(int x, int z) {
                return height.applyAsInt(x, z);
            }

            public TerrainSummary sample(int x, int z) {
                return new TerrainSummary(surfaceHeight(x, z), BlockId.GRASS, BlockId.DIRT, BlockId.STONE, -32, 20);
            }

            public ChunkData generate(ChunkPos position) {
                return new ChunkData(position, base(this, position));
            }
        };
    }

    private static byte[] base(TerrainSource terrain, ChunkPos position) {
        byte[] blocks = new byte[ChunkData.BLOCK_COUNT];
        for (int z = 0; z < 16; z++)
            for (int x = 0; x < 16; x++) {
                var summary = terrain.sample(position.x() * 16 + x, position.z() * 16 + z);
                for (int y = 0; y < 16; y++) blocks[(y * 16 + z) * 16 + x] = summary.materialAt(position.y() * 16 + y);
            }
        return blocks;
    }
}
